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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = DatabaseTierConfig.Builder.class)
public final class DatabaseTierConfig {

    private final RdsInstance instance;

    public DatabaseTierConfig(Builder builder) {
        this.instance = builder.instance;
    }

    public String getInstance() {
        return instance != null ? instance.name() : null;
    }

    public String getInstanceClass() {
        return instance != null ? instance.getInstanceClass() : null;
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
        final DatabaseTierConfig other = (DatabaseTierConfig) obj;
        return (instance == other.instance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(DatabaseTierConfig other) {
        return new Builder().instance(other.getInstance());
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    @JsonIgnoreProperties(value = {"instanceClass"})
    public static final class Builder {

        private RdsInstance instance;

        private Builder() {

        }

        public Builder instance(RdsInstance instance) {
            this.instance = instance;
            return this;
        }

        public Builder instance(String instance) {
            try {
                this.instance = RdsInstance.valueOf(instance);
            } catch (IllegalArgumentException e) {
                this.instance = RdsInstance.ofInstanceClass(instance);
            }
            return this;
        }

        public DatabaseTierConfig build() {
            return new DatabaseTierConfig(this);
        }
    }

    enum RdsInstance {
        T3_MICRO("db.t3.micro", "2 vCPUs 1 GiB RAM"),
        T3_SMALL("db.t3.small", "2 vCPUs 2 GiB RAM"),
        T3_MEDIUM("db.t3.medium", "2 vCPUs 4 GiB RAM"),
        T3_LARGE("db.t3.large", "2 vCPUs 8 GiB RAM"),
        T3_XL("db.t3.xlarge", "4 vCPUs 16 GiB RAM"),
        T3_2XL("db.t3.2xlarge", "8 vCPUs 32 GiB RAM"),
        M5_LARGE("db.m5.large", "2 vCPUs 8 GiB RAM"),
        M5_XL("db.m5.xlarge", "4 vCPUs 16 GiB RAM"),
        M5_2XL("db.m5.2xlarge", "8 vCPUs 32 GiB RAM"),
        M5_4XL("db.m5.4xlarge", "16 vCPUs 64 GiB RAM"),
        M5_12XL("db.m5.12xlarge", "48 vCPUs 192 GiB RAM"),
        M5_24XL("db.m5.24xlarge", "96 vCPUs 384 GiB RAM"),
        R5_LARGE("db.r5.large", "2 vCPUs 16 GiB RAM"),
        R5_XL("db.r5.xlarge", "4 vCPUs 32 GiB RAM"),
        R5_2XL("db.r5.2xlarge", "8 vCPUs 64 GiB RAM"),
        R5_4XL("db.r5.4xlarge", "16 vCPUs 128 GiB RAM"),
        R5_12XL("db.r5.12xlarge", "48 vCPUs 384 GiB RAM"),
        R5_24XL("db.r5.24xlarge", "96 vCPUs 768 GiB RAM");

        private final String instanceClass;
        private final String description;

        RdsInstance(String name, String description) {
            this.instanceClass = name;
            this.description = description;
        }

        public String getInstanceClass() {
            return instanceClass;
        }

        public String getDescription() {
            return description;
        }

        public static RdsInstance ofInstanceClass(String instanceClass) {
            RdsInstance instance = null;
            for (RdsInstance ec2 : RdsInstance.values()) {
                if (ec2.getInstanceClass().equals(instanceClass)) {
                    instance = ec2;
                    break;
                }
            }
            return instance;
        }
    }
}
