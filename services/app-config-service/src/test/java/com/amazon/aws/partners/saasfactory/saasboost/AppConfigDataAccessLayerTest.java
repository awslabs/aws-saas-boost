package com.amazon.aws.partners.saasfactory.saasboost;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppConfigDataAccessLayerTest {

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

            Collections.sort(instances, AppConfigDataAccessLayer.RDS_INSTANCE_COMPARATOR);

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

            assertTrue(lastIndexOfT != -1, "lastIndexOfT is defined");
            assertTrue(lastIndexOfM != -1, "lastIndexOfM is defined");
            assertTrue(lastIndexOfM4 != -1, "lastIndexOfM4 is defined");
            assertTrue(firstIndexOfM != -1, "firstIndexOfM is defined");
            assertTrue(firstIndexOfR != -1, "firstIndexOfR is defined");
            assertTrue(firstIndexOfM5 != -1, "firstIndexOfM5 is defined");
            assertTrue(lastIndexOfMicro != -1, "lastIndexOfMicro is defined");
            assertTrue(lastIndexOfSmall != -1, "lastIndexOfSmall is defined");
            assertTrue(lastIndexOfMedium != -1, "lastIndexOfMedium is defined");
            assertTrue(lastIndexOfLarge != -1, "lastIndexOfLarge is defined");
            assertTrue(lastIndexOfXL != -1, "lastIndexOfXL is defined");
            assertTrue(lastIndexOf2XL != -1, "lastIndexOf2XL is defined");
            assertTrue(lastIndexOf4XL != -1, "lastIndexOf4XL is defined");
            assertTrue(lastIndexOf12XL != -1, "lastIndexOf12XL is defined");
            assertTrue(firstIndexOfSmall != -1, "firstIndexOfSmall is defined");
            assertTrue(firstIndexOfMedium != -1, "firstIndexOfMedium is defined");
            assertTrue(firstIndexOfLarge != -1, "firstIndexOfLarge is defined");
            assertTrue(firstIndexOfXL != -1, "firstIndexOfXL is defined");
            assertTrue(firstIndexOf2XL != -1, "firstIndexOf2XL is defined");
            assertTrue(firstIndexOf4XL != -1, "firstIndexOf4XL is defined");
            assertTrue(firstIndexOf12XL != -1, "firstIndexOf12XL is defined");
            assertTrue(firstIndexOf24XL != -1, "firstIndexOf24XL is defined");

            // T's before M's before R's
            assertTrue(lastIndexOfT < firstIndexOfM, "T's before M's");
            assertTrue(lastIndexOfM < firstIndexOfR, "M's before R's");

            // Earlier generations before later
            assertTrue(lastIndexOfM4 < firstIndexOfM5, "4th generations before 5th generations");

            // Smaller compute size before larger
            assertTrue(lastIndexOfMicro < firstIndexOfSmall, "MICRO before SMALL");
            assertTrue(lastIndexOfSmall < firstIndexOfMedium, "SMALL before MEDIUM");
            assertTrue(lastIndexOfMedium < firstIndexOfLarge, "MEDIUM before LARGE");
            assertTrue(lastIndexOfLarge < firstIndexOfXL, "LARGE before XL");
            assertTrue(lastIndexOfXL < firstIndexOf2XL, "XL before 2XL");
            assertTrue(lastIndexOf2XL < firstIndexOf4XL, "2XL before 4XL");
            assertTrue(lastIndexOf4XL < firstIndexOf12XL, "4XL before 12XL");
            assertTrue(lastIndexOf2XL < firstIndexOf24XL, "12XL before 24XL");
        }
    }
}

