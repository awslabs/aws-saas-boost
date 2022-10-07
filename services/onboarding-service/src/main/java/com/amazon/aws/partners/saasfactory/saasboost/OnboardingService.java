/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codepipeline.model.Tag;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.EcrException;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class OnboardingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SYSTEM_API_CALL_DETAIL_TYPE = "System API Call";
    private static final String EVENT_SOURCE = "saas-boost";
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String ONBOARDING_TABLE = System.getenv("ONBOARDING_TABLE");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private static final String SAAS_BOOST_BUCKET = System.getenv("SAAS_BOOST_BUCKET");
    private static final String ONBOARDING_STACK_SNS = System.getenv("ONBOARDING_STACK_SNS");
    private static final String ONBOARDING_APP_STACK_SNS = System.getenv("ONBOARDING_APP_STACK_SNS");
    private static final String ONBOARDING_VALIDATION_QUEUE = System.getenv("ONBOARDING_VALIDATION_QUEUE");
    private static final String ONBOARDING_VALIDATION_DLQ = System.getenv("ONBOARDING_VALIDATION_DLQ");
    private static final String RESOURCES_BUCKET = System.getenv("RESOURCES_BUCKET");
    private static final String TENANT_CONFIG_DLQ = System.getenv("TENANT_CONFIG_DLQ");
    private static final String RESOURCES_BUCKET_TEMP_FOLDER = "00temp/";
    private final OnboardingServiceDAL dal;
    private final CloudFormationClient cfn;
    private final EventBridgeClient eventBridge;
    private final EcrClient ecr;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final Route53Client route53;
    private final SqsClient sqs;
    private final ElasticLoadBalancingV2Client elb;
    private final CodePipelineClient codePipeline;

    public OnboardingService() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new OnboardingServiceDAL();
        this.cfn = Utils.sdkClient(CloudFormationClient.builder(), CloudFormationClient.SERVICE_NAME);
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        this.ecr = Utils.sdkClient(EcrClient.builder(), EcrClient.SERVICE_NAME);
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        try {
            this.presigner = S3Presigner.builder()
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .region(Region.of(System.getenv("AWS_REGION")))
                    .endpointOverride(new URI("https://" + s3.serviceName() + "."
                            + Region.of(System.getenv("AWS_REGION"))
                            + ".amazonaws.com")
                    ) // will break in China regions
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.route53 = Utils.sdkClient(Route53Client.builder(), Route53Client.SERVICE_NAME);
        this.sqs = Utils.sdkClient(SqsClient.builder(), SqsClient.SERVICE_NAME);
        this.elb = Utils.sdkClient(ElasticLoadBalancingV2Client.builder(), ElasticLoadBalancingV2Client.SERVICE_NAME);
        this.codePipeline = Utils.sdkClient(CodePipelineClient.builder(), CodePipelineClient.SERVICE_NAME);
    }

    /**
     * Get an onboarding record by id. Integration for GET /onboarding/{id} endpoint.
     * @param event API Gateway proxy request event containing an id path parameter
     * @param context
     * @return Onboarding object for id or HTTP 404 if not found
     */
    public APIGatewayProxyResponseEvent getOnboarding(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = (Map<String, String>) event.get("pathParameters");
        String onboardingId = params.get("id");
        Onboarding onboarding = dal.getOnboarding(onboardingId);
        if (onboarding != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(200)
                    .withBody(Utils.toJson(onboarding));
        } else {
            response = new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(404);
        }

        return response;
    }

    /**
     * Get all onboarding records. Integration for GET /onboarding endpoint
     * @param event API Gateway proxy request event
     * @param context
     * @return List of onboarding objects
     */
    public APIGatewayProxyResponseEvent getOnboardings(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        List<Onboarding> onboardings;
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        if (queryParams != null && queryParams.containsKey("tenantId") && Utils.isNotBlank(queryParams.get("tenantId"))) {
            onboardings = Collections.singletonList(dal.getOnboardingByTenantId(queryParams.get("tenantId")));
        } else {
            onboardings = dal.getOnboardings();
        }
        response = new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(onboardings));

        return response;
    }

    /**
     * Update an onboarding record by id. Integration for PUT /onboarding/{id} endpoint.
     * @param event API Gateway proxy request event containing an id path parameter
     * @param context
     * @return HTTP 200 if updated, HTTP 400 on failure
     */
    public APIGatewayProxyResponseEvent updateOnboarding(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = (Map<String, String>) event.get("pathParameters");
        String onboardingId = params.get("id");
        Onboarding onboarding = Utils.fromJson((String) event.get("body"), Onboarding.class);
        if (onboarding == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(Map.of("message", "Invalid request body")));
        } else {
            if (onboarding.getId() == null || !onboarding.getId().toString().equals(onboardingId)) {
                LOGGER.error("Can't update onboarding {} at resource {}", onboarding.getId(), onboardingId);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(Map.of("message", "Request body must include id")));
            } else {
                onboarding = dal.updateOnboarding(onboarding);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(onboarding));
            }
        }

        return response;
    }

    /**
     * Delete an onboarding record by id. Integration for DELETE /onboarding/{id} endpoint.
     * @param event API Gateway proxy request event containing an id path parameter
     * @param context
     * @return HTTP 204 if deleted, HTTP 400 on failure
     */
    public APIGatewayProxyResponseEvent deleteOnboarding(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = (Map<String, String>) event.get("pathParameters");
        String onboardingId = params.get("id");
        try {
            //dal.deleteOnboarding(onboardingId);
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(204); // No content
        } catch (Exception e) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody(Utils.toJson(Map.of("message", "Failed to delete onboarding record "
                            + onboardingId)));
        }
        return response;
    }

    /**
     * Starts the tenant onboarding workflow. Integration for POST /onboarding endpoint
     * Emits an Onboarding Created event.
     * @param event API Gateway proxy request event containing an OnboardingRequest object in the request body
     * @param context
     * @return Onboarding object in a created state or HTTP 400 if the request does not contain a name
     */
    public APIGatewayProxyResponseEvent insertOnboarding(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        if (Utils.isBlank(SAAS_BOOST_BUCKET)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_BUCKET");
        }
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalArgumentException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::startOnboarding");

        Utils.logRequestEvent(event);

        // Parse the onboarding request
        OnboardingRequest onboardingRequest = Utils.fromJson((String) event.get("body"), OnboardingRequest.class);
        if (null == onboardingRequest) {
            LOGGER.error("Onboarding request is invalid");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid onboarding request.\"}");
        }
        if (Utils.isBlank(onboardingRequest.getName())) {
            LOGGER.error("Onboarding request is missing tenant name");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Tenant name is required.\"}");
        }
        if (Utils.isBlank(onboardingRequest.getTier())) {
            LOGGER.error("Onboarding request is missing tier");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Tier is required.\"}");
        }
        // TODO check for duplicate tenant name? Do it by just looking at the local onboarding requests, or make
        // a call out to the tenant service?

        // Create a new onboarding request record for a tenant
        Onboarding onboarding = new Onboarding();
        onboarding.setRequest(onboardingRequest);
        // We're using the generated onboarding id as part of the S3 key
        // so, first we need to persist the onboarding record.
        LOGGER.info("Saving new onboarding request");
        onboarding = dal.insertOnboarding(onboarding);

        // Generate the presigned URL for this tenant's ZIP archive
        final String key = RESOURCES_BUCKET_TEMP_FOLDER + onboarding.getId().toString() + ".zip";
        final Duration expires = Duration.ofMinutes(15); // UI times out in 10 min
        PresignedPutObjectRequest presignedObject = presigner.presignPutObject(request -> request
                .signatureDuration(expires)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(RESOURCES_BUCKET)
                        .key(key)
                        .build()
                )
                .build()
        );
        onboarding.setZipFile(presignedObject.url().toString());
        // Don't save the temporary presigned URL to the database. If the user actually uploads
        // a tenant config file, we'll persist the information then.

        // Let everyone know we've created an onboarding request so it can be validated
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                OnboardingEvent.ONBOARDING_INITIATED.detailType(),
                Map.of("onboardingId", onboarding.getId())
        );

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::startOnboarding exec " + totalTimeMillis);

        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(onboarding));
    }

    public void handleOnboardingEvent(Map<String, Object> event, Context context) {
        if ("saas-boost".equals(event.get("source"))) {
            String detailType = (String) event.get("detail-type");
            OnboardingEvent onboardingEvent = OnboardingEvent.fromDetailType(detailType);
            if (onboardingEvent != null) {
                switch (onboardingEvent) {
                    case ONBOARDING_INITIATED:
                        LOGGER.info("Handling Onboarding Initiated");
                        handleOnboardingInitiated(event, context);
                        break;
                    case ONBOARDING_VALID:
                        LOGGER.info("Handling Onboarding Validated");
                        handleOnboardingValidated(event, context);
                        break;
                    case ONBOARDING_TENANT_ASSIGNED:
                        LOGGER.info("Handling Onboarding Tenant Assigned");
                        handleOnboardingTenantAssigned(event, context);
                        break;
                    case ONBOARDING_STACK_STATUS_CHANGED:
                        LOGGER.info("Handling Onboarding Stack Status Changed");
                        handleOnboardingStackStatusChanged(event, context);
                        break;
                    case ONBOARDING_BASE_PROVISIONED:
                        LOGGER.info("Handling Onboarding Base Provisioned");
                        handleOnboardingBaseProvisioned(event, context);
                        break;
                    case ONBOARDING_PROVISIONED:
                        LOGGER.info("Handling Onboarding Provisioning Complete");
                        handleOnboardingProvisioned(event, context);
                        break;
                    case ONBOARDING_DEPLOYMENT_PIPELINE_CREATED:
                        LOGGER.info("Handling Onboarding Deployment Pipeline Created");
                        handleOnboardingDeploymentPipelineCreated(event, context);
                        break;
                    case ONBOARDING_DEPLOYED:
                        LOGGER.info("Handling Onboarding Workloads Deployed");
                        handleOnboardingDeployed(event, context);
                        break;
                }
            } else if (detailType.startsWith("Application Configuration ")) {
                LOGGER.info("Handling App Config Event");
                handleAppConfigEvent(event, context);
            } else if (detailType.startsWith("Tenant ")) {
                LOGGER.info("Handling Tenant Event");
                handleTenantEvent(event, context);
            } else {
                LOGGER.error("Can't find onboarding event for detail-type {}", event.get("detail-type"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else if ("aws.codepipeline".equals(event.get("source"))) {
            LOGGER.info("Handling Onboarding Deployment Pipeline Changed");
            Utils.logRequestEvent(event);
            handleOnboardingDeploymentPipelineChanged(event, context);
        } else {
            LOGGER.error("Unknown event source " + event.get("source"));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleOnboardingInitiated(Map<String, Object> event, Context context) {
        if (Utils.isBlank(ONBOARDING_VALIDATION_QUEUE)) {
            throw new IllegalStateException("Missing required environment variable ONBOARDING_VALIDATION_QUEUE");
        }
        if (OnboardingEvent.validate(event)) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                if (OnboardingStatus.created == onboarding.getStatus()) {
                    try {
                        // Queue this newly created onboarding request for validation
                        LOGGER.info("Publishing message to onboarding validation queue {} {}", onboarding.getId(),
                                ONBOARDING_VALIDATION_QUEUE);
                        sqs.sendMessage(request -> request
                                .queueUrl(ONBOARDING_VALIDATION_QUEUE)
                                .messageBody(Utils.toJson(Map.of("onboardingId", onboarding.getId())))
                        );
                        dal.updateStatus(onboarding.getId(), OnboardingStatus.validating);
                    } catch (SdkServiceException sqsError) {
                        LOGGER.error("sqs:SendMessage error", sqsError);
                        LOGGER.error(Utils.getFullStackTrace(sqsError));
                        throw sqsError;
                    }
                } else {
                    // Onboarding is in the wrong state for validation
                    LOGGER.error("Can not queue onboarding {} for validation with status {}", onboarding.getId(),
                            onboarding.getStatus());
                    // TODO Throw here? Would end up in DLQ.
                }
            } else {
                // Can't find an onboarding record for this id
                LOGGER.error("Can't find onboarding record for {}", detail.get("onboardingId"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Missing onboardingId in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleOnboardingValidated(Map<String, Object> event, Context context) {
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        if (OnboardingEvent.validate(event)) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                if (onboarding.getTenantId() != null) {
                    LOGGER.error("Unexpected validated onboarding request {} with existing tenantId"
                            , onboarding.getId());
                    // TODO throw illegal state?
                }
                if (OnboardingStatus.validating != onboarding.getStatus()) {
                    // TODO Also illegal state
                }
                onboarding = dal.updateStatus(onboarding.getId(), OnboardingStatus.validated);
                // Call the tenant service synchronously to insert the new tenant record
                LOGGER.info("Calling tenant service insert tenant API");
                LOGGER.info(Utils.toJson(onboarding.getRequest()));
                String insertTenantResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                        ApiGatewayHelper.getApiRequest(
                                API_GATEWAY_HOST,
                                API_GATEWAY_STAGE,
                                ApiRequest.builder()
                                        .resource("tenants")
                                        .method("POST")
                                        .body(Utils.toJson(onboarding.getRequest()))
                                        .build()
                        ),
                        API_TRUST_ROLE,
                        (String) event.get("id")
                );
                Map<String, Object> insertedTenant = Utils.fromJson(insertTenantResponseBody, LinkedHashMap.class);
                if (null == insertedTenant) {
                    failOnboarding(onboarding.getId(), "Tenant insert API call failed");
                    return;
                }
                // Update the onboarding record with the new tenant id
                String tenantId = (String) insertedTenant.get("id");
                onboarding.setTenantId(UUID.fromString(tenantId));
                onboarding = dal.updateOnboarding(onboarding);
    
                // Assign a CIDR block to this tenant to use for its VPC
                try {
                    dal.assignCidrBlock(tenantId);
                } catch (Exception e) {
                    // Unexpected error since we have already validated... but eventual consistency
                    failOnboarding(onboarding.getId(), "Could not assign CIDR for tenant VPC");
                    return;
                }

                // Let the tenant service know the onboarding status
                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                        "Tenant Onboarding Status Changed",
                        Map.of(
                                "tenantId", tenantId,
                                "onboardingStatus",  onboarding.getStatus()
                        )
                );

                // Ready to provision the base infrastructure for this tenant
                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                        OnboardingEvent.ONBOARDING_TENANT_ASSIGNED.detailType(),
                        Map.of("onboardingId", onboarding.getId(), "tenant", insertedTenant));
            } else {
                // Can't find an onboarding record for this id
                LOGGER.error("Can't find onboarding record for {}", detail.get("onboardingId"));
                // TODO Throw here? Would end up in Lambda DLQ. EventBridge has already succeeded.
            }
        } else {
            LOGGER.error("Missing onboardingId in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in Lambda DLQ. EventBridge has already succeeded.
        }
    }

    protected void handleOnboardingTenantAssigned(Map<String, Object> event, Context context) {
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_ENV");
        }
        if (Utils.isBlank(SAAS_BOOST_BUCKET)) {
            throw new IllegalArgumentException("Missing required environment variable SAAS_BOOST_BUCKET");
        }
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        if (Utils.isBlank(ONBOARDING_STACK_SNS)) {
            throw new IllegalArgumentException("Missing required environment variable ONBOARDING_STACK_SNS");
        }
        if (OnboardingEvent.validate(event, "tenant")) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                String tenantId = onboarding.getTenantId().toString();
                Map<String, Object> tenant = (Map<String, Object>) detail.get("tenant");
                String cidrBlock = dal.getCidrBlock(onboarding.getTenantId());
                if (Utils.isBlank(cidrBlock)) {
                    // TODO rethrow to DLQ?
                    failOnboarding(onboarding.getId(), "Can't find assigned CIDR for tenant " + tenantId);
                    return;
                }
                String cidrPrefix = cidrBlock.substring(0, cidrBlock.indexOf(".", cidrBlock.indexOf(".") + 1));

                // Make a synchronous call to the settings service for the app config
                Map<String, Object> appConfig = getAppConfig(context);
                if (null == appConfig) {
                    // TODO rethrow to DLQ?
                    failOnboarding(onboarding.getId(), "Settings getAppConfig API call failed");
                    return;
                }

                // And parameters specific to this tenant
                String tenantSubdomain = Objects.toString(tenant.get("subdomain"), "");
                String tier = Objects.toString(tenant.get("tier"), "default");

                String domainName = Objects.toString(appConfig.get("domainName"), "");
                String hostedZone = Objects.toString(appConfig.get("hostedZone"), "");
                String sslCertificateArn = Objects.toString(appConfig.get("sslCertificate"), "");

                List<Parameter> templateParameters = new ArrayList<>();
                templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(SAAS_BOOST_ENV).build());
                templateParameters.add(Parameter.builder().parameterKey("DomainName").parameterValue(domainName).build());
                templateParameters.add(Parameter.builder().parameterKey("HostedZoneId").parameterValue(hostedZone).build());
                templateParameters.add(Parameter.builder().parameterKey("SSLCertificateArn").parameterValue(sslCertificateArn).build());
                templateParameters.add(Parameter.builder().parameterKey("TenantId").parameterValue(tenantId).build());
                templateParameters.add(Parameter.builder().parameterKey("TenantSubDomain").parameterValue(tenantSubdomain).build());
                templateParameters.add(Parameter.builder().parameterKey("CidrPrefix").parameterValue(cidrPrefix).build());
                templateParameters.add(Parameter.builder().parameterKey("Tier").parameterValue(tier).build());

                for (Parameter p : templateParameters) {
                    if (p.parameterValue() == null) {
                        LOGGER.error("OnboardingService::provisionTenant template parameter {} is NULL",
                                p.parameterKey());
                        failOnboarding(onboarding.getId(), "CloudFormation template parameter "
                                + p.parameterKey() + " is NULL");
                        throw new RuntimeException();
                    }
                }

                String tenantShortId = tenantId.substring(0, 8);
                String stackName = "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantShortId;

                // Now run the onboarding stack to provision the infrastructure for this tenant
                LOGGER.info("OnboardingService::provisionTenant create stack " + stackName);
                String templateUrl = "https://" + SAAS_BOOST_BUCKET + ".s3." + AWS_REGION
                        + ".amazonaws.com/tenant-onboarding.yaml";
                String stackId;
                try {
                    CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                            .stackName(stackName)
                            .disableRollback(false)
                            .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                            .notificationARNs(ONBOARDING_STACK_SNS)
                            .templateURL(templateUrl)
                            .parameters(templateParameters)
                            .build()
                    );
                    stackId = cfnResponse.stackId();
                    onboarding.setStatus(OnboardingStatus.provisioning);
                    onboarding.addStack(OnboardingStack.builder()
                            .name(stackName)
                            .arn(stackId)
                            .baseStack(true)
                            .status("CREATE_IN_PROGRESS")
                            .build()
                    );
                    dal.updateOnboarding(onboarding);
                    LOGGER.info("OnboardingService::provisionTenant stack id " + stackId);
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            "Tenant Onboarding Status Changed",
                            Map.of(
                                    "tenantId", tenantId,
                                    "onboardingStatus", onboarding.getStatus()
                            )
                    );
                } catch (SdkServiceException cfnError) {
                    LOGGER.error("cloudformation::createStack failed {}", cfnError.getMessage());
                    LOGGER.error(Utils.getFullStackTrace(cfnError));
                    failOnboarding(onboarding.getId(), cfnError.getMessage());
                    throw cfnError;
                }
            } else {
                // Can't find an onboarding record for this id
                LOGGER.error("Can't find onboarding record for {}", detail.get("onboardingId"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Missing onboardingId in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleOnboardingStackStatusChanged(Map<String, Object> event, Context context) {
        // TODO stack events don't have the onboardingId, so we can't use OnboardingEvent::validate as written
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        if (detail != null && detail.containsKey("tenantId") && detail.containsKey("stackId")
                && detail.containsKey("stackStatus")) {
            String tenantId = (String) detail.get("tenantId");
            String stackId = (String) detail.get("stackId");
            String stackStatus = (String) detail.get("stackStatus");
            OnboardingStatus status = OnboardingStatus.fromStackStatus(stackStatus);

            Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
            if (onboarding != null) {
                LOGGER.info("Updating onboarding stack status {} {}", onboarding.getId(), stackId);
                for (OnboardingStack stack : onboarding.getStacks()) {
                    if (stackId.equals(stack.getArn())) {
                        if (!stackStatus.equals(stack.getStatus())) {
                            LOGGER.info("Stack status changing from {} to {}", stack.getStatus(), stackStatus);
                            stack.setStatus(stackStatus);
                        }
                        if (status != onboarding.getStatus()) {
                            if (OnboardingStatus.deleted == status && !stack.isBaseStack()) {
                                // If we're receiving a DELETE_COMPLETE status for one of the app stacks,
                                // the onboarding record is still in a deleting state because we have to
                                // delete the base stack after all the app stacks are complete
                                LOGGER.info("Skipping onboarding status deleted for app stack {}", stack.getName());
                                onboarding.setStatus(OnboardingStatus.deleting);
                            } else {
                                onboarding.setStatus(status);
                                LOGGER.info("Onboarding status changing from {} to {}", onboarding.getStatus(), status);
                            }
                        }
                        dal.updateOnboarding(onboarding);
                        if (stack.isComplete()) {
                            if (stack.isBaseStack() && onboarding.baseStacksComplete()) {
                                if (stack.isCreated()) {
                                    LOGGER.info("Onboarding base stacks provisioned!");
                                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                                            OnboardingEvent.ONBOARDING_BASE_PROVISIONED.detailType(),
                                            Map.of("onboardingId", onboarding.getId())
                                    );
                                } else if (stack.isUpdated()) {
                                    LOGGER.info("Onboarding base stacks updated");
                                    // TODO handle updating tenant stacks
                                }
                            } else if (!stack.isBaseStack() && onboarding.stacksComplete()) {
                                LOGGER.info("All onboarding stacks provisioned!");
                                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                                        OnboardingEvent.ONBOARDING_PROVISIONED.detailType(),
                                        Map.of("onboardingId", onboarding.getId())
                                );
                            }
                        } else if (!stack.isBaseStack() && stack.isDeleted() && onboarding.appStacksDeleted()) {
                            LOGGER.info("All app stacks deleted");
                            handleBaseProvisioningReadyToDelete(event, context);
                        } else if (stack.isBaseStack() && stack.isDeleted()) {
                            onboarding.setStatus(OnboardingStatus.deleted);
                            dal.updateOnboarding(onboarding);
                            // Let the tenant service know the onboarding status
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                    "Tenant Onboarding Status Changed",
                                    Map.of(
                                            "tenantId", tenantId,
                                            "onboardingStatus",  onboarding.getStatus()
                                    )
                            );
                        }
                        break;
                    }
                }
            } else {
                // Can't find an onboarding record for this id
                LOGGER.error("Can't find onboarding record for tenant {}", detail.get("tenantId"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Missing tenantId and/or stackId in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleOnboardingBaseProvisioned(Map<String, Object> event, Context context) {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_ENV");
        }
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        if (Utils.isBlank(ONBOARDING_APP_STACK_SNS)) {
            throw new IllegalStateException("Missing required environment variable ONBOARDING_APP_STACK_SNS");
        }
        if (Utils.isBlank(RESOURCES_BUCKET)) {
            throw new IllegalStateException("Missing required environment variable RESOURCES_BUCKET");
        }

        //Utils.logRequestEvent(event);
        if (OnboardingEvent.validate(event)) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                String tenantId = onboarding.getTenantId().toString();
                Map<String, Object> tenant = getTenant(tenantId, context);
                // TODO tenant == null means tenant API call failed? retry?
                if (tenant != null) {
                    Map<String, Object> appConfig = getAppConfig(context);
                    if (null == appConfig) {
                        LOGGER.error("Settings get app config API call failed");
                        // TODO retry?
                    }

                    String applicationName = (String) appConfig.get("name");
                    String vpc;
                    String privateSubnetA;
                    String privateSubnetB;
                    String privateRouteTable;
                    String ecsSecurityGroup;
                    String loadBalancerArn;
                    String httpListenerArn;
                    String httpsListenerArn; // might not have an HTTPS listener if they don't have an SSL certificate
                    String ecsCluster;
                    Map<String, Map<String, String>> tenantResources = (Map<String, Map<String, String>>) tenant.get("resources");
                    try {
                        vpc = tenantResources.get("VPC").get("name");
                        privateSubnetA = tenantResources.get("PRIVATE_SUBNET_A").get("name");
                        privateSubnetB = tenantResources.get("PRIVATE_SUBNET_B").get("name");
                        privateRouteTable = tenantResources.get("PRIVATE_ROUTE_TABLE").get("name");
                        ecsCluster = tenantResources.get("ECS_CLUSTER").get("name");
                        ecsSecurityGroup = tenantResources.get("ECS_SECURITY_GROUP").get("name");
                        loadBalancerArn = tenantResources.get("LOAD_BALANCER").get("arn");
                        // Depending on the SSL certificate configuration, one of these 2 listeners must exist
                        if (tenantResources.containsKey("HTTP_LISTENER")) {
                            httpListenerArn = Objects.toString(tenantResources.get("HTTP_LISTENER").get("arn"), "");
                        } else {
                            httpListenerArn = "";
                        }
                        if (tenantResources.containsKey("HTTPS_LISTENER")) {
                            httpsListenerArn = Objects.toString(tenantResources.get("HTTPS_LISTENER").get("arn"), "");
                        } else {
                            httpsListenerArn = "";
                        }
                        if (Utils.isBlank(vpc) || Utils.isBlank(privateSubnetA) || Utils.isBlank(privateSubnetB)
                                || Utils.isBlank(ecsCluster) || Utils.isBlank(ecsSecurityGroup)
                                || Utils.isBlank(loadBalancerArn)
                                || (Utils.isBlank(httpListenerArn) && Utils.isBlank(httpsListenerArn))) {
                            LOGGER.error("Missing required tenant environment resources");
                            failOnboarding(onboarding.getId(), "Missing required tenant environment resources");
                            return;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error parsing tenant resources", e);
                        LOGGER.error(Utils.getFullStackTrace(e));
                        failOnboarding(onboarding.getId(), "Error parsing resources for tenant " + tenantId);
                        return;
                    }

                    String tier = (String) tenant.get("tier");
                    if (Utils.isBlank(tier)) {
                        LOGGER.error("Tenant is missing tier");
                        failOnboarding(onboarding.getId(), "Error retrieving tier for tenant " + tenantId);
                        return;
                    }

                    Map<String, Integer> pathPriority = getPathPriority(appConfig);
                    Properties serviceDiscovery = new Properties();

                    Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
                    for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
                        String serviceName = serviceConfig.getKey();
                        // CloudFormation resource names can only contain alpha numeric characters or a dash
                        String serviceResourceName = serviceName.replaceAll("[^0-9A-Za-z-]", "").toLowerCase();
                        Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
                        Boolean isPublic = (Boolean) service.get("public");
                        String pathPart = (isPublic) ? (String) service.get("path") : "";
                        Integer publicPathRulePriority = (isPublic) ? pathPriority.get(serviceName) : 0;
                        String healthCheck = (String) service.get("healthCheckUrl");

                        // CloudFormation won't let you use dashes or underscores in Mapping second level key names
                        // And it won't let you use Fn::Join or Fn::Split in Fn::FindInMap... so we will mangle this
                        // parameter before we send it in.
                        String clusterOS = ((String) service.getOrDefault("operatingSystem", ""))
                                .replace("_", "");

                        Integer containerPort = (Integer) service.get("containerPort");
                        String containerRepo = (String) service.get("containerRepo");
                        String imageTag = (String) service.getOrDefault("containerTag", "latest");

                        // If there are any private services, we will create an environment variables called
                        // SERVICE_<SERVICE_NAME>_HOST and SERVICE_<SERVICE_NAME>_PORT to pass to the task definitions
                        String serviceEnvName = Utils.toUpperSnakeCase(serviceName);
                        String serviceHost = "SERVICE_" + serviceEnvName + "_HOST";
                        String servicePort = "SERVICE_" + serviceEnvName + "_PORT";
                        if (!isPublic) {
                            LOGGER.debug("Creating service discovery environment variables {}, {}", serviceHost, servicePort);
                            serviceDiscovery.put(serviceHost, serviceResourceName + ".local");
                            serviceDiscovery.put(servicePort, Objects.toString(containerPort));
                        }

                        Map<String, Object> tiers = (Map<String, Object>) service.get("tiers");
                        if (!tiers.containsKey(tier)) {
                            LOGGER.error("Missing tier '{}' definition for tenant {}", tier, tenantId);
                            failOnboarding(onboarding.getId(), "Error retrieving tier for tenant " + tenantId);
                            return;
                        }

                        Map<String, Object> tierConfig = (Map<String, Object>) tiers.get(tier);
                        String clusterInstanceType = (String) tierConfig.get("instanceType");
                        String launchType = (String) service.get("ecsLaunchType");
                        Integer taskMemory = (Integer) tierConfig.get("memory");
                        Integer taskCpu = (Integer) tierConfig.get("cpu");
                        Integer minCount = (Integer) tierConfig.get("min");
                        Integer maxCount = (Integer) tierConfig.get("max");

                        // Does this service use a shared filesystem?
                        Boolean enableEfs = Boolean.FALSE;
                        Boolean enableFSx = Boolean.FALSE;
                        String mountPoint = "/mnt";
                        Boolean encryptFilesystem = Boolean.TRUE;
                        String filesystemLifecycle = "NEVER";
                        Integer fsxStorageGb = 32;
                        Integer fsxThroughputMbs = 8;
                        Integer fsxBackupRetentionDays = 0;
                        String fsxDailyBackupTime = "02:00";
                        String fsxWeeklyMaintenanceTime = "7:01:00";
                        String fsxWindowsMountDrive = "G:";
                        Integer ontapVolumeSize = 40;
                        String fileSystemType = "FSX_WINDOWS";
                        Map<String, Object> filesystem = (Map<String, Object>) tierConfig.get("filesystem");
                        if (filesystem != null && !filesystem.isEmpty()) {
                            fileSystemType = (String) filesystem.get("type");
                            mountPoint = (String) filesystem.get("mountPoint");
                            if ("EFS".equals(fileSystemType)) {
                                enableEfs = Boolean.TRUE;
                                encryptFilesystem = (Boolean) filesystem.get("encrypt");
                                if (encryptFilesystem == null) {
                                    encryptFilesystem = Boolean.FALSE;
                                }
                                filesystemLifecycle = (String) filesystem.get("lifecycle");
                                if (filesystemLifecycle == null) {
                                    filesystemLifecycle = "NEVER";
                                }
                            } else if ("FSX_WINDOWS".equals(fileSystemType) || "FSX_ONTAP".equals(fileSystemType)) {
                                enableFSx = Boolean.TRUE;
                                fsxStorageGb = (Integer) filesystem.get("storageGb");
                                if (fsxStorageGb == null) {
                                    fsxStorageGb = "FSX_ONTAP".equals(fileSystemType) ? 1024 : 32;
                                }
                                fsxThroughputMbs = (Integer) filesystem.get("throughputMbs");
                                if (fsxThroughputMbs == null) {
                                    fsxThroughputMbs = "FSX_ONTAP".equals(fileSystemType) ? 128 : 8;
                                }
                                fsxBackupRetentionDays = (Integer) filesystem.get("backupRetentionDays");
                                if (fsxBackupRetentionDays == null) {
                                    fsxBackupRetentionDays = 0; // Turn off automated backups
                                }
                                fsxDailyBackupTime = (String) filesystem.get("dailyBackupTime");
                                if (fsxDailyBackupTime == null) {
                                    fsxDailyBackupTime = "02:00"; // 2:00 AM
                                }
                                fsxWeeklyMaintenanceTime = (String) filesystem.get("weeklyMaintenanceTime");
                                if (fsxWeeklyMaintenanceTime == null) {
                                    fsxWeeklyMaintenanceTime = "7:01:00"; // Sun 1:00 AM
                                }
                                fsxWindowsMountDrive = (String) filesystem.get("windowsMountDrive");
                                if (fsxWindowsMountDrive == null) {
                                    fsxWindowsMountDrive = "G:";
                                }
                                if ("FSX_ONTAP".equals(fileSystemType)) {
                                    ontapVolumeSize = (Integer) filesystem.get("volumeSize");
                                    if (ontapVolumeSize == null) {
                                        ontapVolumeSize = 40;
                                    }
                                }
                            }
                            if (enableEfs || fileSystemType == null) {
                                fileSystemType = "FSX_WINDOWS";
                            }
                        }

                        // Does this service use a relational database?
                        Boolean enableDatabase = Boolean.FALSE;
                        String dbInstanceClass = "";
                        String dbEngine = "";
                        String dbVersion = "";
                        String dbFamily = "";
                        String dbUsername = "";
                        String dbPasswordRef = "";
                        Integer dbPort = -1;
                        String dbDatabase = "";
                        String dbBootstrap = "";
                        Map<String, Object> database = (Map<String, Object>) service.get("database");
                        if (database != null && !database.isEmpty()) {
                            enableDatabase = Boolean.TRUE;
                            dbEngine = (String) database.get("engineName");
                            dbVersion = (String) database.get("version");
                            dbFamily = (String) database.get("family");
                            Map<String, Object> databaseTiers = (Map<String, Object>) database.get("tiers");
                            Map<String, Object> databaseTierConfig = (Map<String, Object>) databaseTiers.get(tier);
                            dbInstanceClass = (String) databaseTierConfig.get("instanceClass");
                            dbDatabase = Objects.toString(database.get("database"), "");
                            dbUsername = (String) database.get("username");
                            dbPort = (Integer) database.get("port");
                            dbBootstrap = Objects.toString(database.get("bootstrapFilename"), "");
                            dbPasswordRef = (String) database.get("passwordParam");
                        }

                        List<Parameter> templateParameters = new ArrayList<>();
                        templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(SAAS_BOOST_ENV).build());
                        templateParameters.add(Parameter.builder().parameterKey("TenantId").parameterValue(tenantId).build());
                        templateParameters.add(Parameter.builder().parameterKey("ServiceName").parameterValue(serviceName).build());
                        templateParameters.add(Parameter.builder().parameterKey("ServiceResourceName").parameterValue(serviceResourceName).build());
                        templateParameters.add(Parameter.builder().parameterKey("ContainerRepository").parameterValue(containerRepo).build());
                        templateParameters.add(Parameter.builder().parameterKey("ContainerRepositoryTag").parameterValue(imageTag).build());
                        templateParameters.add(Parameter.builder().parameterKey("ECSCluster").parameterValue(ecsCluster).build());
                        templateParameters.add(Parameter.builder()
                                .parameterKey("OnboardingDdbTable")
                                .parameterValue(ONBOARDING_TABLE).build());
                        templateParameters.add(Parameter.builder().parameterKey("PubliclyAddressable").parameterValue(isPublic.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("PublicPathRoute").parameterValue(pathPart).build());
                        templateParameters.add(Parameter.builder().parameterKey("PublicPathRulePriority").parameterValue(publicPathRulePriority.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("VPC").parameterValue(vpc).build());
                        templateParameters.add(Parameter.builder().parameterKey("SubnetPrivateA").parameterValue(privateSubnetA).build());
                        templateParameters.add(Parameter.builder().parameterKey("SubnetPrivateB").parameterValue(privateSubnetB).build());
                        templateParameters.add(Parameter.builder().parameterKey("PrivateRouteTable").parameterValue(privateRouteTable).build());
                        templateParameters.add(Parameter.builder().parameterKey("ECSLoadBalancerHttpListener").parameterValue(httpListenerArn).build());
                        templateParameters.add(Parameter.builder().parameterKey("ECSLoadBalancerHttpsListener").parameterValue(httpsListenerArn).build());
                        templateParameters.add(Parameter.builder().parameterKey("ECSSecurityGroup").parameterValue(ecsSecurityGroup).build());
                        templateParameters.add(Parameter.builder().parameterKey("ContainerOS").parameterValue(clusterOS).build());
                        templateParameters.add(Parameter.builder().parameterKey("ClusterInstanceType").parameterValue(clusterInstanceType).build());
                        templateParameters.add(Parameter.builder().parameterKey("TaskLaunchType").parameterValue(launchType).build());
                        templateParameters.add(Parameter.builder().parameterKey("TaskMemory").parameterValue(taskMemory.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("TaskCPU").parameterValue(taskCpu.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("MinTaskCount").parameterValue(minCount.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("MaxTaskCount").parameterValue(maxCount.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("ContainerPort").parameterValue(containerPort.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("ContainerHealthCheckPath").parameterValue(healthCheck).build());
                        templateParameters.add(Parameter.builder().parameterKey("UseEFS").parameterValue(enableEfs.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("FileSystemMountPoint").parameterValue(mountPoint).build());
                        templateParameters.add(Parameter.builder().parameterKey("EncryptEFS").parameterValue(encryptFilesystem.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("EFSLifecyclePolicy").parameterValue(filesystemLifecycle).build());
                        templateParameters.add(Parameter.builder().parameterKey("UseFSx").parameterValue(enableFSx.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("FSxFileSystemType").parameterValue(fileSystemType.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("FSxWindowsMountDrive").parameterValue(fsxWindowsMountDrive).build());
                        templateParameters.add(Parameter.builder().parameterKey("FSxDailyBackupTime").parameterValue(fsxDailyBackupTime).build());
                        templateParameters.add(Parameter.builder().parameterKey("FSxBackupRetention").parameterValue(fsxBackupRetentionDays.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("FileSystemThroughput").parameterValue(fsxThroughputMbs.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("FileSystemStorage").parameterValue(fsxStorageGb.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("FSxWeeklyMaintenanceTime").parameterValue(fsxWeeklyMaintenanceTime).build());
                        templateParameters.add(Parameter.builder().parameterKey("OntapVolumeSize").parameterValue(ontapVolumeSize.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("UseRDS").parameterValue(enableDatabase.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSInstanceClass").parameterValue(dbInstanceClass).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSEngine").parameterValue(dbEngine).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSEngineVersion").parameterValue(dbVersion).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSParameterGroupFamily").parameterValue(dbFamily).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSUsername").parameterValue(dbUsername).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSPasswordParam").parameterValue(dbPasswordRef).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSPort").parameterValue(dbPort.toString()).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSDatabase").parameterValue(dbDatabase).build());
                        templateParameters.add(Parameter.builder().parameterKey("RDSBootstrap").parameterValue(dbBootstrap).build());
                        // TODO rework these last 2?
                        templateParameters.add(Parameter.builder().parameterKey("MetricsStream").parameterValue("").build());
                        templateParameters.add(Parameter.builder().parameterKey("EventBus").parameterValue(SAAS_BOOST_EVENT_BUS).build());
                        templateParameters.add(Parameter.builder().parameterKey("Tier").parameterValue(tier).build());
                        for (Parameter p : templateParameters) {
                            LOGGER.info("{} => {}", p.parameterKey(), p.parameterValue());
                            if (p.parameterValue() == null) {
                                LOGGER.error("OnboardingService::provisionTenant template parameter {} is NULL", p.parameterKey());
                                dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                                // TODO throw here?
                                throw new RuntimeException("CloudFormation template parameter " + p.parameterKey() + " is NULL");
                            }
                        }

                        // Make the stack name look like what CloudFormation would have done for a nested stack
                        String tenantShortId = tenantId.substring(0, 8);
                        String stackName = "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantShortId + "-app-"
                                + serviceResourceName + "-" + Utils.randomString(12).toUpperCase();
                        if (stackName.length() > 128) {
                            stackName = stackName.substring(0, 128);
                        }
                        // Now run the onboarding stack to provision the infrastructure for this application service
                        LOGGER.info("OnboardingService::provisionApplication create stack " + stackName);
                        String templateUrl = "https://" + SAAS_BOOST_BUCKET + ".s3." + AWS_REGION
                                + ".amazonaws.com/tenant-onboarding-app.yaml";
                        String stackId;
                        try {
                            CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                                    .stackName(stackName)
                                    .disableRollback(false)
                                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                                    .notificationARNs(ONBOARDING_APP_STACK_SNS)
                                    .templateURL(templateUrl)
                                    .parameters(templateParameters)
                                    .build()
                            );
                            stackId = cfnResponse.stackId();
                            onboarding.setStatus(OnboardingStatus.provisioning);
                            onboarding.addStack(OnboardingStack.builder()
                                    .name(stackName)
                                    .arn(stackId)
                                    .baseStack(false)
                                    .status("CREATE_IN_PROGRESS")
                                    .build()
                            );
                            onboarding = dal.updateOnboarding(onboarding);
                            LOGGER.info("OnboardingService::provisionApplication stack id " + stackId);
                        } catch (CloudFormationException cfnError) {
                            LOGGER.error("cloudformation::createStack failed", cfnError);
                            LOGGER.error(Utils.getFullStackTrace(cfnError));
                            failOnboarding(onboarding.getId(), cfnError.awsErrorDetails().errorMessage());
                            return;
                        }
                    }

                    String environmentFile = "tenants/" + tenantId + "/ServiceDiscovery.env";
                    ByteArrayOutputStream environmentFileContents = new ByteArrayOutputStream();
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                            environmentFileContents, StandardCharsets.UTF_8)
                    )) {
                        serviceDiscovery.store(writer, null);
                        s3.putObject(request -> request
                                        .bucket(RESOURCES_BUCKET)
                                        .key(environmentFile)
                                        .build(),
                                RequestBody.fromBytes(environmentFileContents.toByteArray())
                        );
                    } catch (S3Exception s3Error) {
                        LOGGER.error("Error putting service discovery file to S3");
                        LOGGER.error(Utils.getFullStackTrace(s3Error));
                        failOnboarding(onboarding.getId(), s3Error.awsErrorDetails().errorMessage());
                    } catch (IOException ioe) {
                        LOGGER.error("Error writing service discovery data to output stream");
                        LOGGER.error(Utils.getFullStackTrace(ioe));
                        failOnboarding(onboarding.getId(), "Error writing service discovery data to output stream");
                    }
                } else {
                    LOGGER.error("Can't parse get tenant api response");
                    failOnboarding(onboarding.getId(), "Can't fetch tenant " + tenantId);
                }
            } else {
                LOGGER.error("No onboarding record for {}", detail.get("onboardingId"));
            }
        } else {
            LOGGER.error("Missing onboardingId in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleOnboardingProvisioned(Map<String, Object> event, Context context) {
        // Provisioning is complete so we can deploy the workloads. Doing this after all stacks have finished
        // instead of as each non base stack finishes because until all services are up and ready the tenant
        // can't use the solution.
        if (OnboardingEvent.validate(event)) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                LOGGER.info("Triggering deployment pipelines for tenant {}", onboarding.getTenantId());

                // Publish a deployment event for each of the configured services in appConfig
                Map<String, Object> appConfig = getAppConfig(context);
                Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
                for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
                    Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            "Workload Ready For Deployment",
                            Map.of(
                                    "tenantId", onboarding.getTenantId(),
                                    "repository-name", service.get("containerRepo"),
                                    "image-tag", service.get("containerTag")
                            )
                    );
                }
            } else {
                LOGGER.error("Can't find onboarding record for {}", detail.get("onboardingId"));
            }
        } else {
            LOGGER.error("Missing onboardingId in event detail {}", Utils.toJson(event.get("detail")));
        }
    }

    protected  void handleOnboardingDeploymentPipelineCreated(Map<String, Object> event, Context context) {
        // TODO stack events don't have the onboardingId, so we can't use OnboardingEvent::validate as written
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        if (detail != null && detail.containsKey("tenantId") && detail.containsKey("stackId")
                && detail.containsKey("stackName") && detail.containsKey("pipeline")) {
            String tenantId = (String) detail.get("tenantId");
            String stackId = (String) detail.get("stackId");
            String stackName = (String) detail.get("stackName");
            String pipeline = (String) detail.get("pipeline");

            Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
            if (onboarding != null) {
                for (OnboardingStack stack : onboarding.getStacks()) {
                    if (stackId.equals(stack.getArn())) {
                        LOGGER.info("Updating onboarding {} stack {} pipeline {}", onboarding.getId(),
                                stackName, pipeline);
                        stack.setPipeline(pipeline);
                        dal.updateOnboarding(onboarding);
                        break;
                    }
                }
            } else {
                LOGGER.error("Can't find onboarding record for tenant {}", tenantId);
            }
        } else {
            LOGGER.error("Missing required keys in event detail {}", Utils.toJson(event.get("detail")));
        }
    }

    protected void handleOnboardingDeploymentPipelineChanged(Map<String, Object> event, Context context) {
        if ("aws.codepipeline".equals(event.get("source"))) {
            List<String> resources = (List<String>) event.get("resources");
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            String pipeline = (String) detail.get("pipeline");
            try {
                String pipelineArn = resources.get(0);
                LOGGER.info("Fetching tenant id from CodePipeline tags");
                ListTagsForResourceResponse tagsResponse = codePipeline.listTagsForResource(request -> request
                        .resourceArn(pipelineArn)
                );
                Tag tenantTag = tagsResponse.tags().stream().filter(t -> "Tenant".equals(t.key())).findFirst().get();
                String tenantId = tenantTag.value();
                Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
                if (onboarding != null) {
                    tenantId = onboarding.getTenantId().toString();

                    String pipelineState = (String) detail.get("state");
                    for (OnboardingStack stack : onboarding.getStacks()) {
                        if (pipeline.equals(stack.getPipeline())) {
                            // When the pipeline is created it is automatically started (there's no way to prevent this)
                            // and will fail because the source for the pipeline is not available when it's created. Even
                            // if we made the source available (the docker image to deploy), there's no guarantee that the
                            // container infrastructure would be ready yet. We trigger the first run of the pipeline after
                            // all of the infrastructure is provisioned.

                            // Skip setting the failed status the first time around
                            if ("FAILED".equals(pipelineState) && Utils.isEmpty(stack.getPipelineStatus())) {
                                LOGGER.info("Onboarding {} stack {} ignoring initial failed pipeline state",
                                        onboarding.getId(), stack.getName());
                                break;
                            }

                            // Otherwise, update the pipeline status
                            LOGGER.info("Updating onboarding {} stack {} pipeline {} state to {}", onboarding.getId(),
                                    stack.getName(), pipeline, pipelineState);
                            stack.setPipelineStatus(pipelineState);
                            onboarding = dal.updateOnboarding(onboarding);
                            break;
                        }
                    }

                    if ("STARTED".equals(pipelineState)) {
                        dal.updateStatus(onboarding.getId(), OnboardingStatus.deploying);
                        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                "Tenant Onboarding Status Changed",
                                Map.of(
                                        "tenantId", tenantId,
                                        "onboardingStatus", OnboardingStatus.deploying
                                )
                        );
                    } else if ("FAILED".equals(pipelineState) || "CANCELED".equals(pipelineState)) {
                        boolean firstFailure = false;
                        for (OnboardingStack stack : onboarding.getStacks()) {
                            if (pipeline.equals(stack.getPipeline())) {
                                if (Utils.isEmpty(stack.getPipelineStatus())) {
                                    firstFailure = true;
                                }
                                break;
                            }
                        }
                        if (!firstFailure) {
                            failOnboarding(onboarding.getId(), "Pipeline " + pipeline + " failed");
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                    "Tenant Onboarding Status Changed",
                                    Map.of(
                                            "tenantId", tenantId,
                                            "onboardingStatus", "failed"
                                    )
                            );
                        }
                    } else if ("SUCCEEDED".equals(pipelineState)) {
                        if (onboarding.stacksDeployed()) {
                            dal.updateStatus(onboarding.getId(), OnboardingStatus.deployed);
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                    "Tenant Onboarding Status Changed",
                                    Map.of(
                                            "tenantId", tenantId,
                                            "onboardingStatus", OnboardingStatus.deployed
                                    )
                            );
                        }
                    }
                } else {
                    LOGGER.error("Can't find onboarding record for tenant {}", tenantId);
                }
            } catch (Exception e) {
                LOGGER.error("Error fetching tenant id from pipeline {}", pipeline);
                LOGGER.error(Utils.getFullStackTrace(e));
            }
        }
    }

    protected void handleOnboardingDeployed(Map<String, Object> event, Context context) {

    }

    protected void handleOnboardingFailed(Map<String, Object> event, Context context) {

    }

    public SQSBatchResponse processValidateOnboardingQueue(SQSEvent event, Context context) {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        if (Utils.isBlank(ONBOARDING_VALIDATION_DLQ)) {
            throw new IllegalStateException("Missing required environment variable ONBOARDING_VALIDATION_DLQ");
        }
        List<SQSBatchResponse.BatchItemFailure> retry = new ArrayList<>();
        List<SQSEvent.SQSMessage> fatal = new ArrayList<>();
        sqsMessageLoop:
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            String messageId = message.getMessageId();
            String messageBody = message.getBody();

            LinkedHashMap<String, Object> detail = Utils.fromJson(messageBody, LinkedHashMap.class);
            String onboardingId = (String) detail.get("onboardingId");
            LOGGER.info("Processing onboarding validation for {}", onboardingId);
            Onboarding onboarding = dal.getOnboarding(onboardingId);
            OnboardingRequest onboardingRequest = onboarding.getRequest();
            if (onboardingRequest == null) {
                LOGGER.error("No onboarding request data for {}", onboardingId);
                fatal.add(message);
                failOnboarding(onboardingId, "Onboarding record has no request content");
            } else if (OnboardingStatus.validating != onboarding.getStatus()) {
                LOGGER.warn("Onboarding in unexpected state for validation {} {}", onboardingId, onboarding.getStatus());
                fatal.add(message);
                failOnboarding(onboardingId, "Onboarding can't be validated when in state "
                        + onboarding.getStatus());
            } else {
                Map<String, Object> appConfig = getAppConfig(context);
                // Check to see if there are any images in the ECR repo before allowing onboarding
                Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
                if (services.isEmpty()) {
                    LOGGER.warn("No application services defined in AppConfig");
                    retry.add(SQSBatchResponse.BatchItemFailure.builder()
                            .withItemIdentifier(messageId)
                            .build()
                    );
                } else {
                    int missingImages = 0;
                    for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
                        String serviceName = serviceConfig.getKey();
                        Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
                        String ecrRepo = (String) service.get("containerRepo");
                        String imageTag = (String) service.getOrDefault("containerTag", "latest");
                        if (Utils.isNotBlank(ecrRepo)) {
                            try {
                                ListImagesResponse dockerImages = ecr.listImages(request -> request
                                        .repositoryName(ecrRepo));
                                boolean imageAvailable = false;
                                //ListImagesResponse::hasImageIds will return true if the imageIds object is not null
                                if (dockerImages.hasImageIds()) {
                                    for (ImageIdentifier image : dockerImages.imageIds()) {
                                        if (imageTag.equals(image.imageTag())) {
                                            imageAvailable = true;
                                            break;
                                        }
                                    }
                                }
                                if (!imageAvailable) {
                                    // Not valid yet, no container image to deploy
                                    LOGGER.warn("Application Service {} does not have an available image tagged {}",
                                            serviceName, imageTag);
                                    missingImages++;
                                }
                            } catch (EcrException ecrError) {
                                LOGGER.error("ecr:ListImages error", ecrError.getMessage());
                                LOGGER.error(Utils.getFullStackTrace(ecrError));
                                // TODO do we bail here or retry?
                                failOnboarding(onboardingId, "Can't list images from ECR "
                                        + ecrError.awsErrorDetails().errorMessage());
                                fatal.add(message);
                                continue sqsMessageLoop;
                            }
                        } else {
                            // TODO no repo defined for this service yet...
                            LOGGER.warn("Application Service {} does not have a container image repository defined",
                                    serviceName);
                            missingImages++;
                        }
                    }
                    if (missingImages > 0) {
                        retry.add(SQSBatchResponse.BatchItemFailure.builder()
                                .withItemIdentifier(messageId)
                                .build()
                        );
                        continue;
                    }

                    String tier = onboardingRequest.getTier();
                    boolean invaildTierConfig = false;
                    for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
                        Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
                        Map<String, Object> tiers = (Map<String, Object>) service.get("tiers");
                        if (!tiers.containsKey(tier) || tiers.get(tier) == null || ((Map) tiers.get(tier)).isEmpty()) {
                            LOGGER.warn("Missing tier configuration for service '{}' tier '{}'", serviceConfig.getKey(), tier);
                            invaildTierConfig = true;
                        }
                    }
                    if (invaildTierConfig) {
                        retry.add(SQSBatchResponse.BatchItemFailure.builder()
                                .withItemIdentifier(messageId)
                                .build()
                        );
                        continue;
                    }

                    // Do we have any CIDR blocks left for a new tenant VPC
                    if (!dal.availableCidrBlock()) {
                        LOGGER.error("No CIDR blocks available for new VPC");
                        failOnboarding(onboardingId, "No CIDR blocks available for new VPC");
                        fatal.add(message);
                        continue;
                    }

                    // Make sure we're using a unique subdomain per tenant
                    String subdomain = onboardingRequest.getSubdomain();
                    if (Utils.isNotBlank(subdomain)) {
                        String hostedZoneId = (String) appConfig.get("hostedZone");
                        String domainName = (String) appConfig.get("domainName");
                        if (Utils.isBlank(hostedZoneId) || Utils.isBlank(domainName)) {
                            LOGGER.error("Can't onboard a subdomain without domain name and hosted zone");
                            failOnboarding(onboardingId, "Can't define tenant subdomain " + subdomain
                                    + " without a domain name and hosted zone.");
                            fatal.add(message);
                            continue;
                        } else {
                            // Ask Route53 for all the records of this hosted zone
                            try {
                                ListResourceRecordSetsResponse recordSets = route53.listResourceRecordSets(r -> r
                                        .hostedZoneId(hostedZoneId)
                                );
                                if (recordSets.hasResourceRecordSets()) {
                                    boolean duplicateSubdomain = false;
                                    for (ResourceRecordSet recordSet : recordSets.resourceRecordSets()) {
                                        if (RRType.A == recordSet.type()) {
                                            // Hosted Zone alias for the tenant subdomain
                                            String recordSetName = recordSet.name();
                                            String existingSubdomain = recordSetName.substring(0,
                                                    recordSetName.indexOf(domainName) - 1);
                                            if (subdomain.equalsIgnoreCase(existingSubdomain)) {
                                                duplicateSubdomain = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (duplicateSubdomain) {
                                        LOGGER.error("Tenant subdomain " + subdomain
                                                + " is already in use for this hosted zone.");
                                        failOnboarding(onboardingId, "Tenant subdomain " + subdomain
                                                + " is already in use for this hosted zone.");
                                        fatal.add(message);
                                        continue;
                                    }
                                }
                            } catch (Route53Exception route53Error) {
                                LOGGER.error("route53:ListResourceRecordSets error", route53Error);
                                LOGGER.error(Utils.getFullStackTrace(route53Error));
                                failOnboarding(onboardingId, "Can't list Route53 record sets "
                                        + route53Error.awsErrorDetails().errorMessage());
                                fatal.add(message);
                                continue;
                            }
                        }
                    }

                    // Check if Quotas will be exceeded.
                    try {
                        Map<String, Object> retMap = checkLimits(context);
                        Boolean passed = (Boolean) retMap.get("passed");
                        String quotaMessage = (String) retMap.get("message");
                        if (!passed) {
                            LOGGER.error("Provisioning will exceed limits. {}", quotaMessage);
                            failOnboarding(onboardingId, "Provisioning will exceed limits " + quotaMessage);
                            fatal.add(message);
                            continue;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Error checking Service Quotas with Private API quotas/check", e);
                        LOGGER.warn((Utils.getFullStackTrace(e)));
                        // TODO retry here and see if Quotas comes back online?
                        retry.add(SQSBatchResponse.BatchItemFailure.builder()
                                .withItemIdentifier(messageId)
                                .build()
                        );
                        continue;
                    }

                    // If we made it to the end without continuing on to the next SQS message,
                    // this message is valid
                    LOGGER.info("Onboarding request validated for {}", onboardingId);
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                            OnboardingEvent.ONBOARDING_VALID.detailType(),
                            Map.of("onboardingId", onboarding.getId())
                    );
                }
            }
        }
        if (!fatal.isEmpty()) {
            LOGGER.info("Moving non-recoverable failures to DLQ");
            SendMessageBatchResponse dlq = sqs.sendMessageBatch(request -> request
                    .queueUrl(ONBOARDING_VALIDATION_DLQ)
                    .entries(fatal.stream()
                            .map(msg -> SendMessageBatchRequestEntry.builder()
                                    .id(msg.getMessageId())
                                    .messageBody(msg.getBody())
                                    .build()
                            )
                            .collect(Collectors.toList())
                    )
            );
            LOGGER.info(dlq.toString());
        }
        return SQSBatchResponse.builder().withBatchItemFailures(retry).build();
    }

    public SQSBatchResponse processTenantConfigQueue(SQSEvent event, Context context) {
        LOGGER.info(Utils.toJson(event));
        if (Utils.isBlank(TENANT_CONFIG_DLQ)) {
            throw new IllegalStateException("Missing required environment variable TENANT_CONFIG_DLQ");
        }
        List<SQSBatchResponse.BatchItemFailure> retry = new ArrayList<>();
        List<SQSEvent.SQSMessage> fatal = new ArrayList<>();

        // A new tenant custom config file was put in the onboarding "temp" folder named with the
        // onboarding id. We need to rename the file with the tenant id so it can be accessed by
        // the application. If the onboarding record doesn't have a tenant assigned yet, we'll retry.
        for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
            String messageId = sqsMessage.getMessageId();
            String messageBody = sqsMessage.getBody();

            LinkedHashMap<String, Object> message = Utils.fromJson(messageBody, LinkedHashMap.class);
            LinkedHashMap<String, Object> detail = (LinkedHashMap<String, Object>) message.get("detail");
            String bucket = (String) ((Map<String, Object>) detail.get("bucket")).get("name");
            String key = (String) ((Map<String, Object>) detail.get("object")).get("key");
            LOGGER.info("Processing resources bucket PUT {}, {}", bucket, key);
            // key will be something like 00temp/77baa019-d95f-4a5c-8c11-6edf1f01fcf8.zip
            // parse the onboarding id out of the path
            String ext = key.substring(key.lastIndexOf("."));
            String onboardingId = key.substring(
                    (key.indexOf(RESOURCES_BUCKET_TEMP_FOLDER) + RESOURCES_BUCKET_TEMP_FOLDER.length()),
                    (key.length() - ext.length())
            );
            Onboarding onboarding = dal.getOnboarding(onboardingId);
            if (onboarding != null) {
                UUID tenantId = onboarding.getTenantId();
                if (tenantId == null) {
                    // It's possible that the file upload finished before a tenant record got
                    // assigned to this onboarding record. We'll retry after a short timeout.
                    LOGGER.warn("No tenant id yet for onboarding {}", onboardingId);
                    retry.add(SQSBatchResponse.BatchItemFailure.builder()
                            .withItemIdentifier(messageId)
                            .build()
                    );
                } else {
                    String destination = "tenants/" + tenantId.toString() + "/" + tenantId.toString() + ext;
                    try {
                        s3.copyObject(request -> request
                                .sourceBucket(bucket)
                                .sourceKey(key)
                                .destinationBucket(bucket)
                                .destinationKey(destination)
                        );
                        s3.deleteObject(request -> request
                                .bucket(bucket)
                                .key(key)
                        );
                        LOGGER.info("Renamed tenant config file to {}", destination);
                        // Save the fact that we have a config file for this onboarding
                        onboarding.setZipFile(destination);
                        dal.updateOnboarding(onboarding);
                    } catch (S3Exception s3Error) {
                        LOGGER.error("Failed to move object {}/{} to {}", bucket, key, destination);
                        LOGGER.error(s3Error.awsErrorDetails().errorMessage());
                        LOGGER.error(Utils.getFullStackTrace(s3Error));
                        retry.add(SQSBatchResponse.BatchItemFailure.builder()
                                .withItemIdentifier(messageId)
                                .build()
                        );
                    }
                }
            } else {
                fatal.add(sqsMessage);
                LOGGER.error("Can't find onboarding record for {}", onboardingId);
            }
        }

        if (!fatal.isEmpty()) {
            LOGGER.info("Moving non-recoverable failures to DLQ");
            SendMessageBatchResponse dlq = sqs.sendMessageBatch(request -> request
                    .queueUrl(TENANT_CONFIG_DLQ)
                    .entries(fatal.stream()
                            .map(msg -> SendMessageBatchRequestEntry.builder()
                                    .id(msg.getMessageId())
                                    .messageBody(msg.getBody())
                                    .build()
                            )
                            .collect(Collectors.toList())
                    )
            );
            LOGGER.info(dlq.toString());
        }
        return SQSBatchResponse.builder().withBatchItemFailures(retry).build();
    }

    public APIGatewayProxyResponseEvent updateProvisionedTenant(Map<String, Object> event, Context context) {
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::updateProvisionedTenant");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        // Unlike when we initially provision a tenant and compare the "global" settings
        // to the potentially overriden per-tenant settings, here we're expecting to be
        // told what to set the compute parameters to and assume the proceeding code that
        // called us has save those values globally or per-tenant as appropriate.
        Map<String, Object> tenant = Utils.fromJson((String) event.get("body"), Map.class);
        Onboarding onboarding = dal.getOnboardingByTenantId((String) tenant.get("id"));
        if (onboarding == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"No onboarding record for tenant id " + tenant.get("id") + "\"}");
        } else {
            UUID tenantId = onboarding.getTenantId();
            String stackId = "";//onboarding.getStackId();

            // We have an inconsistency with how the Lambda source folder is managed.
            // If you update an existing SaaS Boost installation with the installer script
            // it will create a new S3 "folder" for the Lambda code packages to force
            // CloudFormation to update the functions. We are now saving this change as
            // part of the global settings, but we'll need to go fetch it here because it's
            // not part of the onboarding request data nor is it part of the tenant data.
            Map<String, Object> settings = fetchSettingsForTenantUpdate(context);
            final String lambdaSourceFolder = (String) settings.get("SAAS_BOOST_LAMBDAS_FOLDER");
            final String templateUrl = "https://" + settings.get("SAAS_BOOST_BUCKET") + ".s3.amazonaws.com/" + settings.get("ONBOARDING_TEMPLATE");

            List<Parameter> templateParameters = new ArrayList<>();
            templateParameters.add(Parameter.builder().parameterKey("TenantId").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("Environment").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").usePreviousValue(Boolean.TRUE).build());
            if (Utils.isNotBlank(lambdaSourceFolder)) {
                LOGGER.info("Overriding previous template parameter LambdaSourceFolder to {}", lambdaSourceFolder);
                templateParameters.add(Parameter.builder().parameterKey("LambdaSourceFolder").parameterValue(lambdaSourceFolder).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("LambdaSourceFolder").usePreviousValue(Boolean.TRUE).build());
            }
            templateParameters.add(Parameter.builder().parameterKey("DockerHostOS").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("DockerHostInstanceType").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerRepository").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerPort").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerHealthCheckPath").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("CodePipelineRoleArn").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("ArtifactBucket").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("TransitGateway").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("TenantTransitGatewayRouteTable").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("EgressTransitGatewayRouteTable").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("CidrPrefix").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("DomainName").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("SSLCertArnParam").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("HostedZoneId").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("UseEFS").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("MountPoint").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("EncryptEFS").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("EFSLifecyclePolicy").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("UseRDS").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSInstanceClass").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSEngine").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSEngineVersion").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSParameterGroupFamily").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSMasterUsername").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSMasterPasswordParam").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSPort").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSDatabase").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSBootstrap").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("MetricsStream").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("ALBAccessLogsBucket").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("EventBus").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("UseFSx").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxWindowsMountDrive").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxDailyBackupTime").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxBackupRetention").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxThroughputCapacity").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxStorageCapacity").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxWeeklyMaintenanceTime").usePreviousValue(Boolean.TRUE).build());

            Integer taskMemory = (Integer) tenant.get("memory");
            if (taskMemory != null) {
                LOGGER.info("Overriding previous template parameter TaskMemory to {}", taskMemory);
                templateParameters.add(Parameter.builder().parameterKey("TaskMemory").parameterValue(taskMemory.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("TaskMemory").usePreviousValue(Boolean.TRUE).build());
            }

            Integer taskCpu = (Integer) tenant.get("cpu");
            if (taskCpu != null) {
                LOGGER.info("Overriding previous template parameter TaskCPU to {}", taskCpu);
                templateParameters.add(Parameter.builder().parameterKey("TaskCPU").parameterValue(taskCpu.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("TaskCPU").usePreviousValue(Boolean.TRUE).build());
            }

            Integer taskCount = (Integer) tenant.get("min");
            if (taskCount != null) {
                LOGGER.info("Overriding previous template parameter TaskCount to {}", taskCount);
                templateParameters.add(Parameter.builder().parameterKey("TaskCount").parameterValue(taskCount.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("TaskCount").usePreviousValue(Boolean.TRUE).build());
            }

            Integer maxCount = (Integer) tenant.get("max");
            if (maxCount != null) {
                LOGGER.info("Overriding previous template parameter MaxTaskCount to {}", maxCount);
                templateParameters.add(Parameter.builder().parameterKey("MaxTaskCount").parameterValue(maxCount.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("MaxTaskCount").usePreviousValue(Boolean.TRUE).build());
            }

            String billingPlan = (String) tenant.get("planId");
            if (billingPlan != null) {
                LOGGER.info("Overriding previous template parameter BillingPlan to {}", Utils.isBlank(billingPlan) ? "''" : billingPlan);
                templateParameters.add(Parameter.builder().parameterKey("BillingPlan").parameterValue(billingPlan).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("BillingPlan").usePreviousValue(Boolean.TRUE).build());
            }

            // Pass in the subdomain each time because a blank value
            // means delete the Route53 record set
            String subdomain = (String) tenant.get("subdomain");
            if (subdomain == null) {
                subdomain = "";
            }
            LOGGER.info("Setting template parameter TenantSubDomain to {}", subdomain);

            templateParameters.add(Parameter.builder().parameterKey("TenantSubDomain").parameterValue(subdomain).build());
            try {
                UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                        .stackName(stackId)
                        .usePreviousTemplate(Boolean.FALSE)
                        .templateURL(templateUrl)
                        .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                        .parameters(templateParameters)
                        .build()
                );
                stackId = cfnResponse.stackId();
                dal.updateStatus(onboarding.getId(), OnboardingStatus.updating);
                LOGGER.info("OnboardingService::updateProvisionedTenant stack id " + stackId);
            } catch (SdkServiceException cfnError) {
                // CloudFormation throws a 400 error if it doesn't detect any resources in a stack
                // need to be updated. Swallow this error.
                if (cfnError.getMessage().contains("No updates are to be performed")) {
                    LOGGER.warn("cloudformation::updateStack error {}", cfnError.getMessage());
                } else {
                    LOGGER.error("cloudformation::updateStack failed {}", cfnError.getMessage());
                    LOGGER.error(Utils.getFullStackTrace(cfnError));
                    dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                    throw cfnError;
                }
            }

            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody("{\"stackId\": \"" + stackId + "\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::updateProvisionedTenant exec " + totalTimeMillis);
        return response;
    }

    protected void handleAppConfigEvent(Map<String, Object> event, Context context) {
        String detailType = (String) event.get("detail-type");
        if ("Application Configuration Changed".equals(detailType)) {
            handleUpdateInfrastructure(event, context);
        }
    }

    protected void handleUpdateInfrastructure(Map<String, Object> event, Context context) {
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        LOGGER.info("Handling App Config Update Infrastructure Event");

        String stackName;
        // Have to cheat here and ask for a secret until we can authenticate against the public api
        // or we have to copy the settings get by id resource to the private api.
        ApiRequest getSettingsRequest = ApiRequest.builder()
                .resource("settings/SAAS_BOOST_STACK/secret")
                .method("GET")
                .build();
        SdkHttpFullRequest getSettingsApiRequest = ApiGatewayHelper
                .getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, getSettingsRequest);
        LOGGER.info("Fetching SaaS Boost stack name from Settings Service");
        try {
            String getSettingsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                    getSettingsApiRequest, API_TRUST_ROLE, context.getAwsRequestId()
            );
            Map<String, String> getSettingsResponse = Utils.fromJson(getSettingsResponseBody, LinkedHashMap.class);
            stackName = getSettingsResponse.get("value");
        } catch (Exception e) {
            LOGGER.error("Error invoking API settings");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        Map<String, Object> appConfig = getAppConfig(context);
        Map<String, Object> services = (Map<String, Object>) appConfig.get("services");

        String domainName = (String) appConfig.getOrDefault("domainName", "");
        String hostedZone = chooseHostedZoneParameter(stackName, domainName, cfn, route53);

        // If there's an existing hosted zone, we need to tell the AppConfig about it
        // Otherwise, if there's a domain name, CloudFormation will create a hosted zone
        // and the stack listener will tell AppConfig about the newly created one.
        if (Utils.isNotBlank(hostedZone)) {
            LOGGER.info("Publishing appConfig update event for Route53 hosted zone {}", hostedZone);
            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                    "Application Configuration Resource Changed",
                    Map.of("hostedZone", hostedZone));
        }

        LOGGER.info("Calling cloudFormation update-stack --stack-name {}", stackName);
        String stackId = null;
        try {
            UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                    .stackName(stackName)
                    .usePreviousTemplate(Boolean.TRUE)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .parameters(
                            Parameter.builder().parameterKey("SaaSBoostBucket").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("LambdaSourceFolder").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("Environment").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("AdminEmailAddress").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("PublicApiStage").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("PrivateApiStage").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("Version").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("DeployActiveDirectory").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("ADPasswordParam").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("DomainName").parameterValue(domainName).build(),
                            Parameter.builder().parameterKey("HostedZone").parameterValue(hostedZone).build(),
                            Parameter.builder().parameterKey("ApplicationServices").parameterValue(
                                    String.join(",", services.keySet())).build(),
                            Parameter.builder().parameterKey("CreateMacroResources").usePreviousValue(Boolean.TRUE).build()
                    )
                    .build()
            );
            stackId = cfnResponse.stackId();
            LOGGER.info("OnboardingService::updateAppConfig stack id " + stackId);
        } catch (SdkServiceException cfnError) {
            // CloudFormation throws a 400 error if it doesn't detect any resources in a stack
            // need to be updated. Swallow this error.
            if (cfnError.getMessage().contains("No updates are to be performed")) {
                LOGGER.warn("cloudformation::updateStack error {}", cfnError.getMessage());
            } else {
                LOGGER.error("cloudformation::updateStack failed {}", cfnError.getMessage());
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }
        }
    }

    protected void handleTenantEvent(Map<String, Object> event, Context context) {
        String detailType = (String) event.get("detail-type");
        if ("Tenant Deleted".equals(detailType)) {
            handleTenantDeleted(event, context);
        } else if ("Tenant Disabled".equals(detailType)) {
            handleTenantDisabled(event, context);
        } else if ("Tenant Enabled".equals(detailType)) {
            handleTenantEnabled(event, context);
        }
    }

    protected void handleTenantDeleted(Map<String, Object> event, Context context) {
        LOGGER.info("Handling Tenant Deleted Event");
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String tenantId = (String) detail.get("tenantId");
        Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
        if (onboarding != null) {
            LOGGER.info("Deleting application stacks for tenant {}", tenantId);
            for (OnboardingStack stack : onboarding.getStacks()) {
                if (!stack.isBaseStack() && !Arrays.asList("DELETE_COMPLETE", "DELETE_IN_PROGRESS")
                        .contains(stack.getStatus())) {
                    try {
                        LOGGER.info("Deleting stack {}", stack.getName());
                        cfn.deleteStack(request -> request.stackName(stack.getArn()));
                        stack.setStatus("DELETE_IN_PROGRESS");
                    } catch (SdkServiceException cfnError) {
                        if (cfnError.getMessage().contains("does not exist")) {
                            LOGGER.warn("Stack {} does not exist!", stack.getArn());
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                    "Onboarding Stack Status Changed",
                                    Map.of("tenantId", tenantId,
                                            "stackId", stack.getArn(),
                                            "stackStatus", "DELETE_COMPLETE")
                            );
                        } else {
                            LOGGER.error("CloudFormation error", cfnError);
                            LOGGER.error(Utils.getFullStackTrace(cfnError));
                        }
                    }
                }
            }
            onboarding.setStatus(OnboardingStatus.deleting);
            dal.updateOnboarding(onboarding);

            // Let the tenant service know the onboarding status
            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                    "Tenant Onboarding Status Changed",
                    Map.of(
                            "tenantId", tenantId,
                            "onboardingStatus",  onboarding.getStatus()
                    )
            );

            // Just in case we're called with no app stacks
            if (onboarding.appStacksDeleted()) {
                handleBaseProvisioningReadyToDelete(event, context);
            }
        } else {
            // Can't find an onboarding record for this id
            LOGGER.error("Can't find onboarding record for tenant {}", detail.get("tenantId"));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleTenantDisabled(Map<String, Object> event, Context context) {
        LOGGER.info("Handling Tenant Disabled Event");
        enableDisableTenant(event, context, true);
    }

    protected void handleTenantEnabled(Map<String, Object> event, Context context) {
        LOGGER.info("Handling Tenant Enabled Event");
        enableDisableTenant(event, context, false);
    }

    private void enableDisableTenant(Map<String, Object> event, Context context, boolean disable) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String tenantId = (String) detail.get("tenantId");
        Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
        if (onboarding != null) {
            boolean update = false;
            for (OnboardingStack stack : onboarding.getStacks()) {
                if (!stack.isBaseStack()) {
                    LOGGER.info("Calling cloudFormation update-stack --stack-name {}", stack.getName());
                    try {
                        DescribeStacksResponse describeStacksResponse = cfn.describeStacks(r -> r
                                .stackName(stack.getArn())
                        );
                        List<Parameter> parameters = new ArrayList<>();
                        Stack appStack = describeStacksResponse.stacks().get(0);
                        for (Parameter parameter : appStack.parameters()) {
                            if (!"Disable".equals(parameter.parameterKey())) {
                                parameters.add(Parameter.builder()
                                        .parameterKey(parameter.parameterKey())
                                        .usePreviousValue(Boolean.TRUE)
                                        .build()
                                );
                            }
                        }
                        // We disable tenant access to the application by swapping the load balancer listener rules
                        // to a fixed response error string instead of a forward to the target group. We have to do
                        // this on each application service stack because the default load balancer rule in the base
                        // provisioning stack isn't used as long as there are any other listener rules on the ALB.
                        parameters.add(Parameter.builder()
                                .parameterKey("Disable")
                                .parameterValue(String.valueOf(disable))
                                .build()
                        );

                        UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                                .stackName(stack.getArn())
                                .usePreviousTemplate(Boolean.TRUE)
                                .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                                .parameters(parameters)
                                .build()
                        );
                        String stackId = cfnResponse.stackId();
                        if (!stack.getArn().equals(stackId)) {
                            LOGGER.info("Updating stack id does not equal existing stack arn");
                        }
                        update = true;
                        stack.setStatus("UPDATE_IN_PROGRESS");
                    } catch (SdkServiceException cfnError) {
                        // CloudFormation throws a 400 error if it doesn't detect any resources in a stack
                        // need to be updated. Swallow this error.
                        if (cfnError.getMessage().contains("No updates are to be performed")) {
                            LOGGER.warn("cloudformation::updateStack error {}", cfnError.getMessage());
                        } else {
                            LOGGER.error("cloudformation::updateStack failed {}", cfnError.getMessage());
                            LOGGER.error(Utils.getFullStackTrace(cfnError));
                            throw cfnError;
                        }
                    }
                }
            }

            if (update) {
                onboarding.setStatus(OnboardingStatus.updating);
                dal.updateOnboarding(onboarding);

                // Let the tenant service know the onboarding status
                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                        "Tenant Onboarding Status Changed",
                        Map.of(
                                "tenantId", tenantId,
                                "onboardingStatus",  onboarding.getStatus()
                        )
                );
            }
        } else {
            // Can't find an onboarding record for this id
            LOGGER.error("Can't find onboarding record for tenant {}", detail.get("tenantId"));
        }
    }

    protected void handleBaseProvisioningReadyToDelete(Map<String, Object> event, Context context) {
        LOGGER.info("Handling Tenant Deleted Event");
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String tenantId = (String) detail.get("tenantId");
        Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
        if (onboarding != null) {
            boolean update = false;
            if (onboarding.appStacksDeleted()) {
                for (OnboardingStack stack : onboarding.getStacks()) {
                    if (stack.isBaseStack() && !Arrays.asList("DELETE_COMPLETE", "DELETE_IN_PROGRESS")
                            .contains(stack.getStatus())) {
                        try {
                            LOGGER.info("Deleting base stacks for tenant {}", tenantId);
                            cfn.deleteStack(request -> request.stackName(stack.getArn()));
                            update = true;
                            stack.setStatus("DELETE_IN_PROGRESS");
                        } catch (SdkServiceException cfnError) {
                            if (cfnError.getMessage().contains("does not exist")) {
                                LOGGER.warn("Stack {} does not exist!", stack.getArn());
                                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                        "Onboarding Stack Status Changed",
                                        Map.of("tenantId", tenantId,
                                                "stackId", stack.getArn(),
                                                "stackStatus", "DELETE_COMPLETE")
                                );
                            } else {
                                LOGGER.error("CloudFormation error", cfnError);
                                LOGGER.error(Utils.getFullStackTrace(cfnError));
                            }
                        }
                    }
                }

                if (update) {
                    onboarding.setStatus(OnboardingStatus.deleting);
                    dal.updateOnboarding(onboarding);

                    // Let the tenant service know the onboarding status
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            "Tenant Onboarding Status Changed",
                            Map.of(
                                    "tenantId", tenantId,
                                    "onboardingStatus",  onboarding.getStatus()
                            )
                    );
                }
            } else {
                LOGGER.error("App stacks still exist. Can't delete base stacks.");
            }
        } else {
            // Can't find an onboarding record for this id
            LOGGER.error("Can't find onboarding record for tenant {}", detail.get("tenantId"));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void failOnboarding(String onboardingId, String message) {
        failOnboarding(UUID.fromString(onboardingId), message);
    }

    protected void failOnboarding(UUID onboardingId, String message) {
        dal.updateStatus(onboardingId, OnboardingStatus.failed);
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                OnboardingEvent.ONBOARDING_FAILED.detailType(), Map.of("onboardingId", onboardingId,
                        "message", message));
    }

    protected Map<String, Object> fetchSettingsForTenantUpdate(Context context) {
        Map<String, Object> settings;
        try {
            String getSettingResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                    ApiGatewayHelper.getApiRequest(
                            API_GATEWAY_HOST,
                            API_GATEWAY_STAGE,
                            ApiRequest.builder()
                                    .resource("settings?setting=SAAS_BOOST_BUCKET&setting=SAAS_BOOST_LAMBDAS_FOLDER&setting=ONBOARDING_TEMPLATE")
                                    .method("GET")
                                    .build()
                    ),
                    API_TRUST_ROLE,
                    context.getAwsRequestId()
            );
            List<Map<String, Object>> settingsResponse = Utils.fromJson(getSettingResponseBody, ArrayList.class);
            settings = settingsResponse
                    .stream()
                    .collect(Collectors.toMap(
                            setting -> (String) setting.get("name"), setting -> setting.get("value")
                    ));
        } catch (Exception e) {
            LOGGER.error("Error invoking API " + API_GATEWAY_STAGE + "/settings?setting=SAAS_BOOST_BUCKET&setting=SAAS_BOOST_LAMBDAS_FOLDER&setting=ONBOARDING_TEMPLATE");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return settings;
    }

    /*
    Check deployed services against service quotas to make sure limits will not be exceeded.
     */
    protected Map<String, Object> checkLimits(Context context) throws Exception {
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing environment variable API_TRUST_ROLE");
        }
        long startMillis = System.currentTimeMillis();
        Map<String, Object> valMap;
        ApiRequest tenantsRequest = ApiRequest.builder()
                .resource("quotas/check")
                .method("GET")
                .build();
        SdkHttpFullRequest apiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, tenantsRequest);
        String responseBody;
        try {
            LOGGER.info("API call for quotas/check");
            responseBody = ApiGatewayHelper.signAndExecuteApiRequest(apiRequest, API_TRUST_ROLE, context.getAwsRequestId());
//            LOGGER.info("API response for quoatas/check: " + responseBody);
            valMap = Utils.fromJson(responseBody, HashMap.class);
        } catch (Exception e) {
            LOGGER.error("Error invoking API quotas/check");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        LOGGER.debug("checkLimits: Total time to check service limits: " + (System.currentTimeMillis() - startMillis));
        return valMap;
    }

    protected Map<String, Object> getAppConfig(Context context) {
        // Fetch all of the services configured for this application
        LOGGER.info("Calling settings service to fetch app config");
        String getAppConfigResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                ApiGatewayHelper.getApiRequest(
                        API_GATEWAY_HOST,
                        API_GATEWAY_STAGE,
                        ApiRequest.builder()
                                .resource("settings/config")
                                .method("GET")
                                .build()
                ),
                API_TRUST_ROLE,
                context.getAwsRequestId()
        );
        Map<String, Object> appConfig = Utils.fromJson(getAppConfigResponseBody, LinkedHashMap.class);
        return appConfig;
    }

    protected Map<String, Object> getTenant(UUID tenantId, Context context) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Can't fetch blank tenant id");
        }
        return getTenant(tenantId.toString(), context);
    }

    protected Map<String, Object> getTenant(String tenantId, Context context) {
        if (Utils.isBlank(tenantId)) {
            throw new IllegalArgumentException("Can't fetch blank tenant id");
        }
        // Fetch the tenant for this onboarding
        LOGGER.info("Calling tenant service to fetch tenant {}", tenantId);
        String getTenantResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                ApiGatewayHelper.getApiRequest(
                        API_GATEWAY_HOST,
                        API_GATEWAY_STAGE,
                        ApiRequest.builder()
                                .resource("tenants/" + tenantId)
                                .method("GET")
                                .build()
                ),
                API_TRUST_ROLE,
                context.getAwsRequestId()
        );
        Map<String, Object> tenant = Utils.fromJson(getTenantResponseBody, LinkedHashMap.class);
        return tenant;
    }

    protected static Map<String, Integer> getPathPriority(Map<String, Object> appConfig) {
        Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
        Map<String, Integer> pathLength = new HashMap<>();

        // Collect the string length of the path for each public service
        for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
            String serviceName = serviceConfig.getKey();
            Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
            Boolean isPublic = (Boolean) service.get("public");
            if (isPublic) {
                String pathPart = Objects.toString(service.get("path"), "");
                pathLength.put(serviceName, pathPart.length());
            }
        }
        // Order the services by longest (most specific) to shortest (least specific) path length
        LinkedHashMap<String, Integer> pathPriority = pathLength.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (value1, value2) -> value1, LinkedHashMap::new
                ));
        // Set the ALB listener rule priority so that they most specific paths (the longest ones) have
        // a higher priority than the less specific paths so the rules are evaluated in the proper order
        // i.e. a path of /feature* needs to be evaluate before a catch all path of /* or you'll never
        // route to the /feature* rule because /* will have already matched
        int priority = 0;
        for (String publicService : pathPriority.keySet()) {
            pathPriority.put(publicService, ++priority);
        }
        return pathPriority;
    }

    // separate out route53 client for testability
    protected static String getExistingHostedZone(String domainName, Route53Client route53Client) {
        String existingHostedZone = "";
        if (Utils.isNotEmpty(domainName)) {
            String nextDnsName = null;
            String nextHostedZone = null;
            ListHostedZonesByNameResponse response;
            do {
                response = route53Client.listHostedZonesByName(ListHostedZonesByNameRequest.builder()
                        .dnsName(nextDnsName)
                        .hostedZoneId(nextHostedZone)
                        .maxItems("100")
                        .build()
                );
                nextDnsName = response.nextDNSName();
                nextHostedZone = response.nextHostedZoneId();
                if (response.hasHostedZones()) {
                    for (HostedZone hostedZone : response.hostedZones()) {
                        // If there are multiple hosted zones for a given domain name, what should we do?
                        // We could sort the response by "CallerReference" which appears to be a timestamp.
                        // In the documentation, we can just tell people if they're suffering from
                        // https://github.com/awslabs/aws-saas-boost/issues/74 to go clean things up manually first?
                        if (hostedZone.name().startsWith(domainName)
                                && hostedZone.config() != null
                                && Boolean.FALSE.equals(hostedZone.config().privateZone())) {
                            // Created by SaaS Boost CloudFormation?
                            // TODO do we do this check? seems safest for now.
                            if ((domainName + " Public DNS zone").equals(hostedZone.config().comment())) {
                                LOGGER.info("Found existing hosted zone {} for domain {}", hostedZone, domainName);
                                // Hosted zone id will be prefixed with /hostedzone/
                                existingHostedZone = hostedZone.id().replace("/hostedzone/", "");
                                break;
                            }
                        }
                    }
                }
            } while (response.isTruncated());
        }
        return existingHostedZone;
    }

    protected static String chooseHostedZoneParameter(
                String stackName, 
                String domainName, 
                CloudFormationClient cfnClient, // separate out clients for testability
                Route53Client route53Client) {
        if (Utils.isNotBlank(domainName)) {
            final String hostedZoneCfnName = "PublicDomainHostedZone";
            // need to find the core stack to find whether we already created a hostedZone
            // this call might throw a CloudFormationException if the stack or the core resource does not exist
            // in either case, we couldn't possibly continue with our updateInfrastructure operation, so allow
            // it to bubble up into the logs. unfortunately there's currently no way to percolate a serious fatal
            // state error through the system
            final StackResourceDetail coreStackResourceDetail = cfnClient.describeStackResource(
                    DescribeStackResourceRequest.builder()
                            .stackName(stackName)
                            .logicalResourceId("core")
                            .build())
                    .stackResourceDetail();
            boolean saasBoostOwnsHostedZone = false;
            try {
                cfnClient.describeStackResource(DescribeStackResourceRequest.builder()
                        .stackName(coreStackResourceDetail.physicalResourceId())
                        .logicalResourceId(hostedZoneCfnName)
                        .build());
                // because we didn't throw, the resource must exist. so saasBoost has created
                // a hostedZone for this environment
                saasBoostOwnsHostedZone = true;
            } catch (CloudFormationException cfne) {
                // if the exception is that the resource does not exist, that means that we have not created
                // a hosted zone in this environment. any other exception is unexpected and should be rethrown
                if (!cfne.getMessage().contains("Resource " + hostedZoneCfnName + " does not exist")) {
                    throw new RuntimeException(cfne);
                }
            }

            if (!saasBoostOwnsHostedZone) {
                String existingHostedZone = getExistingHostedZone(domainName, route53Client);
                if (Utils.isNotBlank(existingHostedZone)) {
                    /*
                     * If there exists a hostedZone and we don't own it, we want to pass that hostedZone
                     * name to the stack, since that means it won't be created
                     */
                    return existingHostedZone;
                }
            } else {
                /*
                 * If there exists a hostedZone and we DO own it, we don't want to pass the hostedZone
                 * name to the stack, since the condition to create the hostedZone will evaluate to 
                 * false and the owned hostedZone will be deleted
                 * 
                 * If there does not exist a hostedZone we won't own it (because it doesn't exist) but
                 * either way we would not want to pass a hostedZone name in so that the stack
                 * creates one. This might be happening on an initial configuration of domainName in
                 * AppConfig.
                 */
            }
        }
        return "";
    }
}