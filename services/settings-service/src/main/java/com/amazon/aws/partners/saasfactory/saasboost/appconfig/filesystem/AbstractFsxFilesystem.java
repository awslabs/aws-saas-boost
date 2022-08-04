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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem;

import java.util.Objects;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public abstract class AbstractFsxFilesystem extends AbstractFilesystem {

    private Integer storageGb;
    private String windowsMountDrive;
    // not included for EFS since throughput is a special case
    private Integer throughputMbs;
    private Integer backupRetentionDays;
    private String dailyBackupTime;
    private String weeklyMaintenanceTime;

    protected AbstractFsxFilesystem(Builder b) {
        super(b);
        if (b.storageGb == null) {
            throw new IllegalArgumentException("Cannot specify an FSx filesystem without storageGb.");
        }
        this.storageGb = b.storageGb;
        this.windowsMountDrive = b.windowsMountDrive;
        this.throughputMbs = b.throughputMbs;
        this.backupRetentionDays = b.backupRetentionDays;
        this.dailyBackupTime = b.dailyBackupTime;
        this.weeklyMaintenanceTime = b.weeklyMaintenanceTime;
    }

    public Integer getStorageGb() {
        return this.storageGb;
    }

    public String getWindowsMountDrive() {
        return this.windowsMountDrive;
    }

    public Integer getThroughputMbs() {
        return this.throughputMbs;
    }

    public Integer getBackupRetentionDays() {
        return this.backupRetentionDays;
    }

    public String getDailyBackupTime() {
        return this.dailyBackupTime;
    }

    public String getWeeklyMaintenanceTime() {
        return this.weeklyMaintenanceTime;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        // Same reference?
        if (this == obj) {
            return true;
        }
        // Same type?
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AbstractFsxFilesystem other = (AbstractFsxFilesystem) obj;
        return Utils.nullableEquals(this.getStorageGb(), other.getStorageGb())
                && Utils.nullableEquals(this.getWindowsMountDrive(), other.getWindowsMountDrive())
                && Utils.nullableEquals(this.getBackupRetentionDays(), other.getBackupRetentionDays())
                && Utils.nullableEquals(this.getDailyBackupTime(), other.getDailyBackupTime())
                && Utils.nullableEquals(this.getWeeklyMaintenanceTime(), other.getWeeklyMaintenanceTime())
                && Utils.nullableEquals(this.getThroughputMbs(), other.getThroughputMbs())
                && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getStorageGb(), this.getWindowsMountDrive(), 
                this.getBackupRetentionDays(), this.getDailyBackupTime(), this.getWeeklyMaintenanceTime(), 
                this.getThroughputMbs());
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    protected static abstract class Builder extends AbstractFilesystem.Builder{
        private Integer storageGb;
        private String windowsMountDrive;
        private Integer backupRetentionDays;
        private String dailyBackupTime;
        private String weeklyMaintenanceTime;
        private Integer throughputMbs;

        public Builder storageCapacity(Integer storageCapacity) {
            this.storageGb = storageCapacity;
            return this;
        }

        public Builder windowsMountDrive(String windowsMountDrive) {
            this.windowsMountDrive = windowsMountDrive;
            return this;
        }

        public Builder backupRetentionDays(Integer backupRetentionDays) {
            this.backupRetentionDays = backupRetentionDays;
            return this;
        }

        public Builder dailyBackupTime(String dailyBackupTime) {
            this.dailyBackupTime = dailyBackupTime;
            return this;
        }

        public Builder weeklyMaintenanceStartTime(String weeklyMaintenanceStartTime) {
            this.weeklyMaintenanceTime = weeklyMaintenanceStartTime;
            return this;
        }

        public Builder throughputMbs(Integer throughputMbs) {
            this.throughputMbs = throughputMbs;
            return this;
        }
    }
}
