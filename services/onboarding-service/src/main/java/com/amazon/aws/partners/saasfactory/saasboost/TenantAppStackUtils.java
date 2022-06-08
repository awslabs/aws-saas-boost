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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

public class TenantAppStackUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantAppStackParameter.class);

    private final Map<TenantAppStackParameter, String> parameterMap;
    private final String tier;
    private final Map<String, Object> tenant;
    private final Map<String, Object> appConfig;
    private final Map<String, Integer> pathPriorities;
    private final Properties serviceDiscoveryProperties;

    private TenantAppStackUtils(Builder b) {
        this.tenant = b.tenant;
        this.appConfig = b.appConfig;
        this.pathPriorities = OnboardingService.getPathPriority(appConfig);
        this.serviceDiscoveryProperties = new Properties();

        parameterMap = new HashMap<>();
        parameterMap.put(TenantAppStackParameter.ENVIRONMENT, b.environment);
        parameterMap.put(TenantAppStackParameter.EVENT_BUS, b.eventBus);
        parameterMap.put(TenantAppStackParameter.METRICS_STREAM, "");
        parameterMap.putAll(getParametersFromTenant(tenant));
        this.tier = (String) tenant.get("tier");
        if (Utils.isBlank(tier)) {
            throw new IllegalArgumentException("Error retrieving tier for tenant");
        }
    }

    /**
     * Gets the list of CloudFormation-compatible Parameters for a valid service in the AppConfig.
     * 
     * This function as a side effect will add to the service discovery Properties object, accessible
     * via {@link #getServiceDiscoveryProperties()}.
     * 
     * @return the parameters in CloudFormation model form
     */
    public List<Parameter> getParametersForService(Map<String, Object> serviceConfig) {
        parameterMap.putAll(getParametersFromServiceConfig(serviceConfig));
        return parameterMap.entrySet().stream()
            .map(entry -> Parameter.builder()
                .parameterKey(entry.getKey().getPlainName())
                .parameterValue(entry.getValue())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Returns the ServiceDiscoveryProperties object held by this Manager.
     * 
     * These properties should be automatically added to for private services on
     * calls to {@link #getParametersForService(String, Map)} to avoid potentially
     * unnecessary precomputation of parameters and properties.
     * 
     * @return the ServiceDiscoveryProperties object held by this Manager
     */
    public Properties getServiceDiscoveryProperties() {
        return this.serviceDiscoveryProperties;
    }

    // VisibleForTesting
    protected static Map<TenantAppStackParameter, String> getParametersFromTenant(Map<String, Object> tenant) {
        Map<TenantAppStackParameter, String> parameters = new HashMap<>();
        parameters.put(TenantAppStackParameter.TENANT_ID, (String) tenant.get("id"));

        if (!tenant.containsKey("resources")) {
            throw new IllegalArgumentException("Missing resources in Tenant config.");
        }
        // fill parameters from tenant resources. for some resources we need the name and others the arn
        Map<String, Map<String, String>> tenantResources = 
                (Map<String, Map<String, String>>) tenant.get("resources");
        List<TenantAppStackParameter> namedResources = List.of(
                TenantAppStackParameter.VPC,
                TenantAppStackParameter.PRIVATE_SUBNET_A,
                TenantAppStackParameter.PRIVATE_SUBNET_B,
                TenantAppStackParameter.ECS_CLUSTER,
                TenantAppStackParameter.ECS_SECURITY_GROUP
        );
        List<TenantAppStackParameter> arnResources = List.of(
                TenantAppStackParameter.LOAD_BALANCER,
                TenantAppStackParameter.HTTP_LISTENER,
                TenantAppStackParameter.HTTPS_LISTENER
        );
        
        for (TenantAppStackParameter parameter : namedResources) {
            String detail = "name";
            boolean allowBlankValues = false;
            parameters.put(parameter, 
                    pullParameterFromTenantResources(tenantResources, parameter.name(), detail, allowBlankValues));
        }
        for (TenantAppStackParameter parameter : arnResources) {
            String detail = "arn";
            boolean allowBlankValues = (parameter.equals(TenantAppStackParameter.HTTP_LISTENER) 
                    || parameter.equals(TenantAppStackParameter.HTTPS_LISTENER));
            parameters.put(parameter, 
                    pullParameterFromTenantResources(tenantResources, parameter.name(), detail, allowBlankValues));
        }

        // check that one of HTTP_LISTENER or HTTPS_LISTENER exists, otherwise fail
        if (Utils.isBlank(parameters.get(TenantAppStackParameter.HTTP_LISTENER))
                && Utils.isBlank(parameters.get(TenantAppStackParameter.HTTPS_LISTENER))) {
            throw new IllegalArgumentException("Missing one of HTTP or HTTPS listeners in Tenant config.");
        }

        return parameters;
    }

    // VisibleForTesting
    protected static String pullParameterFromTenantResources(
            Map<String, Map<String, String>> tenantResources, 
            String parameter, 
            String detail, 
            boolean allowBlank) {
        if (tenantResources.containsKey(parameter)) {
            return tenantResources.get(parameter).get(detail);
        }
        if (allowBlank) {
            return "";
        }
        throw new IllegalArgumentException("Tenant resources did not contain required parameter: " + parameter);
    }

    // VisibleForTesting
    protected Map<TenantAppStackParameter, String> getParametersFromServiceConfig(Map<String, Object> serviceConfig) {
        String serviceName = (String) serviceConfig.get("name");
        if (serviceName == null) {
            throw new IllegalArgumentException("Missing required service config: name");
        }
        String serviceResourceName = serviceName.replaceAll("[^0-9A-Za-z-]", "").toLowerCase();
        Boolean isPublic = (Boolean) serviceConfig.get("public");
        if (isPublic == null) {
            throw new IllegalArgumentException("Missing required config: public");
        }
        Integer containerPort = (Integer) serviceConfig.get("containerPort");
        if (containerPort == null) {
            throw new IllegalArgumentException("Missing required config: containerPort");
        }
        
        // If this is a private service, we will create an environment variables called
        // SERVICE_<SERVICE_NAME>_HOST and SERVICE_<SERVICE_NAME>_PORT to pass to the task definitions
        if (!isPublic) {
            String serviceEnvName = Utils.toUpperSnakeCase(serviceName);
            // TODO this pattern should be standardized + javadoc'ed
            String serviceHost = "SERVICE_" + serviceEnvName + "_HOST";
            String servicePort = "SERVICE_" + serviceEnvName + "_PORT";
            LOGGER.debug("Creating service discovery environment variables {}, {}", serviceHost, servicePort);
            serviceDiscoveryProperties.put(serviceHost, serviceResourceName + ".local");
            serviceDiscoveryProperties.put(servicePort, containerPort);
        }
        
        Map<String, Object> tiers = (Map<String, Object>) serviceConfig.get("tiers");
        if (!tiers.containsKey(tier)) {
            LOGGER.error("Service AppConfig missing tier '{}' definition", tier);
            throw new IllegalArgumentException("Service AppConfig missing configurations for tier: " + tier);
        }
        
        Map<String, Object> tierConfig = (Map<String, Object>) tiers.get(tier);
        
        // CloudFormation won't let you use dashes or underscores in Mapping second level key names
        // And it won't let you use Fn::Join or Fn::Split in Fn::FindInMap... so we will mangle this
        // parameter before we send it in.
        String clusterOS = ((String) serviceConfig.getOrDefault("operatingSystem", ""))
                .replace("_", "");
        Integer publicPathRulePriority = (isPublic) ? pathPriorities.get(serviceName) : 0;
        String containerTag = (String) serviceConfig.getOrDefault("containerTag", "latest");
        String publicPathRoute = (isPublic) ? (String) serviceConfig.get("path") : "";
        String instanceType = (String) tierConfig.get("instanceType");
        String healthCheckUrl = (String) serviceConfig.get("healthCheckUrl");

        Map<TenantAppStackParameter, String> parameters = new HashMap<>();
        parameters.put(TenantAppStackParameter.SERVICE_NAME, serviceName);
        parameters.put(TenantAppStackParameter.SERVICE_RESOURCE_NAME, serviceResourceName);
        parameters.put(TenantAppStackParameter.CONTAINER_REPOSITORY, (String) serviceConfig.get("containerRepo"));
        parameters.put(TenantAppStackParameter.CONTAINER_REPOSITORY_TAG, containerTag);
        parameters.put(TenantAppStackParameter.PUBLICLY_ADDRESSABLE, isPublic.toString());
        parameters.put(TenantAppStackParameter.PUBLIC_PATH_ROUTE, publicPathRoute);
        parameters.put(TenantAppStackParameter.PUBLIC_PATH_RULE_PRIORITY, publicPathRulePriority.toString());
        parameters.put(TenantAppStackParameter.CONTAINER_OS, clusterOS);
        parameters.put(TenantAppStackParameter.CLUSTER_INSTANCE_TYPE, instanceType);
        parameters.put(TenantAppStackParameter.TASK_LAUNCH_TYPE, (String) serviceConfig.get("ecsLaunchType"));
        parameters.put(TenantAppStackParameter.TASK_MEMORY, ((Integer) tierConfig.get("memory")).toString());
        parameters.put(TenantAppStackParameter.TASK_CPU, ((Integer) tierConfig.get("cpu")).toString());
        parameters.put(TenantAppStackParameter.MIN_TASK_COUNT, ((Integer) tierConfig.get("min")).toString());
        parameters.put(TenantAppStackParameter.MAX_TASK_COUNT, ((Integer) tierConfig.get("max")).toString());
        parameters.put(TenantAppStackParameter.CONTAINER_PORT, containerPort.toString());
        parameters.put(TenantAppStackParameter.CONTAINER_HEALTH_CHECK_PATH, healthCheckUrl);

        parameters.putAll(getFilesystemParameters((Map<String, Object>)tierConfig.get("filesystem")));
        parameters.putAll(getDatabaseParameters((Map<String, Object>)tierConfig.get("database")));

        return parameters;
    }

    // VisibleForTesting
    protected static Map<TenantAppStackParameter, String> getFilesystemParameters(Map<String, Object> filesystem) {
        Map<TenantAppStackParameter, String> parameters = new HashMap<>();

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

        parameters.put(TenantAppStackParameter.USE_EFS, enableEfs.toString());
        parameters.put(TenantAppStackParameter.MOUNT_POINT, mountPoint);
        parameters.put(TenantAppStackParameter.ENCRYPT_EFS, encryptFilesystem.toString());
        parameters.put(TenantAppStackParameter.EFS_LIFECYCLE_POLICY, filesystemLifecycle);
        parameters.put(TenantAppStackParameter.USE_FSX, enableFSx.toString());
        parameters.put(TenantAppStackParameter.FSX_WINDOWS_MOUNT_DRIVE, fsxWindowsMountDrive);
        parameters.put(TenantAppStackParameter.FSX_DAILY_BACKUP_TIME, fsxDailyBackupTime);
        parameters.put(TenantAppStackParameter.FSX_BACKUP_RETENTION, fsxBackupRetentionDays.toString());
        parameters.put(TenantAppStackParameter.FSX_THROUGHPUT_CAPACITY, fsxThroughputMbs.toString());
        parameters.put(TenantAppStackParameter.FSX_STORAGE_CAPACITY, fsxStorageGb.toString());
        parameters.put(TenantAppStackParameter.FSX_WEEKLY_MAINTENANCE_TIME, fsxWeeklyMaintenanceTime);

        return parameters;
    }

    // VisibleForTesting
    protected static Map<TenantAppStackParameter, String> getDatabaseParameters(Map<String, Object> database) {
        Map<TenantAppStackParameter, String> parameters = new HashMap<>();

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
        if (database != null && !database.isEmpty()) {
            enableDatabase = Boolean.TRUE;
            dbEngine = (String) database.get("engineName");
            dbVersion = (String) database.get("version");
            dbFamily = (String) database.get("family");
            dbInstanceClass = (String) database.get("instanceClass");
            dbDatabase = Objects.toString(database.get("database"), "");
            dbUsername = (String) database.get("username");
            dbPort = (Integer) database.get("port");
            dbBootstrap = Objects.toString(database.get("bootstrapFilename"), "");
            dbPasswordRef = (String) database.get("passwordParam");
        }
        
        parameters.put(TenantAppStackParameter.USE_RDS, enableDatabase.toString());
        parameters.put(TenantAppStackParameter.RDS_INSTANCE_CLASS, dbInstanceClass);
        parameters.put(TenantAppStackParameter.RDS_ENGINE, dbEngine);
        parameters.put(TenantAppStackParameter.RDS_ENGINE_VERSION, dbVersion);
        parameters.put(TenantAppStackParameter.RDS_PARAMETER_GROUP_FAMILY, dbFamily);
        parameters.put(TenantAppStackParameter.RDS_USERNAME, dbUsername);
        parameters.put(TenantAppStackParameter.RDS_PASSWORD_PARAM, dbPasswordRef);
        parameters.put(TenantAppStackParameter.RDS_PORT, dbPort.toString());
        parameters.put(TenantAppStackParameter.RDS_DATABASE, dbDatabase);
        parameters.put(TenantAppStackParameter.RDS_BOOTSTRAP, dbBootstrap);

        return parameters;
    }

    // VisibleForTesting
    protected Map<TenantAppStackParameter, String> getParameterMap() {
        return this.parameterMap;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String environment;
        private String eventBus;
        private Map<String, Object> tenant;
        private Map<String, Object> appConfig;

        public Builder withEnvironment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder withEventBus(String eventBus) {
            this.eventBus = eventBus;
            return this;
        }

        public Builder withTenant(Map<String, Object> tenant) {
            this.tenant = tenant;
            return this;
        }

        public Builder withAppConfig(Map<String, Object> appConfig) {
            this.appConfig = appConfig;
            return this;
        }

        public TenantAppStackUtils build() {
            if (environment == null) {
                throw new IllegalArgumentException("Tenant App Stack Parameters requires environment");
            }
            if (tenant == null || appConfig == null) {
                throw new IllegalArgumentException(
                    "Tenant App Stack Parameters must be built from Tenant and AppConfig objects.");
            }
            return new TenantAppStackUtils(this);
        }
    }

    /**
     * Represents a parameter for the Tenant-App Cloudformation Stack. 
     * 
     * The name of the Enum key must match the key of the Tenant resources map (and AppConfig)
     * and the value must match the Parameter expected by CloudFormation.
     * 
     * TODO this can be refactored to a shared model layer to allow for safe name changes
     */
    // VisibleForTesting
    protected enum TenantAppStackParameter {
        CLUSTER_INSTANCE_TYPE("ClusterInstanceType"),
        CONTAINER_HEALTH_CHECK_PATH("ContainerHealthCheckPath"),
        CONTAINER_OS("ContainerOS"),
        CONTAINER_PORT("ContainerPort"),
        CONTAINER_REPOSITORY("ContainerRepository"),
        CONTAINER_REPOSITORY_TAG("ContainerRepositoryTag"),
        ECS_CLUSTER("ECSCluster"),
        ECS_SECURITY_GROUP("ECSSecurityGroup"),
        EFS_LIFECYCLE_POLICY("EFSLifecyclePolicy"),
        ENCRYPT_EFS("EncryptEFS"),
        ENVIRONMENT("Environment"),
        EVENT_BUS("EventBus"),
        FSX_BACKUP_RETENTION("FSxBackupRetention"),
        FSX_DAILY_BACKUP_TIME("FSxDailyBackupTime"),
        FSX_STORAGE_CAPACITY("FSxStorageCapacity"),
        FSX_THROUGHPUT_CAPACITY("FSxThroughputCapacity"),
        FSX_WEEKLY_MAINTENANCE_TIME("FSxWeeklyMaintenanceTime"),
        FSX_WINDOWS_MOUNT_DRIVE("FSxWindowsMountDrive"),
        HTTP_LISTENER("ECSLoadBalancerHttpListener"),
        HTTPS_LISTENER("ECSLoadBalancerHttpsListener"),
        LOAD_BALANCER("ECSLoadBalancer"),
        MAX_TASK_COUNT("MaxTaskCount"),
        METRICS_STREAM("MetricsStream"),
        MIN_TASK_COUNT("MinTaskCount"),
        MOUNT_POINT("MountPoint"),
        PRIVATE_SUBNET_A("SubnetPrivateA"),
        PRIVATE_SUBNET_B("SubnetPrivateB"),
        PUBLICLY_ADDRESSABLE("PubliclyAddressable"),
        PUBLIC_PATH_ROUTE("PublicPathRoute"),
        PUBLIC_PATH_RULE_PRIORITY("PublicPathRulePriority"),
        RDS_BOOTSTRAP("RDSBootstrap"),
        RDS_DATABASE("RDSDatabase"),
        RDS_ENGINE("RDSEngine"),
        RDS_ENGINE_VERSION("RDSEngineVersion"),
        RDS_INSTANCE_CLASS("RDSInstanceClass"),
        RDS_PARAMETER_GROUP_FAMILY("RDSParameterGroupFamily"),
        RDS_PASSWORD_PARAM("RDSPasswordParam"),
        RDS_PORT("RDSPort"),
        RDS_USERNAME("RDSUsername"),
        TASK_CPU("TaskCPU"),
        TASK_LAUNCH_TYPE("TaskLaunchType"),
        TASK_MEMORY("TaskMemory"),
        TENANT_ID("TenantId"),
        SERVICE_NAME("ServiceName"),
        SERVICE_RESOURCE_NAME("ServiceResourceName"),
        USE_EFS("UseEFS"),
        USE_FSX("UseFSx"),
        USE_RDS("UseRDS"),
        VPC("VPC");

        private final String plainParameterName;

        private TenantAppStackParameter(String plainParameterName) {
            this.plainParameterName = plainParameterName;
        }

        public String getPlainName() {
            return this.plainParameterName;
        }
    }
}
