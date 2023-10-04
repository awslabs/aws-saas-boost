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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.AbstractFilesystemTierConfig;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

public abstract class AbstractFsxFilesystemTierConfig extends AbstractFilesystemTierConfig {
    
    private Integer storageGb;
    private Integer throughputMbs;
    private Integer backupRetentionDays;
    private String dailyBackupTime;
    private String weeklyMaintenanceTime;

    protected AbstractFsxFilesystemTierConfig(Builder b) {
        super(b);
        if (b.storageGb == null) {
            throw new IllegalArgumentException("Cannot specify an FSx filesystem tier without storageGb.");
        }
        this.storageGb = b.storageGb;
        this.throughputMbs = b.throughputMbs;
        this.backupRetentionDays = b.backupRetentionDays;
        this.dailyBackupTime = b.dailyBackupTime;
        this.weeklyMaintenanceTime = b.weeklyMaintenanceTime;
    }

    public Integer getStorageGb() {
        return this.storageGb;
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
        final AbstractFsxFilesystemTierConfig other = (AbstractFsxFilesystemTierConfig) obj;
        return Utils.nullableEquals(this.getStorageGb(), other.getStorageGb())
                && Utils.nullableEquals(this.getBackupRetentionDays(), other.getBackupRetentionDays())
                && Utils.nullableEquals(this.getDailyBackupTime(), other.getDailyBackupTime())
                && Utils.nullableEquals(this.getWeeklyMaintenanceTime(), other.getWeeklyMaintenanceTime())
                && Utils.nullableEquals(this.getThroughputMbs(), other.getThroughputMbs())
                && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getStorageGb(), this.getBackupRetentionDays(),
                this.getDailyBackupTime(), this.getWeeklyMaintenanceTime(), this.getThroughputMbs());
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    protected abstract static class Builder extends AbstractFilesystemTierConfig.Builder {
        private Integer storageGb;
        private Integer backupRetentionDays;
        private String dailyBackupTime;
        private String weeklyMaintenanceTime;
        private Integer throughputMbs;

        public Builder storageCapacity(Integer storageCapacity) {
            this.storageGb = storageCapacity;
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
