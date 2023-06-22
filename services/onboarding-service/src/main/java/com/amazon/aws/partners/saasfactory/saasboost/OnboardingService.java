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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class OnboardingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingService.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_APP_CLIENT = System.getenv("API_APP_CLIENT");
    private static final String ONBOARDING_TABLE = System.getenv("ONBOARDING_TABLE");
    private static final String RESOURCES_BUCKET = System.getenv("RESOURCES_BUCKET");
    private static final String TENANT_CONFIG_DLQ = System.getenv("TENANT_CONFIG_DLQ");
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String EVENT_SOURCE = "saas-boost";
    private static final String RESOURCES_BUCKET_TEMP_FOLDER = "00temp/";
    private static final String TENANT_ONBOARDING_STATUS_CHANGED = "Tenant Onboarding Status Changed";
    private final OnboardingServiceDAL dal;
    private final EventBridgeClient eventBridge;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final SqsClient sqs;
    private final SecretsManagerClient secrets;
    private ApiGatewayHelper api;

    public OnboardingService() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        if (Utils.isBlank(ONBOARDING_TABLE)) {
            throw new IllegalStateException("Missing environment variable ONBOARDING_TABLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new OnboardingServiceDAL();
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
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
        this.sqs = Utils.sdkClient(SqsClient.builder(), SqsClient.SERVICE_NAME);
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
            onboardings = List.of(dal.getOnboardingByTenantId(queryParams.get("tenantId")));
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
        // TODO check for duplicate tenant name? Do it by just looking at the local onboarding requests,
        // TODO or make a call out to the tenant service?

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
                .withStatusCode(201)
                .withBody(Utils.toJson(onboarding));
    }

    public void handleOnboardingEvent(Map<String, Object> event, Context context) {
        if (!OnboardingEvent.validate(event)) {
            String detailType = (String) event.get("detail-type");
            OnboardingEvent onboardingEvent = OnboardingEvent.fromDetailType(detailType);
            if (onboardingEvent != null) {
                switch (onboardingEvent) {
                    case ONBOARDING_VALIDATED:
                        LOGGER.info("Handling Onboarding Validated");
                        handleOnboardingValidated(event, context);
                        break;
                    case ONBOARDING_PROVISIONING:
                        LOGGER.info("Handling Onboarding Resources Provisioning");
                        handleOnboardingProvisioning(event, context);
                        break;
                    case ONBOARDING_PROVISIONED:
                        LOGGER.info("Handling Onboarding Resources Provisioned");
                        handleOnboardingProvisioned(event, context);
                        break;
                    case ONBOARDING_DEPLOYING:
                        LOGGER.info("Handling Onboarding Workloads Deploying");
                        handleOnboardingDeploying(event, context);
                        break;
                    case ONBOARDING_DEPLOYED:
                        LOGGER.info("Handling Onboarding Workloads Deployed");
                        handleOnboardingDeployed(event, context);
                        break;
                    case ONBOARDING_COMPLETED:
                        LOGGER.info("Handling Onboarding Completed");
                        handleOnboardingCompleted(event, context);
                        break;
                    case ONBOARDING_FAILED:
                        LOGGER.info("Handling Onboarding Failed");
                        handleOnboardingFailed(event, context);
                        break;
                    default:
                        LOGGER.error("Unknown Onboarding Event!");
                }
            } else if (detailType.startsWith("Tenant ")) {
                LOGGER.info("Handling Tenant Event");
                handleTenantEvent(event, context);
            } else {
                LOGGER.error("Can't find onboarding event for detail-type {}", event.get("detail-type"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Invalid SaaS Boost Onboarding Event " + Utils.toJson(event));
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
//
//                // Assign a CIDR block to this tenant to use for its VPC
//                try {
//                    dal.assignCidrBlock(tenantId);
//                } catch (Exception e) {
//                    // Unexpected error since we have already validated... but eventual consistency
//                    failOnboarding(onboarding.getId(), "Could not assign CIDR for tenant VPC");
//                    return;
//                }
//
                // Let the tenant service know the onboarding status
                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                        TENANT_ONBOARDING_STATUS_CHANGED,
                        Map.of(
                                "tenantId", tenantId,
                                "onboardingStatus",  onboarding.getStatus()
                        )
                );

                // Ready to provision the infrastructure for this tenant
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

    protected void handleOnboardingProvisioning(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        Onboarding onboarding = dal.updateStatus((String) detail.get("onboardingId"),
                OnboardingStatus.provisioning.name());
        // Let the tenant service know the onboarding status
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                TENANT_ONBOARDING_STATUS_CHANGED,
                Map.of(
                        "tenantId", onboarding.getTenantId(),
                        "onboardingStatus",  onboarding.getStatus()
                )
        );
    }

    protected void handleOnboardingProvisioned(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        Onboarding onboarding = dal.updateStatus((String) detail.get("onboardingId"),
                OnboardingStatus.provisioned.name());
        // Let the tenant service know the onboarding status
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                TENANT_ONBOARDING_STATUS_CHANGED,
                Map.of(
                        "tenantId", onboarding.getTenantId(),
                        "onboardingStatus",  onboarding.getStatus()
                )
        );
    }

    protected void handleOnboardingDeploying(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        Onboarding onboarding = dal.updateStatus((String) detail.get("onboardingId"),
                OnboardingStatus.deploying.name());
        // Let the tenant service know the onboarding status
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                TENANT_ONBOARDING_STATUS_CHANGED,
                Map.of(
                        "tenantId", onboarding.getTenantId(),
                        "onboardingStatus",  onboarding.getStatus()
                )
        );
    }

    protected void handleOnboardingDeployed(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        Onboarding onboarding = dal.updateStatus((String) detail.get("onboardingId"),
                OnboardingStatus.deployed.name());
        // Let the tenant service know the onboarding status
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                TENANT_ONBOARDING_STATUS_CHANGED,
                Map.of(
                        "tenantId", onboarding.getTenantId(),
                        "onboardingStatus",  onboarding.getStatus()
                )
        );

    }

    protected void handleOnboardingCompleted(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        Onboarding onboarding = dal.updateStatus((String) detail.get("onboardingId"),
                OnboardingStatus.completed.name());
        // Let the tenant service know the onboarding status
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                TENANT_ONBOARDING_STATUS_CHANGED,
                Map.of(
                        "tenantId", onboarding.getTenantId(),
                        "onboardingStatus",  onboarding.getStatus()
                )
        );
    }

    protected void handleOnboardingFailed(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        Onboarding onboarding = dal.updateStatus((String) detail.get("onboardingId"),
                OnboardingStatus.failed.name());
        // Let the tenant service know the onboarding status
        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                TENANT_ONBOARDING_STATUS_CHANGED,
                Map.of(
                        "tenantId", onboarding.getTenantId(),
                        "onboardingStatus",  onboarding.getStatus()
                )
        );
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

        } else {
            // Can't find an onboarding record for this tenant id
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

        } else {
            // Can't find an onboarding record for this tenant id
            LOGGER.error("Can't find onboarding record for tenant {}", detail.get("tenantId"));
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
}