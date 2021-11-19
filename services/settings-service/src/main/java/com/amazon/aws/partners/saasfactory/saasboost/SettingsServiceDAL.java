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

import com.amazon.aws.partners.saasfactory.saasboost.appconfig.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SettingsServiceDAL {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsServiceDAL.class);
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String OPTIONS_TABLE = System.getenv("OPTIONS_TABLE");
    private static final String AWS_REGION = System.getenv("AWS_REGION");

    // Package private for testing
    static final String SAAS_BOOST_PREFIX = "saas-boost";
    // e.g. /saas-boost/production/SAAS_BOOST_BUCKET
    static final Pattern SAAS_BOOST_PARAMETER_PATTERN = Pattern.compile("^/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/(.+)$");
    // e.g. /saas-boost/test/app/APP_NAME or /saas-boost/test/app/myService/SERVICE_JSON
    static final Pattern SAAS_BOOST_APP_PATTERN = Pattern.compile("^/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/app/(.+)$");
    // e.g. /saas-boost/staging/tenant/00000000-0000-0000-0000-000000000000/DB_HOST
    static final Pattern SAAS_BOOST_TENANT_PATTERN = Pattern.compile("^/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/tenant/(\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12})/(.+)$");

    private final SsmClient ssm;
    private DynamoDbClient ddb;

    public SettingsServiceDAL() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_ENV");
        }
        this.ssm = Utils.sdkClient(SsmClient.builder(), SsmClient.SERVICE_NAME);
        // Warm up SSM for cold start hack
        this.ssm.getParametersByPath(request -> request.path("/" + SAAS_BOOST_PREFIX + "/JUNK"));

        if (Utils.isNotBlank(OPTIONS_TABLE)) {
            this.ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
            // Cold start performance hack -- take the TLS hit for the client in the constructor
            this.ddb.describeTable(request -> request.tableName(OPTIONS_TABLE));
        }
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public List<Setting> getAllSettings() {
        return getAllParametersUnder("/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/", false)
                .stream().map(SettingsServiceDAL::fromParameterStore).collect(Collectors.toList());
    }

    public List<Setting> getAppConfigSettings() {
        return getAllParametersUnder("/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/app/", true)
                .stream().map(SettingsServiceDAL::fromAppParameterStore).collect(Collectors.toList());
    }

    public List<Setting> getTenantSettings(UUID tenantId) {
        return getAllParametersUnder("/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/tenant/" + tenantId.toString(), false)
                .stream().map(parameter -> SettingsServiceDAL.fromTenantParameterStore(tenantId, parameter)).collect(Collectors.toList());
    }

    public List<Parameter> getAllParametersUnder(String parameterStorePathPrefix, boolean recursive) {
        long startTimeMillis = System.currentTimeMillis();
        List<Parameter> parameters = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                GetParametersByPathResponse response = ssm.getParametersByPath(GetParametersByPathRequest
                        .builder()
                        .path(parameterStorePathPrefix)
                        .recursive(recursive)
                        .withDecryption(false) // don't expose secrets by default
                        .nextToken(nextToken)
                        .build()
                );
                nextToken = response.nextToken();
                parameters.addAll(response.parameters());
            } catch (SdkServiceException ssmError) {
                LOGGER.error("ssm:GetParametersByPath error " + ssmError.getMessage());
                throw ssmError;
            }
        } while (nextToken != null && !nextToken.isEmpty());

        if (parameters.isEmpty()) {
            throw new RuntimeException("Error loading Parameter Store SaaS Boost parameters: " + parameterStorePathPrefix);
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::getAllSettingsUnder {} Loaded {} parameters",
                parameterStorePathPrefix, parameters.size());
        LOGGER.info("SettingsServiceDAL::getAllSettingsUnder exec " + totalTimeMillis);
        return parameters;
    }

    public List<Setting> getNamedSettings(List<String> namedSettings) {
        List<Setting> settings = new ArrayList<>();
        List<String> batch = new ArrayList<>();
        LOGGER.info("getNamedSettings");
        long startTime = System.currentTimeMillis();
        try {
            Iterator<String> it = namedSettings.iterator();
            while (it.hasNext()) {
                if (batch.size() < 10) {
                    Parameter namedParameter = toParameterStore(Setting.builder().name(it.next()).build());
                    batch.add(namedParameter.name());

                    // If namedSettings % 10 != 0, then be sure to make a request with the remainder
                    if (!it.hasNext()) {
                        GetParametersResponse response = ssm.getParameters(request -> request.names(batch));
                        for (Parameter parameter : response.parameters()) {
                            settings.add(fromParameterStore(parameter));
                        }
                        // We've reached the end of the request input
                        break;
                    }
                } else {
                    // Batch has reached max size of 10, make the request
                    GetParametersResponse response = ssm.getParameters(request -> request.names(batch));
                    for (Parameter parameter : response.parameters()) {
                        settings.add(fromParameterStore(parameter));
                    }

                    // Clear the batch so we can fill it up for the next request
                    batch.clear();
                }
            }
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameters error", ssmError);
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
        long endTime = System.currentTimeMillis();
        LOGGER.info("getNamedSettings exec: {} ms", endTime - startTime);
        return settings;
    }

    public Setting getSetting(String settingName) {
        return getSetting(settingName, false);
    }

    public Setting getSecret(String settingName) {
        return getSetting(settingName, true);
    }

    public Setting getSetting(String settingName, boolean decrypt) {
        Setting setting = null;
        try {
            Parameter parameter = toParameterStore(Setting.builder().name(settingName).build());
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name(parameter.name())
                    .withDecryption(decrypt)
            );
            setting = fromParameterStore(response.parameter());
        } catch (ParameterNotFoundException pnf) {
            LOGGER.warn("ssm:GetParameter parameter not found for setting {}", settingName);
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameter error " + ssmError.getMessage());
            throw ssmError;
        }
        return setting;
    }

    public String getParameterStoreReference(String settingName) {
        Setting setting = getSetting(settingName);
        String paramStoreRef = "/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/" + setting.getName() + ":" + setting.getVersion();
        return paramStoreRef;
    }

    public Setting getTenantSetting(UUID tenantId, String settingName) {
        return getTenantSetting(tenantId, settingName, false);
    }

    public Setting getTenantSecret(UUID tenantId, String settingName) {
        return getTenantSetting(tenantId, settingName, true);
    }

    public Setting getTenantSetting(UUID tenantId, String settingName, boolean decrypt) {
        Setting setting = null;
        try {
            Parameter parameter = toTenantParameterStore(tenantId, Setting.builder().name(settingName).build());
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name(parameter.name())
                    .withDecryption(decrypt)
            );
            setting = fromTenantParameterStore(tenantId, response.parameter());
        } catch (ParameterNotFoundException pnf) {
            LOGGER.warn("ssm:GetParameter parameter not found for tenant {} setting {}", tenantId.toString(), settingName);
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameter error " + ssmError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ssmError));
        }
        return setting;
    }

    public Setting updateTenantSetting(UUID tenantId, Setting setting) {
        Parameter updated = putParameter(toTenantParameterStore(tenantId, setting));
        return fromTenantParameterStore(tenantId, updated);
    }

    public void deleteTenantSettings(UUID tenantId) {
        long startTimeMillis = System.currentTimeMillis();

        String parameterStorePath = "/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/tenant/" + tenantId.toString();
        List<String> parametersToDelete = SettingsService.TENANT_PARAMS.stream()
                .map(s -> parameterStorePath + "/" + s)
                .collect(Collectors.toList());

        try {
            DeleteParametersResponse response = ssm.deleteParameters(r ->
                    r.names(parametersToDelete)
            );
            LOGGER.info("SettingsServiceDAL::deleteTenantSettings removed " + response.deletedParameters().toString());
            if (response.hasInvalidParameters() && !response.invalidParameters().isEmpty()) {
                LOGGER.warn("SettingsServiceDAL::deleteTenantSettings invalid parameters " + response.invalidParameters().toString());
            }
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:DeleteParameters error " + ssmError.getMessage());
            throw ssmError;
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::deleteTenantSettings exec " + totalTimeMillis);

        return;
    }

    public Setting updateSetting(Setting setting) {
        Parameter updated = putParameter(toParameterStore(setting));
        return fromParameterStore(updated);
    }

    private void deleteSetting(Setting setting) {
        deleteParameter(toParameterStore(setting));
    }

    private Parameter putParameter(Parameter parameter) {
        Parameter updated = null;
        try {
            PutParameterResponse response = ssm.putParameter(request -> request
                    .type(parameter.type())
                    .overwrite(true)
                    .name(parameter.name())
                    .value(parameter.value())
            );
            updated = Parameter.builder()
                    .name(parameter.name())
                    .value(parameter.value())
                    .type(parameter.type())
                    .version(response.version())
                    .build();
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:PutParameter error " + ssmError.getMessage());
            throw ssmError;
        }
        return updated;
    }

    public void deleteParameter(Parameter parameter) {
        try {
            ssm.deleteParameter(request -> request
                    .name(parameter.name())
            );
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:DeleteParameter error " + ssmError.getMessage());
            throw ssmError;
        }
        return;
    }

    public List<Map<String, Object>> rdsOptions() {
        List<Map<String, Object>> orderableOptionsByRegion = new ArrayList<>();
        QueryResponse response = ddb.query(request -> request
                .tableName(OPTIONS_TABLE)
                .keyConditionExpression("#region = :region")
                .expressionAttributeNames(Stream
                        .of(new AbstractMap.SimpleEntry<>("#region", "region"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
                .expressionAttributeValues(Stream
                        .of(new AbstractMap.SimpleEntry<>(":region", AttributeValue.builder().s(AWS_REGION).build()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
        );
        response.items().forEach(item ->
                orderableOptionsByRegion.add(fromAttributeValueMap(item))
        );
        return orderableOptionsByRegion;
    }

    private static final Comparator<Map<String, Object>> INSTANCE_TYPE_COMPARATOR = ((instance1, instance2) -> {
        // T's before M's before R's
        int compare = 0;
        char type1 = ((String) instance1.get("instance")).charAt(0);
        char type2 = ((String) instance2.get("instance")).charAt(0);
        if (type1 != type2) {
            if ('T' == type1) {
                compare = -1;
            } else if ('T' == type2) {
                compare = 1;
            } else if ('M' == type1) {
                compare = -1;
            } else if ('M' == type2) {
                compare = 1;
            }
        }
        return compare;
    });

    private static final Comparator<Map<String, Object>> INSTANCE_GENERATION_COMPARATOR = ((instance1, instance2) -> {
        Integer gen1 = Integer.valueOf(((String) instance1.get("instance")).substring(1, 2));
        Integer gen2 = Integer.valueOf(((String) instance2.get("instance")).substring(1, 2));
        return gen1.compareTo(gen2);
    });

    private static final Comparator<Map<String, Object>> INSTANCE_SIZE_COMPARATOR = ((instance1, instance2) -> {
        String size1 = ((String) instance1.get("instance")).substring(3);
        String size2 = ((String) instance2.get("instance")).substring(3);
        List<String> sizes = Arrays.asList(
                "MICRO",
                "SMALL",
                "MEDIUM",
                "LARGE",
                "XL",
                "2XL",
                "4XL",
                "12XL",
                "24XL"
        );
        return Integer.compare(sizes.indexOf(size1), sizes.indexOf(size2));
    });

    public static final Comparator<Map<String, Object>> RDS_INSTANCE_COMPARATOR = INSTANCE_TYPE_COMPARATOR.thenComparing(INSTANCE_GENERATION_COMPARATOR).thenComparing(INSTANCE_SIZE_COMPARATOR);

    public static Map<String, Object> fromAttributeValueMap(Map<String, AttributeValue> item) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("engine", item.get("engine").s());
        option.put("region", item.get("region").s());
        Map<String, AttributeValue> optionAttributes = item.get("options").m();
        option.put("name", optionAttributes.get("name").s());
        option.put("description", optionAttributes.get("description").s());

        List<Map<String, Object>> instances = new ArrayList<>();
        for (Map.Entry<String, AttributeValue> optionAttribute : optionAttributes.get("instances").m().entrySet()) {
            //build the instance entry
            Map<String, Object> instance = new LinkedHashMap<>(); // Used a linked map so we can sort stuff
            Map<String, AttributeValue> instanceAttributes = optionAttribute.getValue().m();
            instance.put("instance",optionAttribute.getKey());
            instance.put("class",instanceAttributes.get("class").s());
            instance.put("description",instanceAttributes.get("description").s());

            List<Map<String, String>> versions = new ArrayList<>();
            List<AttributeValue> versionAttributes = instanceAttributes.get("versions").l();
            for (AttributeValue versionAttribute : versionAttributes) {
                versions.add(
                        versionAttribute.m().entrySet().stream()
                                .collect(Collectors.toMap(
                                        entry -> entry.getKey(),
                                        entry -> entry.getValue().s()
                                ))
                );
            }

            instance.put("versions", versions);
            instances.add(instance);
        }
        Collections.sort(instances, RDS_INSTANCE_COMPARATOR);
        option.put("instances", instances);

        return option;
    }

    public AppConfig setAppConfig(AppConfig appConfig) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsServiceDAL::setAppConfig");
        for (Setting setting : toSettings(appConfig)) {
            LOGGER.info("Updating setting {} {} in app config", setting.getName(), setting.getValue());
            if (setting.isSecure()) {
                Setting existing = getSetting(setting.getName());
                if (existing != null) {
                    LOGGER.info("Existing Secret {} {}", setting.getName(), existing.getValue());
                } else {
                    LOGGER.info("Secret {} doesn't exist yet", setting.getName());
                }
                // If we were passed the encrypted string for a secret (from the UI),
                // don't overwrite the secret with that gibberish...
                if (existing != null && existing.getValue().equals(setting.getValue())) {
                    // Nothing has changed, don't overwrite the value in Parameter Store
                    LOGGER.info("Skipping update of secret because encrypted values are the same");
                    continue;
                }
            }
            LOGGER.info("Calling put parameter {}", setting.getName());
            updateSetting(setting);
        }
        // Return a fresh copy of the config object to be sure all the encrypted
        // values are represented
        appConfig = getAppConfig();
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::setAppConfig exec " + totalTimeMillis);
        return appConfig;
    }

    public AppConfig getAppConfig() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsServiceDAL::getAppConfig");
        Map<String, String> appSettings = getAppConfigSettings()
                .stream()
                .collect(
                        Collectors.toMap(Setting::getName, Setting::getValue)
                );

        // Get the secret value for the optional billing provider or you'll always
        // be testing for empty against the encrypted hash of the "N/A" sentinel string
        AppConfig appConfig = toAppConfig(appSettings, getSecret("app/BILLING_API_KEY"));

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::getAppConfig exec " + totalTimeMillis);
        return appConfig;
    }

    private static AppConfig toAppConfig(Map<String, String> appSettings, Setting billingApiKey) {
        LOGGER.info("SettingsServiceDAL:toAppConfig recv {}", appSettings);
        AppConfig.Builder appConfigBuilder = AppConfig.builder()
                .name(appSettings.get("APP_NAME"))
                .domainName(appSettings.get("DOMAIN_NAME"))
                .sslCertArn(appSettings.get("SSL_CERT_ARN"));

        for (Map.Entry<String, String> appSetting : appSettings.entrySet()) {
            // every key that contains a "/" is necessarily nested under app
            // e.g. /app/service_001/tier_001/DB_PASSWORD
            //      /app/service_001/SERVICE_JSON
            if (appSetting.getKey().contains("/") && appSetting.getKey().endsWith("SERVICE_JSON")) {
                ServiceConfig serviceConfig = Utils.fromJson(appSetting.getValue(), ServiceConfig.class);
                appConfigBuilder.addServiceConfig(serviceConfig);
            }
        }

        if (billingApiKey != null) {
            String apiKey = billingApiKey.getValue();
            if (Utils.isNotEmpty(apiKey)) {
                appConfigBuilder.billing(BillingProvider.builder()
                        .apiKey(appSettings.get("BILLING_API_KEY"))
                        .build());
            }
        }

        return appConfigBuilder.build();
    }

    public void deleteAppConfig() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SettingsServiceDAL::deleteAppConfig");
        // NOTE: Could also implement this like deleteTenantSettings by combining SettingsService::REQUIRED_PARAMS
        // and SettingsService::READ_WRITE_PARAMS and building the Parameter Store path by hand to avoid the call(s)
        // to getParameters before the call to deleteParameters
        List<String> parametersToDelete = toSettings(getAppConfig()).stream()
                .map(s -> toParameterStore(s).name())
                .collect(Collectors.toList());
        List<String> batch = new ArrayList<>();
        try {
            Iterator<String> it = parametersToDelete.iterator();
            while (it.hasNext()) {
                if (batch.size() < 10) {
                    batch.add(it.next());
                    // If parametersToDelete % 10 != 0, then be sure to make a request with the remainder
                    if (!it.hasNext()) {
                        DeleteParametersResponse response = ssm.deleteParameters(r ->
                                r.names(batch)
                        );
                        LOGGER.info("SettingsServiceDAL::deleteAppConfig removed " + response.deletedParameters().toString());
                        if (response.hasInvalidParameters() && !response.invalidParameters().isEmpty()) {
                            LOGGER.warn("SettingsServiceDAL::deleteAppConfig invalid parameters " + response.invalidParameters().toString());
                        }
                        // We've reached the end of the request input
                        break;
                    }
                } else {
                    // Batch has reached max size of 10, make the request
                    DeleteParametersResponse response = ssm.deleteParameters(r ->
                            r.names(batch)
                    );
                    LOGGER.info("SettingsServiceDAL::deleteAppConfig removed " + response.deletedParameters().toString());
                    if (response.hasInvalidParameters() && !response.invalidParameters().isEmpty()) {
                        LOGGER.warn("SettingsServiceDAL::deleteAppConfig invalid parameters " + response.invalidParameters().toString());
                    }
                    // Clear the batch so we can fill it up for the next request
                    batch.clear();
                }
            }
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:DeleteParameters error", ssmError);
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::deleteAppConfig exec " + totalTimeMillis);
        return;
    }

    public static Setting fromParameterStore(Parameter parameter) {
        Setting setting = null;
        if (parameter != null) {
            String parameterStoreName = parameter.name();
            if (Utils.isEmpty(parameterStoreName)) {
                throw new RuntimeException("Can't get Setting name for blank Parameter Store name [" + parameter.toString() + "]");
            }
            String settingName = null;
            Matcher regex = SAAS_BOOST_PARAMETER_PATTERN.matcher(parameterStoreName);
            if (regex.matches()) {
                settingName = regex.group(1);
            }
            if (settingName == null) {
                throw new RuntimeException("Parameter Store Parameter " + parameter.name() + " does not match SaaS Boost pattern");
            }

            setting = Setting.builder()
                    .name(settingName) // name now might be <serviceName>/SETTING
                    .value(!"N/A".equals(parameter.value()) ? parameter.value() : "")
                    .readOnly(!SettingsService.READ_WRITE_PARAMS.contains(settingName))
                    .secure(ParameterType.SECURE_STRING == parameter.type())
                    .version(parameter.version())
                    .build();
        }
        return setting;
    }

    public static Parameter toParameterStore(Setting setting) {
        if (setting == null || !Setting.isValidSettingName(setting.getName())) {
            throw new RuntimeException("Can't create Parameter Store parameter with invalid Setting name");
        }
        String parameterName = "/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/" + setting.getName();
        String parameterValue = (Utils.isEmpty(setting.getValue())) ? "N/A" : setting.getValue();
        Parameter parameter = Parameter.builder()
                .type(setting.isSecure() ? ParameterType.SECURE_STRING : ParameterType.STRING)
                .name(parameterName)
                .value(parameterValue)
                .build();
        return parameter;
    }

    public static Setting fromAppParameterStore(Parameter parameter) {
        Setting setting = null;
        if (parameter != null) {
            String parameterStoreName = parameter.name();
            if (Utils.isEmpty(parameterStoreName)) {
                throw new RuntimeException("Can't get Setting name for blank Parameter Store name");
            }
            String settingName = null;
            Matcher regex = SAAS_BOOST_APP_PATTERN.matcher(parameterStoreName);
            if (regex.matches()) {
                settingName = regex.group(1);
            }
            if (settingName == null) {
                throw new RuntimeException("Parameter Store Parameter " + parameterStoreName + " does not match SaaS Boost app pattern " + SAAS_BOOST_APP_PATTERN);
            }
            setting = Setting.builder()
                    .name(settingName)
                    .value(!"N/A".equals(parameter.value()) ? parameter.value() : "")
                    .readOnly(false)
                    .secure(ParameterType.SECURE_STRING == parameter.type())
                    .version(parameter.version())
                    .build();
        }
        return setting;
    }

    public static Parameter toAppParameterStore(Setting setting) {
        if (setting == null || Utils.isEmpty(setting.getName())) {
            throw new RuntimeException("Can't create Parameter Store parameter from blank Setting name");
        }
        String parameterName = "/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/app/" + setting.getName();
        String parameterValue = (Utils.isEmpty(setting.getValue())) ? "N/A" : setting.getValue();
        return Parameter.builder()
                .type(setting.isSecure() ? ParameterType.SECURE_STRING : ParameterType.STRING)
                .name(parameterName)
                .value(parameterValue)
                .build();
    }

    public static Setting fromTenantParameterStore(UUID tenantId, Parameter parameter) {
        Setting setting = null;
        if (parameter != null) {
            String parameterStoreName = parameter.name();
            if (Utils.isEmpty(parameterStoreName)) {
                throw new RuntimeException("Can't get Setting name for blank Parameter Store name");
            }
            String settingName = null;
            Matcher regex = SAAS_BOOST_TENANT_PATTERN.matcher(parameterStoreName);
            if (regex.matches()) {
                if (!regex.group(1).equals(tenantId.toString())) {
                    throw new RuntimeException("Parameter Store Parameter " + parameterStoreName + " does not belong to tenant " + tenantId.toString());
                }
                settingName = regex.group(2);
            }
            if (settingName == null) {
                throw new RuntimeException("Parameter Store Parameter " + parameterStoreName + " does not match SaaS Boost tenant pattern " + SAAS_BOOST_TENANT_PATTERN.toString());
            }
            setting = Setting.builder()
                    .name(settingName)
                    .value(!"N/A".equals(parameter.value()) ? parameter.value() : "")
                    .readOnly(false)
                    .secure(ParameterType.SECURE_STRING == parameter.type())
                    .version(parameter.version())
                    .build();
        }
        return setting;
    }

    public static Parameter toTenantParameterStore(UUID tenantId, Setting setting) {
        if (setting == null || Utils.isEmpty(setting.getName())) {
            throw new RuntimeException("Can't create Parameter Store parameter from blank Setting name");
        }
        String parameterName = "/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/tenant/" + tenantId.toString() + "/" + setting.getName();
        String parameterValue = (Utils.isEmpty(setting.getValue())) ? "N/A" : setting.getValue();
        return Parameter.builder()
                .type(setting.isSecure() ? ParameterType.SECURE_STRING : ParameterType.STRING)
                .name(parameterName)
                .value(parameterValue)
                .build();
    }

    public static List<Setting> toSettings(AppConfig appConfig) {
        final String basePath = "app/";
        List<Setting> settings = new ArrayList<>();

        settings.add(Setting.builder()
                .name(basePath + "APP_NAME")
                .value(appConfig.getName())
                .readOnly(false)
                .build());
        settings.add(Setting.builder()
                .name(basePath + "DOMAIN_NAME")
                .value(appConfig.getDomainName())
                .readOnly(false)
                .build());
        settings.add(Setting.builder()
                .name(basePath + "SSL_CERT_ARN")
                .value(appConfig.getSslCertArn())
                .readOnly(false)
                .build());

        for (ServiceConfig serviceConfig : appConfig.getServices().values()) {
            // we're keeping the DB_MASTER_PASSWORD separate so we have an accessible form *somewhere*
            // but that means we need to create the DB_MASTER_PASSWORD for each ServiceTier, as some tiers
            // may have databases, some may not, and all may have different passwords (for whatever reason)
            for (Map.Entry<String, ServiceTierConfig> nameAndTierConfig : serviceConfig.getTiers().entrySet()) {
                String dbPasswordSettingValue = null;
                if (nameAndTierConfig.getValue().hasDatabase()) {
                    dbPasswordSettingValue = nameAndTierConfig.getValue().getDatabase().getPassword();
                }
                // tiers are /saas-boost/env/app/serviceName/tierName/
                Setting dbPasswordSetting = Setting.builder()
                        .name(basePath + serviceConfig.getName() + "/"
                                + nameAndTierConfig.getKey() + "/DB_MASTER_PASSWORD")
                        .value(dbPasswordSettingValue)
                        .secure(true).readOnly(false).build();
                settings.add(dbPasswordSetting);

                // place the passwordParam so appConfig holders can find the password if they need it
                nameAndTierConfig.getValue().getDatabase().setPasswordParam(toParameterStore(dbPasswordSetting).name());
            }

            settings.add(Setting.builder()
                    .name(basePath + serviceConfig.getName() + "/SERVICE_JSON")
                    .value(Utils.toJson(serviceConfig))
                    .readOnly(false).build());
        }

        String billingApiKeySettingValue = null;
        if (appConfig.getBilling() != null) {
            billingApiKeySettingValue = appConfig.getBilling().getApiKey();
        }
        settings.add(Setting.builder()
                .name(basePath + "BILLING_API_KEY")
                .value(billingApiKeySettingValue)
                .readOnly(false)
                .secure(true)
                .build());

        return settings;
    }
}
