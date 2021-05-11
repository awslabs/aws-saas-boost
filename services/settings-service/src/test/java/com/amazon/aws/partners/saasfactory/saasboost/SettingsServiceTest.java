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

import java.util.ArrayList;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SettingsServiceTest {

    private static Pattern pattern;
    private static String paramKey;
    private static String ssmPathPrefix;
    private static String saasBoostEnv;
    private static UUID tenantId;
    private Parameter param;

    @BeforeClass
    public static void setup() {
        ssmPathPrefix = "saas-boost";
        saasBoostEnv = "test";
        tenantId = UUID.randomUUID();
        pattern = Pattern.compile("^\\/" + ssmPathPrefix + "\\/" + saasBoostEnv + "\\/(.+)$");

        paramKey = "SAAS_BOOST_BUCKET";
    }

    @Before
    public void init() {
        param = Parameter.builder()
                .name("/" + ssmPathPrefix + "/" + saasBoostEnv + "/" + paramKey)
                .value("saas-boost-s3-bucket")
                .build();
    }

    @Test
    public void testSsmParamRegex() {
        System.out.println("testSsmParamRegex");
        Matcher regex = pattern.matcher(param.name());

        //System.out.println("SSM Parameter matches regex pattern " + regex.matches());
        assertTrue("Parameter name doesn't match regex pattern", regex.matches());

        //System.out.println("Regex grouping = " + regex.group(1));
        assertEquals("Regex grouping " + regex.group(1) + " does not match parameter key", paramKey, regex.group(1));
    }

    @Test
    public void testCollectTenantParams() {
        System.out.println("testCollectTenantParams");
        String parameterStorePath = "/" + ssmPathPrefix + "/" + saasBoostEnv + "/tenant/" + tenantId.toString();
        List<String> parametersToDelete = SettingsService.TENANT_PARAMS.stream()
                .map(s -> parameterStorePath + "/" + s)
                .collect(Collectors.toList());
        System.out.println(parametersToDelete.toString());
    }

    @Test
    public void testTenantParamRegex() {
        System.out.println("testTenantParamRegex");
        String tenantId = "a0c169ed-7a30-4fa2-a6d9-675e0adbdc7f";
        //String tenantId = "2905470B-05CE-4262-BF87-8A2E3C36F8D3";
        Parameter tenantParam = Parameter.builder()
                .name("/" + ssmPathPrefix + "/" + saasBoostEnv + "/tenant/" + tenantId + "/DB_HOST")
                .value("localhost.localdomain")
                .build();
        final Pattern pattern = Pattern.compile("^\\/" + ssmPathPrefix + "\\/" + saasBoostEnv + "\\/tenant\\/(\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12})\\/(.+)$");
        Matcher regex = pattern.matcher(tenantParam.name());
        //System.out.println(tenantParam.name());
        assertTrue("Parameter name doesn't match tenant regex pattern", regex.matches());

        assertEquals("Regex grouping " + regex.group(1) + " does not match tenant id", tenantId, regex.group(1));
        //System.out.println("Regex grouping = " + regex.group(2));
        assertEquals("Regex grouping " + regex.group(2) + " does not match parameter key", "DB_HOST", regex.group(2));
    }

    @Test
    public void testAppConfigJson() throws Exception {
        System.out.println("testAppConfigJson");
        //OperatingSystem os = OperatingSystem.WIN_2019_FULL;
        //System.out.println(Utils.toJson(os));

        AppConfig appConfig = AppConfig.builder().build();
        System.out.println("Default CPU = " + appConfig.getDefaultCpu());
        StringBuilder buffer = new StringBuilder("{");
        buffer.append("name:");
        buffer.append(appConfig.getName());
        buffer.append(",");
        buffer.append("domainName:");
        buffer.append(appConfig.getDomainName());
        buffer.append(",");
        buffer.append("minCount:");
        buffer.append(appConfig.getMinCount());
        buffer.append(",");
        buffer.append("maxCount:");
        buffer.append(appConfig.getMaxCount());
        buffer.append(",");
        buffer.append("computeSize:");
        buffer.append(appConfig.getComputeSize());
        buffer.append(",");
        buffer.append("defaultCpu:");
        buffer.append(appConfig.getDefaultCpu());
        buffer.append(",");
        buffer.append("defaultMemory:");
        buffer.append(appConfig.getDefaultMemory());
        buffer.append(",");
        buffer.append("containerPort:");
        buffer.append(appConfig.getContainerPort());
        buffer.append(",");
        buffer.append("healthCheckURL:");
        buffer.append(appConfig.getHealthCheckURL());
        buffer.append(",");
        buffer.append("operatingSystem:");
        buffer.append(appConfig.getOperatingSystem());
        buffer.append(",");
        buffer.append("instanceType:");
        buffer.append(appConfig.getInstanceType());
        buffer.append(",");
        buffer.append("filesystem:");
        buffer.append(appConfig.getFilesystem());
        buffer.append(",");
        buffer.append("database:");
        buffer.append(appConfig.getDatabase());
        buffer.append("}");
        System.out.println(buffer.toString());

        Map<OperatingSystem, String> options = new EnumMap<>(OperatingSystem.class);
        for (OperatingSystem o : OperatingSystem.values()) {
            options.put(o, o.getDescription());
        }
        System.out.println(Utils.toJson(options));
    }

    @Test
    public void testSetAppConfig() {
        String body = "{\n" +
                "    \"billing\": null,\n" +
                "    \"computeSize\": \"S\",\n" +
                "    \"containerPort\": 8080,\n" +
                "    \"database\": {\n" +
                "        \"database\": \"123\",\n" +
                "        \"engine\": \"MS_SQL_ENTERPRISE\",\n" +
                "        \"engineName\": \"aurora-mysql\",\n" +
                "        \"family\": \"sqlserver-ex-15.0\",\n" +
                "        \"instance\": \"T3_XL\",\n" +
                "        \"instanceClass\": \"db.t3.small\",\n" +
                "        \"password\": \"admin1234\",\n" +
                "        \"username\": \"dbadmin\",\n" +
                "        \"version\": \"15.00.4043.16.v1\"\n" +
                "    },\n" +
                "    \"domainName\": \"\",\n" +
//                "    \"filesystem\": {\n" +
//                "        \"fileSystemType\" : \"fsx\",\n" +
//                "    },\n" +
                "    \"filesystem\": {\n" +
                "        \"fileSystemType\" : \"fsx\",\n" +
                "        \"mountPoint\": \"test\",\n" +
                "        \"fsx\": {\n" +
                "            \"storageGb\": 0,\n" +
                "            \"throughputMbs\": 0,\n" +
                "            \"backupRetentionDays\": 0,\n" +
                "            \"dailyBackupTime\": \"\",\n" +
                "            \"weeklyMaintenanceTime\": \"\",\n" +
                "            \"windowsMountDrive\": \"\"\n" +
                "        },\n" +
                "        \"efs\": {\n" +
                "            \"encryptAtRest\": false,\n" +
                "            \"lifecycle\": 14,\n" +
                "            \"filesystemLifecycle\": \"AFTER_14_DAYS\"\n" +
                "        }\n" +
                  "     }, \n" +

//                "    \"filesystem\": {\n" +
//                "        \"encryptAtRest\": false,\n" +
//                "        \"lifecycle\": 14,\n" +
//                "        \"mountPoint\": \"/mnt\"\n" +
//                "    },\n" +
                "    \"healthCheckURL\": \"/index.html\",\n" +
                "    \"maxCount\": 2,\n" +
                "    \"minCount\": 1,\n" +
                "    \"name\": \"saas-app\",\n" +
                "    \"operatingSystem\": \"LINUX\"\n" +
                "}\n";

        System.out.println(body);
        AppConfig appConfig = Utils.fromJson(body, AppConfig.class);

        System.out.println("LifeCycle: " + appConfig.getFilesystem().getEfs().getLifecycle());

        boolean sqlServerError = false;
        if (appConfig.getDatabase() != null) {
            EnumSet<Database.RDS_ENGINE> sqlServer = EnumSet.of(
                    Database.RDS_ENGINE.MS_SQL_EXPRESS,
                    Database.RDS_ENGINE.MS_SQL_STANDARD,
                    Database.RDS_ENGINE.MS_SQL_WEB,
                    Database.RDS_ENGINE.MS_SQL_ENTERPRISE);
            for (Database.RDS_ENGINE mssql : sqlServer) {
                if (mssql.name().equals(appConfig.getDatabase().getEngine())) {
                    // CloudFormation won't let you specify a database name for SQL Server
                    if (appConfig.getDatabase().getDatabase() != null && !appConfig.getDatabase().getDatabase().isEmpty()) {
                        sqlServerError = true;
                    }
                    break;
                }
            }
        }

        assertTrue(sqlServerError);

//        for (Setting setting : SettingsServiceDAL.toSettings(appConfig)) {
//            if (setting.isSecure()) {
//                Setting existing = getSetting(setting.getName());
//                // If we were passed the encrypted string for a secret (from the UI),
//                // don't overwrite the secret with that gibberish...
//                if (existing != null && existing.getValue().equals(setting.getValue())) {
//                    // Nothing has changed, don't overwrite the value in Parameter Store
//                    continue;
//                }
//            }
//            updateSetting(setting);
//        }
    }

    @Test
    public void testToParameterStore() {

    }

    @Test
    public void testFromParameterStore() {

    }
    
    @Test
    public void testRdsOptions() throws Exception {
        InputStream json = this.getClass().getClassLoader().getResourceAsStream("options.json");
        Map<String, Object> options = Utils.fromJson(json, HashMap.class);
        ArrayList<Map<String, Object>> dbOptions = (ArrayList<Map<String, Object>>) options.get("dbOptions");

        for (Map<String, Object> engine : dbOptions) {
            ArrayList<Map<String, Object>> instances = (ArrayList<Map<String, Object>>) engine.get("instances");
            
//            System.out.println("Unsorted");
//            for (Map<String, Object> instance : instances) {
//                System.out.println(instance.get("instance"));
//            }
//            System.out.println();
            
            Collections.sort(instances, RDS_INSTANCE_TYPE);
            System.out.println("Sorted");
            for (Map<String, Object> instance : instances) {
                System.out.println(instance.get("instance"));
            }
            System.out.println();
        }
    }
    
    static final Comparator<Map<String, Object>> INSTANCE_TYPE = ((instance1, instance2) -> {
                // T's before M's before R's
                char type1 = ((String) instance1.get("instance")).charAt(0);
                char type2 = ((String) instance2.get("instance")).charAt(0);
                int compare = 0;
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
    
    static final Comparator<Map<String, Object>> INSTANCE_GENERATION = ((instance1, instance2) -> {
                Integer gen1 = Integer.valueOf(((String) instance1.get("instance")).substring(1, 2));
                Integer gen2 = Integer.valueOf(((String) instance2.get("instance")).substring(1, 2));
                return gen1.compareTo(gen2);
    });
    
    static final Comparator<Map<String, Object>> INSTANCE_SIZE = ((instance1, instance2) -> {
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
    
    static final Comparator<Map<String, Object>> RDS_INSTANCE_TYPE = INSTANCE_TYPE.thenComparing(INSTANCE_GENERATION).thenComparing(INSTANCE_SIZE);
}