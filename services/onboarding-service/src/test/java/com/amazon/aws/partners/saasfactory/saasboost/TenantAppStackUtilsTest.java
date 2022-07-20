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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.amazon.aws.partners.saasfactory.saasboost.TenantAppStackUtils.TenantAppStackParameter;

import org.junit.Before;
import org.junit.Test;

public class TenantAppStackUtilsTest {
    private static final String ENV = "myEnvironment";
    private static final String EVENT_BUS = "sb-myEnvironment-event-bus";

    private Map<String, Object> validAppConfig;
    private Map<String, Object> validServiceConfig;
    private Map<String, Object> validTierConfig;
    private Map<String, Object> validTenant;
    private Map<String, Map<String, String>> validTenantResources;

    @Before
    public void setup() {
        validAppConfig = Utils.fromJson(
                getClass().getClassLoader().getResourceAsStream("appConfig.json"), 
                LinkedHashMap.class);
        // yes, this is the worst. a shared object model would allow us to simply 
        // deserialize the appConfig.json into an object and operate on that.
        validServiceConfig = (Map<String, Object>) ((Map<String, Object>) 
                validAppConfig.get("services")).get("main_service");
        validTierConfig = (Map<String, Object>) ((Map<String, Object>) validServiceConfig.get("tiers")).get("default");
        validTenant = Utils.fromJson(
                getClass().getClassLoader().getResourceAsStream("tenant.json"), 
                LinkedHashMap.class);
        validTenantResources = (Map<String, Map<String, String>>) validTenant.get("resources");
    }

    // Test Builder/Constructor

    @Test
    public void builder_valid() {
        // no IllegalArgumentException is thrown
        TenantAppStackUtils thisInstance = TenantAppStackUtils.builder()
                .withEnvironment(ENV)
                .withEventBus(EVENT_BUS)
                .withTenant(validTenant)
                .withAppConfig(validAppConfig)
                .build();
        
        // serviceDiscoveryProperties should exist but be empty until getParametersForService is called
        Properties serviceDiscoveryProps = thisInstance.getServiceDiscoveryProperties();
        assertNotNull(serviceDiscoveryProps);
        assertTrue("No Service discovery properties should be added before parameters are loaded", 
                serviceDiscoveryProps.isEmpty());
        
        // parameterMap should be non-null
        Map<TenantAppStackParameter, String> parameterMap = thisInstance.getParameterMap();
        assertNotNull(parameterMap);

        // the EVENT_BUS, ENVIRONMENT, and METRICS_STREAM parameters should be set
        assertParameterInMapIs(parameterMap, TenantAppStackParameter.ENVIRONMENT, ENV);
        assertParameterInMapIs(parameterMap, TenantAppStackParameter.EVENT_BUS, EVENT_BUS);
        assertParameterInMapIs(parameterMap, TenantAppStackParameter.METRICS_STREAM, "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_nullEnv() {
        TenantAppStackUtils.builder()
                .withEnvironment(null)
                .withEventBus(EVENT_BUS)
                .withTenant(validTenant)
                .withAppConfig(validAppConfig)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_nullTenant() {
        TenantAppStackUtils.builder()
                .withEnvironment(ENV)
                .withEventBus(EVENT_BUS)
                .withTenant(null)
                .withAppConfig(validAppConfig)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_nullAppConfig() {
        TenantAppStackUtils.builder()
                .withEnvironment(ENV)
                .withEventBus(EVENT_BUS)
                .withTenant(validTenant)
                .withAppConfig(null)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_tierlessTenant() {
        Map<String, Object> tierlessTenant = new LinkedHashMap<String, Object>(validTenant);
        tierlessTenant.remove("tier");
        TenantAppStackUtils.builder()
                .withEnvironment(ENV)
                .withEventBus(EVENT_BUS)
                .withTenant(tierlessTenant)
                .withAppConfig(validAppConfig)
                .build();
    }

    // Test Extracting Tenant Configs to Parameters

    @Test
    public void pullParameterFromTenantResources_parameterExists_blankAllowed() {
        String expected = getDetailFromValidTenantResources("HTTP_LISTENER", "name");
        assertEquals(expected, TenantAppStackUtils.pullParameterFromTenantResources(
                validTenantResources, "HTTP_LISTENER", "name", true));
    }

    @Test
    public void pullParameterFromTenantResources_parameterExists_blankNotAllowed() {
        String expected = getDetailFromValidTenantResources("HTTP_LISTENER", "name");
        assertEquals(expected, TenantAppStackUtils.pullParameterFromTenantResources(
                validTenantResources, "HTTP_LISTENER", "name", false));
    }

    @Test
    public void pullParameterFromTenantResources_parameterNotExists_blankAllowed() {
        assertEquals("", TenantAppStackUtils.pullParameterFromTenantResources(
                validTenantResources, "nonExistentParam", "detail", true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void pullParameterFromTenantResources_parameterNotExists_blankNotAllowed() {
        TenantAppStackUtils.pullParameterFromTenantResources(
                validTenantResources, "nonExistentParam", "detail", false);
    }

    @Test
    public void getParametersFromTenant_valid() {
        Map<TenantAppStackParameter, String> tenantParams = TenantAppStackUtils.getParametersFromTenant(validTenant);
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.TENANT_ID, (String) validTenant.get("id"));
        
        String expectedVpc = getDetailFromValidTenantResources("VPC", "name");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.VPC, expectedVpc);
        String expectedPrivateSubnetA = getDetailFromValidTenantResources("PRIVATE_SUBNET_A", "name");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.PRIVATE_SUBNET_A, expectedPrivateSubnetA);
        String expectedPrivateSubnetB = getDetailFromValidTenantResources("PRIVATE_SUBNET_B", "name");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.PRIVATE_SUBNET_B, expectedPrivateSubnetB);
        String expectedEcsCluster = getDetailFromValidTenantResources("ECS_CLUSTER", "name");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.ECS_CLUSTER, expectedEcsCluster);
        String expectedEcsSG = getDetailFromValidTenantResources("ECS_SECURITY_GROUP", "name");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.ECS_SECURITY_GROUP, expectedEcsSG);
        String expectedLoadBalancer = getDetailFromValidTenantResources("LOAD_BALANCER", "arn");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.LOAD_BALANCER, expectedLoadBalancer);
        String expectedHttpListener = getDetailFromValidTenantResources("HTTP_LISTENER", "arn");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.HTTP_LISTENER, expectedHttpListener);
        String expectedHttpsListener = getDetailFromValidTenantResources("HTTPS_LISTENER", "arn");
        assertParameterInMapIs(tenantParams, TenantAppStackParameter.HTTPS_LISTENER, expectedHttpsListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getParametersFromTenant_noTenantResources() {
        Map<String, Object> tenantWithoutResources = new LinkedHashMap<String, Object>(validTenant);
        tenantWithoutResources.remove("resources");
        TenantAppStackUtils.getParametersFromTenant(tenantWithoutResources);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getParametersFromTenant_noHttpOrHttpsListener() {
        Map<String, Map<String, String>> tenantResourcesWithoutListeners = 
                new LinkedHashMap<String, Map<String, String>>(validTenantResources);
        tenantResourcesWithoutListeners.remove("HTTP_LISTENER");
        tenantResourcesWithoutListeners.remove("HTTPS_LISTENER");
        Map<String, Object> tenantWithoutListeners = new LinkedHashMap<String, Object>(validTenant);
        tenantWithoutListeners.put("resources", tenantResourcesWithoutListeners);
        TenantAppStackUtils.getParametersFromTenant(tenantWithoutListeners);
    }

    // Test Extracting AppConfig to Parameters

    @Test
    public void getDatabaseParameters_nullAndEmptyDatabaseReturnsDefaults() {
        Map<String, Object> databaseConfig = null;
        Map<TenantAppStackParameter, String> databaseParameters = 
                TenantAppStackUtils.getDatabaseParameters(databaseConfig, "");
        assertNotNull(databaseParameters);
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.USE_RDS, Boolean.FALSE.toString());
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_INSTANCE_CLASS, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_ENGINE, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_ENGINE_VERSION, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_PARAMETER_GROUP_FAMILY, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_USERNAME, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_PASSWORD_PARAM, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_PORT, "-1");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_DATABASE, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_BOOTSTRAP, "");

        databaseConfig = Map.of();
        databaseParameters = TenantAppStackUtils.getDatabaseParameters(databaseConfig, "");
        assertNotNull(databaseParameters);
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.USE_RDS, Boolean.FALSE.toString());
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_INSTANCE_CLASS, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_ENGINE, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_ENGINE_VERSION, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_PARAMETER_GROUP_FAMILY, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_USERNAME, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_PASSWORD_PARAM, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_PORT, "-1");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_DATABASE, "");
        assertParameterInMapIs(databaseParameters, TenantAppStackParameter.RDS_BOOTSTRAP, "");
    }

    @Test
    public void getDatabaseParameters_valid() {
        Map<String, Object> databaseConfig = (Map<String, Object>) validServiceConfig.get("database");
        Map<String, Object> databaseTiers = (Map<String, Object>) databaseConfig.get("tiers");
        Map<String, Object> databaseTierConfigDefault = (Map<String, Object>) databaseTiers.get("default");
        Map<TenantAppStackParameter, String> databaseParameters = 
                TenantAppStackUtils.getDatabaseParameters(databaseConfig, "default");
        assertNotNull(databaseParameters);
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.USE_RDS, Boolean.TRUE.toString());
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_INSTANCE_CLASS, (String) databaseTierConfigDefault.get("instanceClass"));
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_ENGINE, (String) databaseConfig.get("engineName"));
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_ENGINE_VERSION, (String) databaseConfig.get("version"));
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_PARAMETER_GROUP_FAMILY, (String) databaseConfig.get("family"));
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_USERNAME, (String) databaseConfig.get("username"));
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_PASSWORD_PARAM, (String) databaseConfig.get("passwordParam"));
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_PORT, ((Integer) databaseConfig.get("port")).toString());
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_DATABASE, (String) databaseConfig.get("database"));
        assertParameterInMapIs(databaseParameters, 
                TenantAppStackParameter.RDS_BOOTSTRAP, (String) databaseConfig.get("bootstrapFilename"));
    }

    @Test
    public void getFilesystemParameters_nullAndEmptyFilesystemReturnsDefaults() {
        Map<String, Object> filesystemConfig = null;
        Map<TenantAppStackParameter, String> filesystemParameters = 
                TenantAppStackUtils.getFilesystemParameters(filesystemConfig);
        assertNotNull(filesystemParameters);
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.USE_EFS, Boolean.FALSE.toString());
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.MOUNT_POINT, "");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.ENCRYPT_EFS, Boolean.FALSE.toString());
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.EFS_LIFECYCLE_POLICY, "NEVER");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.USE_FSX, Boolean.FALSE.toString());
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_WINDOWS_MOUNT_DRIVE, "");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_DAILY_BACKUP_TIME, "");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_BACKUP_RETENTION, "7");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_THROUGHPUT_CAPACITY, "0");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_STORAGE_CAPACITY, "0");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_WEEKLY_MAINTENANCE_TIME, "");

        filesystemConfig = Map.of();
        filesystemParameters = TenantAppStackUtils.getFilesystemParameters(filesystemConfig);
        assertNotNull(filesystemParameters);
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.USE_EFS, Boolean.FALSE.toString());
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.MOUNT_POINT, "");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.ENCRYPT_EFS, Boolean.FALSE.toString());
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.EFS_LIFECYCLE_POLICY, "NEVER");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.USE_FSX, Boolean.FALSE.toString());
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_WINDOWS_MOUNT_DRIVE, "");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_DAILY_BACKUP_TIME, "");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_BACKUP_RETENTION, "7");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_THROUGHPUT_CAPACITY, "0");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_STORAGE_CAPACITY, "0");
        assertParameterInMapIs(filesystemParameters, TenantAppStackParameter.FSX_WEEKLY_MAINTENANCE_TIME, "");
    }

    @Test
    public void getFilesystemParameters_valid() {
        Map<String, Object> filesystemConfig = (Map<String, Object>) validTierConfig.get("filesystem");
        Map<String, Object> efsConfig = (Map<String, Object>) filesystemConfig.get("efs");
        Map<TenantAppStackParameter, String> filesystemParameters = 
                TenantAppStackUtils.getFilesystemParameters(filesystemConfig);
        assertNotNull(filesystemParameters);
        assertParameterInMapIs(filesystemParameters, 
                TenantAppStackParameter.USE_EFS, Boolean.TRUE.toString());
        assertParameterInMapIs(filesystemParameters, 
                TenantAppStackParameter.MOUNT_POINT, (String) filesystemConfig.get("mountPoint"));
        assertParameterInMapIs(filesystemParameters, 
                TenantAppStackParameter.ENCRYPT_EFS, ((Boolean) efsConfig.get("encryptAtRest")).toString());
        assertParameterInMapIs(filesystemParameters, 
                TenantAppStackParameter.EFS_LIFECYCLE_POLICY, (String) efsConfig.get("filesystemLifecycle"));
    }

    @Test
    public void getParametersFromServiceConfig_valid() {
        TenantAppStackUtils thisInstance = TenantAppStackUtils.builder()
                        .withEnvironment(ENV)
                        .withEventBus(EVENT_BUS)
                        .withTenant(validTenant)
                        .withAppConfig(validAppConfig)
                        .build();
        Map<TenantAppStackParameter, String> serviceConfigParameters = 
                        thisInstance.getParametersFromServiceConfig(validServiceConfig);
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.SERVICE_NAME, "main_service");
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.SERVICE_RESOURCE_NAME, "mainservice");
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.CONTAINER_REPOSITORY, 
                        (String) validServiceConfig.get("containerRepo"));
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.CONTAINER_REPOSITORY_TAG, 
                        (String) validServiceConfig.getOrDefault("containerTag", "latest"));
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.PUBLICLY_ADDRESSABLE, 
                        ((Boolean) validServiceConfig.get("public")).toString());
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.PUBLIC_PATH_ROUTE, 
                        (String) validServiceConfig.get("path"));
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.PUBLIC_PATH_RULE_PRIORITY, "2");
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.CONTAINER_OS, 
                        ((String) validServiceConfig.getOrDefault("operatingSystem", "")).replace("_", ""));
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.CLUSTER_INSTANCE_TYPE, 
                        (String) validTierConfig.get("instanceType"));
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.TASK_LAUNCH_TYPE, 
                        (String) validServiceConfig.get("ecsLaunchType"));
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.TASK_MEMORY, 
                        ((Integer) validTierConfig.get("memory")).toString());
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.TASK_CPU, 
                        ((Integer) validTierConfig.get("cpu")).toString());
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.MIN_TASK_COUNT, 
                        ((Integer) validTierConfig.get("min")).toString());
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.MAX_TASK_COUNT, 
                        ((Integer) validTierConfig.get("max")).toString());
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.CONTAINER_PORT, 
                        ((Integer) validServiceConfig.get("containerPort")).toString());
        assertParameterInMapIs(serviceConfigParameters, TenantAppStackParameter.CONTAINER_HEALTH_CHECK_PATH, 
                        (String) validServiceConfig.get("healthCheckUrl"));
    }

    @Test
    public void getParametersFromServiceConfig_privateServiceGeneratesServiceDiscoveryProperties() {
        Map<String, Object> privateService = new LinkedHashMap<String, Object>(validServiceConfig);
        privateService.put("public", false);
        privateService.put("name", "priv_serv");
        privateService.put("containerPort", 8443);
        validAppConfig.put("services", Map.of("priv_serv", privateService));
        TenantAppStackUtils thisInstance = TenantAppStackUtils.builder()
                        .withEnvironment(ENV)
                        .withEventBus(EVENT_BUS)
                        .withTenant(validTenant)
                        .withAppConfig(validAppConfig)
                        .build();
        // generate serviceDiscoveryProperties with a call to getParametersFromServiceConfig
        thisInstance.getParametersFromServiceConfig(privateService);
        Properties serviceDiscoveryProperties = thisInstance.getServiceDiscoveryProperties();
        assertTrue(serviceDiscoveryProperties.containsKey("SERVICE_PRIV_SERV_HOST"));
        assertEquals("privserv.local", serviceDiscoveryProperties.get("SERVICE_PRIV_SERV_HOST"));
        assertTrue(serviceDiscoveryProperties.containsKey("SERVICE_PRIV_SERV_PORT"));
        assertEquals(8443, serviceDiscoveryProperties.get("SERVICE_PRIV_SERV_PORT"));
    }

    private String getDetailFromValidTenantResources(String resource, String detail) {
        return ((Map<String, String>) validTenantResources.get(resource)).get(detail);
    }

    private void assertParameterInMapIs(
            Map<TenantAppStackParameter, String> paramMap, 
            TenantAppStackParameter param, 
            String value) {
        assertParameterInMap(paramMap, param);
        assertEquals("Expected " + param + " to be " + value + " but was " + paramMap.get(param), 
                value, paramMap.get(param));
    }

    private void assertParameterInMap(Map<TenantAppStackParameter, String> paramMap, TenantAppStackParameter param) {
        assertTrue("ParameterMap should contain " + param, paramMap.containsKey(param));
    }
}
