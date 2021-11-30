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

import com.amazon.aws.partners.saasfactory.saasboost.appconfig.AppConfig;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.AppConfigHelper;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.OperatingSystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.ServiceConfig;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SettingsService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private final static Logger LOGGER = LoggerFactory.getLogger(SettingsService.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String SAAS_BOOST_BUCKET = System.getenv("SAAS_BOOST_BUCKET");
    private static final String CLOUDFRONT_DISTRIBUTION = System.getenv("CLOUDFRONT_DISTRIBUTION");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private static final String SYSTEM_API_CALL_DETAIL_TYPE = "System API Call";
    private static final String SYSTEM_API_CALL_SOURCE = "saas-boost";
    private final static Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    final static List<String> REQUIRED_PARAMS = Collections.unmodifiableList(
            Arrays.asList("SAAS_BOOST_BUCKET", "CODE_PIPELINE_BUCKET", "CODE_PIPELINE_ROLE", "ECR_REPO", "ONBOARDING_WORKFLOW",
                    "ONBOARDING_SNS", "ONBOARDING_TEMPLATE", "TRANSIT_GATEWAY", "TRANSIT_GATEWAY_ROUTE_TABLE", "EGRESS_ROUTE_TABLE",
                    "SAAS_BOOST_ENVIRONMENT", "SAAS_BOOST_STACK", "SAAS_BOOST_LAMBDAS_FOLDER")
    );
    final static List<String> READ_WRITE_PARAMS = Collections.unmodifiableList(
            Arrays.asList("DOMAIN_NAME", "HOSTED_ZONE", "SSL_CERT_ARN", "APP_NAME", "METRICS_STREAM", "BILLING_API_KEY",
                    "SERVICE_NAME", "IS_PUBLIC", "PATH", "COMPUTE_SIZE", "TASK_CPU", "TASK_MEMORY", "CONTAINER_PORT", "HEALTH_CHECK",
                    "FILE_SYSTEM_MOUNT_POINT", "FILE_SYSTEM_ENCRYPT", "FILE_SYSTEM_LIFECYCLE", "MIN_COUNT", "MAX_COUNT", "DB_ENGINE",
                    "DB_VERSION", "DB_PARAM_FAMILY", "DB_INSTANCE_TYPE", "DB_NAME", "DB_HOST", "DB_PORT", "DB_MASTER_USERNAME",
                    "DB_MASTER_PASSWORD", "DB_BOOTSTRAP_FILE", "CLUSTER_OS", "CLUSTER_INSTANCE_TYPE",
                    //Added for FSX
                    "FILE_SYSTEM_TYPE", // EFS or FSX
                    "FSX_STORAGE_GB", // GB 32 to 65,536
                    "FSX_THROUGHPUT_MBS", // MB/s
                    "FSX_BACKUP_RETENTION_DAYS", // 7 to 35
                    "FSX_DAILY_BACKUP_TIME", //HH:MM in UTC
                    "FSX_WEEKLY_MAINTENANCE_TIME",//d:HH:MM in UTC
                    "FSX_WINDOWS_MOUNT_DRIVE")
    );

    final static List<String> TENANT_PARAMS = Collections.unmodifiableList(
            Arrays.asList("DB_HOST", "ALB")
    );
    private final SettingsServiceDAL dal;
    private final EventBridgeClient eventBridge;
    private final S3Client s3;
    private final S3Presigner presigner;

    public SettingsService() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new SettingsServiceDAL();

        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
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
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent getSettings(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        LOGGER.info("getSettings recv: " + event);

        final long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);
        List<Setting> settings = new ArrayList<>();
        // ?key1=val1
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        // ?key2=val1&key=val2
        Map<String, List<String>> multiValueQueryParams = (Map<String, List<String>>) event.get("multiValueQueryStringParameters");
        // Only return one set of params
        LOGGER.info("getSettings queryParams: "+ queryParams);
        LOGGER.info("getSettings multiValueQueryParams: " + multiValueQueryParams);
        if (queryParams != null && queryParams.containsKey("readOnly")) {
            LOGGER.error("queryParams included readOnly, but we're ignoring readOnly!");
//            if (Boolean.parseBoolean(queryParams.get("readOnly"))) {
//                settings = dal.getImmutableSettings();
//            } else {
//                settings = dal.getMutableSettings();
//            }
        }
        // Or, filter to return just a few params (ideally, less than 10)
        if (multiValueQueryParams != null && multiValueQueryParams.containsKey("setting")) {
            List<String> namedSettings = multiValueQueryParams.get("setting");
            settings = dal.getNamedSettings(namedSettings);
        }
        // Otherwise, return all params
        if (settings.isEmpty()) {
            settings = dal.getAllSettings();
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::getSettings exec " + totalTimeMillis);
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(settings));
    }

    public APIGatewayProxyResponseEvent getSetting(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String settingName = params.get("id");
        LOGGER.info("SettingsService::getSetting " + settingName);
        Setting setting = dal.getSetting(settingName);
        if (setting != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(setting));
        } else {
            response = new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(404);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::getSetting exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getSecret(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String settingName = params.get("id");
        LOGGER.info("SettingsService::getSecret " + settingName);
        Setting setting = dal.getSecret(settingName);
        if (setting != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(setting));
        } else {
            response = new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(404);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::getSecret exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getParameterStoreReference(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String settingName = params.get("id");
        LOGGER.info("SettingsService::getParameterStoreReference " + settingName);
        String parameterStoreRef = dal.getParameterStoreReference(settingName);
        if (parameterStoreRef != null) {
            Map<String, String> body = new HashMap<>();
            body.put("reference-key", parameterStoreRef);
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(body));
        } else {
            response = new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(404);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::getParameterStoreReference exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent updateSetting(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::updateSetting");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String key = params.get("id");
        LOGGER.info("SettingsService::updateSetting " + key);
        try {
            Setting setting = Utils.fromJson((String) event.get("body"), Setting.class);
            if (setting == null) {
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody("{\"message\":\"Empty request body.\"}");
            } else {
                if (setting.getName() == null || !setting.getName().equals(key)) {
                    LOGGER.error("SettingsService::updateSetting Can't update setting " + setting.getName() + " at resource " + key);
                    response = new APIGatewayProxyResponseEvent()
                            .withHeaders(CORS)
                            .withStatusCode(400)
                            .withBody("{\"message\":\"Invalid resource for setting.\"}");
                } else if (!SettingsService.READ_WRITE_PARAMS.contains(key)) {
                    LOGGER.error("SettingsService::updateSetting Setting " + key + " cannot be modified");
                    response = new APIGatewayProxyResponseEvent()
                            .withHeaders(CORS)
                            .withStatusCode(400)
                            .withBody("{\"message\":\"Can't modify immutable setting " + key + ".\"}");
                } else {
                    setting = dal.updateSetting(setting);
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(200)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(setting));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unable to update");
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Invalid JSON\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::updateSetting exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getTenantSettings(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);

        Map<String, String> params = (Map) event.get("pathParameters");
        String tenantId = params.get("id");
        UUID tenantUUID;
        try {
            tenantUUID = UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Invalid id for setting.\"}");
        }
        List<Setting> settings = dal.getTenantSettings(tenantUUID);

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::getTenantSettings exec " + totalTimeMillis);
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(settings));
    }

    public APIGatewayProxyResponseEvent getTenantSetting(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String tenantId = params.get("id");
        String settingName = params.get("setting");
        LOGGER.info("SettingsService::getTenantSetting " + settingName);
        UUID tenantUUID;
        try {
            tenantUUID = UUID.fromString(tenantId);
        } catch (IllegalArgumentException e) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Invalid id for setting.\"}");
        }

        Setting setting = dal.getTenantSetting(tenantUUID, settingName);
        if (setting != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(setting));
        } else {
            response = new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(404);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::getTenantSetting exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent updateTenantSetting(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::updateTenantSetting");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        LOGGER.info(Utils.toJson(params));
        String tenantId = params.get("id");
        String key = params.get("setting");
        LOGGER.info("SettingsService::updateTenantSetting " + key);
        try {
            Setting setting = Utils.fromJson((String) event.get("body"), Setting.class);
            if (setting == null) {
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody("{\"message\":\"Invalid resource for setting.\"}");
            } else {
                if (setting.getName() == null || !setting.getName().equals(key)) {
                    LOGGER.error("SettingsService::updateTenantSetting Can't update setting " + setting.getName() + " at resource " + key);
                    response = new APIGatewayProxyResponseEvent()
                            .withHeaders(CORS)
                            .withStatusCode(400)
                            .withBody("{\"message\":\"Invalid resource for setting.\"}");
                } else {
                    setting = dal.updateTenantSetting(UUID.fromString(tenantId), setting);
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(200)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(setting));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unable to update");
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Invalid Json\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::updateTenantSetting exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent deleteTenantSettings(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::deleteTenantSettings");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        LOGGER.info(Utils.toJson(params));
        String tenantId = params.get("id");

        try {
            dal.deleteTenantSettings(UUID.fromString(tenantId));
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(200);
        } catch (Exception e) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Error deleting tenant settings.\"}");
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::deleteTenantSettings exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent configOptions(Map<String, Object> event, Context context) {
        if (Utils.isBlank(CLOUDFRONT_DISTRIBUTION)) {
            throw new IllegalStateException("Missing environment variable CLOUDFRONT_DISTRIBUTION");
        }
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::configOptions");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        Map<String, Object> options = new HashMap<>();
        options.put("osOptions", Arrays.stream(OperatingSystem.values())
                .collect(
                        Collectors.toMap(OperatingSystem::name, OperatingSystem::getDescription)
                ));
        options.put("dbOptions", dal.rdsOptions());

        // Create a presigned S3 URL to upload the database bootstrap file to
        String bucket = Utils.isNotBlank(SAAS_BOOST_BUCKET) ? SAAS_BOOST_BUCKET : dal.getSetting("SAAS_BOOST_BUCKET").getValue();
        String key = "bootstrap.sql";
        final Duration expires = Duration.ofMinutes(15); // UI times out in 10 min

        // Make sure we have a CORS bucket policy in place
        GetBucketCorsResponse corsResponse = null;
        boolean corsDoesntExists = false;
        try {
            corsResponse = s3.getBucketCors(request -> request.bucket(bucket));
        } catch (S3Exception corsConfigError) {
            corsDoesntExists = corsConfigError.getMessage().startsWith("The CORS configuration does not exist");
        }
        if (corsDoesntExists || (corsResponse != null && (!corsResponse.hasCorsRules() || corsResponse.corsRules().isEmpty()))) {
            LOGGER.info("SaaS Boost bucket does not have a CORS policy yet");
            try {
                PutBucketCorsResponse cors = s3.putBucketCors(request -> request
                        .bucket(bucket)
                        .corsConfiguration(CORSConfiguration.builder()
                                .corsRules(Arrays.asList(
                                        CORSRule.builder()
                                                .allowedOrigins("http://localhost:3000")
                                                .allowedMethods("PUT").build(),
                                        CORSRule.builder()
                                                .allowedOrigins(CLOUDFRONT_DISTRIBUTION)
                                                .allowedMethods("PUT").build()
                                ))
                                .build()
                        )
                );
            } catch (SdkServiceException s3Error) {
                LOGGER.error("S3 error placing CORS policy on SaaS Boost bucket", s3Error);
                LOGGER.error(Utils.getFullStackTrace(s3Error));
                throw s3Error;
            }
        }
        // Generate the presigned URL
        PresignedPutObjectRequest presignedObject = presigner.presignPutObject(request -> request
                .signatureDuration(expires)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
                )
                .build()
        );
        Map<String, Object> uploadOptions = new HashMap<>();
        uploadOptions.put("url", presignedObject.url());
        uploadOptions.put("headers", presignedObject.signedHeaders());
        uploadOptions.put("method", presignedObject.httpRequest().method().toString());
        options.put("sqlUploadOptions", uploadOptions);

        response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody(Utils.toJson(options));

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::configOptions exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getAppConfig(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::getAppConfig");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        AppConfig appConfig = dal.getAppConfig();
        response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(appConfig));

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::getAppConfig exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent setAppConfig(Map<String, Object> event, Context context) {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_EVENT_BUS");
        }
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::setAppConfig");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        try {
            AppConfig appConfig = Utils.fromJson((String) event.get("body"), AppConfig.class);
            if (appConfig == null) {
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody("{\"message\":\"Empty request body.\"}");
            } else if (appConfig.getName() == null || appConfig.getName().isEmpty()) {
                LOGGER.error("Can't insert application configuration without an app name");
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody("{\"message\":\"Application name is required.\"");
            } else {
                // There are some settings which may be set by the installer before this API
                // is ever called
                AppConfig currentAppConfig = dal.getAppConfig();

                // Save all the settings for this app config
                appConfig = dal.setAppConfig(appConfig);

                // If they didn't tell the installer to use a domain name, but now have passed it in with
                // app config, we need to update the CloudFormation stack so it will create a hosted zone
                if (AppConfigHelper.isDomainChanged(currentAppConfig, appConfig)) {
                    LOGGER.info("AppConfig domain name has changed");
                    triggerDomainNameChange();
                }

                // If billing is enabled, trigger the event to establish the master billing provider account
                // artifacts using the 3rd party API key the ISV provided as part of the config.
                if (AppConfigHelper.isBillingChanged(currentAppConfig, appConfig) &&
                        AppConfigHelper.isBillingFirstTime(currentAppConfig, appConfig)) {
                    LOGGER.info("AppConfig now has a billing provider. Triggering billing setup.");
                    triggerBillingSetup();
                }

                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(appConfig));
            }
        } catch (Exception e) {
            LOGGER.error("Unable to parse incoming JSON");
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Invalid JSON\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::setAppConfig exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent updateAppConfig(Map<String, Object> event, Context context) {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_EVENT_BUS");
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
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::updateAppConfig");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        try {
            AppConfig updatedAppConfig = Utils.fromJson((String) event.get("body"), AppConfig.class);
            if (updatedAppConfig == null) {
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody("{\"message\":\"Empty request body.\"}");
            } else if (updatedAppConfig.getName() == null || updatedAppConfig.getName().isEmpty()) {
                LOGGER.error("Can't update application configuration without an app name");
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody("{\"message\":\"Application name is required.\"");
            } else {
                AppConfig currentAppConfig = dal.getAppConfig();
                updatedAppConfig = dal.setAppConfig(updatedAppConfig);

                if (AppConfigHelper.isDomainChanged(currentAppConfig, updatedAppConfig)) {
                    LOGGER.info("AppConfig domain name has changed");
                    triggerDomainNameChange();
                }

                if (AppConfigHelper.isBillingChanged(currentAppConfig, updatedAppConfig)) {
                    String apiKey1 = currentAppConfig.getBilling() != null ? currentAppConfig.getBilling().getApiKey() : null;
                    String apiKey2 = updatedAppConfig.getBilling() != null ? updatedAppConfig.getBilling().getApiKey() : null;
                    LOGGER.info("AppConfig billing provider has changed {} != {}", apiKey1, apiKey2);
                    if (AppConfigHelper.isBillingFirstTime(currentAppConfig, updatedAppConfig)) {
                        // 1. We didn't have a billing provider and now we do, trigger setup
                        // Existing provisioned tenants won't be subscribed to a billing plan
                        // so we don't need to update the tenant stacks.
                        LOGGER.info("AppConfig now has a billing provider. Triggering billing setup.");
                        triggerBillingSetup();
                    } else if (AppConfigHelper.isBillingRemoved(currentAppConfig, updatedAppConfig)) {
                        // 2. We had a billing provider and now we don't, disable integration
                        LOGGER.info("AppConfig has removed the billing provider.");
                        // TODO how do we cleanup the billing provider integration?
                    } else {
                        // 3. We had a billing provider and we're just changing the value of the key, that is
                        // taken care of by dal.setAppConfig and we don't need to trigger a setup because
                        // it's already been done.
                        LOGGER.info("AppConfig billing provider API key in-place change.");
                    }
                }

                if (AppConfigHelper.isComputeChanged(currentAppConfig, updatedAppConfig) ||
                        AppConfigHelper.isAutoScalingChanged(currentAppConfig, updatedAppConfig)) {
                    LOGGER.info("AppConfig compute and/or scaling has changed. Triggering update of default setting tenants.");
                    // Get all the provisioned tenants who have not customized
                    // their compute settings so we can update them to the new
                    // global settings.
                    ApiRequest getTenantsRequest = ApiRequest.builder()
                            .resource("tenants/provisioned?overrideDefaults=false")
                            .method("GET")
                            .build();
                    SdkHttpFullRequest getTenantsApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, getTenantsRequest);
                    try {
                        String getTenantsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(getTenantsApiRequest, API_TRUST_ROLE, context.getAwsRequestId());
                        ArrayList<Map<String, Object>> provisionedTenantsWithDefaultSettings = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
                        if (provisionedTenantsWithDefaultSettings != null) {
                            LOGGER.info("{} tenants with default settings to update", provisionedTenantsWithDefaultSettings.size());
                            for (Map<String, Object> tenant : provisionedTenantsWithDefaultSettings) {
                                // The onboarding service update tenant call expects to be given the
                                // values to use as parameters for the CloudFormation stack.
                                // AppConfig will delegate to ComputeSize for memory and cpu if it's set.
                                // TODO POEPPT
                                //tenant.put("memory", updatedAppConfig.getDefaultMemory());
                                //tenant.put("cpu", updatedAppConfig.getDefaultCpu());
                                //tenant.put("minCount", updatedAppConfig.getMinCount());
                                //tenant.put("maxCount", updatedAppConfig.getMaxCount());

                                LOGGER.info("Triggering update for tenant {}", tenant.get("id"));
                                Map<String, Object> systemApiRequest = new HashMap<>();
                                systemApiRequest.put("resource", "onboarding/update/tenant");
                                systemApiRequest.put("method", "PUT");
                                systemApiRequest.put("body", Utils.toJson(tenant));
                                publishEvent(SYSTEM_API_CALL_DETAIL_TYPE, SYSTEM_API_CALL_SOURCE, systemApiRequest);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error invoking API " + API_GATEWAY_STAGE + "/tenants/provisioned?overrideDefaults=false");
                        LOGGER.error(Utils.getFullStackTrace(e));
                        throw new RuntimeException(e);
                    }
                }

                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(updatedAppConfig));
            }
        } catch (Exception e) {
            LOGGER.error("Unable to update");
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Invalid JSON\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::updateAppConfig exec " + totalTimeMillis);
        return response;
    }
    
    public APIGatewayProxyResponseEvent deleteAppConfig(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::deleteAppConfig");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        try {
            dal.deleteAppConfig();
            response = new APIGatewayProxyResponseEvent()
            .withHeaders(CORS)
            .withStatusCode(200);
        } catch (Exception e) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Error deleting application settings.\"}");
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsService::deleteAppConfig exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent updateServiceConfig(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::updateServiceConfig");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        final Map<String, BiFunction<ServiceConfig.Builder, String, ServiceConfig.Builder>> allowedKeys = Map.of(
                "ECR_REPO", ServiceConfig.Builder::containerRepo
        );

        // PUT /settings/config/{serviceName}/{key}
        Map<String, String> pathParameters = (Map) event.get("pathParameters");
        String serviceName = pathParameters.get("serviceName");
        String jsonKey = pathParameters.get("key");

        if (!allowedKeys.containsKey(jsonKey)) {
            // we only accept allowedKeys, 400 Bad Request otherwise
            String errorMessage = String.format("Can only accept keys: %s", allowedKeys.keySet());
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"" + errorMessage + ".\"}");
        }

        String jsonValue = (String) Utils.fromJson((String) event.get("body"), HashMap.class).get("value");
        // get AppConfig and alter the config
        AppConfig existingAppConfig = dal.getAppConfig();
        ServiceConfig requestedService = existingAppConfig.getServices().get(serviceName);
        ServiceConfig editedService = null;

        // the service they request to update must actually exist
        if (requestedService != null) {
            editedService = allowedKeys.get(jsonKey).apply(ServiceConfig.builder(requestedService), jsonValue).build();
            AppConfig newAppConfig = AppConfig.builder(existingAppConfig).addServiceConfig(editedService).build();
            dal.setAppConfig(newAppConfig);
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(200)
                    .withBody(Utils.toJson(newAppConfig));
        } else {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(404)
                    .withBody("{\"message\":\"Service not found.\"}");
        }

        LOGGER.info("SettingsService::updateServiceConfig exec " + (System.currentTimeMillis() - startTimeMillis));
        return response;
    }

    private void triggerBillingSetup() {
        Map<String, Object> billingSetupDetail = new HashMap<>();
        billingSetupDetail.put("message", "System Setup");
        publishEvent("Billing System Setup", "saas-boost", billingSetupDetail);
    }

    private void triggerDomainNameChange() {
        Map<String, Object> domainNameDetail = new HashMap<>();
        domainNameDetail.put("resource", "onboarding/update/domain");
        domainNameDetail.put("method", "PUT");
        publishEvent(SYSTEM_API_CALL_DETAIL_TYPE, SYSTEM_API_CALL_SOURCE, domainNameDetail);
    }

    private void publishEvent(String type, String source, Map<String, Object> detail) {
        try {
            PutEventsRequestEntry event = PutEventsRequestEntry.builder()
                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                    .detailType(type)
                    .source(source)
                    .detail(Utils.toJson(detail))
                    .build();
            PutEventsResponse eventBridgeResponse = eventBridge.putEvents(r -> r
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
            LOGGER.error("events::PutEvents", eventBridgeError);
            LOGGER.error(Utils.getFullStackTrace(eventBridgeError));
            throw eventBridgeError;
        }
    }

}