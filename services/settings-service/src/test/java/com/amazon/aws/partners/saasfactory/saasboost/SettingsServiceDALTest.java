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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterType;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

// Note that these tests will only work if you run them from Maven or if you add
// the AWS_REGION and SAAS_BOOST_ENV environment variables to your IDE's configuration settings
public class SettingsServiceDALTest {

    private static String env;
    private static Setting emptyBillingApiKey;
    private static Setting billingApiKey;
    private HashMap<String, String> appSettings;

    public SettingsServiceDALTest() {
        if (System.getenv("AWS_REGION") == null || System.getenv("SAAS_BOOST_ENV") == null) {
            throw new IllegalStateException("Missing required environment variables for tests!");
        }
    }

    @BeforeClass
    public static void setup() {
        env = System.getenv("SAAS_BOOST_ENV");
        emptyBillingApiKey = Setting.builder()
                .name("BILLING_API_KEY")
                .value("")
                .secure(true)
                .readOnly(false)
                .version(null)
                .description(null)
                .build();
        billingApiKey = Setting.builder()
                .name("BILLING_API_KEY")
                .value("1234567890")
                .secure(true)
                .readOnly(false)
                .version(null)
                .description(null)
                .build();
    }

    @Before
    public void init() {
        appSettings = new HashMap<>();
        appSettings.put("APP_NAME", "test");
        appSettings.put("DOMAIN_NAME", "example.com");
        appSettings.put("SSL_CERT_ARN", "arn:aws:acm:region:account:certificate/certificate_ID_1");
        appSettings.put("HEALTH_CHECK", "/index.html");
        appSettings.put("COMPUTE_SIZE", "M");
        appSettings.put("TASK_CPU", "1024");
        appSettings.put("TASK_MEMORY", "2048");
        appSettings.put("CONTAINER_PORT", "7000");
        appSettings.put("MIN_COUNT", "1");
        appSettings.put("MAX_COUNT", "2");
        appSettings.put("CLUSTER_OS", "LINUX");
        appSettings.put("CLUSTER_INSTANCE_TYPE", "t3.medium");
    }

    @Test
    public void testFromParameterStore() {
        String settingName = "SAAS_BOOST_BUCKET";
        String parameterName = "/" + SettingsServiceDAL.SAAS_BOOST_PREFIX + "/" + env + "/" + settingName;
        String parameterValue = "sb-" + env + "-artifacts-test";

        assertTrue("null parameter returns null setting", SettingsServiceDAL.fromParameterStore(null) == null);
        assertThrows("null parameter name throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.fromParameterStore(Parameter.builder().build());});
        assertThrows("Empty parameter name throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.fromParameterStore(Parameter.builder().name("").build());});
        assertThrows("Blank parameter name is invalid pattern throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.fromParameterStore(Parameter.builder().name(" ").build());});
        assertThrows("Invalid pattern parameter name throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.fromParameterStore(Parameter.builder().name("foobar").build());});

        Parameter validParam = Parameter.builder()
                .name(parameterName)
                .value(parameterValue)
                .type(ParameterType.STRING)
                .version(null)
                .build();
        Setting expectedValidSetting = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .readOnly(true)
                .secure(false)
                .version(null)
                .description(null)
                .build();
        assertEquals("Valid " + parameterName + " param equals " + settingName + " setting", expectedValidSetting, SettingsServiceDAL.fromParameterStore(validParam));

        String readWriteParameterName ="/" + SettingsServiceDAL.SAAS_BOOST_PREFIX + "/" + env + "/APP_NAME";
        Parameter readWriteParameter = Parameter.builder()
                .name(readWriteParameterName)
                .value("foobar")
                .type(ParameterType.STRING)
                .version(null)
                .build();
        Setting expectedReadWriteSetting = Setting.builder()
                .name("APP_NAME")
                .value("foobar")
                .readOnly(false)
                .secure(false)
                .version(null)
                .description(null)
                .build();
        assertEquals("Read/Write param " + readWriteParameterName + " equals APP_NAME setting", expectedReadWriteSetting, SettingsServiceDAL.fromParameterStore(readWriteParameter));

        Parameter emptyParameter = Parameter.builder()
                .name(parameterName)
                .value("N/A")
                .type(ParameterType.STRING)
                .version(null)
                .build();
        Setting expectedEmptySetting = Setting.builder()
                .name(settingName)
                .value("")
                .readOnly(true)
                .secure(false)
                .version(null)
                .description(null)
                .build();
        assertEquals("Empty " + parameterName + " param equals blank setting", expectedEmptySetting, SettingsServiceDAL.fromParameterStore(emptyParameter));

        Parameter secretParameter = Parameter.builder()
                .name(parameterName)
                .value(parameterValue)
                .type(ParameterType.SECURE_STRING)
                .version(null)
                .build();
        Setting expectedSecretSetting = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .readOnly(true)
                .secure(true)
                .version(null)
                .description(null)
                .build();
        assertEquals("Valid secret param equals secure setting", expectedSecretSetting, SettingsServiceDAL.fromParameterStore(secretParameter));
    }

    @Test
    public void testToParameterStore() {
        String settingName = "SAAS_BOOST_BUCKET";
        String parameterName = "/" + SettingsServiceDAL.SAAS_BOOST_PREFIX + "/" + env + "/" + settingName;
        String parameterValue = "sb-" + env + "-artifacts-test";

        assertThrows("null setting throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.toParameterStore(null);});
        assertThrows("null setting name throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.toParameterStore(Setting.builder().build());});
        assertThrows("Empty setting name throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.toParameterStore(Setting.builder().name("").build());});
        assertThrows("Blank setting name throws RuntimeException", RuntimeException.class, () -> {SettingsServiceDAL.toParameterStore(Setting.builder().name(" ").build());});

        Parameter expectedEmptyParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.STRING)
                .value("N/A")
                .build();
        Setting settingNullValue = Setting.builder()
                .name(settingName)
                .value(null)
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals("null setting value equals N/A parameter value", expectedEmptyParameter, SettingsServiceDAL.toParameterStore(settingNullValue));

        Setting settingEmptyValue = Setting.builder()
                .name(settingName)
                .value("")
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals("Empty setting value equals N/A parameter value", expectedEmptyParameter, SettingsServiceDAL.toParameterStore(settingEmptyValue));

        Parameter expectedBlankParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.STRING)
                .value(" ")
                .build();
        Setting settingBlankValue = Setting.builder()
                .name(settingName)
                .value(" ")
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals("Blank setting value equals N/A parameter value", expectedBlankParameter, SettingsServiceDAL.toParameterStore(settingBlankValue));

        Parameter expectedValueParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.STRING)
                .value(parameterValue)
                .build();
        Setting settingWithValue = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals("Setting value equals parameter value", expectedValueParameter, SettingsServiceDAL.toParameterStore(settingWithValue));

        Parameter expectedSecretParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.SECURE_STRING)
                .value(parameterValue)
                .build();
        Setting settingSecretValue = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .description(null)
                .version(null)
                .secure(true)
                .readOnly(false)
                .build();
        assertEquals("Setting secret value equals secure parameter", expectedSecretParameter, SettingsServiceDAL.toParameterStore(settingSecretValue));
    }

    @Test
    public void testToAppConfigNoExtensions() throws Exception {
        // TODO POEPPT
//        System.out.println("testToAppConfigNoExtensions");
//        AppConfig expected = AppConfig.builder()
//                .name(appSettings.get("APP_NAME"))
//                .domainName(appSettings.get("DOMAIN_NAME"))
//                .sslCertArn(appSettings.get("SSL_CERT_ARN"))
//                .healthCheckURL(appSettings.get("HEALTH_CHECK"))
//                .computeSize(appSettings.get("COMPUTE_SIZE"))
//                .defaultCpu(appSettings.get("TASK_CPU"))
//                .defaultMemory(appSettings.get("TASK_MEMORY"))
//                .containerPort(appSettings.get("CONTAINER_PORT"))
//                .minCount(appSettings.get("MIN_COUNT"))
//                .maxCount(appSettings.get("MAX_COUNT"))
//                .operatingSystem(appSettings.get("CLUSTER_OS"))
//                .instanceType(appSettings.get("CLUSTER_INSTANCE_TYPE"))
//                .database(null)
//                .filesystem(null)
//                .billing(null)
//                .build();
//        AppConfig actual = SettingsServiceDAL.toAppConfig(appSettings, emptyBillingApiKey);
//
//        assertEquals("AppConfig with no extensions equals AppConfig from settings", expected, actual);
//        assertEquals("AppConfig with no extensions and null billing provider", expected, SettingsServiceDAL.toAppConfig(appSettings, null));
    }

    @Test
    public void testToAppConfigWithBilling() throws Exception {
        // TODO POEPPT
//        System.out.println("testToAppConfigWithBilling");
//        appSettings.put("BILLING_API_KEY", billingApiKey.getValue());
//
//        AppConfig expected = AppConfig.builder()
//                .name(appSettings.get("APP_NAME"))
//                .domainName(appSettings.get("DOMAIN_NAME"))
//                .sslCertArn(appSettings.get("SSL_CERT_ARN"))
//                .healthCheckURL(appSettings.get("HEALTH_CHECK"))
//                .computeSize(appSettings.get("COMPUTE_SIZE"))
//                .defaultCpu(appSettings.get("TASK_CPU"))
//                .defaultMemory(appSettings.get("TASK_MEMORY"))
//                .containerPort(appSettings.get("CONTAINER_PORT"))
//                .minCount(appSettings.get("MIN_COUNT"))
//                .maxCount(appSettings.get("MAX_COUNT"))
//                .operatingSystem(appSettings.get("CLUSTER_OS"))
//                .instanceType(appSettings.get("CLUSTER_INSTANCE_TYPE"))
//                .database(null)
//                .filesystem(null)
//                .billing(BillingProvider.builder().apiKey(billingApiKey.getValue()).build())
//                .build();
//        AppConfig actual = SettingsServiceDAL.toAppConfig(appSettings, billingApiKey);
//
//        assertEquals("AppConfig with billing provider equals AppConfig from settings", expected, actual);
    }

    @Test
    public void testToAppConfigWithEFS() throws Exception {
        // TODO POEPPT
//        System.out.println("testToAppConfigWithEFS");
//        appSettings.put("FILE_SYSTEM_TYPE", "EFS");
//        appSettings.put("FILE_SYSTEM_MOUNT_POINT", "/mnt");
//        appSettings.put("FILE_SYSTEM_ENCRYPT", "true");
//        appSettings.put("FILE_SYSTEM_LIFECYCLE", "NEVER");
//
//        AppConfig expected = AppConfig.builder()
//                .name(appSettings.get("APP_NAME"))
//                .domainName(appSettings.get("DOMAIN_NAME"))
//                .sslCertArn(appSettings.get("SSL_CERT_ARN"))
//                .healthCheckURL(appSettings.get("HEALTH_CHECK"))
//                .computeSize(appSettings.get("COMPUTE_SIZE"))
//                .defaultCpu(appSettings.get("TASK_CPU"))
//                .defaultMemory(appSettings.get("TASK_MEMORY"))
//                .containerPort(appSettings.get("CONTAINER_PORT"))
//                .minCount(appSettings.get("MIN_COUNT"))
//                .maxCount(appSettings.get("MAX_COUNT"))
//                .operatingSystem(appSettings.get("CLUSTER_OS"))
//                .instanceType(appSettings.get("CLUSTER_INSTANCE_TYPE"))
//                .database(null)
//                .filesystem(SharedFilesystem.builder()
//                        .fileSystemType("EFS")
//                        .mountPoint("/mnt")
//                        .efs(EfsFilesystem.builder()
//                                .encryptAtRest(true)
//                                .lifecycle("NEVER")
//                                .build()
//                        )
//                        .fsx(null)
//                        .build()
//                )
//                .billing(null)
//                .build();
//        AppConfig actual = SettingsServiceDAL.toAppConfig(appSettings, emptyBillingApiKey);
//
//        assertEquals("AppConfig with EFS file system equals AppConfig from settings", expected, actual);
    }

    @Test
    public void testToAppConfigWithFSx() throws Exception {
        // TODO POEPPT
//        System.out.println("testToAppConfigWithFSx");
//        appSettings.put("FILE_SYSTEM_TYPE", "FSX");
//        appSettings.put("FILE_SYSTEM_MOUNT_POINT", "FileShare");
//        appSettings.put("FSX_BACKUP_RETENTION_DAYS", "30");
//        appSettings.put("FSX_DAILY_BACKUP_TIME", "01:00");
//        appSettings.put("FSX_STORAGE_GB", "256");
//        appSettings.put("FSX_THROUGHPUT_MBS", "500");
//        appSettings.put("FSX_WEEKLY_MAINTENANCE_TIME", "07:01:00");
//        appSettings.put("FSX_WINDOWS_MOUNT_DRIVE", "Z:");
//
//        AppConfig expected = AppConfig.builder()
//                .name(appSettings.get("APP_NAME"))
//                .domainName(appSettings.get("DOMAIN_NAME"))
//                .sslCertArn(appSettings.get("SSL_CERT_ARN"))
//                .healthCheckURL(appSettings.get("HEALTH_CHECK"))
//                .computeSize(appSettings.get("COMPUTE_SIZE"))
//                .defaultCpu(appSettings.get("TASK_CPU"))
//                .defaultMemory(appSettings.get("TASK_MEMORY"))
//                .containerPort(appSettings.get("CONTAINER_PORT"))
//                .minCount(appSettings.get("MIN_COUNT"))
//                .maxCount(appSettings.get("MAX_COUNT"))
//                .operatingSystem(appSettings.get("CLUSTER_OS"))
//                .instanceType(appSettings.get("CLUSTER_INSTANCE_TYPE"))
//                .database(null)
//                .filesystem(SharedFilesystem.builder()
//                        .fileSystemType("FSX")
//                        .mountPoint("FileShare")
//                        .efs(null)
//                        .fsx(FsxFilesystem.builder()
//                                .windowsMountDrive("Z:")
//                                .backupRetentionDays(30)
//                                .storageGb(256)
//                                .dailyBackupTime("01:00")
//                                .throughputMbs(500)
//                                .weeklyMaintenanceTime("07:01:00")
//                                .build()
//                        )
//                        .build()
//                )
//                .billing(null)
//                .build();
//        AppConfig actual = SettingsServiceDAL.toAppConfig(appSettings, emptyBillingApiKey);
//
//        assertEquals("AppConfig with FSx file system equals AppConfig from settings", expected, actual);
    }

    @Test
    public void testToAppConfigWithDatabase() throws Exception {
        // TODO POEPPT
//        System.out.println("testToAppConfigWithDatabase");
//        appSettings.put("DB_ENGINE", "AURORA_PG");
//        appSettings.put("DB_NAME", "test");
//        appSettings.put("DB_VERSION", "11.7");
//        appSettings.put("DB_PARAM_FAMILY", "aurora-postgresql11");
//        appSettings.put("DB_INSTANCE_TYPE", "M5_4XL");
//        appSettings.put("DB_MASTER_USERNAME", "saasboost");
//        appSettings.put("DB_MASTER_PASSWORD", "foobar");
//        appSettings.put("DB_BOOTSTRAP_FILE", "bootstrap.sql");
//
//        AppConfig expected = AppConfig.builder()
//                .name(appSettings.get("APP_NAME"))
//                .domainName(appSettings.get("DOMAIN_NAME"))
//                .sslCertArn(appSettings.get("SSL_CERT_ARN"))
//                .healthCheckURL(appSettings.get("HEALTH_CHECK"))
//                .computeSize(appSettings.get("COMPUTE_SIZE"))
//                .defaultCpu(appSettings.get("TASK_CPU"))
//                .defaultMemory(appSettings.get("TASK_MEMORY"))
//                .containerPort(appSettings.get("CONTAINER_PORT"))
//                .minCount(appSettings.get("MIN_COUNT"))
//                .maxCount(appSettings.get("MAX_COUNT"))
//                .operatingSystem(appSettings.get("CLUSTER_OS"))
//                .instanceType(appSettings.get("CLUSTER_INSTANCE_TYPE"))
//                .database(Database.builder()
//                        .engine("AURORA_PG")
//                        .family("aurora-postgresql11")
//                        .version("11.7")
//                        .instance("M5_4XL")
//                        .database("test")
//                        .username("saasboost")
//                        .password("foobar")
//                        .bootstrapFilename("bootstrap.sql")
//                        .build()
//                )
//                .filesystem(null)
//                .billing(null)
//                .build();
//        AppConfig actual = SettingsServiceDAL.toAppConfig(appSettings, emptyBillingApiKey);
//
//        assertEquals("AppConfig with RDS database equals AppConfig from settings", expected, actual);
    }

    @Test
    public void testRdsOptionsSorting() throws Exception {
        try (InputStream json = Files.newInputStream(Path.of(this.getClass().getClassLoader().getResource("rdsInstancesUnsorted.json").toURI()))) {
            LinkedHashMap<String, Object> options = Utils.fromJson(json, LinkedHashMap.class);
            ArrayList<LinkedHashMap<String, String>> instances = (ArrayList<LinkedHashMap<String, String>>) options.get("instances");

            //System.out.println("Unsorted:");
            //System.out.println(Utils.toJson(instances));

            Collections.sort(instances, SettingsServiceDAL.RDS_INSTANCE_COMPARATOR);

            //System.out.println();
            //System.out.println("Sorted:");
            //System.out.println(Utils.toJson(instances));

            int lastIndexOfT = -1;
            int lastIndexOfM = -1;
            int lastIndexOfM4 = -1;

            int firstIndexOfM = -1;
            int firstIndexOfR = -1;
            int firstIndexOfM5 = -1;

            int lastIndexOfMicro = -1;
            int lastIndexOfSmall = -1;
            int lastIndexOfMedium = -1;
            int lastIndexOfLarge = -1;
            int lastIndexOfXL = -1;
            int lastIndexOf2XL = -1;
            int lastIndexOf4XL = -1;
            int lastIndexOf12XL = -1;

            int firstIndexOfSmall = -1;
            int firstIndexOfMedium = -1;
            int firstIndexOfLarge = -1;
            int firstIndexOfXL = -1;
            int firstIndexOf2XL = -1;
            int firstIndexOf4XL = -1;
            int firstIndexOf12XL = -1;
            int firstIndexOf24XL = -1;

            for (int i = instances.size(); i-- > 0;) {
                LinkedHashMap<String, String> instance = instances.get(i);
                char type = ((String) instance.get("instance")).charAt(0);
                Integer generation = Integer.valueOf(((String) instance.get("instance")).substring(1, 2));
                String size = ((String) instance.get("instance")).substring(3);

                if ('T' == type) {
                    if (lastIndexOfT == -1) {
                        lastIndexOfT = i;
                    }
                    if (3 == generation) {
                        if ("MICRO".equals(size) && lastIndexOfMicro == -1) {
                            lastIndexOfMicro = i;
                        }
                        if ("SMALL".equals(size) && lastIndexOfSmall == -1) {
                            lastIndexOfSmall = i;
                        }
                        if ("MEDIUM".equals(size) && lastIndexOfMedium == -1) {
                            lastIndexOfMedium = i;
                        }
                        if ("LARGE".equals(size) && lastIndexOfLarge == -1) {
                            lastIndexOfLarge = i;
                        }
                        if ("XL".equals(size) && lastIndexOfXL == -1) {
                            lastIndexOfXL = i;
                        }
                        if ("2XL".equals(size) && lastIndexOf2XL == -1) {
                            lastIndexOf2XL = i;
                        }
                        // These don't really exist in the T class, but it helps to test
                        // all possibilities in a single instance class due to how the
                        // comparator is supposed to work
                        if ("4XL".equals(size) && lastIndexOf4XL == -1) {
                            lastIndexOf4XL = i;
                        }
                        if ("12XL".equals(size) && lastIndexOf12XL == -1) {
                            lastIndexOf12XL = i;
                        }
                    }
                }
                if ('M' == type && lastIndexOfM == -1) {
                    lastIndexOfM = i;
                }
                if ('M' == type && 4 == generation && lastIndexOfM4 == -1) {
                    lastIndexOfM4 = i;
                }
            }

            for (int i = 0; i < instances.size(); i++) {
                LinkedHashMap<String, String> instance = instances.get(i);
                char type = ((String) instance.get("instance")).charAt(0);
                Integer generation = Integer.valueOf(((String) instance.get("instance")).substring(1, 2));
                String size = ((String) instance.get("instance")).substring(3);

                if ('T' == type && 3 == generation) {
                    if ("SMALL".equals(size) && firstIndexOfSmall == -1) {
                        firstIndexOfSmall = i;
                    }
                    if ("MEDIUM".equals(size) && firstIndexOfMedium == -1) {
                        firstIndexOfMedium = i;
                    }
                    if ("LARGE".equals(size) && firstIndexOfLarge == -1) {
                        firstIndexOfLarge = i;
                    }
                    if ("XL".equals(size) && firstIndexOfXL == -1) {
                        firstIndexOfXL = i;
                    }
                    if ("2XL".equals(size) && firstIndexOf2XL == -1) {
                        firstIndexOf2XL = i;
                    }
                    // These don't really exist in the T class, but it helps to test
                    // all possibilities in a single instance class due to how the
                    // comparator is supposed to work
                    if ("4XL".equals(size) && firstIndexOf4XL == -1) {
                        firstIndexOf4XL = i;
                    }
                    if ("12XL".equals(size) && firstIndexOf12XL == -1) {
                        firstIndexOf12XL = i;
                    }
                    if ("24XL".equals(size) && firstIndexOf24XL == -1) {
                        firstIndexOf24XL = i;
                    }
                }
                if ('M' == type && firstIndexOfM == -1) {
                    firstIndexOfM = i;
                }
                if ('R' == type && firstIndexOfR == -1) {
                    firstIndexOfR = i;
                }
                if ('M' == type && 5 == generation && firstIndexOfM5 == -1) {
                    firstIndexOfM5 = i;
                }
            }

            assertTrue("lastIndexOfT is defined", lastIndexOfT != -1);
            assertTrue("lastIndexOfM is defined", lastIndexOfM != -1);
            assertTrue("lastIndexOfM4 is defined", lastIndexOfM4 != -1);
            assertTrue("firstIndexOfM is defined", firstIndexOfM != -1);
            assertTrue("firstIndexOfR is defined", firstIndexOfR != -1);
            assertTrue("firstIndexOfM5 is defined", firstIndexOfM5 != -1);
            assertTrue("lastIndexOfMicro is defined", lastIndexOfMicro != -1);
            assertTrue("lastIndexOfSmall is defined", lastIndexOfSmall != -1);
            assertTrue("lastIndexOfMedium is defined", lastIndexOfMedium != -1);
            assertTrue("lastIndexOfLarge is defined", lastIndexOfLarge != -1);
            assertTrue("lastIndexOfXL is defined", lastIndexOfXL != -1);
            assertTrue("lastIndexOf2XL is defined", lastIndexOf2XL != -1);
            assertTrue("lastIndexOf4XL is defined", lastIndexOf4XL != -1);
            assertTrue("lastIndexOf12XL is defined", lastIndexOf12XL != -1);
            assertTrue("firstIndexOfSmall is defined", firstIndexOfSmall != -1);
            assertTrue("firstIndexOfMedium is defined", firstIndexOfMedium != -1);
            assertTrue("firstIndexOfLarge is defined", firstIndexOfLarge != -1);
            assertTrue("firstIndexOfXL is defined", firstIndexOfXL != -1);
            assertTrue("firstIndexOf2XL is defined", firstIndexOf2XL != -1);
            assertTrue("firstIndexOf4XL is defined", firstIndexOf4XL != -1);
            assertTrue("firstIndexOf12XL is defined", firstIndexOf12XL != -1);
            assertTrue("firstIndexOf24XL is defined", firstIndexOf24XL != -1);

            // T's before M's before R's
            assertTrue("T's before M's", lastIndexOfT < firstIndexOfM);
            assertTrue("M's before R's", lastIndexOfM < firstIndexOfR);

            // Earlier generations before later
            assertTrue("4th generations before 5th generations", lastIndexOfM4 < firstIndexOfM5);

            // Smaller compute size before larger
            assertTrue("MICRO before SMALL", lastIndexOfMicro < firstIndexOfSmall);
            assertTrue("SMALL before MEDIUM", lastIndexOfSmall < firstIndexOfMedium);
            assertTrue("MEDIUM before LARGE", lastIndexOfMedium < firstIndexOfLarge);
            assertTrue("LARGE before XL", lastIndexOfLarge < firstIndexOfXL);
            assertTrue("XL before 2XL", lastIndexOfXL < firstIndexOf2XL);
            assertTrue("2XL before 4XL", lastIndexOf2XL < firstIndexOf4XL);
            assertTrue("4XL before 12XL", lastIndexOf4XL < firstIndexOf12XL);
            assertTrue("12XL before 24XL", lastIndexOf2XL < firstIndexOf24XL);
        }
    }
}
