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

package com.amazon.aws.partners.saasfactory.saasboost.filesystem;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.AbstractFilesystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.AbstractFilesystemTierConfig;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.efs.EfsFilesystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.efs.EfsFilesystemTierConfig;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx.FsxOntapFilesystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx.FsxOntapFilesystemTierConfig;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx.FsxWindowsFilesystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx.FsxWindowsFilesystemTierConfig;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class AbstractFilesystemTest {

    @Test
    public void deserialize_efsFilesystem_basic() {
        String efsJson = "{\"type\":\"EFS\", \"mountPoint\":\"/mnt\", \"tiers\":{"
                + "\"Free\":{\"encrypt\":false, \"lifecycle\":\"NEVER\"}, " 
                + "\"Gold\":{\"encrypt\":true, \"lifecycle\":\"AFTER_7_DAYS\", \"encryptionKey\":\"encryptionARN\"}}}";
        AbstractFilesystem fs = Utils.fromJson(efsJson, AbstractFilesystem.class);
        assertEquals(EfsFilesystem.class, fs.getClass());
        assertEquals("/mnt", fs.getMountPoint());
        assertNotNull(fs.getTiers());
        for (Map.Entry<String, ? extends AbstractFilesystemTierConfig> tierEntry : fs.getTiers().entrySet()) {
            String tierName = tierEntry.getKey();
            assertEquals(EfsFilesystemTierConfig.class, tierEntry.getValue().getClass());
            EfsFilesystemTierConfig tier = (EfsFilesystemTierConfig) tierEntry.getValue();
            switch (tierName) {
                case "Free": {
                    assertEquals(false, tier.getEncrypt());
                    assertNull(tier.getEncryptionKey());
                    assertEquals(EfsFilesystemTierConfig.EfsLifecycle.NEVER.name(), tier.getLifecycle());
                    break;
                }
                case "Gold": {
                    assertEquals(true, tier.getEncrypt());
                    assertEquals("encryptionARN", tier.getEncryptionKey());
                    assertEquals(EfsFilesystemTierConfig.EfsLifecycle.AFTER_7_DAYS.name(), tier.getLifecycle());
                    break;
                }
                default: fail("Deserialize filesystem JSON found an unexpected tier. "
                        + "Wanted [Free|Gold] but found " + tierName);
            }
        }
    }

    @Test
    public void deserialize_fsxWindowsFilesystem_basic() {
        String fsxWindowsJson = "{\"type\":\"FSX_WINDOWS\", \"mountPoint\":\"/mnt\", \"windowsMountDrive\":\"G:\\\\\\\\\","
                + "\"tiers\":{"
                + "\"Free\":{\"encrypt\":false, \"storageGb\":100, \"throughputMbs\":\"200\", \"backupRetentionDays\":1, \"dailyBackupTime\":\"12:00\", \"weeklyMaintenanceTime\":\"3:09:00\"}," 
                + "\"Gold\":{\"encrypt\":true, \"encryptionKey\":\"encryptionARN\", \"storageGb\":1000, \"throughputMbs\":\"2000\", \"backupRetentionDays\":10, \"dailyBackupTime\":\"23:55\", \"weeklyMaintenanceTime\":\"6:12:00\"}}}";
        AbstractFilesystem fs = Utils.fromJson(fsxWindowsJson, AbstractFilesystem.class);
        assertEquals(FsxWindowsFilesystem.class, fs.getClass());
        FsxWindowsFilesystem fsxWindowsFs = (FsxWindowsFilesystem) fs;
        assertEquals("/mnt", fs.getMountPoint());
        assertEquals("G:\\\\", fsxWindowsFs.getWindowsMountDrive());
        assertNotNull(fs.getTiers());
        for (Map.Entry<String, ? extends AbstractFilesystemTierConfig> tierEntry : fs.getTiers().entrySet()) {
            String tierName = tierEntry.getKey();
            assertEquals(FsxWindowsFilesystemTierConfig.class, tierEntry.getValue().getClass());
            FsxWindowsFilesystemTierConfig tier = (FsxWindowsFilesystemTierConfig) tierEntry.getValue();
            switch (tierName) {
                case "Free": {
                    assertEquals(false, tier.getEncrypt());
                    assertNull(tier.getEncryptionKey());
                    assertEquals(Integer.valueOf(100), tier.getStorageGb());
                    assertEquals(Integer.valueOf(200), tier.getThroughputMbs());
                    assertEquals(Integer.valueOf(1), tier.getBackupRetentionDays());
                    assertEquals("12:00", tier.getDailyBackupTime());
                    assertEquals("3:09:00", tier.getWeeklyMaintenanceTime());
                    break;
                }
                case "Gold": {
                    assertEquals(true, tier.getEncrypt());
                    assertEquals("encryptionARN", tier.getEncryptionKey());
                    assertEquals(Integer.valueOf(1000), tier.getStorageGb());
                    assertEquals(Integer.valueOf(2000), tier.getThroughputMbs());
                    assertEquals(Integer.valueOf(10), tier.getBackupRetentionDays());
                    assertEquals("23:55", tier.getDailyBackupTime());
                    assertEquals("6:12:00", tier.getWeeklyMaintenanceTime());
                    break;
                }
                default: fail("Deserialize filesystem JSON found an unexpected tier. "
                        + "Wanted [Free|Gold] but found " + tierName);
            }
        }
    }

    @Test
    public void deserialize_fsxOntapFilesystem_basic() {
        String fsxWindowsJson = "{\"type\":\"FSX_ONTAP\", \"mountPoint\":\"/mnt\", \"windowsMountDrive\":\"G:\\\\\\\\\","
                + "\"tiers\":{"
                + "\"Free\":{\"encrypt\":false, \"storageGb\":100, \"throughputMbs\":\"200\", \"backupRetentionDays\":1, \"dailyBackupTime\":\"12:00\", \"weeklyMaintenanceTime\":\"3:09:00\", \"volumeSize\":300}," 
                + "\"Gold\":{\"encrypt\":true, \"encryptionKey\":\"encryptionARN\", \"storageGb\":1000, \"throughputMbs\":\"2000\", \"backupRetentionDays\":10, \"dailyBackupTime\":\"23:55\", \"weeklyMaintenanceTime\":\"6:12:00\", \"volumeSize\":3000}}}";
        AbstractFilesystem fs = Utils.fromJson(fsxWindowsJson, AbstractFilesystem.class);
        assertEquals(FsxOntapFilesystem.class, fs.getClass());
        FsxOntapFilesystem fsxOntapFs = (FsxOntapFilesystem) fs;
        assertEquals("/mnt", fs.getMountPoint());
        assertEquals("G:\\\\", fsxOntapFs.getWindowsMountDrive());
        assertNotNull(fs.getTiers());
        for (Map.Entry<String, ? extends AbstractFilesystemTierConfig> tierEntry : fs.getTiers().entrySet()) {
            String tierName = tierEntry.getKey();
            assertEquals(FsxOntapFilesystemTierConfig.class, tierEntry.getValue().getClass());
            FsxOntapFilesystemTierConfig tier = (FsxOntapFilesystemTierConfig) tierEntry.getValue();
            switch (tierName) {
                case "Free": {
                    assertEquals(false, tier.getEncrypt());
                    assertNull(tier.getEncryptionKey());
                    assertEquals(Integer.valueOf(100), tier.getStorageGb());
                    assertEquals(Integer.valueOf(200), tier.getThroughputMbs());
                    assertEquals(Integer.valueOf(1), tier.getBackupRetentionDays());
                    assertEquals("12:00", tier.getDailyBackupTime());
                    assertEquals("3:09:00", tier.getWeeklyMaintenanceTime());
                    assertEquals(Integer.valueOf(300), tier.getVolumeSize());
                    break;
                }
                case "Gold": {
                    assertEquals(true, tier.getEncrypt());
                    assertEquals("encryptionARN", tier.getEncryptionKey());
                    assertEquals(Integer.valueOf(1000), tier.getStorageGb());
                    assertEquals(Integer.valueOf(2000), tier.getThroughputMbs());
                    assertEquals(Integer.valueOf(10), tier.getBackupRetentionDays());
                    assertEquals("23:55", tier.getDailyBackupTime());
                    assertEquals("6:12:00", tier.getWeeklyMaintenanceTime());
                    assertEquals(Integer.valueOf(3000), tier.getVolumeSize());
                    break;
                }
                default: fail("Deserialize filesystem JSON found an unexpected tier. "
                        + "Wanted [Free|Gold] but found " + tierName);
            }
        }
    }
}
