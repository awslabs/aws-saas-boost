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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ServiceTierConfig.Builder.class)
public class ServiceTierConfig {
    private final Integer min;
    private final Integer max;
    private final ComputeSize computeSize;
    private final Integer cpu;
    private final Integer memory;
    private final String instanceType;
    private final SharedFilesystem filesystem;
    private final Database database;

    private ServiceTierConfig(Builder builder) {
        this.min = builder.min;
        this.max = builder.max;
        this.computeSize = builder.computeSize;
        this.cpu = builder.cpu;
        this.memory = builder.memory;
        this.instanceType = builder.instanceType;
        this.filesystem = builder.filesystem;
        this.database = builder.database;
    }

    public static ServiceTierConfig.Builder builder() {
        return new ServiceTierConfig.Builder();
    }

    public static ServiceTierConfig.Builder builder(ServiceTierConfig other) {
        return new Builder()
            .min(other.getMin())
            .max(other.getMax())
            .computeSize(other.getComputeSize())
            .cpu(other.getCpu())
            .memory(other.getMemory())
            .instanceType(other.getInstanceType())
            .filesystem(other.getFilesystem())
            .database(other.getDatabase());
    }

    public Integer getMin() {
        return min;
    }

    public Integer getMax() {
        return max;
    }

    public ComputeSize getComputeSize() {
        return computeSize;
    }

    public Integer getCpu() {
        if (getComputeSize() != null) {
            return getComputeSize().getCpu();
        }
        return cpu;
    }

    public Integer getMemory() {
        if (getComputeSize() != null) {
            return getComputeSize().getMemory();
        }
        return memory;
    }

    public String getInstanceType() {
        if (getComputeSize() != null) {
            return getComputeSize().getInstanceType();
        }
        return instanceType;
    }

    public SharedFilesystem getFilesystem() {
        return filesystem;
    }

    public Database getDatabase() {
        return database;
    }

    public boolean hasDatabase() {
        return database != null;
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private Integer min;
        private Integer max;
        private ComputeSize computeSize;
        private Integer cpu;
        private Integer memory;
        private String instanceType;
        private SharedFilesystem filesystem;
        private Database database;

        private Builder() {
        }

        public Builder min(String min) {
            this.min = min != null && !min.isEmpty() ? Integer.valueOf(min) : null;
            return this;
        }

        public Builder min(Integer min) {
            this.min = min;
            return this;
        }

        public Builder max(String max) {
            this.max = max != null && !max.isEmpty() ? Integer.valueOf(max) : null;
            return this;
        }

        public Builder max(Integer max) {
            this.max = max;
            return this;
        }

        public Builder computeSize(String computeSize) {
            if (computeSize != null && !computeSize.isEmpty()) {
                try {
                    this.computeSize = ComputeSize.valueOf(computeSize);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Can't find ComputeSize for value " + computeSize);
                }
            }
            return this;
        }

        public Builder computeSize(ComputeSize computeSize) {
            this.computeSize = computeSize;
            return this;
        }

        public Builder cpu(Integer cpu) {
            this.cpu = cpu;
            return this;
        }

        public Builder cpu(String cpu) {
            this.cpu = cpu != null && !cpu.isEmpty() ? Integer.valueOf(cpu) : null;
            return this;
        }

        public Builder memory(Integer memory) {
            this.memory = memory;
            return this;
        }

        public Builder memory(String memory) {
            this.memory = memory != null && !memory.isEmpty() ? Integer.valueOf(memory) : null;
            return this;
        }

        public Builder instanceType(String instanceType) {
            this.instanceType = instanceType;
            return this;
        }

        public Builder filesystem(SharedFilesystem filesystem) {
            this.filesystem = filesystem;
            return this;
        }

        public Builder database(Database database) {
            this.database = database;
            return this;
        }

        public ServiceTierConfig build() {
            // TODO do validation on cpu/memory/computeSize
            return new ServiceTierConfig(this);
        }
    }
}
