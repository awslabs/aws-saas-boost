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

import com.amazon.aws.partners.saasfactory.saasboost.appconfig.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class SettingsService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsService.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String RESOURCES_BUCKET = System.getenv("RESOURCES_BUCKET");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");

    static final List<String> REQUIRED_PARAMS = Collections.unmodifiableList(
            Arrays.asList("SAAS_BOOST_BUCKET", "CODE_PIPELINE_BUCKET", "CODE_PIPELINE_ROLE", "ECR_REPO", "ONBOARDING_WORKFLOW",
                    "ONBOARDING_SNS", "ONBOARDING_TEMPLATE", "TRANSIT_GATEWAY", "TRANSIT_GATEWAY_ROUTE_TABLE", "EGRESS_ROUTE_TABLE",
                    "SAAS_BOOST_ENVIRONMENT", "SAAS_BOOST_STACK", "SAAS_BOOST_LAMBDAS_FOLDER")
    );
    static final List<String> READ_WRITE_PARAMS = Collections.unmodifiableList(
            Arrays.asList("DOMAIN_NAME", "HOSTED_ZONE", "SSL_CERT_ARN", "APP_NAME", "METRICS_STREAM", "BILLING_API_KEY",
                    "SERVICE_NAME", "IS_PUBLIC", "PATH", "COMPUTE_SIZE", "TASK_CPU", "TASK_MEMORY", "CONTAINER_PORT", "HEALTH_CHECK",
                    "FILE_SYSTEM_MOUNT_POINT", "FILE_SYSTEM_ENCRYPT", "FILE_SYSTEM_LIFECYCLE", "MIN_COUNT", "MAX_COUNT", "DB_ENGINE",
                    "DB_VERSION", "DB_PARAM_FAMILY", "DB_INSTANCE_TYPE", "DB_NAME", "DB_HOST", "DB_PORT", "DB_MASTER_USERNAME",
                    "DB_PASSWORD", "DB_BOOTSTRAP_FILE", "CLUSTER_OS", "CLUSTER_INSTANCE_TYPE",
                    //Added for FSX
                    "FILE_SYSTEM_TYPE", // EFS or FSX
                    "FSX_STORAGE_GB", // GB 32 to 65,536
                    "FSX_THROUGHPUT_MBS", // MB/s
                    "FSX_BACKUP_RETENTION_DAYS", // 7 to 35
                    "FSX_DAILY_BACKUP_TIME", //HH:MM in UTC
                    "FSX_WEEKLY_MAINTENANCE_TIME",//d:HH:MM in UTC
                    "FSX_WINDOWS_MOUNT_DRIVE")
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

        final long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);
        List<Setting> settings = new ArrayList<>();
        // ?key1=val1
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        // ?key2=val1&key=val2
        Map<String, List<String>> multiValueQueryParams = (Map<String, List<String>>) event.get("multiValueQueryStringParameters");
        // Only return one set of params
        LOGGER.info("getSettings queryParams: " + queryParams);
        LOGGER.info("getSettings multiValueQueryParams: " + multiValueQueryParams);
        if (queryParams != null && queryParams.containsKey("readOnly")) {
            LOGGER.error("queryParams included readOnly, but we're ignoring readOnly!");
            //TODO why has this changed?
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

    public APIGatewayProxyResponseEvent options(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsService::configOptions");
        //Utils.logRequestEvent(event);

        Map<String, Object> options = new HashMap<>();
        options.put("osOptions", Arrays.stream(OperatingSystem.values())
                .collect(
                        Collectors.toMap(OperatingSystem::name, OperatingSystem::getDescription)
                ));
        options.put("dbOptions", dal.rdsOptions());
        options.put("acmOptions", dal.acmCertificateOptions());

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
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

    public APIGatewayProxyResponseEvent updateAppConfig(Map<String, Object> event, Context context) {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_EVENT_BUS");
        }
        if (Utils.isBlank(RESOURCES_BUCKET)) {
            throw new IllegalStateException("Missing environment variable RESOURCES_BUCKET");
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
        APIGatewayProxyResponseEvent response;

        AppConfig updatedAppConfig = Utils.fromJson((String) event.get("body"), AppConfig.class);
        if (updatedAppConfig == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Invalid request body.\"}");
        } else if (updatedAppConfig.getName() == null || updatedAppConfig.getName().isEmpty()) {
            LOGGER.error("Can't update application configuration without an app name");
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Application name is required.\"}");
        } else {
            AppConfig currentAppConfig = dal.getAppConfig();
            if (currentAppConfig.isEmpty()) {
                LOGGER.info("Processing first time app config save");
                // First time setting the app config object don't bother going through all of the validation
                updatedAppConfig = dal.setAppConfig(updatedAppConfig);

                // If the app config has any databases, get the presigned S3 urls to upload bootstrap files
                generateDatabaseBootstrapFileUrl(updatedAppConfig);

                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                        AppConfigEvent.APP_CONFIG_CHANGED.detailType(),
                        Collections.EMPTY_MAP
                );

                if (AppConfigHelper.isBillingFirstTime(currentAppConfig, updatedAppConfig)) {
                    // 1. We didn't have a billing provider and now we do, trigger setup
                    // Existing provisioned tenants won't be subscribed to a billing plan
                    // so we don't need to update the tenant stacks.
                    LOGGER.info("AppConfig now has a billing provider. Triggering billing setup.");
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                            "Billing System Setup",
                            Map.of("message", "System Setup")
                    );
                }

                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(updatedAppConfig));
            } else {
                LOGGER.info("Processing update to existing app config");
                List<Map<String, Object>> provisionedTenants = getProvisionedTenants(context);
                boolean provisioned = !provisionedTenants.isEmpty();
                boolean okToUpdate = validateAppConfigUpdate(currentAppConfig, updatedAppConfig, provisioned);
                boolean fireUpdateAppConfigEvent = false;

                if (okToUpdate) {
                    LOGGER.info("Ok to proceed with app config update");
                    if (AppConfigHelper.isDomainChanged(currentAppConfig, updatedAppConfig)) {
                        LOGGER.info("AppConfig domain name has changed");
                        fireUpdateAppConfigEvent = true;
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
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                                    "Billing System Setup",
                                    Map.of("message", "System Setup")
                            );
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

                    if (AppConfigHelper.isServicesChanged(currentAppConfig, updatedAppConfig)) {
                        LOGGER.info("AppConfig application services changed");
                        //LOGGER.info(Utils.toJson(currentAppConfig));
                        //LOGGER.info(Utils.toJson(updatedAppConfig));
                        Set<String> removedServices = AppConfigHelper.removedServices(currentAppConfig, updatedAppConfig);
                        if (!removedServices.isEmpty()) {
                            LOGGER.info("Services {} were removed from AppConfig: deleting their parameters.", removedServices);
                            for (String serviceName : removedServices) {
                                dal.deleteServiceConfig(currentAppConfig, serviceName);
                            }
                        }
                        fireUpdateAppConfigEvent = true;
                    }

                    // TODO how do we want to deal with tier settings changes?

                    // TODO do we want to allow adding new services to the config?
                    LOGGER.info("Persisting updated app config");
                    updatedAppConfig = dal.setAppConfig(updatedAppConfig);

                    // If the app config has any databases, get the presigned S3 urls to upload bootstrap files
                    if (!provisioned) {
                        generateDatabaseBootstrapFileUrl(updatedAppConfig);
                    }

                    if (fireUpdateAppConfigEvent) {
                        Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, "saas-boost",
                                AppConfigEvent.APP_CONFIG_CHANGED.detailType(),
                                Collections.EMPTY_MAP
                        );
                    }

                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(200)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(updatedAppConfig));
                } else {
                    LOGGER.info("App config update validation failed");
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(CORS)
                            .withBody("{\"message\":\"Application config update validation failed.\"}");
                }
            }
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

    public void handleAppConfigEvent(Map<String, Object> event, Context context) {
        if ("saas-boost".equals(event.get("source"))) {
            AppConfigEvent appConfigEvent = AppConfigEvent.fromDetailType((String) event.get("detail-type"));
            if (appConfigEvent != null) {
                switch (appConfigEvent) {
                    case APP_CONFIG_RESOURCE_CHANGED:
                        LOGGER.info("Handling App Config Resource Changed");
                        handleAppConfigResourceChanged(event, context);
                        break;
                    case APP_CONFIG_CHANGED:
                        // We produce this event, but currently aren't consuming it
                        break;
                }
            } else {
                LOGGER.error("Can't find app config event for detail-type {}", event.get("detail-type"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else if ("aws.s3".equals(event.get("source"))) {
            LOGGER.info("Handling App Config Resources File S3 Event");
            handleAppConfigResourcesFileEvent(event, context);
        } else {
            LOGGER.error("Unknown event source " + event.get("source"));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleAppConfigResourceChanged(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String json = Utils.toJson(detail);
        if (json != null) {
            AppConfig changedAppConfig = Utils.fromJson(json, AppConfig.class);
            if (changedAppConfig != null) {
                AppConfig existingAppConfig = dal.getAppConfig();
                // Only updated the hosted zone if it was passed in
                if (json.contains("hostedZone")
                        && AppConfigHelper.isHostedZoneChanged(existingAppConfig, changedAppConfig)) {
                    LOGGER.info("Updating hosted zone from {} to {}", existingAppConfig.getHostedZone(),
                            changedAppConfig.getHostedZone());
                    // TODO be nice to fix this so you don't have to know the secret path
                    dal.updateSetting(Setting.builder()
                            .name(SettingsServiceDAL.APP_BASE_PATH + "HOSTED_ZONE")
                            .value(changedAppConfig.getHostedZone())
                            .build()
                    );
                }
                // Only update the services if they were passed in
                if (json.contains("services") && changedAppConfig.getServices() != null) {
                    for (Map.Entry<String, ServiceConfig> changedService : changedAppConfig.getServices().entrySet()) {
                        String changedServiceName = changedService.getKey();
                        ServiceConfig changedServiceConfig = changedService.getValue();
                        ServiceConfig requestedService = existingAppConfig.getServices().get(changedServiceName);
                        if (requestedService != null) {
                            String changedContainerRepo = changedServiceConfig.getContainerRepo();
                            String existingContainerRepo = requestedService.getContainerRepo();
                            if (!Utils.nullableEquals(existingContainerRepo, changedContainerRepo)) {
                                LOGGER.info("Updating service {} ECR repo from {} to {}", changedServiceName,
                                        requestedService.getContainerRepo(), changedServiceConfig.getContainerRepo());
                                ServiceConfig editedService = ServiceConfig.builder(requestedService)
                                        .containerRepo(changedServiceConfig.getContainerRepo())
                                        .build();
                                dal.setServiceConfig(editedService);
                            }
                        } else {
                            LOGGER.error("Can't find app config service {}", changedServiceName);
                        }
                    }
                }
            } else {
                LOGGER.error("Can't parse event detail as AppConfig {}", json);
            }
        } else {
            LOGGER.error("Can't serialize detail to JSON {}", event.get("detail"));
        }
    }

    protected void handleAppConfigResourcesFileEvent(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        // A database bootstrap file was uploaded for one of the app config services.
        // We'll update the service config so Onboarding will know to run the file as
        // part of provisioning RDS.
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String bucket = (String) ((Map<String, Object>) detail.get("bucket")).get("name");
        String key = (String) ((Map<String, Object>) detail.get("object")).get("key");
        LOGGER.info("Processing resources bucket PUT {}, {}", bucket, key);

        // key will be services/${service_name}/bootstrap.sql
        String serviceName = key.substring("services/".length(), (key.length() - "/bootstrap.sql".length()));
        AppConfig appConfig = dal.getAppConfig();
        for (Map.Entry<String, ServiceConfig> serviceConfig : appConfig.getServices().entrySet()) {
            if (serviceName.equals(serviceConfig.getKey())) {
                ServiceConfig service = serviceConfig.getValue();
                LOGGER.info("Saving bootstrap.sql file for {}", service.getName());
                service.getDatabase().setBootstrapFilename(key);
                dal.setServiceConfig(service);
                break;
            }
        }
    }

    protected List<Map<String, Object>> getProvisionedTenants(Context context) {
        // Fetch all of the provisioned tenants
        LOGGER.info("Calling tenant service to fetch all provisioned tenants");
        String getTenantsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                ApiGatewayHelper.getApiRequest(
                        API_GATEWAY_HOST,
                        API_GATEWAY_STAGE,
                        ApiRequest.builder()
                                .resource("tenants?status=provisioned")
                                .method("GET")
                                .build()
                ),
                API_TRUST_ROLE,
                context.getAwsRequestId()
        );
        List<Map<String, Object>> tenants = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
        if (tenants == null) {
            tenants = new ArrayList<>();
        }
        return tenants;
    }

    protected static boolean validateAppConfigUpdate(AppConfig currentAppConfig, AppConfig updatedAppConfig,
                                                     boolean provisionedTenants) {
        boolean domainNameValid = true;
        if (AppConfigHelper.isDomainChanged(currentAppConfig, updatedAppConfig)) {
            if (provisionedTenants) {
                LOGGER.error("Can't change domain name after onboarding tenants");
                domainNameValid = false;
            } else {
                if (Utils.isNotBlank(currentAppConfig.getDomainName())) {
                    LOGGER.error("Can only set a new domain name not change an existing domain name");
                    domainNameValid = false;
                } else {
                    domainNameValid = true;
                }
            }
        }

        boolean serviceConfigValid = true;
        if (AppConfigHelper.isServicesChanged(currentAppConfig, updatedAppConfig)) {
            if (provisionedTenants) {
                Set<String> removedServices = AppConfigHelper.removedServices(currentAppConfig, updatedAppConfig);
                if (!removedServices.isEmpty()) {
                    LOGGER.error("Can't remove existing application services after onboarding tenants");
                    serviceConfigValid = false;
                }
            }
        }

        return domainNameValid && serviceConfigValid;
    }

    protected void generateDatabaseBootstrapFileUrl(AppConfig appConfig) {
        // Create the pre-signed S3 URLs for the bootstrap SQL files. We won't save these to the
        // database record because the user might not upload any SQL files. If they do, we'll
        // process those uploads async and persist the relevant data to the database.
        for (Map.Entry<String, ServiceConfig> serviceConfig : appConfig.getServices().entrySet()) {
            String serviceName = serviceConfig.getKey();
            ServiceConfig service = serviceConfig.getValue();
            if (service.hasDatabase() && Utils.isBlank(service.getDatabase().getBootstrapFilename())) {
                try {
                    // Create a presigned S3 URL to upload the database bootstrap file to
                    final String key = "services/" + serviceName + "/bootstrap.sql";
                    final Duration expires = Duration.ofMinutes(15); // UI times out in 10 min
                    PresignedPutObjectRequest presignedObject = presigner.presignPutObject(request -> request
                            .signatureDuration(expires)
                            .putObjectRequest(PutObjectRequest.builder()
                                    .bucket(RESOURCES_BUCKET)
                                    .key(key)
                                    .build()
                            ).build()
                    );
                    service.getDatabase().setBootstrapFilename(presignedObject.url().toString());
                } catch (S3Exception s3Error) {
                    LOGGER.error("s3 presign url failed", s3Error);
                    LOGGER.error(Utils.getFullStackTrace(s3Error));
                    throw s3Error;
                }
            }
        }
    }
}