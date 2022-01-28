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
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.ecr.EcrClient;
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

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OnboardingService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final String SYSTEM_API_CALL_DETAIL_TYPE = "System API Call";
    private static final String SYSTEM_API_CALL_SOURCE = "saas-boost";
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private static final String SAAS_BOOST_BUCKET = System.getenv("SAAS_BOOST_BUCKET");
    private static final String ONBOARDING_SNS = System.getenv("ONBOARDING_SNS");
    private final CloudFormationClient cfn;
    private final EventBridgeClient eventBridge;
    private final EcrClient ecr;
    private final OnboardingServiceDAL dal;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final Route53Client route53;

    public OnboardingService() {
        final long startTimeMillis = System.currentTimeMillis();
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

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::getOnboarding");

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
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

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::getOnboardings");

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
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
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        if (Utils.isBlank(SAAS_BOOST_BUCKET)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_BUCKET");
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::startOnboarding");

        Utils.logRequestEvent(event);

        // Check to see if there are any images in the ECR repo before allowing onboarding
        // TODO change this to check for an application definition
//        try {
//            ListImagesResponse dockerImages = ecr.listImages(request -> request.repositoryName(ECR_REPO));
//            //ListImagesResponse::hasImageIds will return true if the imageIds object is not null
//            if (!dockerImages.hasImageIds() || dockerImages.imageIds().isEmpty()) {
//                return new APIGatewayProxyResponseEvent()
//                        .withStatusCode(400)
//                        .withHeaders(CORS)
//                        .withBody("{\"message\": \"No workload image deployed to ECR.\"}");
//            }
//        } catch (SdkServiceException ecrError) {
//            LOGGER.error("ecr:ListImages error", ecrError.getMessage());
//            LOGGER.error(Utils.getFullStackTrace(ecrError));
//            throw ecrError;
//        }

        // Parse the onboarding request
        Map<String, Object> requestBody = Utils.fromJson((String) event.get("body"), HashMap.class);
        if (null == requestBody) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid Json in Request.\"}");
        }

        String tenantName = (String) requestBody.get("name");
        if (Utils.isBlank(tenantName)) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Tenant name is required.\"}");
        }

        // Make sure we're not trying to onboard a tenant to an existing subdomain
        String subdomain = (String) requestBody.get("subdomain");
        if (Utils.isNotBlank(subdomain)) {
            Map<String, String> settings;
            LOGGER.info("Fetching SaaS Boost hosted zone id from Settings Service");
            try {
                String getSettingsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                        ApiGatewayHelper.getApiRequest(
                                API_GATEWAY_HOST,
                                API_GATEWAY_STAGE,
                                ApiRequest.builder()
                                        .resource("settings?setting=HOSTED_ZONE&setting=DOMAIN_NAME")
                                        .method("GET")
                                        .build()),
                        API_TRUST_ROLE,
                        context.getAwsRequestId()
                );
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
                final String hostedZoneId = settings.get("HOSTED_ZONE");
                final String domainName = settings.get("DOMAIN_NAME");

                if (Utils.isBlank(hostedZoneId) || Utils.isBlank(domainName)) {
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(CORS)
                            .withBody("{\"message\": \"Can't define tenant subdomain " + subdomain + " without a domain name and hosted zone.\"}");
                }
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

        // Check if Quotas will be exceeded.
        // TODO: We should add check for CIDRs here!
        try {
            LOGGER.info("Check Service Quota Limits");
            Map<String, Object> retMap = checkLimits(context);
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

        // Create a new onboarding request record for a tenant
        Onboarding onboarding = new Onboarding();
        onboarding.setTenantName((String) requestBody.get("name"));
        // We're using the generated onboarding id as part of the S3 key
        // so, first we need to persist the onboarding record, then we'll
        // have to update it. TODO rethink this...
        LOGGER.info("Saving new onboarding request");
        onboarding = dal.insertOnboarding(onboarding);

        // Generate the presigned URL for this tenant's ZIP archive
        final String key = "temp/" + onboarding.getId().toString() + ".zip";
        final Duration expires = Duration.ofMinutes(15); // UI times out in 10 min
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
        onboarding = dal.updateOnboarding(onboarding);
        LOGGER.info("Updated onboarding request with S3 URL");
        try {
            // Set the attributes for our new tenant
            Map<String, Object> tenant = new HashMap<>();
            tenant.put("active", true);
            tenant.put("onboardingStatus", onboarding.getStatus().toString());
            tenant.put("tier", requestBody.getOrDefault("tier", "default")); //TODO remove default?
            tenant.put("name", tenantName);
            if (Utils.isNotBlank(subdomain)) {
                tenant.put("subdomain", subdomain);
            }
            String planId = (String) requestBody.get("planId");
            if (Utils.isNotBlank(planId)) {
                tenant.put("planId", planId);
            }
            // Call the tenant service synchronously to insert the new tenant record
            LOGGER.info("Calling tenant service insert tenant API");
            LOGGER.info(Utils.toJson(tenant));
            String insertTenantResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                    ApiGatewayHelper.getApiRequest(
                            API_GATEWAY_HOST,
                            API_GATEWAY_STAGE,
                            ApiRequest.builder()
                                    .resource("tenants")
                                    .method("POST")
                                    .body(Utils.toJson(tenant))
                                    .build()
                    ),
                    API_TRUST_ROLE,
                    context.getAwsRequestId()
            );
            Map<String, Object> insertedTenant = Utils.fromJson(insertTenantResponseBody, LinkedHashMap.class);
            if (null == insertedTenant) {
                dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Tenant insert API call failed.\"}");
            }
            // Update the onboarding record with the new tenant id
            onboarding.setTenantId(UUID.fromString((String) insertedTenant.get("id")));
            onboarding = dal.updateOnboarding(onboarding);

            // Invoke the provisioning API async
            Map<String, Object> provision = new HashMap<>();
            provision.put("onboardingId", onboarding.getId());
            provision.put("tenant", insertedTenant);
            Map<String, Object> systemApiRequest = new HashMap<>();
            systemApiRequest.put("resource", "onboarding/provision");
            systemApiRequest.put("method", "POST");
            systemApiRequest.put("body", Utils.toJson(provision));
            try {
                publishEvent(systemApiRequest, SYSTEM_API_CALL_DETAIL_TYPE);
            } catch (Exception e) {
                LOGGER.error("Error publishing tenant provision event to EventBridge");
                dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Tenant provision API call failed.\"}");
            }
        } catch (Exception e) {
            LOGGER.error("Error invoking API tenants");
            dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::startOnboarding exec " + totalTimeMillis);

        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(onboarding));
    }

    public APIGatewayProxyResponseEvent updateStatus(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        APIGatewayProxyResponseEvent response;
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
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
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
        if (Utils.isBlank(ONBOARDING_SNS)) {
            throw new IllegalArgumentException("Missing required environment variable ONBOARDING_SNS");
        }

        Utils.logRequestEvent(event);
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingService::provisionTenant");
        APIGatewayProxyResponseEvent response;

        Map<String, Object> onboardingRequest = Utils.fromJson((String) event.get("body"), LinkedHashMap.class);
        // The invocation event sent to us must contain the tenant we're
        // provisioning for and the onboarding job that's tracking it
        Map<String, Object> tenant = (LinkedHashMap<String, Object>) onboardingRequest.get("tenant");
        UUID onboardingId;
        UUID tenantId;
        try {
            onboardingId = UUID.fromString((String) onboardingRequest.get("onboardingId"));
            tenantId = UUID.fromString(((String) tenant.get("id")).toLowerCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.error("Error parsing onboarding request ids", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid request body.\"}");
        }

        Map<String, Object> appConfig = getAppConfig(context);
        if (null == appConfig) {
            dal.updateStatus(onboardingId, OnboardingStatus.failed);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Settings get app config API call failed.\"}");
        }

        // And parameters specific to this tenant
        String cidrPrefix;
        try {
            String cidrBlock = dal.assignCidrBlock(tenantId.toString());
            cidrPrefix = cidrBlock.substring(0, cidrBlock.indexOf(".", cidrBlock.indexOf(".") + 1));
        } catch (Exception e) {
            dal.updateStatus(onboardingId, OnboardingStatus.failed);
            throw e;
        }

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
        templateParameters.add(Parameter.builder().parameterKey("TenantId").parameterValue(tenantId.toString()).build());
        templateParameters.add(Parameter.builder().parameterKey("TenantSubDomain").parameterValue(tenantSubdomain).build());
        templateParameters.add(Parameter.builder().parameterKey("CidrPrefix").parameterValue(cidrPrefix).build());
        templateParameters.add(Parameter.builder().parameterKey("Tier").parameterValue(tier).build());

        for (Parameter p : templateParameters) {
            if (p.parameterValue() == null) {
                LOGGER.error("OnboardingService::provisionTenant template parameter {} is NULL", p.parameterKey());
                dal.updateStatus(onboardingId, OnboardingStatus.failed);
                throw new RuntimeException("CloudFormation template parameter " + p.parameterKey() + " is NULL");
            }
        }

        String tenantShortId = tenantId.toString().substring(0, 8);
        String stackName = "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantShortId;

        // Now run the onboarding stack to provision the infrastructure for this tenant
        LOGGER.info("OnboardingService::provisionTenant create stack " + stackName);
        Onboarding onboarding = dal.getOnboarding(onboardingId);
        onboarding.setTenantId(tenantId);
        onboarding.setTenantName((String) tenant.get("name")); // TODO do we use this anywhere?
        String stackId;
        try {
            CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                    .stackName(stackName)
                    .disableRollback(true) // This was set to DO_NOTHING to ease debugging of failed stacks. Maybe not appropriate for "production". If we change this we'll have to add a whole bunch of IAM delete permissions to the execution role.
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .notificationARNs(ONBOARDING_SNS)
                    .templateURL("https://" + SAAS_BOOST_BUCKET + ".s3.amazonaws.com/tenant-onboarding.yaml")
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

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::provisionTenant exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent provisionApplication(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
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
        final long startTimeMillis = System.currentTimeMillis();

        LOGGER.info("OnboardingService::provisionApplication");
        Map<String, Object> onboardingRequest = Utils.fromJson((String) event.get("body"), LinkedHashMap.class);

        // Load the onboarding request for this tenant
        UUID tenantId;
        Onboarding onboarding;
        try {
            tenantId = UUID.fromString((String) onboardingRequest.get("tenantId"));
            onboarding = dal.getOnboardingByTenantId(tenantId.toString());
            if (onboarding == null) {
                throw new NullPointerException("No onboarding record for tenant id " + tenantId.toString());
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.error("Error parsing onboarding request tenant id", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid request body.\"}");
        }

        // Fetch the details for this tenant
        LinkedHashMap<String, Object> tenant = null;
        try {
            LOGGER.info("Calling tenant service to get tenant {}", tenantId);
            String getTenantResponse = ApiGatewayHelper.signAndExecuteApiRequest(
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
            tenant = Utils.fromJson(getTenantResponse, LinkedHashMap.class);
            if (tenant == null) {
                throw new NullPointerException("Can't parse get tenant api response");
            }
        } catch (Exception e) {
            dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
            LOGGER.error("Error calling get tenant API", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Can't fetch tenant " + tenantId + ".\"}");
        }

        Map<String, Object> appConfig = getAppConfig(context);
        if (null == appConfig) {
            dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Settings get app config API call failed.\"}");
        }

        String applicationName = (String) appConfig.get("name");
        String vpc;
        String privateSubnetA;
        String privateSubnetB;
        String ecsSecurityGroup;
        String loadBalancerArn;
        String httpListenerArn;
        String httpsListenerArn = ""; // might not have an HTTPS listener if they don't have an SSL certificate
        String ecsCluster;
        String fsxDns;
        Map<String, Map<String, String>> tenantResources = (Map<String, Map<String, String>>) tenant.get("resources");
        try {
            vpc = tenantResources.get("VPC").get("name");
            privateSubnetA = tenantResources.get("PRIVATE_SUBNET_A").get("name");
            privateSubnetB = tenantResources.get("PRIVATE_SUBNET_B").get("name");
            ecsCluster = tenantResources.get("ECS_CLUSTER").get("name");
            ecsSecurityGroup = tenantResources.get("ECS_SECURITY_GROUP").get("name");
            loadBalancerArn = tenantResources.get("LOAD_BALANCER").get("arn");
            httpListenerArn = tenantResources.get("HTTP_LISTENER").get("arn");
            if (tenantResources.containsKey("HTTPS_LISTENER")) {
                httpsListenerArn = Objects.toString(tenantResources.get("HTTPS_LISTENER").get("arn"), "");
            }
            if (Utils.isBlank(vpc) || Utils.isBlank(privateSubnetA) || Utils.isBlank(privateSubnetB)
                    || Utils.isBlank(ecsCluster) || Utils.isBlank(ecsSecurityGroup) || Utils.isBlank(loadBalancerArn)
                    || Utils.isBlank(httpListenerArn)) { // OK if HTTPS listener is blank
                throw new IllegalArgumentException("Missing required tenant environment resources");
            }
        } catch (Exception e) {
            dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
            LOGGER.error("Error parsing tenant resources", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Error parsing resources for tenant " + tenantId + ".\"}");
        }

        String tier = (String) tenant.get("tier");
        if (Utils.isBlank(tier)) {
            dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
            LOGGER.error("Tenant is missing tier");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Error retrieving tier for tenant " + tenantId + ".\"}");
        }

        List<String> applicationServiceStacks = new ArrayList<>();

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
            String serviceEnvName = serviceName.replaceAll("\\s+", "_").toUpperCase();
            String serviceHost = "SERVICE_" + serviceEnvName + "_HOST";
            String servicePort = "SERVICE_" + serviceEnvName + "_PORT";
            if (!isPublic) {
                LOGGER.debug("Creating service discovery environment variables {}, {}", serviceHost, servicePort);
                serviceDiscovery.put(serviceHost, serviceResourceName + ".local");
                serviceDiscovery.put(servicePort, Objects.toString(containerPort));
            }

            Map<String, Object> tiers = (Map<String, Object>) service.get("tiers");
            if (!tiers.containsKey(tier)) {
                dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                LOGGER.error("Missing tier '{}' definition for tenant {}", tier, tenantId);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Error retrieving tier for tenant " + tenantId + ".\"}");
            }

            Map<String, Object> tierConfig = (Map<String, Object>) tiers.get(tier);
            String clusterInstanceType = (String) tierConfig.get("instanceType");
            Integer taskMemory = (Integer) tierConfig.get("memory");
            Integer taskCpu = (Integer) tierConfig.get("cpu");
            Integer minCount = (Integer) tierConfig.get("min");
            Integer maxCount = (Integer) tierConfig.get("max");

            // Does this service use a shared filesystem?
            Boolean enableEfs = Boolean.FALSE;
            Boolean enableFSx = Boolean.FALSE;
            String mountPoint = "";
            Boolean encryptFilesystem = Boolean.FALSE;
            String filesystemLifecycle = "NEVER";
            String fileSystemType = "";
            Integer fsxStorageGb = 0;
            Integer fsxThroughputMbs = 0;
            Integer fsxBackupRetentionDays = 7;
            String fsxDailyBackupTime = "";
            String fsxWeeklyMaintenanceTime = "";
            String fsxWindowsMountDrive = "";
            Map<String, Object> filesystem = (Map<String, Object>) tierConfig.get("filesystem");
            if (filesystem != null && !filesystem.isEmpty()) {
                fileSystemType = (String) filesystem.get("fileSystemType");
                mountPoint = (String) filesystem.get("mountPoint");
                if ("EFS".equals(fileSystemType)) {
                    enableEfs = Boolean.TRUE;
                    Map<String, Object> efsConfig = (Map<String, Object>) filesystem.get("efs");
                    encryptFilesystem = (Boolean) efsConfig.get("encryptAtRest");
                    filesystemLifecycle = (String) efsConfig.get("filesystemLifecycle");
                } else if ("FSX".equals(fileSystemType)) {
                    enableFSx = Boolean.TRUE;
                    Map<String, Object> fsxConfig = (Map<String, Object>) filesystem.get("fsx");
                    fsxStorageGb = (Integer) fsxConfig.get("storageGb"); // GB 32 to 65,536
                    fsxThroughputMbs = (Integer) fsxConfig.get("throughputMbs"); // MB/s
                    fsxBackupRetentionDays = (Integer) fsxConfig.get("backupRetentionDays"); // 7 to 35
                    fsxDailyBackupTime = (String) fsxConfig.get("dailyBackupTime"); //HH:MM in UTC
                    fsxWeeklyMaintenanceTime = (String) fsxConfig.get("weeklyMaintenanceTime");//d:HH:MM in UTC
                    fsxWindowsMountDrive = (String) fsxConfig.get("windowsMountDrive");
                }
            }

            // Does this service use a relational database?
            Boolean enableDatabase = Boolean.FALSE;
            String dbInstanceClass = "";
            String dbEngine = "";
            String dbVersion = "";
            String dbFamily = "";
            String dbMasterUsername = "";
            String dbMasterPasswordRef = "";
            Integer dbPort = -1;
            String dbDatabase = "";
            String dbBootstrap = "";
            Map<String, Object> database = (Map<String, Object>) tierConfig.get("database");
            if (database != null && !database.isEmpty()) {
                enableDatabase = Boolean.TRUE;
                dbEngine = (String) database.get("engine");
                dbVersion = (String) database.get("version");
                dbFamily = (String) database.get("family");
                dbInstanceClass = (String) database.get("instanceClass");
                dbMasterUsername = (String) database.get("username");
                dbPort = (Integer) database.get("port");
                dbDatabase = (String) database.get("database");
                // TODO fix this dbBootstrap = (String) database.get("DB_BOOTSTRAP_FILE");
                //dbMasterPasswordRef = (String) database.get("password");
                dbMasterPasswordRef = "/saas-boost/" + SAAS_BOOST_ENV + "/DB_MASTER_PASSWORD";
            }

            List<Parameter> templateParameters = new ArrayList<>();
//        templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").parameterValue(settings.get("SAAS_BOOST_BUCKET")).build());
//        templateParameters.add(Parameter.builder().parameterKey("LambdaSourceFolder").parameterValue(settings.get("SAAS_BOOST_LAMBDAS_FOLDER")).build());
//        templateParameters.add(Parameter.builder().parameterKey("ArtifactBucket").parameterValue(settings.get("CODE_PIPELINE_BUCKET")).build());
//        templateParameters.add(Parameter.builder().parameterKey("ALBAccessLogsBucket").parameterValue(settings.get("ALB_ACCESS_LOGS_BUCKET")).build());
//        templateParameters.add(Parameter.builder().parameterKey("BillingPlan").parameterValue(billingPlan).build());
//        templateParameters.add(Parameter.builder().parameterKey("CodePipelineRoleArn").parameterValue(settings.get("CODE_PIPELINE_ROLE")).build());

            templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(SAAS_BOOST_ENV).build());
            templateParameters.add(Parameter.builder().parameterKey("TenantId").parameterValue(tenantId.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("ServiceName").parameterValue(serviceName).build());
            templateParameters.add(Parameter.builder().parameterKey("ServiceResourceName").parameterValue(serviceResourceName).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerRepository").parameterValue(containerRepo).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerRepositoryTag").parameterValue(imageTag).build());
            templateParameters.add(Parameter.builder().parameterKey("ECSCluster").parameterValue(ecsCluster).build());
            templateParameters.add(Parameter.builder().parameterKey("PubliclyAddressable").parameterValue(isPublic.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("PublicPathRoute").parameterValue(pathPart).build());
            templateParameters.add(Parameter.builder().parameterKey("PublicPathRulePriority").parameterValue(publicPathRulePriority.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("VPC").parameterValue(vpc).build());
            templateParameters.add(Parameter.builder().parameterKey("SubnetPrivateA").parameterValue(privateSubnetA).build());
            templateParameters.add(Parameter.builder().parameterKey("SubnetPrivateB").parameterValue(privateSubnetB).build());
            templateParameters.add(Parameter.builder().parameterKey("ECSLoadBalancer").parameterValue(loadBalancerArn).build());
            templateParameters.add(Parameter.builder().parameterKey("ECSLoadBalancerHttpListener").parameterValue(httpListenerArn).build());
            templateParameters.add(Parameter.builder().parameterKey("ECSLoadBalancerHttpsListener").parameterValue(httpsListenerArn).build());
            templateParameters.add(Parameter.builder().parameterKey("ECSSecurityGroup").parameterValue(ecsSecurityGroup).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerOS").parameterValue(clusterOS).build());
            templateParameters.add(Parameter.builder().parameterKey("ClusterInstanceType").parameterValue(clusterInstanceType).build());
            templateParameters.add(Parameter.builder().parameterKey("TaskMemory").parameterValue(taskMemory.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("TaskCPU").parameterValue(taskCpu.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("MinTaskCount").parameterValue(minCount.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("MaxTaskCount").parameterValue(maxCount.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerPort").parameterValue(containerPort.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("ContainerHealthCheckPath").parameterValue(healthCheck).build());
            templateParameters.add(Parameter.builder().parameterKey("UseEFS").parameterValue(enableEfs.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("MountPoint").parameterValue(mountPoint).build());
            templateParameters.add(Parameter.builder().parameterKey("EncryptEFS").parameterValue(encryptFilesystem.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("EFSLifecyclePolicy").parameterValue(filesystemLifecycle).build());
            templateParameters.add(Parameter.builder().parameterKey("UseFSx").parameterValue(enableFSx.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxWindowsMountDrive").parameterValue(fsxWindowsMountDrive).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxDailyBackupTime").parameterValue(fsxDailyBackupTime).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxBackupRetention").parameterValue(fsxBackupRetentionDays.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxThroughputCapacity").parameterValue(fsxThroughputMbs.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxStorageCapacity").parameterValue(fsxStorageGb.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("FSxWeeklyMaintenanceTime").parameterValue(fsxWeeklyMaintenanceTime).build());
            templateParameters.add(Parameter.builder().parameterKey("UseRDS").parameterValue(enableDatabase.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSInstanceClass").parameterValue(dbInstanceClass).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSEngine").parameterValue(dbEngine).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSEngineVersion").parameterValue(dbVersion).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSParameterGroupFamily").parameterValue(dbFamily).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSMasterUsername").parameterValue(dbMasterUsername).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSMasterPasswordParam").parameterValue(dbMasterPasswordRef).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSPort").parameterValue(dbPort.toString()).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSDatabase").parameterValue(dbDatabase).build());
            templateParameters.add(Parameter.builder().parameterKey("RDSBootstrap").parameterValue(dbBootstrap).build());
            // TODO rework these last 2?
            templateParameters.add(Parameter.builder().parameterKey("MetricsStream").parameterValue("").build());
            templateParameters.add(Parameter.builder().parameterKey("EventBus").parameterValue(SAAS_BOOST_EVENT_BUS).build());
            for (Parameter p : templateParameters) {
                //LOGGER.info("{} => {}", p.parameterKey(), p.parameterValue());
                if (p.parameterValue() == null) {
                    LOGGER.error("OnboardingService::provisionTenant template parameter {} is NULL", p.parameterKey());
                    dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                    throw new RuntimeException("CloudFormation template parameter " + p.parameterKey() + " is NULL");
                }
            }

            // Make the stack name look like what CloudFormation would have done for a nested stack
            String tenantShortId = tenantId.toString().substring(0, 8);
            String stackName = "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantShortId + "-app-" + serviceResourceName
                    + "-" + Utils.randomString(12).toUpperCase();
            if (stackName.length() > 128) {
                stackName = stackName.substring(0, 128);
            }
            // Now run the onboarding stack to provision the infrastructure for this application service
            LOGGER.info("OnboardingService::provisionApplication create stack " + stackName);

            String stackId;
            try {
                CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                        .stackName(stackName)
                        .disableRollback(true)
                        //.onFailure("DO_NOTHING") // This was set to DO_NOTHING to ease debugging of failed stacks. Maybe not appropriate for "production". If we change this we'll have to add a whole bunch of IAM delete permissions to the execution role.
                        //.timeoutInMinutes(60) // Some resources can take a really long time to light up. Do we want to specify this?
                        .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                        //.notificationARNs(settings.get("ONBOARDING_SNS"))
                        .templateURL("https://" + SAAS_BOOST_BUCKET + ".s3.amazonaws.com/tenant-onboarding-app.yaml")
                        .parameters(templateParameters)
                        .build()
                );
                stackId = cfnResponse.stackId();
                onboarding.setStatus(OnboardingStatus.provisioning);
                //onboarding.setStackId(stackId);
                onboarding = dal.updateOnboarding(onboarding);
                LOGGER.info("OnboardingService::provisionApplication stack id " + stackId);
                applicationServiceStacks.add(stackId);
            } catch (SdkServiceException cfnError) {
                LOGGER.error("cloudformation::createStack failed {}", cfnError.getMessage());
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                throw cfnError;
            }
        }

        if (!serviceDiscovery.isEmpty()) {
            String environmentFile = "tenants/" + tenantId.toString() + "/ServiceDiscovery.env";
            ByteArrayOutputStream environmentFileContents = new ByteArrayOutputStream();
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                        environmentFileContents, StandardCharsets.UTF_8)
                )) {
                serviceDiscovery.store(writer, null);
                s3.putObject(request -> request
                        .bucket(SAAS_BOOST_BUCKET)
                        .key(environmentFile)
                        .build(),
                        RequestBody.fromBytes(environmentFileContents.toByteArray())
                );
            } catch (SdkServiceException s3Error) {
                LOGGER.error("Error putting service discovery file to S3");
                LOGGER.error(Utils.getFullStackTrace(s3Error));
                dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                throw s3Error;
            } catch (IOException ioe) {
                LOGGER.error("Error writing service discovery data to output stream");
                LOGGER.error(Utils.getFullStackTrace(ioe));
                dal.updateStatus(onboarding.getId(), OnboardingStatus.failed);
                throw new RuntimeException(ioe);
            }
        }

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody(Utils.toJson(applicationServiceStacks));

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::provisionApplication exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent statusEventListener(Map<String, Object> event, Context context) {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        final long startTimeMillis = System.currentTimeMillis();
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
        final long startTimeMillis = System.currentTimeMillis();
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
        String encodedUrl;

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
            String stackId = onboarding.getStackId();

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

        Map<String, String> settings;
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

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody("{\"stackId\": \"" + stackId + "\"}");

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::resetDomainName exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent updateAppConfig(Map<String, Object> event, Context context) {
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
        LOGGER.info("OnboardingService::updateAppConfig");
        Utils.logRequestEvent(event);

        String stackName;
        ApiRequest getSettingsRequest = ApiRequest.builder()
                .resource("settings/SAAS_BOOST_STACK/secret")
                .method("GET")
                .build();
        SdkHttpFullRequest getSettingsApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, getSettingsRequest);
        LOGGER.info("Fetching SaaS Boost stack name from Settings Service");
        try {
            String getSettingsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(getSettingsApiRequest, API_TRUST_ROLE, context.getAwsRequestId());
            Map<String, String> getSettingsResponse = Utils.fromJson(getSettingsResponseBody, LinkedHashMap.class);
            stackName = getSettingsResponse.get("value");
        } catch (Exception e) {
            LOGGER.error("Error invoking API settings");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        Map<String, Object> appConfig = getAppConfig(context);
        Map<String, Object> services = (Map<String, Object>) appConfig.get("services");

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
                            Parameter.builder().parameterKey("DomainName").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("SSLCertificate").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("PublicApiStage").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("PrivateApiStage").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("Version").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("DeployActiveDirectory").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("ADPasswordParam").usePreviousValue(Boolean.TRUE).build(),
                            Parameter.builder().parameterKey("ApplicationServices").parameterValue(String.join(",", services.keySet())).build()
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

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody("{\"stackId\": \"" + stackId + "\"}");

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingService::updateAppConfig exec " + totalTimeMillis);
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
    private Map<String, Object> checkLimits(Context context) throws Exception {
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

    private void publishEvent(final Map<String, Object> eventBridgeDetail, final String detailType) {
        try {
            final PutEventsRequestEntry event = PutEventsRequestEntry.builder()
                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                    .detailType(detailType)
                    .source(SYSTEM_API_CALL_SOURCE)
                    .detail(Utils.toJson(eventBridgeDetail))
                    .build();
            PutEventsResponse eventBridgeResponse = eventBridge.putEvents(request -> request
                    .entries(event)
            );
            for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                    LOGGER.info("Put event success {} {}", entry.toString(), event.toString());
                } else {
                    LOGGER.error("Put event failed {}", entry.toString());
                }
            }
        } catch (SdkServiceException eventBridgeError) {
            LOGGER.error("events:PutEvents", eventBridgeError);
            LOGGER.error(Utils.getFullStackTrace(eventBridgeError));
            throw eventBridgeError;
        }
    }

    protected Map<String, Object> getAppConfig(Context context) {
        // Fetch all of the services configured for this application
        LOGGER.info("Calling settings service get app config API");
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
//        // For testing until we've integrated
//        try (InputStream json = getClass().getClassLoader().getResourceAsStream("appConfigSingle.json")) {
//            appConfig = Utils.fromJson(json, LinkedHashMap.class);
//        } catch (IOException ioe) {
//            LOGGER.error("Error creating input stream from class resource appConfig.json", ioe);
//        }
        return appConfig;
    }

    protected Map<String, Integer> getPathPriority(Map<String, Object> appConfig) {
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
}