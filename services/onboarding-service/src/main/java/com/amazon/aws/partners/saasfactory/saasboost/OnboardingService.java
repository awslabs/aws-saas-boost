/**
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
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import software.amazon.awssdk.services.sfn.model.StartExecutionResponse;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OnboardingService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private final static Logger LOGGER = LoggerFactory.getLogger(OnboardingService.class);
    private final static ObjectMapper MAPPER = new ObjectMapper();
    private final static Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final String SYSTEM_API_CALL_DETAIL_TYPE = "System API Call";
    private static final String SYSTEM_API_CALL_SOURCE = "saas-boost";
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String ECR_REPO = System.getenv("ECR_REPO");
    private static final String ONBOARDING_WORKFLOW = System.getenv("ONBOARDING_WORKFLOW");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private static final String SAAS_BOOST_BUCKET = System.getenv("SAAS_BOOST_BUCKET");
//    private static final String CLOUDFRONT_DISTRIBUTION = System.getenv("CLOUDFRONT_DISTRIBUTION");
    private final CloudFormationClient cfn;
    private final SfnClient snf;
    private final EventBridgeClient eventBridge;
    private final EcrClient ecr;
    private final OnboardingServiceDAL dal;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final Route53Client route53;
    private static final String AWS_REGION = System.getenv("AWS_REGION");

    public OnboardingService() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new OnboardingServiceDAL();
        this.cfn = Utils.sdkClient(CloudFormationClient.builder(), CloudFormationClient.SERVICE_NAME);
        this.snf = Utils.sdkClient(SfnClient.builder(), SfnClient.SERVICE_NAME);
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        this.ecr = Utils.sdkClient(EcrClient.builder(), EcrClient.SERVICE_NAME);
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        try {
            this.presigner = S3Presigner.builder()
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .region(Region.of(AWS_REGION))
                    .endpointOverride(new URI("https://" + s3.serviceName() + "." + Region.of(AWS_REGION) + ".amazonaws.com")) // will break in China regions
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.route53 = Utils.sdkClient(Route53Client.builder(), Route53Client.SERVICE_NAME);

        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent getOnboarding(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::getOnboarding");

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
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
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::getOnboarding exec " + totalTimeMillis);

        return response;
    }

    public APIGatewayProxyResponseEvent getOnboardings(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::getOnboardings");

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        List<Onboarding> onboardings = dal.getOnboardings();
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(200)
                    .withBody(Utils.toJson(onboardings));

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::getOnboardings exec " + totalTimeMillis);

        return response;
    }

    public APIGatewayProxyResponseEvent startOnboarding(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        if (Utils.isBlank(ONBOARDING_WORKFLOW)) {
            throw new IllegalStateException("Missing required environment variable ONBOARDING_WORKFLOW");
        }

        if (Utils.isBlank(SAAS_BOOST_BUCKET)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_BUCKET");
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::startOnboarding");

        Utils.logRequestEvent(event);

        // Check to see if there are any images in the ECR repo before allowing onboarding
        try {
            ListImagesResponse dockerImages = ecr.listImages(request -> request.repositoryName(ECR_REPO));
            //ListImagesResponse::hasImageIds will return true if the imageIds object is not null
            if (!dockerImages.hasImageIds() || dockerImages.imageIds().isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"No workload image deployed to ECR.\"}");
            }
        } catch (SdkServiceException ecrError) {
            LOGGER.error("ecr:ListImages error", ecrError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ecrError));
            throw ecrError;
        }

        // Parse the onboarding request
        Map<String, Object> requestBody = Utils.fromJson((String) event.get("body"), HashMap.class);
        if (null == requestBody) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid Json in Request.\"}");
        }
        String tenantName = (String) requestBody.get("name");
        String subdomain = (String) requestBody.get("subdomain");
        String tshirt = (String) requestBody.get("computeSize");
        ComputeSize computeSize = null;
        Integer memory = (Integer) requestBody.get("memory");
        Integer cpu = (Integer) requestBody.get("cpu");
        Integer minCount = (Integer) requestBody.get("minCount");
        Integer maxCount = (Integer) requestBody.get("maxCount");
        String planId = (String) requestBody.get("planId");

        // Make sure we're not trying to onboard a tenant to an existing subdomain
        if (Utils.isNotBlank(subdomain)) {
            Map<String, String> settings = null;
            ApiRequest getSettingsRequest = ApiRequest.builder()
                    .resource("settings?setting=HOSTED_ZONE&setting=DOMAIN_NAME")
                    .method("GET")
                    .build();
            SdkHttpFullRequest getSettingsApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, getSettingsRequest);
            LOGGER.info("Fetching SaaS Boost hosted zone id from Settings Service");
            try {
                String getSettingsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(getSettingsApiRequest, API_TRUST_ROLE, context.getAwsRequestId());
                ArrayList<Map<String, String>> getSettingsResponse = Utils.fromJson(getSettingsResponseBody, ArrayList.class);
                if (null == getSettingsResponse) {
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(CORS)
                            .withBody("{\"message\": \"Invalid response body.\"}");
                }
                settings = getSettingsResponse
                        .stream()
                        .collect(Collectors.toMap(
                                setting -> setting.get("name"), setting -> setting.get("value")
                        ));
                String hostedZoneId = settings.get("HOSTED_ZONE");
                String domainName = settings.get("DOMAIN_NAME");

                // Ask Route53 for all the records of this hosted zone
                ListResourceRecordSetsResponse recordSets = route53.listResourceRecordSets(request -> request.hostedZoneId(hostedZoneId));
                if (recordSets.hasResourceRecordSets()) {
                    for (ResourceRecordSet recordSet : recordSets.resourceRecordSets()) {
                        if (RRType.A == recordSet.type()) {
                            // Hosted Zone alias for the tenant subdomain
                            String recordSetName = recordSet.name();
                            String existingSubdomain = recordSetName.substring(0, recordSetName.indexOf(domainName) - 1);
                            LOGGER.info("Existing tenant subdomain " + existingSubdomain + " for record set " + recordSetName);
                            if (subdomain.equalsIgnoreCase(existingSubdomain)) {
                                return new APIGatewayProxyResponseEvent()
                                        .withStatusCode(400)
                                        .withHeaders(CORS)
                                        .withBody("{\"message\": \"Tenant subdomain " + subdomain + " is already in use.\"}");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error invoking API settings?setting=HOSTED_ZONE&setting=DOMAIN_NAME");
                LOGGER.error(Utils.getFullStackTrace(e));
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\":\"Error invoking settings API\"}");
            }
        }

        // Create a new onboarding request record for a tenant
        if (Utils.isBlank(tenantName)) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Tenant name is required.\"}");
        }

        if (Utils.isNotEmpty(tshirt)) {
            try {
                computeSize = ComputeSize.valueOf(tshirt);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Invalid compute size {}", tshirt);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\":\"Invalid compute size\"}");
            }
        }

        Boolean overrideDefaults = (computeSize != null || memory != null || cpu != null || minCount != null || maxCount != null);
        if (overrideDefaults) {
            if (!validateTenantOverrides(computeSize, memory, cpu, minCount, maxCount)) {
                LOGGER.error("Invalid default overrides. Both compute sizing and min and max counts must be set");
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\":\"Invalid default overrides. Both compute sizing and min and max counts must be set.\"}");
            }
        }

        //check if Quotas will be exceeded.
        try {
            LOGGER.info("Check Service Quota Limits");
            Map<String, Object> retMap = checkLimits();
            Boolean passed = (Boolean) retMap.get("passed");
            String message = (String) retMap.get("message");
            if (!passed) {
                LOGGER.error("Provisioning will exceed limits. {}", message);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\":\"Provisioning will exceed limits. " + message + "\"}");
            }
        } catch (Exception e) {
            LOGGER.error((Utils.getFullStackTrace(e)));
            throw new RuntimeException("Error checking Service Quotas with Private API quotas/check");
        }

        //*TODO:  We should add check for CIDRs here!

        UUID onboardingId = UUID.randomUUID();
        Onboarding onboarding = new Onboarding(onboardingId, OnboardingStatus.created);
        onboarding.setTenantName((String) requestBody.get("name"));
        onboarding = dal.insertOnboarding(onboarding);

        // Collect up the input we need to send to the tenant service via our Step Functions workflow
        Map<String, Object> tenant = new HashMap<>();
        tenant.put("active", true);
        tenant.put("onboardingStatus", onboarding.getStatus().toString());
        tenant.put("name", tenantName);
        if (Utils.isNotBlank(subdomain)) {
            tenant.put("subdomain", subdomain);
        }
        tenant.put("overrideDefaults", overrideDefaults);
        if (overrideDefaults) {
            if (computeSize != null) {
                tenant.put("computeSize", computeSize.name());
                tenant.put("memory", computeSize.getMemory());
                tenant.put("cpu", computeSize.getCpu());
            } else {
                tenant.put("memory", memory);
                tenant.put("cpu", cpu);
            }
            tenant.put("maxCount", maxCount);
            tenant.put("minCount", minCount);
        }
        if (Utils.isNotBlank(planId)) {
            tenant.put("planId", planId);
        }

        //generate a pre-signed url to upload  the zip file
        String key = "temp/" + onboarding.getId().toString() + ".zip";
        final Duration expires = Duration.ofMinutes(15); // UI times out in 10 min

        // Generate the presigned URL
        PresignedPutObjectRequest presignedObject = presigner.presignPutObject(request -> request
                .signatureDuration(expires)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(SAAS_BOOST_BUCKET)
                        .key(key)
                        .build()
                )
                .build()
        );

        onboarding.setZipFileUrl(presignedObject.url().toString());

        Map<String, Object> input = new HashMap<>();
        input.put("onboardingId", onboarding.getId().toString());
        input.put("tenant", tenant);
        String executionName = onboarding.getId().toString();
        String inputJson = Utils.toJson(input);
        try {
            LOGGER.info("OnboardingService::startOnboarding Starting Step Functions execution");
            LOGGER.info(inputJson);
            StartExecutionResponse response = snf.startExecution(StartExecutionRequest
                    .builder()
                    .name(executionName)
                    .input(inputJson)
                    .stateMachineArn(ONBOARDING_WORKFLOW)
                    .build()
            );
            LOGGER.info("OnboardingService::startOnboarding Step Functions responded with " + response.toString());
        } catch (SdkServiceException snfError) {
            LOGGER.error("OnboardingService::startOnboarding Step Functions error " + snfError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(snfError));
            dal.updateStatus(onboardingId, OnboardingStatus.failed);
            throw snfError;
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::startOnboarding exec " + totalTimeMillis);

        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(onboarding));
    }

    protected static boolean validateTenantOverrides(ComputeSize computeSize, Integer memory, Integer cpu, Integer minCount, Integer maxCount) {
        boolean computeOverride = (computeSize != null || (memory != null && cpu != null));
        boolean invalidComputeOverride = (computeSize == null && (memory == null || cpu == null));
        boolean asgOverride = (minCount != null && maxCount != null);
        boolean invalidAsgOverride = ((minCount != null && maxCount == null) || (maxCount != null && minCount == null));

        boolean valid;
        if (invalidComputeOverride || invalidAsgOverride) {
            valid = false;
        } else if ((computeOverride && !asgOverride) || (asgOverride && !computeOverride)) {
            valid = false;
        } else {
            valid = (computeOverride && asgOverride);
        }

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("computeSize", computeSize);
        overrides.put("memory", memory);
        overrides.put("cpu", cpu);
        overrides.put("minCount", minCount);
        overrides.put("maxCount", maxCount);
        LOGGER.info(Utils.toJson(overrides));

        return valid;
    }

    public APIGatewayProxyResponseEvent updateStatus(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        APIGatewayProxyResponseEvent response = null;
        LOGGER.info("OnboardingService::updateStatus");
        Map<String, String> params = (Map) event.get("pathParameters");
        String onboardingId = params.get("id");
        LOGGER.info("OnboardingService::updateStatus " + onboardingId);
        Onboarding onboarding = Utils.fromJson((String) event.get("body"), Onboarding.class);
        if (onboarding == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\":\"Empty request body.\"}");
        } else {
            if (onboarding.getId() == null || !onboarding.getId().toString().equals(onboardingId)) {
                LOGGER.error("Can't update onboarding status " + onboarding.getId() + " at resource " + onboardingId);
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody("{\"message\":\"Invalid resource for onboarding.\"}");
            } else {
                onboarding = dal.updateStatus(onboarding.getId(), onboarding.getStatus());
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(onboarding));
            }
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::updateStatus exec " + totalTimeMillis);

        return response;
    }

    public APIGatewayProxyResponseEvent provisionTenant(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
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

        Utils.logRequestEvent(event);
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::provisionTenant");
        APIGatewayProxyResponseEvent response = null;

        Map<String, Object> requestBody = (Map<String, Object>) event.get("body");
        if (requestBody.isEmpty()) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\":\"Empty request body.\"}");
        } else {
            // The invocation event sent to us must contain the tenant we're
            // provisioning for and the onboarding job that's tracking it
            UUID onboardingId = UUID.fromString((String) requestBody.get("onboardingId"));
            Map<String, Object> tenant = Utils.fromJson((String) requestBody.get("tenant"), HashMap.class);
            if (null == tenant) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Invalid request body.\"}");
            }
            UUID tenantId = UUID.fromString(((String) tenant.get("id")).toLowerCase());

            // Get the settings for this SaaS Boost install for this SaaS Boost "environment"
            Map<String, String> settings = null;
            ApiRequest getSettingsRequest = ApiRequest.builder()
                    .resource("settings")
                    .method("GET")
                    .build();
            SdkHttpFullRequest getSettingsApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, getSettingsRequest);
            try {
                String getSettingsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(getSettingsApiRequest, API_TRUST_ROLE, context.getAwsRequestId());
                ArrayList<Map<String, String>> getSettingsResponse = Utils.fromJson(getSettingsResponseBody, ArrayList.class);
                if (null == getSettingsResponse) {
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(CORS)
                            .withBody("{\"message\": \"Invalid settings response.\"}");
                }
                settings = getSettingsResponse
                        .stream()
                        .collect(Collectors.toMap(
                                setting -> setting.get("name"), setting -> setting.get("value")
                        ));
            } catch (Exception e) {
                LOGGER.error("Error invoking API settings");
                dal.updateStatus(onboardingId, OnboardingStatus.failed);
                LOGGER.error(Utils.getFullStackTrace(e));
                throw new RuntimeException(e);
            }

            // We can't continue if any of the SaaS Boost settings are blank
            if (settings == null || settings.isEmpty()) {
                LOGGER.error("One or more required SaaS Boost parameters is missing.");
                dal.updateStatus(onboardingId, OnboardingStatus.failed);
                throw new RuntimeException("SaaS Boost parameters are missing.");
            }

            // And parameters specific to this tenant
            String cidrPrefix = null;
            try {
                String cidrBlock = dal.assignCidrBlock(tenantId.toString());
                cidrPrefix = cidrBlock.substring(0, cidrBlock.indexOf(".", cidrBlock.indexOf(".") + 1));
            } catch (Exception e) {
                dal.updateStatus(onboardingId, OnboardingStatus.failed);
                throw e;
            }

            String taskMemory = settings.get("TASK_MEMORY");
            if (tenant.get("memory") != null) {
                try {
                    taskMemory = ((Integer) tenant.get("memory")).toString();
                    LOGGER.info("Override default task memory with {}", taskMemory);
                } catch (NumberFormatException nfe) {
                    LOGGER.error("Can't parse tenant task memory from {}", tenant.get("memory"));
                    dal.updateStatus(onboardingId, OnboardingStatus.failed);
                    LOGGER.error(Utils.getFullStackTrace(nfe));
                }
            }
            String taskCpu = settings.get("TASK_CPU");
            if (tenant.get("cpu") != null) {
                try {
                    taskCpu = ((Integer) tenant.get("cpu")).toString();
                    LOGGER.info("Override default task CPU with {}", taskCpu);
                } catch (NumberFormatException nfe) {
                    LOGGER.error("Can't parse tenant task CPU from {}", tenant.get("cpu"));
                    dal.updateStatus(onboardingId, OnboardingStatus.failed);
                    LOGGER.error(Utils.getFullStackTrace(nfe));
                }
            }
            String taskCount = settings.get("MIN_COUNT");
            if (tenant.get("minCount") != null) {
                try {
                    taskCount = ((Integer) tenant.get("minCount")).toString();
                } catch (NumberFormatException nfe) {
                    LOGGER.error("Can't parse tenant min task count from {}", tenant.get("minCount"));
                    dal.updateStatus(onboardingId, OnboardingStatus.failed);
                    LOGGER.error(Utils.getFullStackTrace(nfe));
                }
            }
            String maxTaskCount = settings.get("MAX_COUNT");
            if (tenant.get("maxCount") != null) {
                try {
                    maxTaskCount = ((Integer) tenant.get("maxCount")).toString();
                } catch (NumberFormatException nfe) {
                    LOGGER.error("Can't parse tenant max task count from {}", tenant.get("maxCount"));
                    dal.updateStatus(onboardingId, OnboardingStatus.failed);
                    LOGGER.error(Utils.getFullStackTrace(nfe));
                }
            }
            String tenantSubdomain = (String) tenant.get("subdomain");
            if (tenantSubdomain == null) {
                tenantSubdomain = "";
            }

            // Did the ISV configure the application for a shared filesystem?

            Boolean enableEfs = Boolean.FALSE;
            Boolean enableFSx = Boolean.FALSE;
            String mountPoint = "";
            Boolean encryptFilesystem = Boolean.FALSE;
            String filesystemLifecycle = "NEVER";
            String fileSystemType = settings.get("FILE_SYSTEM_TYPE");
            String fsxStorageGb = "0";
            String fsxThroughputMbs = "0";
            String fsxBackupRetentionDays = "7";
            String fsxDailyBackupTime = "";
            String fsxWeeklyMaintenanceTime = "";
            String fsxWindowsMountDrive = "";
            String fsxUseOntap = "";
            String fsxOntapVolumeSize = "0";


            if (null != fileSystemType && !fileSystemType.isEmpty()) {
                mountPoint = settings.get("FILE_SYSTEM_MOUNT_POINT");
                if ("FSX".equals(fileSystemType)) {
                    enableFSx = true;
                    fsxStorageGb = settings.get("FSX_STORAGE_GB"); // GB 32 to 65,536
                    if (tenant.get("fsxStorageGb") != null) {
                        try {
                            fsxStorageGb = ((Integer) tenant.get("fsxStorageGb")).toString();
                            LOGGER.info("Override default FSX Storage GB with {}", fsxStorageGb);
                        } catch (NumberFormatException nfe) {
                            LOGGER.error("Can't parse tenant task FSX Storage GB from {}", tenant.get("fsxStorageGb"));
                            dal.updateStatus(onboardingId, OnboardingStatus.failed);
                            LOGGER.error(Utils.getFullStackTrace(nfe));
                        }
                    }

                    fsxThroughputMbs = settings.get("FSX_THROUGHPUT_MBS"); // MB/s
                    if (tenant.get("fsxThroughputMbs") != null) {
                        try {
                            fsxThroughputMbs = ((Integer) tenant.get("fsxThroughputMbs")).toString();
                            LOGGER.info("Override default FSX Throughput with {}", fsxThroughputMbs);
                        } catch (NumberFormatException nfe) {
                            LOGGER.error("Can't parse tenant task FSX Throughput from {}", tenant.get("fsxThroughputMbs"));
                            dal.updateStatus(onboardingId, OnboardingStatus.failed);
                            LOGGER.error(Utils.getFullStackTrace(nfe));
                        }
                    }

                    fsxBackupRetentionDays = settings.get("FSX_BACKUP_RETENTION_DAYS"); // 7 to 35
                    if (tenant.get("fsxBackupRetentionDays") != null) {
                        try {
                            fsxBackupRetentionDays = ((Integer) tenant.get("fsxBackupRetentionDays")).toString();
                            LOGGER.info("Override default FSX Throughput with {}", fsxBackupRetentionDays);
                        } catch (NumberFormatException nfe) {
                            LOGGER.error("Can't parse tenant task FSX Throughput from {}", tenant.get("fsxBackupRetentionDays"));
                            dal.updateStatus(onboardingId, OnboardingStatus.failed);
                            LOGGER.error(Utils.getFullStackTrace(nfe));
                        }
                    }

                    fsxDailyBackupTime = settings.get("FSX_DAILY_BACKUP_TIME"); //HH:MM in UTC
                    if (tenant.get("fsxDailyBackupTime") != null) {
                        fsxDailyBackupTime = (String) tenant.get("fsxDailyBackupTime");
                            LOGGER.info("Override default FSX Daily Backup time with {}", fsxDailyBackupTime);
                    }

                    fsxWeeklyMaintenanceTime = settings.get("FSX_WEEKLY_MAINTENANCE_TIME");//d:HH:MM in UTC
                    if (tenant.get("fsxWeeklyMaintenanceTime") != null) {
                        fsxWeeklyMaintenanceTime = (String) tenant.get("fsxWeeklyMaintenanceTime");
                        LOGGER.info("Override default FSX Weekly Maintenance time with {}", fsxWeeklyMaintenanceTime);
                    }

                    fsxWindowsMountDrive = settings.get("FSX_WINDOWS_MOUNT_DRIVE");
                    //Note:  Do not want to override the FSX_WINDOWS_MOUNT_DRIVE as that should be same for all tenants

                    fsxUseOntap = settings.get("FSX_USE_ONTAP");

                    fsxOntapVolumeSize = settings.get("FSX_ONTAP_VOLUME_SIZE_MBS"); // MB/s
                    if (tenant.get("fsxOntapVolumeSize") != null) {
                        try {
                            fsxOntapVolumeSize = ((Integer) tenant.get("fsxOntapVolumeSize")).toString();
                            LOGGER.info("Override default FSX ONTAP volume size with {}", fsxOntapVolumeSize);
                        } catch (NumberFormatException nfe) {
                            LOGGER.error("Can't parse tenant task FSX ONTAP volume size from {}", tenant.get("fsxOntapVolumeSize"));
                            dal.updateStatus(onboardingId, OnboardingStatus.failed);
                            LOGGER.error(Utils.getFullStackTrace(nfe));
                        }
                    }

                } else { //this is for EFS file system
                    enableEfs = true;
                    encryptFilesystem = Boolean.valueOf(settings.get("FILE_SYSTEM_ENCRYPT"));
                    filesystemLifecycle = settings.get("FILE_SYSTEM_LIFECYCLE");
                }
            }

            // Did the ISV configure the application for a database?
            Boolean enableDatabase = Boolean.FALSE;
            String dbInstanceClass = "";
            String dbEngine = "";
            String dbVersion = "";
            String dbFamily = "";
            String dbMasterUsername = "";
            String dbMasterPasswordRef = "";
            String dbPort = "";
            String dbDatabase = "";
            String dbBootstrap = "";
            if (settings.get("DB_ENGINE") != null && !settings.get("DB_ENGINE").isEmpty()) {
                enableDatabase = Boolean.TRUE;
                dbEngine = settings.get("DB_ENGINE");
                dbVersion = settings.get("DB_VERSION");
                dbFamily = settings.get("DB_PARAM_FAMILY");
                dbInstanceClass = settings.get("DB_INSTANCE_TYPE");
                dbMasterUsername = settings.get("DB_MASTER_USERNAME");
                dbPort = settings.get("DB_PORT");
                dbDatabase = settings.get("DB_NAME");
                dbBootstrap = settings.get("DB_BOOTSTRAP_FILE");

                // CloudFormation needs the Parameter Store reference key (version number) to properly
                // decode secure string parameters... So we need to call the private API to get it.
                ApiRequest paramStoreRef = ApiRequest.builder()
                        .resource("settings/DB_MASTER_PASSWORD/ref")
                        .method("GET")
                        .build();
                SdkHttpFullRequest paramStoreRefApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, paramStoreRef);
                try {
                    String paramStoreRefResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(paramStoreRefApiRequest, API_TRUST_ROLE, context.getAwsRequestId());
                    Map<String, String> dbPasswordRef = Utils.fromJson(paramStoreRefResponseBody, HashMap.class);
                    if (null == dbPasswordRef) {
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(400)
                                .withHeaders(CORS)
                                .withBody("{\"message\": \"Invalid response body.\"}");
                    }
                    dbMasterPasswordRef = dbPasswordRef.get("reference-key");
                } catch (Exception e) {
                    LOGGER.error("Error invoking API settings/DB_MASTER_PASSWORD/ref");
                    dal.updateStatus(onboardingId, OnboardingStatus.failed);
                    LOGGER.error(Utils.getFullStackTrace(e));
                    throw new RuntimeException(e);
                }
            }

            // If the tenant is being onboarded into a billing plan, we need to send
            // it through so we can configure it with the 3rd party when the stack completes
            String billingPlan = (String) tenant.get("planId");
            if (billingPlan == null) {
                billingPlan = "";
            }

            // CloudFormation needs the Parameter Store reference key (version number) to properly
            // decode secure string parameters... So we need to call the private API to get it.
            String sslCertArn= settings.get("SSL_CERT_ARN");
            String sslCertArnRef = "";
            if (null != sslCertArn && !"".equals(sslCertArn)) {
                ApiRequest paramStoreRef = ApiRequest.builder()
                        .resource("settings/SSL_CERT_ARN/ref")
                        .method("GET")
                        .build();
                SdkHttpFullRequest paramStoreRefApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, paramStoreRef);
                try {
                    String paramStoreRefResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(paramStoreRefApiRequest, API_TRUST_ROLE, context.getAwsRequestId());
                    Map<String, String> certRef = Utils.fromJson(paramStoreRefResponseBody, HashMap.class);
                    if (null == certRef) {
                        return new APIGatewayProxyResponseEvent()
                                .withStatusCode(400)
                                .withHeaders(CORS)
                                .withBody("{\"message\": \"Invalid response body.\"}");
                    }
                    sslCertArnRef = certRef.get("reference-key");
                } catch (Exception e) {
                    LOGGER.error("Error invoking API settings/SSL_CERT_ARN/ref");
                    dal.updateStatus(onboardingId, OnboardingStatus.failed);
                    LOGGER.error(Utils.getFullStackTrace(e));
                    throw new RuntimeException(e);
                }
            }


        // CloudFormation won't let you use dashes or underscores in Mapping second level key names
            // And it won't let you use Fn::Join or Fn::Split in Fn::FindInMap... so we will mangle this
            // parameter before we send it in.
            String clusterOS = settings.getOrDefault("CLUSTER_OS", "").replace("_", "");

            List<Parameter> templateParameters = new ArrayList<>();
            templateParameters.add(Parameter.builder().parameterKey("TenantId").parameterValue(tenantId.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("TenantSubDomain").parameterValue(tenantSubdomain).build());
            templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(SAAS_BOOST_ENV).build());
            templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").parameterValue(settings.get("SAAS_BOOST_BUCKET")).build());
            templateParameters.add(Parameter.builder().parameterKey("LambdaSourceFolder").parameterValue(settings.get("SAAS_BOOST_LAMBDAS_FOLDER")).build());
            templateParameters.add(Parameter.builder().parameterKey("DockerHostOS").parameterValue(clusterOS).build());
            templateParameters.add(Parameter.builder().parameterKey("DockerHostInstanceType").parameterValue(settings.get("CLUSTER_INSTANCE_TYPE")).build());
            templateParameters.add(Parameter.builder().parameterKey("TaskMemory").parameterValue(taskMemory).build());
            templateParameters.add(Parameter.builder().parameterKey("TaskCPU").parameterValue(taskCpu).build());
            templateParameters.add(Parameter.builder().parameterKey("TaskCount").parameterValue(taskCount).build());
            templateParameters.add(Parameter.builder().parameterKey("MaxTaskCount").parameterValue(maxTaskCount).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerRepository").parameterValue(settings.get("ECR_REPO")).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerPort").parameterValue(settings.get("CONTAINER_PORT")).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerHealthCheckPath").parameterValue(settings.get("HEALTH_CHECK")).build());
            templateParameters.add(Parameter.builder().parameterKey("CodePipelineRoleArn").parameterValue(settings.get("CODE_PIPELINE_ROLE")).build());
            templateParameters.add(Parameter.builder().parameterKey("ArtifactBucket").parameterValue(settings.get("CODE_PIPELINE_BUCKET")).build());
            templateParameters.add(Parameter.builder().parameterKey("TransitGateway").parameterValue(settings.get("TRANSIT_GATEWAY")).build());
            templateParameters.add(Parameter.builder().parameterKey("TenantTransitGatewayRouteTable").parameterValue(settings.get("TRANSIT_GATEWAY_ROUTE_TABLE")).build());
            templateParameters.add(Parameter.builder().parameterKey("EgressTransitGatewayRouteTable").parameterValue(settings.get("EGRESS_ROUTE_TABLE")).build());
            templateParameters.add(Parameter.builder().parameterKey("CidrPrefix").parameterValue(cidrPrefix).build());
            templateParameters.add(Parameter.builder().parameterKey("DomainName").parameterValue(settings.get("DOMAIN_NAME")).build());
            templateParameters.add(Parameter.builder().parameterKey("SSLCertArnParam").parameterValue(sslCertArnRef).build());
            templateParameters.add(Parameter.builder().parameterKey("HostedZoneId").parameterValue(settings.get("HOSTED_ZONE")).build());
            templateParameters.add(Parameter.builder().parameterKey("UseEFS").parameterValue(enableEfs.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("MountPoint").parameterValue(mountPoint).build());
            templateParameters.add(Parameter.builder().parameterKey("EncryptEFS").parameterValue(encryptFilesystem.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("EFSLifecyclePolicy").parameterValue(filesystemLifecycle).build());

            //--> for FSX for Windows  
            templateParameters.add(Parameter.builder().parameterKey("UseFSx").parameterValue(enableFSx.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxWindowsMountDrive").parameterValue(fsxWindowsMountDrive).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxDailyBackupTime").parameterValue(fsxDailyBackupTime).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxBackupRetention").parameterValue(fsxBackupRetentionDays).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxThroughputCapacity").parameterValue(fsxThroughputMbs).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxStorageCapacity").parameterValue(fsxStorageGb).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxWeeklyMaintenanceTime").parameterValue(fsxWeeklyMaintenanceTime).build());
            templateParameters.add(Parameter.builder().parameterKey("FsxUseOntap").parameterValue(fsxUseOntap).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxOntapVolumeSize").parameterValue(fsxOntapVolumeSize).build());
            // <<-
            templateParameters.add(Parameter.builder().parameterKey("UseRDS").parameterValue(enableDatabase.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSInstanceClass").parameterValue(dbInstanceClass).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSEngine").parameterValue(dbEngine).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSEngineVersion").parameterValue(dbVersion).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSParameterGroupFamily").parameterValue(dbFamily).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSMasterUsername").parameterValue(dbMasterUsername).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSMasterPasswordParam").parameterValue(dbMasterPasswordRef).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSPort").parameterValue(dbPort).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSDatabase").parameterValue(dbDatabase).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSBootstrap").parameterValue(dbBootstrap).build());
            templateParameters.add(Parameter.builder().parameterKey("MetricsStream").parameterValue(settings.get("METRICS_STREAM") != null ? settings.get("METRICS_STREAM") : "").build());
            templateParameters.add(Parameter.builder().parameterKey("ALBAccessLogsBucket").parameterValue(settings.get("ALB_ACCESS_LOGS_BUCKET")).build());
            templateParameters.add(Parameter.builder().parameterKey("EventBus").parameterValue(settings.get("EVENT_BUS")).build());
            templateParameters.add(Parameter.builder().parameterKey("BillingPlan").parameterValue(billingPlan).build());
            for (Parameter p : templateParameters) {
                if (p.parameterValue() == null) {
                    LOGGER.error("OnboardingService::provisionTenant template parameter {} is NULL", p.parameterKey());
                    dal.updateStatus(onboardingId, OnboardingStatus.failed);
                    throw new RuntimeException("CloudFormation template parameter " + p.parameterKey() + " is NULL");
                }
            }

            String tenantShortId = tenantId.toString().substring(0, 8);
            String stackName = "Tenant-" + tenantShortId;

            // Now run the onboarding stack to provision the infrastructure for this tenant
            LOGGER.info("OnboardingService::provisionTenant create stack " + stackName);
            Onboarding onboarding = dal.getOnboarding(onboardingId);
            onboarding.setTenantId(tenantId);
            String stackId = null;
            try {
                CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                        .stackName(stackName)
                        .onFailure("DO_NOTHING") // This was set to DO_NOTHING to ease debugging of failed stacks. Maybe not appropriate for "production". If we change this we'll have to add a whole bunch of IAM delete permissions to the execution role.
                        //.timeoutInMinutes(60) // Some resources can take a really long time to light up. Do we want to specify this?
                        .capabilitiesWithStrings("CAPABILITY_NAMED_IAM")
                        .notificationARNs(settings.get("ONBOARDING_SNS"))
                        .templateURL("https://" + settings.get("SAAS_BOOST_BUCKET") + ".s3.amazonaws.com/" + settings.get("ONBOARDING_TEMPLATE"))
                        .parameters(templateParameters)
                        .build()
                );
                stackId = cfnResponse.stackId();
                onboarding.setStatus(OnboardingStatus.provisioning);
                onboarding.setStackId(stackId);
                onboarding = dal.updateOnboarding(onboarding);
                LOGGER.info("OnboardingService::provisionTenant stack id " + stackId);
            } catch (SdkServiceException cfnError) {
                LOGGER.error("cloudformation::createStack failed {}", cfnError.getMessage());
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                dal.updateStatus(onboardingId, OnboardingStatus.failed);
                throw cfnError;
            }

            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(onboarding));
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::provisionTenant exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent statusEventListener(Map<String, Object> event, Context context) {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::statusEventListener");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        if ("aws.codepipeline".equals(event.get("source"))) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            OnboardingStatus status = null;
            Object pipelineState = detail.get("state");
            if ("STARTED".equals(pipelineState)) {
                status = OnboardingStatus.deploying;
            } else if ("FAILED".equals(pipelineState) || "CANCELED".equals(pipelineState)) {
                status = OnboardingStatus.failed;
            } else if ("SUCCEEDED".equals(pipelineState)) {
                status = OnboardingStatus.deployed;
            }
            String pipeline = (String) detail.get("pipeline");
            String prefix = "tenant-";
            if (pipeline != null && pipeline.startsWith(prefix)) {
                String tenantId = pipeline.substring(prefix.length());
                Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
                if (onboarding != null) {
                    tenantId = onboarding.getTenantId().toString();
                    LOGGER.info("OnboardingService::statusEventListener Updating Onboarding status for tenant " + tenantId + " to " + status);
                    onboarding = dal.updateStatus(onboarding.getId(), status);
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(200)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(onboarding));

                    // And update the tenant record
                    if (OnboardingStatus.deployed == onboarding.getStatus()) {
                        try {
                            ObjectNode systemApiRequest = MAPPER.createObjectNode();
                            systemApiRequest.put("resource", "tenants/" + tenantId + "/onboarding");
                            systemApiRequest.put("method", "PUT");
                            systemApiRequest.put("body", "{\"id\":\"" + tenantId + "\", \"onboardingStatus\":\"succeeded\"}");
                            PutEventsRequestEntry systemApiCallEvent = PutEventsRequestEntry.builder()
                                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                                    .detailType(SYSTEM_API_CALL_DETAIL_TYPE)
                                    .source(SYSTEM_API_CALL_SOURCE)
                                    .detail(MAPPER.writeValueAsString(systemApiRequest))
                                    .build();
                            PutEventsResponse eventBridgeResponse = eventBridge.putEvents(r -> r
                                    .entries(systemApiCallEvent)
                            );
                            for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                                if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                                    LOGGER.info("Put event success {} {}", entry.toString(), systemApiCallEvent.toString());
                                } else {
                                    LOGGER.error("Put event failed {}", entry.toString());
                                }
                            }
                        } catch (JsonProcessingException ioe) {
                            LOGGER.error("JSON processing failed");
                            LOGGER.error(Utils.getFullStackTrace(ioe));
                            throw new RuntimeException(ioe);
                        } catch (SdkServiceException eventBridgeError) {
                            LOGGER.error("events::PutEvents");
                            LOGGER.error(Utils.getFullStackTrace(eventBridgeError));
                            throw eventBridgeError;
                        }
                    }
                } else {
                    response = new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(404);
                }
            }
        } else if (event.containsKey("body")) {
            Map<String, Object> body = Utils.fromJson((String) event.get("body"), Map.class);
            if (null == body) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Invalid request body.\"}");
            }
            String tenantId = (String) body.get("tenantId");
            String provisioningStatus = (String) body.get("stackStatus");
            OnboardingStatus status = OnboardingStatus.failed;
            if ("CREATE_COMPLETE".equals(provisioningStatus)) {
                status = OnboardingStatus.provisioned;
            } else if ("UPDATE_COMPLETE".equals(provisioningStatus)) {
                status = OnboardingStatus.updated;
            } else if ("CREATE_FAILED".equals(provisioningStatus) || "DELETE_FAILED".equals(provisioningStatus)) {
                status = OnboardingStatus.failed;
            } else if ("DELETE_COMPLETE".equals(provisioningStatus)) {
                status = OnboardingStatus.deleted;
            }

            Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
            if (onboarding != null) {
                LOGGER.info("OnboardingService::statusEventListener Updating Onboarding status for tenant " + onboarding.getTenantId() + " to " + status);
                onboarding = dal.updateStatus(onboarding.getId(), status);

                //update the Tenant record status
                try {
                    ObjectNode systemApiRequest = MAPPER.createObjectNode();
                    systemApiRequest.put("resource", "tenants/" + tenantId + "/onboarding");
                    systemApiRequest.put("method", "PUT");
                    systemApiRequest.put("body", "{\"id\":\"" + tenantId + "\", \"onboardingStatus\":\"" + status + "\"}");
                    PutEventsRequestEntry systemApiCallEvent = PutEventsRequestEntry.builder()
                            .eventBusName(SAAS_BOOST_EVENT_BUS)
                            .detailType(SYSTEM_API_CALL_DETAIL_TYPE)
                            .source(SYSTEM_API_CALL_SOURCE)
                            .detail(MAPPER.writeValueAsString(systemApiRequest))
                            .build();
                    PutEventsResponse eventBridgeResponse = eventBridge.putEvents(r -> r
                            .entries(systemApiCallEvent)
                    );
                    for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                        if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                            LOGGER.info("Put event success {} {}", entry.toString(), systemApiCallEvent.toString());
                        } else {
                            LOGGER.error("Put event failed {}", entry.toString());
                        }
                    }

                    if (status.equals(OnboardingStatus.provisioned)) {
                        //move the s3 file from the SAAS_BOOST_BUCKET to a key for the tenant and name it config.zip
                        moveTenantConfigFile(onboarding.getId().toString(), tenantId);
                    }
                } catch (JsonProcessingException ioe) {
                    LOGGER.error("JSON processing failed");
                    LOGGER.error(Utils.getFullStackTrace(ioe));
                    throw new RuntimeException(ioe);
                } catch (SdkServiceException eventBridgeError) {
                    LOGGER.error("events::PutEvents");
                    LOGGER.error(Utils.getFullStackTrace(eventBridgeError));
                    throw eventBridgeError;
                }
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(onboarding));
            } else {
                response = new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(404);
            }
        }
        if (response == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Empty request body.\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::statusEventListener exec " + totalTimeMillis);
        return response;
    }

    public Object deleteTenant(Map<String, Object> event, Context context) {
        /*
        Handles a event message to delete a tenant
         */

        //*TODO - Add Lambda function and event rule for "Delete Tenant"
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::deleteTenant");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String tenantId = (String) detail.get("tenantId");
        Onboarding onboarding = dal.getOnboardingByTenantId(tenantId);
        if (onboarding != null) {
            LOGGER.info("OnboardingService::deleteTenant Updating Onboarding status for tenant " + onboarding.getTenantId() + " to DELETING");
            dal.updateStatus(onboarding.getId(), OnboardingStatus.deleting);
        }

        //Now lets delete the CloudFormation stack
        String tenantStackId = "Tenant-" + tenantId.split("-")[0];
        try {
            cfn.deleteStack(DeleteStackRequest.builder().stackName(tenantStackId).build());
        } catch (SdkServiceException cfnError) {
            if (null == cfnError.getMessage() || !cfnError.getMessage().contains("does not exist")) {
                LOGGER.error("deleteCloudFormationStack::deleteStack failed {}", cfnError.getMessage());
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::deleteTenant exec " + totalTimeMillis);
        return null;
    }
    private void moveTenantConfigFile(String onboardingId, String tenantId) {
        if (Utils.isBlank(SAAS_BOOST_BUCKET)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_BUCKET");
        }

        String sourceFile = "temp/" + onboardingId + ".zip";
        LOGGER.info("Start: Move tenant config zip file {} for tenant {}", sourceFile, tenantId);

        //check if S3 file with name onboardingId.zip exists
        String encodedUrl = null;

        try {
            encodedUrl = URLEncoder.encode(SAAS_BOOST_BUCKET + "/" + sourceFile, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
           LOGGER.error("URL could not be encoded: " + e.getMessage());
           throw new RuntimeException("Unable to move tenant zip file " +  sourceFile);
        }

        try {
            ListObjectsRequest listObjects = ListObjectsRequest
                    .builder()
                    .bucket(SAAS_BOOST_BUCKET)
                    .prefix(sourceFile)
                    .build();

            ListObjectsResponse res = s3.listObjects(listObjects);
            if (res.contents().isEmpty()) {
                //no file to copy
                LOGGER.info("No config zip file to copy for tenant {}", tenantId);
                return;
            }

            s3.getObject(GetObjectRequest.builder()
                    .bucket(SAAS_BOOST_BUCKET)
                    .key(sourceFile)
                    .build());
        } catch (S3Exception e) {
            LOGGER.error("Error fetching config zip file {} ", sourceFile + " for tenant " + tenantId);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException("Unable to copy config zip file " +  sourceFile + " for tenant " + tenantId);
        }
        try {
            s3.copyObject(CopyObjectRequest.builder()
                    .copySource(encodedUrl)
                    .destinationBucket(SAAS_BOOST_BUCKET)
                    .destinationKey("tenants/" + tenantId + "/config.zip")
                    .serverSideEncryption("AES256")
                    .build());
        } catch (S3Exception e) {
            LOGGER.error("Error copying config zip file {} to {}", sourceFile, tenantId + "/config.zip");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException("Unable to copy config zip file " +  sourceFile + " for tenant " + tenantId);
        }

        //delete the existing file
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(SAAS_BOOST_BUCKET)
                    .key(sourceFile)
                    .build());
        } catch (S3Exception e) {
            LOGGER.error("Error deleting tenant zip file {}", sourceFile);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException("Unable to delete tenant zip file " +  sourceFile + " for tenant " + tenantId);
        }

        LOGGER.info("Completed: Move tenant config file {} for tenant {}", sourceFile, tenantId);
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
        long startTimeMillis = System.currentTimeMillis();
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
            String stackId = onboarding.getStackId();

            Integer taskMemory = (Integer) tenant.get("memory");
            Integer taskCpu = (Integer) tenant.get("cpu");
            Integer taskCount = (Integer) tenant.get("minCount");
            Integer maxCount = (Integer) tenant.get("maxCount");
            String billingPlan = (String) tenant.get("planId");
            String subdomain = (String) tenant.get("subdomain");

            // We have an inconsistency with how the Lambda source folder is managed.
            // If you update an existing SaaS Boost installation with the installer script
            // it will create a new S3 "folder" for the Lambda code packages to force
            // CloudFormation to update the functions. We are now saving this change as
            // part of the global settings, but we'll need to go fetch it here because it's
            // not part of the onboarding request data nor is it part of the tenant data.
            Map<String, Object> settings = fetchSettingsForTenantUpdate(context);
            String lambdaSourceFolder = (String) settings.get("SAAS_BOOST_LAMBDAS_FOLDER");
            String templateUrl = "https://" + settings.get("SAAS_BOOST_BUCKET") + ".s3.amazonaws.com/" + settings.get("ONBOARDING_TEMPLATE");

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
            templateParameters.add(Parameter.builder().parameterKey("FsxUseOntap").usePreviousValue(Boolean.TRUE).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxOntapVolumeSize").usePreviousValue(Boolean.TRUE).build());

            if (taskMemory != null) {
                LOGGER.info("Overriding previous template parameter TaskMemory to {}", taskMemory);
                templateParameters.add(Parameter.builder().parameterKey("TaskMemory").parameterValue(taskMemory.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("TaskMemory").usePreviousValue(Boolean.TRUE).build());
            }
            if (taskCpu != null) {
                LOGGER.info("Overriding previous template parameter TaskCPU to {}", taskCpu);
                templateParameters.add(Parameter.builder().parameterKey("TaskCPU").parameterValue(taskCpu.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("TaskCPU").usePreviousValue(Boolean.TRUE).build());
            }
            if (taskCount != null) {
                LOGGER.info("Overriding previous template parameter TaskCount to {}", taskCount);
                templateParameters.add(Parameter.builder().parameterKey("TaskCount").parameterValue(taskCount.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("TaskCount").usePreviousValue(Boolean.TRUE).build());
            }
            if (maxCount != null) {
                LOGGER.info("Overriding previous template parameter MaxTaskCount to {}", maxCount);
                templateParameters.add(Parameter.builder().parameterKey("MaxTaskCount").parameterValue(maxCount.toString()).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("MaxTaskCount").usePreviousValue(Boolean.TRUE).build());
            }
            if (billingPlan != null) {
                LOGGER.info("Overriding previous template parameter BillingPlan to {}", Utils.isBlank(billingPlan) ? "''" : billingPlan);
                templateParameters.add(Parameter.builder().parameterKey("BillingPlan").parameterValue(billingPlan).build());
            } else {
                templateParameters.add(Parameter.builder().parameterKey("BillingPlan").usePreviousValue(Boolean.TRUE).build());
            }
            // Pass in the subdomain each time because a blank value
            // means delete the Route53 record set
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

    public APIGatewayProxyResponseEvent resetDomainName(Map<String, Object> event, Context context) {
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
        LOGGER.info("OnboardingService::resetDomainName");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        Map<String, String> settings = null;
        ApiRequest getSettingsRequest = ApiRequest.builder()
                .resource("settings?setting=SAAS_BOOST_STACK&setting=DOMAIN_NAME")
                .method("GET")
                .build();
        SdkHttpFullRequest getSettingsApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, getSettingsRequest);
        LOGGER.info("Fetching SaaS Boost stack and domain name from Settings Service");
        try {
            String getSettingsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(getSettingsApiRequest, API_TRUST_ROLE, context.getAwsRequestId());
            ArrayList<Map<String, String>> getSettingsResponse = Utils.fromJson(getSettingsResponseBody, ArrayList.class);
            settings = getSettingsResponse
                    .stream()
                    .collect(Collectors.toMap(
                            setting -> setting.get("name"), setting -> setting.get("value")
                    ));
        } catch (Exception e) {
            LOGGER.error("Error invoking API settings");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        LOGGER.info("Calling cloudFormation update-stack --stack-name {}", settings.get("SAAS_BOOST_STACK"));
        String stackId = null;
        try {
            UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                    .stackName(settings.get("SAAS_BOOST_STACK"))
                    .usePreviousTemplate(Boolean.TRUE)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .parameters(
                            Parameter.builder().parameterKey("DomainName").parameterValue(settings.get("DOMAIN_NAME")).build(),
                            Parameter.builder().parameterKey("SaaSBoostBucket").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("LambdaSourceFolder").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("Environment").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("AdminEmailAddress").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("PublicApiStage").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("PrivateApiStage").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("Version").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("ADPasswordParam").usePreviousValue(Boolean.TRUE).build()
                    )
                    .build()
            );
            stackId = cfnResponse.stackId();
            LOGGER.info("OnboardingService::resetDomainName stack id " + stackId);
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

        response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody("{\"stackId\": \"" + stackId + "\"}");

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::resetDomainName exec " + totalTimeMillis);
        return response;
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
    private Map<String, Object> checkLimits() throws Exception {
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
        String responseBody = null;
        try {
            LOGGER.info("API call for quotas/check");
            responseBody = ApiGatewayHelper.signAndExecuteApiRequest(apiRequest, API_TRUST_ROLE, "MetricsService-GetTenants");
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

}