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

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.AbstractFilesystem;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = ServiceTierConfig.Builder.class)
public class ServiceTierConfig {
    private final Integer min;
    private final Integer max;
    private final ComputeSize computeSize;
    private final Integer cpu;
    private final Integer memory;
    private final String instanceType;
    private final AbstractFilesystem filesystem;

    private ServiceTierConfig(Builder builder) {
        this.min = builder.min;
        this.max = builder.max;
        this.computeSize = builder.computeSize;
        this.cpu = builder.cpu;
        this.memory = builder.memory;
        this.instanceType = builder.instanceType;
        this.filesystem = builder.filesystem;
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
            .filesystem(other.getFilesystem());
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

    public AbstractFilesystem getFilesystem() {
        return filesystem;
    }

    @Override
    public String toString() {
        return Utils.toJson(this);
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
        final ServiceTierConfig other = (ServiceTierConfig) obj;
        return (
                ((min == null && other.min == null) || (min != null && min.equals(other.min)))
                && ((max == null && other.max == null) || (max != null && max.equals(other.max)))
                && ((computeSize == null && other.computeSize == null) || (computeSize == other.computeSize))
                && ((cpu == null && other.cpu == null) || (cpu != null && cpu.equals(other.cpu)))
                && ((memory == null && other.memory == null) || (memory != null && memory.equals(other.memory)))
                && ((instanceType == null && other.instanceType == null)
                    || (instanceType != null && instanceType.equals(other.instanceType)))
                && ((filesystem == null && other.filesystem == null)
                    || (filesystem != null && filesystem.equals(other.filesystem))));
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, computeSize, cpu, memory, instanceType, filesystem);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private Integer min;
        private Integer max;
        private ComputeSize computeSize;
        private Integer cpu;
        private Integer memory;
        private String instanceType;
        private AbstractFilesystem filesystem;

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

        public Builder filesystem(AbstractFilesystem filesystem) {
            this.filesystem = filesystem;
            return this;
        }

        public ServiceTierConfig build() {
            // TODO do validation on cpu/memory/computeSize
            return new ServiceTierConfig(this);
        }
    }
}
