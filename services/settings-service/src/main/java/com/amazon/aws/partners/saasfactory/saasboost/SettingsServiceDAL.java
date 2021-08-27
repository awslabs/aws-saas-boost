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

    private final static Logger LOGGER = LoggerFactory.getLogger(SettingsServiceDAL.class);
    private final static String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private final static String OPTIONS_TABLE = System.getenv("OPTIONS_TABLE");
    private final static String AWS_REGION = System.getenv("AWS_REGION");

    // Package private for testing
    final static String SAAS_BOOST_PREFIX = "saas-boost";
    // e.g. /saas-boost/production/SAAS_BOOST_BUCKET
    final static Pattern SAAS_BOOST_PARAMETER_PATTERN = Pattern.compile("^\\/" + SAAS_BOOST_PREFIX + "\\/" + SAAS_BOOST_ENV + "\\/(.+)$");
    // e.g. /saas-boost/staging/tenant/00000000-0000-0000-0000-000000000000/DB_HOST
    final static Pattern SAAS_BOOST_TENANT_PATTERN = Pattern.compile("^\\/" + SAAS_BOOST_PREFIX + "\\/" + SAAS_BOOST_ENV + "\\/tenant\\/(\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12})\\/(.+)$");

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
        long startTimeMillis = System.currentTimeMillis();

        Map<String, Setting> parameterStore = new TreeMap<>();
        String nextToken = null;
        do {
            try {
                GetParametersByPathResponse response = ssm.getParametersByPath(GetParametersByPathRequest
                        .builder()
                        .path("/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV)
                        .recursive(false) // don't get the tenant params
                        .withDecryption(false) // don't expose secrets by default
                        .nextToken(nextToken)
                        .build()
                );
                nextToken = response.nextToken();

                for (Parameter parameter : response.parameters()) {
                    LOGGER.info("SettingsServiceDAL::getAllSettings loading Parameter Store param " + parameter.name());
                    Setting setting = fromParameterStore(parameter);
                    parameterStore.put(setting.getName(), setting);
                }
            } catch (SdkServiceException ssmError) {
                LOGGER.error("ssm:GetParametersByPath error " + ssmError.getMessage());
                throw ssmError;
            }
        } while (nextToken != null && !nextToken.isEmpty());

        if (parameterStore.isEmpty()) {
            throw new RuntimeException("Error loading Parameter Store SaaS Boost parameters");
        } else {
            if (!parameterStore.keySet().containsAll(SettingsService.REQUIRED_PARAMS)) {
                for (String required : SettingsService.REQUIRED_PARAMS) {
                    if (!parameterStore.containsKey(required)) {
                        LOGGER.error("SettingsServiceDAL::getAllSettings Missing required parameter " + required);
                    }
                }
                throw new RuntimeException("Missing one or more required parameters");
            }
            LOGGER.info("SettingsServiceDAL::getAllSettings Loaded " + parameterStore.size() + " parameters");
        }

        List<Setting> settings = List.copyOf(parameterStore.values());

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::getAllSettings exec " + totalTimeMillis);

        return settings;
    }

    public List<Setting> getMutableSettings() {
        List<Setting> readWriteSettings = new ArrayList<>();
        for (Setting setting : getAllSettings()) {
            if (SettingsService.READ_WRITE_PARAMS.contains(setting.getName())) {
                readWriteSettings.add(setting);
            }
        }
        return readWriteSettings;
    }

    public List<Setting> getImmutableSettings() {
        List<Setting> readOnlySettings = new ArrayList<>();
        for (Setting setting : getAllSettings()) {
            if (!SettingsService.READ_WRITE_PARAMS.contains(setting.getName())) {
                readOnlySettings.add(setting);
            }
        }
        return readOnlySettings;
    }

    public List<Setting> getNamedSettings(List<String> namedSettings) {
        List<Setting> settings = new ArrayList<>();
        List<String> batch = new ArrayList<>();

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
        return settings;
    }

    public Setting getSetting(String settingName) {
        return getSetting(settingName, false);
    }

    public Setting getSecret(String settingName) {
        return getSetting(settingName, true);
    }

    private Setting getSetting(String settingName, boolean decrypt) {
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

    public List<Setting> getTenantSettings(UUID tenantId) {
        long startTimeMillis = System.currentTimeMillis();

        String parameterStorePath = "/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV + "/tenant/" + tenantId.toString();
        Map<String, Setting> parameterStore = new TreeMap<>();
        String nextToken = null;
        do {
            try {
                GetParametersByPathResponse response = ssm.getParametersByPath(GetParametersByPathRequest
                        .builder()
                        .path(parameterStorePath)
                        .recursive(false)
                        .withDecryption(false) // don't expose secrets by default
                        .nextToken(nextToken)
                        .build()
                );
                nextToken = response.nextToken();

                for (Parameter parameter : response.parameters()) {
                    LOGGER.info("SettingsServiceDAL::getTenantSettings loading Parameter Store param " + parameter.name());
                    Setting setting = fromTenantParameterStore(tenantId, parameter);
                    parameterStore.put(setting.getName(), setting);
                }
            } catch (SdkServiceException ssmError) {
                LOGGER.error("ssm:GetParametersByPath error " + ssmError.getMessage());
                throw ssmError;
            }
        } while (nextToken != null && !nextToken.isEmpty());

        LOGGER.info("SettingsServiceDAL::getTenantSettings Loaded " + parameterStore.size() + " parameters");

        List<Setting> settings = List.copyOf(parameterStore.values());

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::getTenantSettings exec " + totalTimeMillis);

        return settings;
    }

    public Setting getTenantSetting(UUID tenantId, String settingName) {
        return getTenantSetting(tenantId, settingName, false);
    }

    public Setting getTenantSecret(UUID tenantId, String settingName) {
        return getTenantSetting(tenantId, settingName, true);
    }

    private Setting getTenantSetting(UUID tenantId, String settingName, boolean decrypt) {
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

    public void deleteSetting(Setting setting) {
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

    private void deleteParameter(Parameter parameter) {
        try {
            DeleteParameterResponse response = ssm.deleteParameter(request -> request
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

    static final Comparator<Map<String, Object>> INSTANCE_TYPE_COMPARATOR = ((instance1, instance2) -> {
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

    static final Comparator<Map<String, Object>> INSTANCE_GENERATION_COMPARATOR = ((instance1, instance2) -> {
        Integer gen1 = Integer.valueOf(((String) instance1.get("instance")).substring(1, 2));
        Integer gen2 = Integer.valueOf(((String) instance2.get("instance")).substring(1, 2));
        return gen1.compareTo(gen2);
    });

    static final Comparator<Map<String, Object>> INSTANCE_SIZE_COMPARATOR = ((instance1, instance2) -> {
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

    static final Comparator<Map<String, Object>> RDS_INSTANCE_COMPARATOR = INSTANCE_TYPE_COMPARATOR.thenComparing(INSTANCE_GENERATION_COMPARATOR).thenComparing(INSTANCE_SIZE_COMPARATOR);

    protected static Map<String, Object> fromAttributeValueMap(Map<String, AttributeValue> item) {
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
        List<Setting> mutableSettings = getMutableSettings();
        Map<String, String> appSettings = mutableSettings
                .stream()
                .collect(
                        Collectors.toMap(Setting::getName, Setting::getValue)
                );

        // Get the secret value for the optional billing provider or you'll always
        // be testing for empty against the encrypted hash of the "N/A" sentinel string
        AppConfig appConfig = toAppConfig(appSettings, getSecret("BILLING_API_KEY"));

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SettingsServiceDAL::getAppConfig exec " + totalTimeMillis);
        return appConfig;
    }

    protected static AppConfig toAppConfig(Map<String, String> appSettings, Setting billingApiKey) {
        AppConfig appConfig = null;

        Database database = null;
        if (Utils.isNotEmpty(appSettings.get("DB_ENGINE"))) {
            database = Database.builder()
                    .database(appSettings.get("DB_NAME"))
                    .engine(appSettings.get("DB_ENGINE"))
                    .version(appSettings.get("DB_VERSION"))
                    .family(appSettings.get("DB_PARAM_FAMILY"))
                    .instance(appSettings.get("DB_INSTANCE_TYPE"))
                    .username(appSettings.get("DB_MASTER_USERNAME"))
                    .password(appSettings.get("DB_MASTER_PASSWORD"))
                    .bootstrapFilename(appSettings.get("DB_BOOTSTRAP_FILE"))
                    .build();
        }
        SharedFilesystem filesystem = null;
        if (Utils.isNotEmpty(appSettings.get("FILE_SYSTEM_TYPE"))) {
            EfsFilesystem efs = null;
            FsxFilesystem fsx = null;
            if ("EFS".equals(appSettings.get("FILE_SYSTEM_TYPE"))) {
                efs = EfsFilesystem.builder()
                        .encryptAtRest(appSettings.get("FILE_SYSTEM_ENCRYPT"))
                        .lifecycle(appSettings.get("FILE_SYSTEM_LIFECYCLE"))
                        .build();
            } else {  //FSX {
                fsx = FsxFilesystem.builder()
                        .backupRetentionDays(Integer.valueOf(appSettings.get("FSX_BACKUP_RETENTION_DAYS")))
                        .dailyBackupTime(appSettings.get("FSX_DAILY_BACKUP_TIME"))
                        .storageGb(Integer.valueOf(appSettings.get("FSX_STORAGE_GB")))
                        .throughputMbs(Integer.valueOf(appSettings.get("FSX_THROUGHPUT_MBS")))
                        .weeklyMaintenanceTime(appSettings.get("FSX_WEEKLY_MAINTENANCE_TIME"))
                        .windowsMountDrive(appSettings.get("FSX_WINDOWS_MOUNT_DRIVE"))
                        .build();
            }
            filesystem = SharedFilesystem.builder()
                    .fileSystemType(appSettings.get("FILE_SYSTEM_TYPE"))
                    .mountPoint(appSettings.get("FILE_SYSTEM_MOUNT_POINT"))
                    .fsx(fsx)
                    .efs(efs)
                    .build();
        }

        BillingProvider billingProvider = null;
        if (billingApiKey != null) {
            String apiKey = billingApiKey.getValue();
            if (Utils.isNotEmpty(apiKey)) {
                billingProvider = BillingProvider.builder()
                        .apiKey(appSettings.get("BILLING_API_KEY"))
                        .build();
            }
        }

        appConfig = AppConfig.builder()
                .name(appSettings.get("APP_NAME"))
                .domainName(appSettings.get("DOMAIN_NAME"))
                .sslCertArn(appSettings.get("SSL_CERT_ARN"))
                .healthCheckURL(appSettings.get("HEALTH_CHECK"))
                .computeSize(appSettings.get("COMPUTE_SIZE"))
                .defaultCpu(appSettings.get("TASK_CPU"))
                .defaultMemory(appSettings.get("TASK_MEMORY"))
                .containerPort(appSettings.get("CONTAINER_PORT"))
                .minCount(appSettings.get("MIN_COUNT"))
                .maxCount(appSettings.get("MAX_COUNT"))
                .operatingSystem(appSettings.get("CLUSTER_OS"))
                .instanceType(appSettings.get("CLUSTER_INSTANCE_TYPE"))
                .database(database)
                .filesystem(filesystem)
                .billing(billingProvider)
                .build();

        return appConfig;
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
                    .name(settingName)
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
        Parameter parameter = Parameter.builder()
                .type(setting.isSecure() ? ParameterType.SECURE_STRING : ParameterType.STRING)
                .name(parameterName)
                .value(parameterValue)
                .build();
        return parameter;
    }

    public static List<Setting> toSettings(AppConfig appConfig) {
        List<Setting> settings = new ArrayList<>();
        settings.add(Setting.builder().name("APP_NAME").value(appConfig.getName()).readOnly(false).build());
        settings.add(Setting.builder().name("DOMAIN_NAME").value(appConfig.getDomainName()).readOnly(false).build());
        settings.add(Setting.builder().name("SSL_CERT_ARN").value(appConfig.getSslCertArn()).readOnly(false).build());
        settings.add(Setting.builder().name("HEALTH_CHECK").value(appConfig.getHealthCheckURL()).readOnly(false).build());
        String computeSize = appConfig.getComputeSize() != null ? appConfig.getComputeSize().name() : null;
        settings.add(Setting.builder().name("COMPUTE_SIZE").value(computeSize).readOnly(false).build());
        String cpu = appConfig.getDefaultCpu() != null ? appConfig.getDefaultCpu().toString() : null;
        settings.add(Setting.builder().name("TASK_CPU").value(cpu).readOnly(false).build());
        String memory = appConfig.getDefaultMemory() != null ? appConfig.getDefaultMemory().toString() : null;
        settings.add(Setting.builder().name("TASK_MEMORY").value(memory).readOnly(false).build());
        String port = appConfig.getContainerPort() != null ? appConfig.getContainerPort().toString() : null;
        settings.add(Setting.builder().name("CONTAINER_PORT").value(port).readOnly(false).build());
        String minCount = appConfig.getMinCount() != null ? appConfig.getMinCount().toString() : null;
        settings.add(Setting.builder().name("MIN_COUNT").value(minCount).readOnly(false).build());
        String maxCount = appConfig.getMaxCount() != null ? appConfig.getMaxCount().toString() : null;
        settings.add(Setting.builder().name("MAX_COUNT").value(maxCount).readOnly(false).build());
        OperatingSystem os = appConfig.getOperatingSystem();
        if (os != null) {
            settings.add(Setting.builder().name("CLUSTER_OS").value(os.name()).readOnly(false).build());
        }
        settings.add(Setting.builder().name("CLUSTER_INSTANCE_TYPE").value(appConfig.getInstanceType()).readOnly(false).build());
        SharedFilesystem filesystem = appConfig.getFilesystem();
        if (filesystem != null) {
            settings.add(Setting.builder().name("FILE_SYSTEM_TYPE").value(filesystem.getFileSystemType()).readOnly(false).build());
            settings.add(Setting.builder().name("FILE_SYSTEM_MOUNT_POINT").value(filesystem.getMountPoint()).readOnly(false).build());
            //for EFS filesystem
            if ("EFS".equalsIgnoreCase(filesystem.getFileSystemType()) && filesystem.getEfs() != null) {
                settings.add(Setting.builder().name("FILE_SYSTEM_ENCRYPT").value(filesystem.getEfs().getEncryptAtRest().toString()).readOnly(false).build());
                settings.add(Setting.builder().name("FILE_SYSTEM_LIFECYCLE").value(filesystem.getEfs().getFilesystemLifecycle()).readOnly(false).build());
            } else {
                // Remove these settings in case we're updating
                settings.add(Setting.builder().name("FILE_SYSTEM_ENCRYPT").value(null).readOnly(false).build());
                settings.add(Setting.builder().name("FILE_SYSTEM_LIFECYCLE").value(null).readOnly(false).build());
            }
            // for FSX filesystem
            if ("FSX".equalsIgnoreCase(filesystem.getFileSystemType()) && filesystem.getFsx() != null) {
                settings.add(Setting.builder().name("FSX_STORAGE_GB").value(filesystem.getFsx().getStorageGb().toString()).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_THROUGHPUT_MBS").value(filesystem.getFsx().getThroughputMbs().toString()).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_BACKUP_RETENTION_DAYS").value(filesystem.getFsx().getBackupRetentionDays().toString()).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_DAILY_BACKUP_TIME").value(filesystem.getFsx().getDailyBackupTime()).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_WEEKLY_MAINTENANCE_TIME").value(filesystem.getFsx().getWeeklyMaintenanceTime()).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_WINDOWS_MOUNT_DRIVE").value(filesystem.getFsx().getWindowsMountDrive()).readOnly(false).build());
            } else {
                // Remove these settings in case we're updating
                settings.add(Setting.builder().name("FSX_STORAGE_GB").value(null).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_THROUGHPUT_MBS").value(null).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_BACKUP_RETENTION_DAYS").value(null).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_DAILY_BACKUP_TIME").value(null).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_WEEKLY_MAINTENANCE_TIME").value(null).readOnly(false).build());
                settings.add(Setting.builder().name("FSX_WINDOWS_MOUNT_DRIVE").value(null).readOnly(false).build());
            }
        } else {
            // Remove these settings in case we're updating
            settings.add(Setting.builder().name("FILE_SYSTEM_TYPE").value(null).readOnly(false).build());
            //efs
            settings.add(Setting.builder().name("FILE_SYSTEM_MOUNT_POINT").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("FILE_SYSTEM_ENCRYPT").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("FILE_SYSTEM_LIFECYCLE").value(null).readOnly(false).build());
            //fsx
            settings.add(Setting.builder().name("FSX_STORAGE_GB").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("FSX_THROUGHPUT_MBS").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("FSX_BACKUP_RETENTION_DAYS").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("FSX_DAILY_BACKUP_TIME").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("FSX_WEEKLY_MAINTENANCE_TIME").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("FSX_WINDOWS_MOUNT_DRIVE").value(null).readOnly(false).build());
        }

        Database database = appConfig.getDatabase();
        if (database != null) {
            settings.add(Setting.builder().name("DB_ENGINE").value(database.getEngineName()).readOnly(false).build());
            settings.add(Setting.builder().name("DB_VERSION").value(database.getVersion()).readOnly(false).build());
            settings.add(Setting.builder().name("DB_PARAM_FAMILY").value(database.getFamily()).readOnly(false).build());
            settings.add(Setting.builder().name("DB_INSTANCE_TYPE").value(database.getInstanceClass()).readOnly(false).build());
            settings.add(Setting.builder().name("DB_NAME").value(database.getDatabase()).readOnly(false).build());
            settings.add(Setting.builder().name("DB_MASTER_USERNAME").value(database.getUsername()).readOnly(false).build());
            settings.add(Setting.builder().name("DB_MASTER_PASSWORD").value(database.getPassword()).readOnly(false).secure(true).build());
            settings.add(Setting.builder().name("DB_PORT").value(database.getPort().toString()).readOnly(false).build());
            settings.add(Setting.builder().name("DB_BOOTSTRAP_FILE").value(database.getBootstrapFilename()).readOnly(false).build());
        } else {
            // Remove these settings in case we're updating
            settings.add(Setting.builder().name("DB_ENGINE").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("DB_VERSION").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("DB_PARAM_FAMILY").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("DB_INSTANCE_TYPE").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("DB_NAME").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("DB_MASTER_USERNAME").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("DB_MASTER_PASSWORD").value(null).readOnly(false).secure(true).build());
            settings.add(Setting.builder().name("DB_PORT").value(null).readOnly(false).build());
            settings.add(Setting.builder().name("DB_BOOTSTRAP_FILE").value(null).readOnly(false).build());
        }

        BillingProvider billing = appConfig.getBilling();
        if (billing != null) {
            settings.add(Setting.builder().name("BILLING_API_KEY").value(billing.getApiKey()).secure(true).readOnly(false).build());
        } else {
            settings.add(Setting.builder().name("BILLING_API_KEY").value(null).readOnly(false).secure(true).build());
        }
        return settings;
    }
}
