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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.codepipeline.model.Tag;
import software.amazon.awssdk.services.directory.DirectoryClient;
import software.amazon.awssdk.services.directory.model.DescribeDirectoriesResponse;
import software.amazon.awssdk.services.directory.model.DirectoryDescription;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.EcrException;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
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
    private static final String EVENT_SOURCE = "saas-boost";
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String SAAS_BOOST_METRICS_STREAM = System.getenv("SAAS_BOOST_METRICS_STREAM");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_APP_CLIENT = System.getenv("API_APP_CLIENT");
    private static final String SAAS_BOOST_BUCKET = System.getenv("SAAS_BOOST_BUCKET");
    private static final String ONBOARDING_TABLE = System.getenv("ONBOARDING_TABLE");
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
    private final CodePipelineClient codePipeline;
    private final DirectoryClient ds;
    private final SecretsManagerClient secrets;
    private ApiGatewayHelper api;

    public OnboardingService() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new OnboardingServiceDAL();
        this.cfn = Utils.sdkClient(CloudFormationClient.builder(), CloudFormationClient.SERVICE_NAME);
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        this.ecr = Utils.sdkClient(EcrClient.builder(), EcrClient.SERVICE_NAME);
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        try {
            String presignerEndpoint = "https://" + s3.serviceName() + "."
                    + Region.of(AWS_REGION)
                    + "."
                    + Utils.endpointSuffix(AWS_REGION);
            this.presigner = S3Presigner.builder()
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .region(Region.of(AWS_REGION))
                    .endpointOverride(new URI(presignerEndpoint))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.route53 = Utils.sdkClient(Route53Client.builder(), Route53Client.SERVICE_NAME);
        this.sqs = Utils.sdkClient(SqsClient.builder(), SqsClient.SERVICE_NAME);
        this.codePipeline = Utils.sdkClient(CodePipelineClient.builder(), CodePipelineClient.SERVICE_NAME);
        this.ds = Utils.sdkClient(DirectoryClient.builder(), DirectoryClient.SERVICE_NAME);
        this.secrets = Utils.sdkClient(SecretsManagerClient.builder(), SecretsManagerClient.SERVICE_NAME);
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
        if (queryParams != null && queryParams.containsKey("tenantId")
                && Utils.isNotBlank(queryParams.get("tenantId"))) {
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
                    case ONBOARDING_BASE_UPDATED:
                        LOGGER.info("Handling Onboarding Base Updated");
                        handleOnboardingBaseUpdated(event, context);
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
                    default:
                        LOGGER.error("Unknown Onboarding Event!");
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

    protected ApiGatewayHelper apiGatewayHelper() {
        if (this.api == null) {
            // Fetch the app client details from SecretsManager
            LinkedHashMap<String, String> clientDetails;
            try {
                GetSecretValueResponse response = secrets.getSecretValue(request -> request
                        .secretId(API_APP_CLIENT)
                );
                clientDetails = Utils.fromJson(response.secretString(), LinkedHashMap.class);
            } catch (SdkServiceException secretsManagerError) {
                LOGGER.error(Utils.getFullStackTrace(secretsManagerError));
                throw secretsManagerError;
            }
            // Build an API helper with the app client
            this.api = ApiGatewayHelper.builder()
                    .host(API_GATEWAY_HOST)
                    .stage(API_GATEWAY_STAGE)
                    .clientId(clientDetails.get("client_id"))
                    .clientSecret(clientDetails.get("client_secret"))
                    .tokenEndpoint(clientDetails.get("token_endpoint"))
                    .build();
        }
        return this.api;
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
        if (Utils.isBlank(API_APP_CLIENT)) {
            throw new IllegalStateException("Missing required environment variable API_APP_CLIENT");
        }
        if (OnboardingEvent.validate(event)) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                if (onboarding.getTenantId() != null) {
                    LOGGER.error("Unexpected validated onboarding request {} with existing tenantId",
                            onboarding.getId());
                    // TODO throw illegal state?
                }
                if (OnboardingStatus.validating != onboarding.getStatus()) {
                    // TODO Also illegal state
                }
                onboarding = dal.updateStatus(onboarding.getId(), OnboardingStatus.validated);
                // Call the tenant service synchronously to insert the new tenant record
                LOGGER.info("Calling tenant service insert tenant API");
                LOGGER.info(Utils.toJson(onboarding.getRequest()));
                String insertTenantResponseBody = apiGatewayHelper()
                        .authorizedRequest("POST", "tenants", Utils.toJson(onboarding.getRequest()));
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
        if (Utils.isBlank(API_APP_CLIENT)) {
            throw new IllegalStateException("Missing required environment variable API_APP_CLIENT");
        }
        if (Utils.isBlank(ONBOARDING_STACK_SNS)) {
            throw new IllegalArgumentException("Missing required environment variable ONBOARDING_STACK_SNS");
        }
        if (OnboardingEvent.validate(event, "tenant")) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                String tenantId = onboarding.getTenantId().toString();
                String cidrBlock = dal.getCidrBlock(onboarding.getTenantId());
                if (Utils.isBlank(cidrBlock)) {
                    // TODO rethrow to DLQ?
                    failOnboarding(onboarding.getId(), "Can't find assigned CIDR for tenant " + tenantId);
                    return;
                }

                // Make a synchronous call to the settings service for the app config
                Map<String, Object> appConfig = getAppConfig(context);
                if (null == appConfig) {
                    // TODO rethrow to DLQ?
                    failOnboarding(onboarding.getId(), "Settings getAppConfig API call failed");
                    return;
                }

                // And parameters specific to this tenant
                Map<String, Object> tenant = (Map<String, Object>) detail.get("tenant");

                OnboardingBaseStackParameters parameters = new OnboardingBaseStackParameters();
                parameters.setProperty("Environment", SAAS_BOOST_ENV);
                parameters.setProperty("DomainName", (String) appConfig.get("domainName"));
                parameters.setProperty("HostedZoneId", (String) appConfig.get("hostedZone"));
                parameters.setProperty("SSLCertificateArn", (String) appConfig.get("sslCertificate"));
                parameters.setProperty("TenantId", tenantId);
                parameters.setProperty("TenantSubDomain", (String) tenant.get("subdomain"));
                parameters.setProperty("CidrPrefix",
                        cidrBlock.substring(0, cidrBlock.indexOf(".", cidrBlock.indexOf(".") + 1)));
                parameters.setProperty("Tier", (String) tenant.get("tier"));
                parameters.setProperty("PrivateServices", Boolean.toString(hasPrivateServices(appConfig)));
                parameters.setProperty("DeployActiveDirectory", Boolean.toString(hasActiveDirectoryConfigured(appConfig)));

                String tenantShortId = tenantId.substring(0, 8);
                String stackName = "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantShortId;

                // Now run the onboarding stack to provision the infrastructure for this tenant
                LOGGER.info("OnboardingService::provisionTenant create stack " + stackName);
                String templateUrl = "https://" + SAAS_BOOST_BUCKET + ".s3." + AWS_REGION
                        + "." + Utils.endpointSuffix(AWS_REGION) + "/tenant-onboarding.yaml";
                String stackId;
                try {
                    CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                            .stackName(stackName)
                            .disableRollback(false)
                            .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                            .notificationARNs(ONBOARDING_STACK_SNS)
                            .templateURL(templateUrl)
                            .parameters(parameters.forCreate())
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
                                    LOGGER.info("Onboarding base stacks provisioned");
                                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                                            OnboardingEvent.ONBOARDING_BASE_PROVISIONED.detailType(),
                                            Map.of("onboardingId", onboarding.getId())
                                    );
                                } else if (stack.isUpdated()) {
                                    LOGGER.info("Onboarding base stacks updated");
                                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                            OnboardingEvent.ONBOARDING_BASE_UPDATED.detailType(),
                                            Map.of("onboardingId", onboarding.getId())
                                    );
                                }
                            } else if (!stack.isBaseStack() && onboarding.stacksComplete()) {
                                LOGGER.info("All onboarding stacks provisioned");
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
        if (Utils.isBlank(API_APP_CLIENT)) {
            throw new IllegalStateException("Missing required environment variable API_APP_CLIENT");
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
                OnboardingAppStackParameters parameters = new OnboardingAppStackParameters();
                parameters.setProperty("Environment", SAAS_BOOST_ENV);
                // CloudFormation can't natively add more than one Capacity Provider to an ECS cluster
                // and we need a different CP for each service in the shared cluster. We're using the
                // Onboarding Service's database to hold state that the custom resource can read during
                // each service provisioning in order to update the cluster's list of capacity providers.
                parameters.setProperty("OnboardingDdbTable", ONBOARDING_TABLE);
                // Currently using the SaaS Boost event bus for Billing Service metering calls
                parameters.setProperty("EventBus", Objects.toString(SAAS_BOOST_EVENT_BUS, ""));
                // Currently using a Kinesis Firehose for the Analytics Service metrics calls
                parameters.setProperty("MetricsStream", Objects.toString(SAAS_BOOST_METRICS_STREAM, ""));

                // First get the tenant specific parameters created during base provisioning.
                // This requires a call to the Tenant Service.
                try {
                    onboardingAppStackTenantParams(onboarding, parameters, context);
                } catch (RuntimeException e) {
                    LOGGER.error(e.getMessage());
                    LOGGER.error(Utils.getFullStackTrace(e));
                    failOnboarding(onboarding.getId(), e.getMessage());
                    return;
                }

                // Now, load up the app config from the Settings Service
                Map<String, Object> appConfig = getAppConfig(context);
                if (appConfig != null) {
                    // TODO Use the app name for tagging
                    String applicationName = (String) appConfig.get("name");

                    // Need to use these across all services so we'll pass them in to be mutated in place
                    // by the onboardingAppStack*Params methods
                    Map<String, Integer> pathPriority = getPathPriority(appConfig);
                    Properties serviceDiscovery = new Properties();

                    // And for each service in the application, load up all of the CloudFormation template
                    // parameters and call create stack.
                    Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
                    for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
                        createOnboardingAppStack(onboarding, serviceConfig, pathPriority, parameters,
                                serviceDiscovery, context);
                    }

                    // Write the application-wide environment variables to S3 so each service container can load it up
                    try {
                        savePropertiesFileToS3(s3, RESOURCES_BUCKET, "ServiceDiscovery.env",
                                parameters.getProperty("TenantId"), serviceDiscovery);
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(e));
                        failOnboarding(onboarding.getId(), e.getMessage());
                    }
                }
            } else {
                LOGGER.error("No onboarding record for {}", detail.get("onboardingId"));
            }
        } else {
            LOGGER.error("Missing onboardingId in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleOnboardingBaseUpdated(Map<String, Object> event, Context context) {
        // The AppConfig changed in some way with provisioned tenants. We've finished updating the base
        // infrastructure and now we need to update the app services.
        if (OnboardingEvent.validate(event)) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            Onboarding onboarding = dal.getOnboarding((String) detail.get("onboardingId"));
            if (onboarding != null) {
                // TODO we should probably cache appConfig so we don't have to make this call all the time
                // TODO or should we have the event include the appConfig in its detail?
                Map<String, Object> appConfig = getAppConfig(context);
                Map<String, Object> services = (Map<String, Object>) appConfig.get("services");

                // We need to recalculate the path priority rules for the  public services
                Map<String, Integer> pathPriority = getPathPriority(appConfig);

                // We may need to update the service discovery environment variables if private services were added
                Properties serviceDiscovery = new Properties();

                List<String> update = new ArrayList<>();
                for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
                    int found = 0;
                    String serviceName = serviceConfig.getKey();
                    Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
                    for (OnboardingStack stack : onboarding.getStacks()) {
                        // Skip the base stack because we've already updated it
                        if (stack.isBaseStack()) {
                            continue;
                        }
                        if (serviceName.equals(stack.getService())) {
                            found++;
                            if ((Boolean) service.get("public")) {
                                Integer publicPathRulePriority = pathPriority.get(serviceName);
                                // TODO this will break if there's an existing ALB listener rule with this priority
                                LOGGER.info("Calling cloudFormation update-stack --stack-name {}", stack.getName());
                                try {
                                    UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                                            .stackName(stack.getArn())
                                            .usePreviousTemplate(Boolean.TRUE)
                                            .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                                            .parameters(new OnboardingAppStackParameters().forUpdate(
                                                    Map.of(
                                                            "PublicPathRulePriority", publicPathRulePriority.toString()
                                                    )
                                            ))
                                            .build()
                                    );
                                    String stackId = cfnResponse.stackId();
                                    if (!stack.getArn().equals(stackId)) {
                                        LOGGER.error("Updating stack id does not equal existing stack arn");
                                    }
                                    stack.setStatus("UPDATE_IN_PROGRESS");
                                    update.add(stackId);
                                    onboarding.setStatus(OnboardingStatus.updating);
                                    dal.updateOnboarding(onboarding);
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
                            break;
                        }
                    }
                    if (update.isEmpty()) {
                        LOGGER.warn("No publicly addressable services found to update");
                    }

                    if (found == 0) {
                        // New service config
                        LOGGER.info("Adding new service {} for tenant {}", serviceName, onboarding.getTenantId());
                        OnboardingAppStackParameters parameters = new OnboardingAppStackParameters();
                        parameters.setProperty("Environment", SAAS_BOOST_ENV);
                        parameters.setProperty("OnboardingDdbTable", ONBOARDING_TABLE);
                        parameters.setProperty("EventBus", SAAS_BOOST_EVENT_BUS);
                        parameters.setProperty("MetricsStream", SAAS_BOOST_METRICS_STREAM);

                        // First get the tenant specific parameters created during base provisioning.
                        // This requires a call to the Tenant Service.
                        try {
                            onboardingAppStackTenantParams(onboarding, parameters, context);
                        } catch (RuntimeException e) {
                            LOGGER.error(e.getMessage());
                            LOGGER.error(Utils.getFullStackTrace(e));
                            failOnboarding(onboarding.getId(), e.getMessage());
                            return;
                        }
                        if (!update.isEmpty()) {
                            // If we had to update existing public services, we really should wait for
                            // those stacks to be in UPDATE_COMPLETE prior to creating any new service
                            // stacks to avoid race conditions on the load balancer rule priority values.
                            //for (String updatingStack : update) {
                            //    cfn.waiter().waitUntilStackUpdateComplete(request -> request
                            //            .stackName(updatingStack));
                            //}
                            // However, due to our current stack status change listener, we'd mark the
                            // onboarding record provisioned prematurely because it won't have the new
                            // service stack in it yet (happens has part of createOnboardingAppStack call
                            // below) but all of the non base stacks will be in a completed state.
                            // TODO create a more fine grained set of events to track this use case specifically
                            try {
                                // For now we'll arbitrarily give the update stack calls a head start
                                LOGGER.info("Pausing new service stack creation for existing services to update");
                                Thread.sleep(10000 * update.size());
                            } catch (InterruptedException cantSleep) {
                                LOGGER.error("Unable to pause thread");
                                Thread.currentThread().interrupt();
                            }
                        }
                        OnboardingStack newServiceStack = createOnboardingAppStack(onboarding, serviceConfig,
                                pathPriority, parameters, serviceDiscovery, context);
                        update.add(newServiceStack.getArn());
                    }
                }

                // Write the application-wide environment variables to S3 so each service container can load it up
                try {
                    savePropertiesFileToS3(s3, RESOURCES_BUCKET, "ServiceDiscovery.env",
                            onboarding.getTenantId().toString(), serviceDiscovery);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                    LOGGER.error(Utils.getFullStackTrace(e));
                    failOnboarding(onboarding.getId(), e.getMessage());
                    return;
                }

                if (!update.isEmpty()) {
                    onboarding.setStatus(OnboardingStatus.updating);
                    dal.updateOnboarding(onboarding);

                    // Let the tenant service know the onboarding status
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            "Tenant Onboarding Status Changed",
                            Map.of(
                                    "tenantId", onboarding.getTenantId(),
                                    "onboardingStatus",  onboarding.getStatus()
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
                    Map<String, Object> serviceCompute = (Map<String, Object>) service.get("compute");
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            "Workload Ready For Deployment",
                            Map.of(
                                    "tenantId", onboarding.getTenantId(),
                                    "repository-name", serviceCompute.get("containerRepo"),
                                    "image-tag", serviceCompute.get("containerTag")
                            )
                    );
                }

                // Publish an event to subscribe this tenant to the billing system if needed
                if (Utils.isNotBlank(onboarding.getRequest().getBillingPlan())) {
                    // TODO Onboarding probably shouldn't be sending the plan in -- just the tier and/or tenant
                    LOGGER.info("Publishing billing setup event for tenant {}", onboarding.getTenantId());
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            "Billing Tenant Setup",
                            Map.of("tenantId", onboarding.getTenantId(),
                                    "planId", onboarding.getRequest().getBillingPlan()
                            )
                    );
                } else {
                    LOGGER.info("Skipping billing setup, no billing plan for tenant {}", onboarding.getTenantId());
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
                String pipelineArnFromResource = resources.get(0);
                LOGGER.info("pipelineArnFromResource = {} when handle onboarding pipeline status changed", pipelineArnFromResource);
                final String[] lambdaArn = context.getInvokedFunctionArn().split(":");
                final String partition = lambdaArn[1];
                String pipelineArn = pipelineArnFromResource.replace("arn:aws:", "arn:" + partition + ":");
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
                        Map<String, Object> serviceCompute = (Map<String, Object>) service.get("compute");
                        String ecrRepo = (String) serviceCompute.get("containerRepo");
                        String imageTag = (String) serviceCompute.getOrDefault("containerTag", "latest");
                        if (Utils.isNotBlank(ecrRepo)) {
                            try {
                                ListImagesResponse dockerImages = ecr.listImages(request -> request
                                        .repositoryName(ecrRepo));
                                boolean imageAvailable = false;
                                // ListImagesResponse::hasImageIds will return true if the imageIds object is not null
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
                                    LOGGER.warn("Application Service {} does not have an image tagged {}",
                                            serviceName, imageTag);
                                    missingImages++;
                                }
                            } catch (EcrException ecrError) {
                                LOGGER.error("ecr:ListImages error", ecrError);
                                LOGGER.error(Utils.getFullStackTrace(ecrError));
                                // TODO do we bail here or retry?
                                failOnboarding(onboardingId, "Can't list images from ECR "
                                        + ecrError.awsErrorDetails().errorMessage());
                                fatal.add(message);
                                continue sqsMessageLoop;
                            }
                        } else {
                            // TODO no repo defined for this service yet...
                            LOGGER.warn("Application Service {} has no container image repository defined",
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

    protected void handleAppConfigEvent(Map<String, Object> event, Context context) {
        String detailType = (String) event.get("detail-type");
        if ("Application Configuration Changed".equals(detailType)) {
            handleUpdateInfrastructure(event, context);
        } else if ("Application Configuration Update Completed".equals(detailType)) {
            handleUpdateTenantBaseInfrastructure(event, context);
        }
    }

    protected void handleUpdateInfrastructure(Map<String, Object> event, Context context) {
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_APP_CLIENT)) {
            throw new IllegalStateException("Missing required environment variable API_APP_CLIENT");
        }
        LOGGER.info("Handling App Config Update Infrastructure Event");

        String stackName = getSetting(context, "SAAS_BOOST_STACK");

        Map<String, Object> appConfig = getAppConfig(context);
        Map<String, Object> services = (Map<String, Object>) appConfig.get("services");

        LOGGER.info("Calling cloudFormation update-stack --stack-name {}", stackName);
        String stackId;
        try {
            UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                    .stackName(stackName)
                    .usePreviousTemplate(Boolean.TRUE)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .parameters(new CoreStackParameters().forUpdate(
                            Map.of(
                                    "ApplicationServices", String.join(",", services.keySet()),
                                    "AppExtensions",  collectAppExtensions(appConfig)
                            )
                    ))
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

    protected void handleUpdateTenantBaseInfrastructure(Map<String, Object> event, Context context) {
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_APP_CLIENT)) {
            throw new IllegalStateException("Missing required environment variable API_APP_CLIENT");
        }
        LOGGER.info("Handling App Config Update Tenant Base Infrastructure Event");

        List<Map<String, Object>> provisionedTenants = getProvisionedTenants(context);
        if (!provisionedTenants.isEmpty()) {
            LOGGER.info("Updating {} provisioned tenants", provisionedTenants.size());
            Map<String, Object> appConfig = getAppConfig(context);

            List<Parameter> parameters = new OnboardingBaseStackParameters().forUpdate(
                    Map.of(
                            "DomainName", (String) appConfig.get("domainName"),
                            "HostedZoneId", (String) appConfig.get("hostedZone"),
                            "SSLCertificateArn", (String) appConfig.get("sslCertificate"),
                            "PrivateServices", Boolean.toString(hasPrivateServices(appConfig)),
                            "DeployActiveDirectory", Boolean.toString(hasActiveDirectoryConfigured(appConfig))
                    )
            );

            for (Map<String, Object> tenant : provisionedTenants) {
                Onboarding onboarding = dal.getOnboardingByTenantId((String) tenant.get("id"));
                if (onboarding != null) {
                    OnboardingStack baseStack = onboarding.baseStack();
                    if (baseStack != null) {
                        boolean update = false;
                        String stackName = baseStack.getName();
                        LOGGER.info("Calling cloudFormation update-stack --stack-name {}", stackName);
                        try {
                            cfn.updateStack(UpdateStackRequest.builder()
                                    .stackName(stackName)
                                    .usePreviousTemplate(Boolean.TRUE)
                                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                                    .parameters(parameters)
                                    .build()
                            );
                            baseStack.setStatus("UPDATE_IN_PROGRESS");
                            onboarding.setStatus(OnboardingStatus.updating);
                            dal.updateOnboarding(onboarding);
                            update = true;
                        } catch (SdkServiceException cfnError) {
                            // CloudFormation throws a 400 error if it doesn't detect any resources in a stack
                            // need to be updated.
                            if (cfnError.getMessage().contains("No updates are to be performed")) {
                                LOGGER.warn("cloudformation::updateStack {}", cfnError.getMessage());
                                // However, there may be changes to the updated app config that effect
                                // the services, so we need to publish that the base stack has been
                                // updated successfully
                                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                        OnboardingEvent.ONBOARDING_BASE_UPDATED.detailType(),
                                        Map.of("onboardingId", onboarding.getId())
                                );
                            } else {
                                LOGGER.error("cloudformation::updateStack {}", cfnError.getMessage());
                                LOGGER.error(Utils.getFullStackTrace(cfnError));
                                throw cfnError;
                            }
                        }

                        if (update) {
                            // Let the tenant service know the onboarding status
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                    "Tenant Onboarding Status Changed",
                                    Map.of(
                                            "tenantId", tenant.get("id"),
                                            "onboardingStatus",  onboarding.getStatus()
                                    )
                            );
                        }
                    } else {
                        LOGGER.error("Can't find base stack in onboarding record for tenant {}", tenant.get("id"));
                    }
                } else {
                    LOGGER.error("Can't find onboarding record for tenant {}", tenant.get("id"));
                }
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
                // We disable tenant access to the application by swapping the load balancer listener rules
                // to a fixed response error string instead of a forward to the target group. We have to do
                // this on each application service stack because the default load balancer rule in the base
                // provisioning stack isn't used as long as there are any other listener rules on the ALB.
                if (!stack.isBaseStack()) {
                    LOGGER.info("Calling cloudFormation update-stack --stack-name {}", stack.getName());
                    try {
                        UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                                .stackName(stack.getArn())
                                .usePreviousTemplate(Boolean.TRUE)
                                .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                                .parameters(new OnboardingAppStackParameters().forUpdate(
                                        Map.of(
                                                "Disable", String.valueOf(disable)
                                        )
                                ))
                                .build()
                        );
                        String stackId = cfnResponse.stackId();
                        if (!stack.getArn().equals(stackId)) {
                            LOGGER.error("Updating stack id does not equal existing stack arn");
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

    protected Map<String, Object> checkLimits(Context context) throws Exception {
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_APP_CLIENT)) {
            throw new IllegalStateException("Missing environment variable API_APP_CLIENT");
        }
        long startMillis = System.currentTimeMillis();
        Map<String, Object> valMap;
        String responseBody;
        try {
            LOGGER.info("API call for quotas/check");
            responseBody = apiGatewayHelper().authorizedRequest("GET", "quotas/check");
            //LOGGER.info("API response for quoatas/check: " + responseBody);
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
        String getAppConfigResponseBody = apiGatewayHelper().authorizedRequest("GET", "settings/config");
        Map<String, Object> appConfig = Utils.fromJson(getAppConfigResponseBody, LinkedHashMap.class);
        return appConfig;
    }

    protected String getSetting(Context context, String setting) {
        LOGGER.info("Calling settings service to fetch setting {}", setting);
        String getAppConfigResponseBody = apiGatewayHelper().authorizedRequest("GET", "settings/" + setting);
        Map<String, Object> settingObject = Utils.fromJson(getAppConfigResponseBody, LinkedHashMap.class);
        return (String) settingObject.get("value");
    }

    protected Map<String, String> getSettings(Context context, String... settings) {
        LOGGER.info("Calling settings service to fetch settings {}", settings);
        StringBuilder queryParams = new StringBuilder();
        for (Iterator<String> iter = Arrays.stream(settings).iterator(); iter.hasNext();) {
            queryParams.append("setting=");
            queryParams.append(iter.next());
            if (iter.hasNext()) {
                queryParams.append("&");
            }
        }

        String getSettingsResponseBody = apiGatewayHelper().authorizedRequest("GET", "settings?" + queryParams.toString());
        List<Map<String, String>> settingsList = Utils.fromJson(getSettingsResponseBody, ArrayList.class);
        return settingsList.stream()
                .collect(Collectors.toMap(
                        entry -> entry.get("name"),
                        entry -> entry.get("value")
                )
        );
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
        String getTenantResponseBody = apiGatewayHelper().authorizedRequest("GET", "tenants/" + tenantId);
        Map<String, Object> tenant = Utils.fromJson(getTenantResponseBody, LinkedHashMap.class);
        return tenant;
    }

    protected List<Map<String, Object>> getProvisionedTenants(Context context) {
        // Fetch all of the provisioned tenants
        LOGGER.info("Calling tenant service to fetch all provisioned tenants");
        String getTenantsResponseBody = apiGatewayHelper().authorizedRequest("GET", "tenants?status=provisioned");
        List<Map<String, Object>> tenants = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
        if (tenants == null) {
            tenants = new ArrayList<>();
        }
        return tenants;
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
        // Set the ALB listener rule priority so that the most specific paths (the longest ones) have
        // a higher priority than the less specific paths so the rules are evaluated in the proper order
        // i.e. a path of /feature* needs to be evaluate before a catch all path of /* or you'll never
        // route to the /feature* rule because /* will have already matched
        int priority = 0;
        for (String publicService : pathPriority.keySet()) {
            pathPriority.put(publicService, ++priority);
        }
        return pathPriority;
    }

    protected static String collectAppExtensions(Map<String, Object> appConfig) {
        Set<String> appExtensions = new HashSet<>();
        Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
        for (Map.Entry<String, Object> service : services.entrySet()) {
            Map<String, Object> serviceConfig = (Map<String, Object>) service.getValue();
            if (serviceConfig.containsKey("s3") && serviceConfig.get("s3") != null) {
                appExtensions.add("s3");
            }
        }
        return String.join(",", appExtensions);
    }

    protected static boolean hasPrivateServices(Map<String, Object> appConfig) {
        Map<String, Map<String, Object>> services =
                (Map<String, Map<String, Object>>) appConfig.get("services");
        boolean privateServices = false;
        for (Map<String, Object> service : services.values()) {
            privateServices = privateServices || !(Boolean) service.get("public");
        }
        return privateServices;
    }

    protected static boolean hasActiveDirectoryConfigured(Map<String, Object> appConfig) {
        Map<String, Map<String, Object>> services =
                (Map<String, Map<String, Object>>) appConfig.get("services");
        boolean configureAd = false;
        for (Map<String, Object> service : services.values()) {
            Map<String, Object> filesystem = (Map<String, Object>) service.get("filesystem");
            if (filesystem != null) {
                configureAd = configureAd || (Boolean) filesystem.getOrDefault("configureManagedAd", false);
            }
        }
        return configureAd;
    }

    protected void onboardingAppStackTenantParams(Onboarding onboarding, OnboardingAppStackParameters parameters,
                                                   Context context) {
        String tenantId = onboarding.getTenantId().toString();
        Map<String, Object> tenant = getTenant(tenantId, context);
        // TODO tenant == null means tenant API call failed? retry?
        if (tenant != null) {
            Map<String, Map<String, String>> tenantResources = (Map<String, Map<String, String>>) tenant.get("resources");
            try {
                parameters.setProperty("TenantId", (String) tenant.get("id"));
                parameters.setProperty("Tier", (String) tenant.get("tier"));
                parameters.setProperty("VPC", tenantResources.get("VPC").get("name"));
                parameters.setProperty("SubnetPrivateA", tenantResources.get("PRIVATE_SUBNET_A").get("name"));
                parameters.setProperty("SubnetPrivateB", tenantResources.get("PRIVATE_SUBNET_B").get("name"));
                parameters.setProperty("PrivateRouteTable", tenantResources.get("PRIVATE_ROUTE_TABLE").get("name"));
                parameters.setProperty("ECSCluster", tenantResources.get("ECS_CLUSTER").get("name"));
                parameters.setProperty("ECSSecurityGroup", tenantResources.get("ECS_SECURITY_GROUP").get("name"));

                // Will only exist if private services are defined
                if (tenantResources.containsKey("PRIVATE_SERVICE_DISCOVERY_NAMESPACE")) {
                    parameters.setProperty("ServiceDiscoveryNamespace",
                            tenantResources.get("PRIVATE_SERVICE_DISCOVERY_NAMESPACE").get("name"));
                }

                // Depending on the SSL certificate configuration, one of these 2 listeners must exist
                if (tenantResources.containsKey("HTTP_LISTENER")) {
                    parameters.setProperty("ECSLoadBalancerHttpListener",
                            tenantResources.get("HTTP_LISTENER").get("arn"));
                }
                if (tenantResources.containsKey("HTTPS_LISTENER")) {
                    parameters.setProperty("ECSLoadBalancerHttpsListener",
                            tenantResources.get("HTTPS_LISTENER").get("arn"));
                }

                if (tenantResources.containsKey("ACTIVE_DIRECTORY_ID")) {
                    String directoryId = tenantResources.get("ACTIVE_DIRECTORY_ID").get("name");
                    DescribeDirectoriesResponse directoriesResponse = ds.describeDirectories(request -> request
                            .directoryIds(directoryId));
                    DirectoryDescription directory = directoriesResponse.directoryDescriptions().get(0);
                    parameters.setProperty("ActiveDirectoryId", directoryId);
                    parameters.setProperty("ActiveDirectoryDnsIps", String.join(",", directory.dnsIpAddrs()));
                    parameters.setProperty("ActiveDirectoryDnsName", directory.name()); // Not Access URL
                    if (tenantResources.containsKey("ACTIVE_DIRECTORY_CREDENTIALS")) {
                        parameters.setProperty("ActiveDirectoryCredentials",
                                tenantResources.get("ACTIVE_DIRECTORY_CREDENTIALS").get("arn"));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Error parsing resources for tenant " + tenantId, e);
            }
        } else {
            throw new RuntimeException("Can't fetch tenant " + tenantId);
        }
    }

    protected void onboardingAppStackServiceParams(Map.Entry<String, Object> serviceConfig,
                                                   Map<String, Integer> pathPriority,
                                                   OnboardingAppStackParameters parameters,
                                                   Properties serviceDiscovery,
                                                   Context context) {
        // CloudFormation resource names can only contain alpha numeric characters or a dash
        String serviceName = serviceConfig.getKey();
        String serviceResourceName = serviceName.replaceAll("[^0-9A-Za-z-]", "").toLowerCase();
        parameters.setProperty("ServiceName", serviceName);
        parameters.setProperty("ServiceResourceName", serviceResourceName);

        Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
        // Load all the compute parameters for this service and tier the tenant is onboarding into
        onboardingAppStackComputeParams(service, parameters);

        // Setup the network routing for this service
        Boolean isPublic = (Boolean) service.get("public");
        parameters.setProperty("PubliclyAddressable", isPublic.toString());
        if (isPublic) {
            parameters.setProperty("PublicPathRoute", (String) service.get("path"));
            parameters.setProperty("PublicPathRoute", (String) service.get("path"));
            parameters.setProperty("PublicPathRulePriority", pathPriority.get(serviceName).toString());
        } else {
            // If there are any private services, we will create an environment variables called
            // SERVICE_<SERVICE_NAME>_HOST and SERVICE_<SERVICE_NAME>_PORT to pass to the task definitions
            String serviceEnvName = Utils.toUpperSnakeCase(serviceName);
            String serviceHost = "SERVICE_" + serviceEnvName + "_HOST";
            String servicePort = "SERVICE_" + serviceEnvName + "_PORT";
            LOGGER.debug("Creating service discovery environment variables {}, {}", serviceHost, servicePort);
            serviceDiscovery.put(serviceHost, serviceResourceName + ".local");
            serviceDiscovery.put(servicePort, parameters.getProperty("ContainerPort"));
        }

        // Load all the object storage parameters for this service and tier the tenant is onboarding into
        onboardingAppStackObjectStorageParams(service, parameters);

        // Load up all the shared file system parameters for this service and tier the tenant is onboarding into
        onboardingAppStackFileSystemParams(service, parameters, context);

        // Load up all the relational database parameters for this service and tier the tenant is onboarding into
        onboardingAppStackDatabaseParams(service, parameters);
    }

    protected void onboardingAppStackComputeParams(Map<String, Object> service,
                                                         OnboardingAppStackParameters parameters) {
        Map<String, Object> compute = (Map<String, Object>) service.get("compute");
        parameters.setProperty("ContainerRepository", (String) compute.get("containerRepo"));
        parameters.setProperty("ContainerRepositoryTag", (String) compute.get("containerTag"));
        parameters.setProperty("TaskLaunchType", (String) compute.get("ecsLaunchType"));
        // CloudFormation won't let you use dashes or underscores in Mapping second level key names
        // And it won't let you use Fn::Join or Fn::Split in Fn::FindInMap... so we will mangle this
        // parameter before we send it in.
        String containerOperatingSystem = ((String) compute.getOrDefault("operatingSystem", ""))
                .replace("_", "");
        parameters.setProperty("ContainerOS", containerOperatingSystem);
        parameters.setProperty("ContainerPort", ((Integer) compute.get("containerPort")).toString());
        parameters.setProperty("ContainerHealthCheckPath", (String) compute.get("healthCheckUrl"));
        parameters.setProperty("EnableECSExec", ((Boolean) compute.get("ecsExecEnabled")).toString());

        String tier = parameters.getProperty("Tier");
        Map<String, Object> tiers = (Map<String, Object>) compute.get("tiers");
        if (!tiers.containsKey(tier)) {
            throw new RuntimeException("Missing compute definition for tier " + tier);
        }
        Map<String, Object> computeTier = (Map<String, Object>) tiers.get(tier);
        parameters.setProperty("ClusterInstanceType", (String) computeTier.get("instanceType"));
        parameters.setProperty("TaskMemory", ((Integer) computeTier.get("memory")).toString());
        parameters.setProperty("TaskCPU", ((Integer) computeTier.get("cpu")).toString());
        parameters.setProperty("MinTaskCount", ((Integer) computeTier.get("min")).toString());
        parameters.setProperty("MaxTaskCount", ((Integer) computeTier.get("max")).toString());
        parameters.setProperty("MinAutoScalingGroupSize", ((Integer) computeTier.get("ec2min")).toString());
        parameters.setProperty("MaxAutoScalingGroupSize", ((Integer) computeTier.get("ec2max")).toString());
    }

    protected void onboardingAppStackFileSystemParams(Map<String, Object> service,
                                                      OnboardingAppStackParameters parameters,
                                                      Context context) {
        Map<String, Object> filesystem = (Map<String, Object>) service.get("filesystem");
        // Does this service use a shared filesystem?
        if (filesystem != null && !filesystem.isEmpty()) {
            parameters.setProperty("FileSystemMountPoint", (String) filesystem.get("mountPoint"));

            String tier = parameters.getProperty("Tier");
            Map<String, Object> tiers = (Map<String, Object>) filesystem.get("tiers");
            if (!tiers.containsKey(tier)) {
                throw new RuntimeException("Missing compute definition for tier " + tier);
            }
            Map<String, Object> filesystemTierConfig = (Map<String, Object>) tiers.get(tier);

            String fileSystemType = (String) filesystem.get("type");
            if ("EFS".equals(fileSystemType)) {
                parameters.setProperty("UseEFS", "true");
                parameters.setProperty("EncryptEFS", ((Boolean) filesystemTierConfig.get("encrypt")).toString());
                parameters.setProperty("EFSLifecyclePolicy", (String) filesystemTierConfig.get("lifecycle"));
            } else if ("FSX_WINDOWS".equals(fileSystemType) || "FSX_ONTAP".equals(fileSystemType)) {
                parameters.setProperty("UseFSx", "true");
                parameters.setProperty("FSxFileSystemType", fileSystemType);
                parameters.setProperty("FileSystemStorage", ((Integer) filesystemTierConfig.get("storageGb")).toString());
                parameters.setProperty("FileSystemThroughput",
                        ((Integer) filesystemTierConfig.get("throughputMbs")).toString());
                parameters.setProperty("FSxWindowsMountDrive", (String) filesystemTierConfig.get("windowsMountDrive"));
                parameters.setProperty("FSxDailyBackupTime", (String) filesystemTierConfig.get("dailyBackupTime"));
                parameters.setProperty("FSxBackupRetention",
                        ((Integer) filesystemTierConfig.get("backupRetentionDays")).toString());
                parameters.setProperty("FSxWeeklyMaintenanceTime",
                        (String) filesystemTierConfig.get("weeklyMaintenanceTime"));
                if ("FSX_ONTAP".equals(fileSystemType)) {
                    parameters.setProperty("OntapVolumeSize", ((Integer) filesystemTierConfig.get("volumeSize")).toString());
                }
            } else {
                parameters.setProperty("UseEFS", "false");
                parameters.setProperty("UseFSx", "false");
            }
        }
    }

    protected void onboardingAppStackDatabaseParams(Map<String, Object> service,
                                                   OnboardingAppStackParameters parameters) {
        Map<String, Object> database = (Map<String, Object>) service.get("database");
        // Does this service use a relational database?
        if (database != null && !database.isEmpty()) {
            parameters.setProperty("UseRDS", "true");
            parameters.setProperty("RDSEngine", (String) database.get("engineName"));
            parameters.setProperty("RDSEngineVersion", (String) database.get("version"));
            parameters.setProperty("RDSParameterGroupFamily", (String) database.get("family"));
            parameters.setProperty("RDSDatabase", (String) database.get("database"));
            parameters.setProperty("RDSPort", ((Integer) database.get("port")).toString());
            parameters.setProperty("RDSUsername", (String) database.get("username"));
            parameters.setProperty("RDSPasswordParam", (String) database.get("passwordParam"));
            parameters.setProperty("RDSBootstrap", (String) database.get("bootstrapFilename"));

            String tier = parameters.getProperty("Tier");
            Map<String, Object> tiers = (Map<String, Object>) database.get("tiers");
            if (!tiers.containsKey(tier)) {
                throw new RuntimeException("Missing database definition for tier " + tier);
            }
            Map<String, Object> databaseTierConfig = (Map<String, Object>) tiers.get(tier);
            parameters.setProperty("RDSInstanceClass", (String) databaseTierConfig.get("instanceClass"));
        } else {
            parameters.setProperty("UseRDS", "false");
        }
    }

    protected void onboardingAppStackObjectStorageParams(Map<String, Object> service,
                                                         OnboardingAppStackParameters parameters) {
        Map<String, Object> s3 = (Map<String, Object>) service.get("s3");
        if (s3 != null) {
            parameters.setProperty("TenantStorageBucket", (String) s3.get("bucketName"));
        }
    }

    protected OnboardingStack createOnboardingAppStack(Onboarding onboarding,
                                            Map.Entry<String, Object> serviceConfig,
                                            Map<String, Integer> pathPriority,
                                            OnboardingAppStackParameters parameters,
                                            Properties serviceDiscovery,
                                            Context context) {
        OnboardingStack stack = null;
        try {
            onboardingAppStackServiceParams(serviceConfig, pathPriority, parameters,
                    serviceDiscovery, context);
        } catch (RuntimeException e) {
            LOGGER.error(e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            failOnboarding(onboarding.getId(), e.getMessage());
            return stack;
        }

        // Make the stack name look like what CloudFormation would have done for a nested stack
        String tenantId = onboarding.getTenantId().toString();
        String tenantShortId = tenantId.substring(0, 8);
        String stackName = "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantShortId + "-app-"
                + parameters.getProperty("ServiceResourceName") + "-"
                + Utils.randomString(12).toUpperCase();
        if (stackName.length() > 128) {
            stackName = stackName.substring(0, 128);
        }

        try {
            // Now run the onboarding stack to provision the infrastructure for this application service
            LOGGER.info("OnboardingService create stack " + stackName);
            String templateUrl = "https://" + SAAS_BOOST_BUCKET + ".s3." + AWS_REGION
                    + "." + Utils.endpointSuffix(AWS_REGION) + "/tenant-onboarding-app.yaml";
            String stackId;
            try {
                CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                        .stackName(stackName)
                        .disableRollback(false)
                        .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                        .notificationARNs(ONBOARDING_APP_STACK_SNS)
                        .templateURL(templateUrl)
                        .parameters(parameters.forCreate())
                        .build()
                );
                stackId = cfnResponse.stackId();

                // Save state in the Onboarding database
                stack = OnboardingStack.builder()
                        .service(parameters.getProperty("ServiceName"))
                        .name(stackName)
                        .arn(stackId)
                        .baseStack(false)
                        .status("CREATE_IN_PROGRESS")
                        .build();
                onboarding.addStack(stack);
                onboarding.setStatus(OnboardingStatus.provisioning);
                onboarding = dal.updateOnboarding(onboarding);
                LOGGER.info("OnboardingService stack id " + stackId);
            } catch (CloudFormationException cfnError) {
                LOGGER.error("cloudformation::createStack failed", cfnError);
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                failOnboarding(onboarding.getId(), cfnError.awsErrorDetails().errorMessage());
            }
        } catch (RuntimeException e) {
            // Template parameters validation failed
            LOGGER.error(e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            failOnboarding(onboarding.getId(), e.getMessage());
        }
        return stack;
    }

    protected void savePropertiesFileToS3(S3Client s3, String bucket, String filename,
                                          String tenantId, Properties properties) {
        // Write the application-wide environment variables to S3 so each service container can load it up
        String environmentFile = "tenants/" + tenantId + "/" + filename;
        ByteArrayOutputStream environmentFileContents = new ByteArrayOutputStream();
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                environmentFileContents, StandardCharsets.UTF_8)
        )) {
            properties.store(writer, null);
            s3.putObject(request -> request
                            .bucket(bucket)
                            .key(environmentFile)
                            .build(),
                    RequestBody.fromBytes(environmentFileContents.toByteArray())
            );
        } catch (S3Exception s3Error) {
            LOGGER.error("Error putting service discovery file to S3 {}", s3Error.awsErrorDetails().errorMessage());
            throw s3Error;
        } catch (IOException ioe) {
            LOGGER.error("Error writing data to output stream");
            throw new RuntimeException(ioe);
        }
    }
}