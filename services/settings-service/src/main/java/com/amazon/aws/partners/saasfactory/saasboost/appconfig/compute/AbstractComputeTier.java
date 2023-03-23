package com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

public abstract class AbstractComputeTier {
    private final Integer min;
    private final Integer max;
    private final ComputeSize computeSize;
    private final Integer cpu;
    private final Integer memory;
    private final String instanceType;
    private final Integer ec2min;
    private final Integer ec2max;

    protected AbstractComputeTier(Builder builder) {
        this.min = builder.min;
        this.max = builder.max;
        this.computeSize = builder.computeSize;
        this.cpu = builder.cpu;
        this.memory = builder.memory;
        this.instanceType = builder.instanceType;
        this.ec2min = builder.ec2min;
        this.ec2max = builder.ec2max;
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

    public Integer getEc2min() {
        return ec2min;
    }

    public Integer getEc2max() {
        return ec2max;
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
        final AbstractComputeTier other = (AbstractComputeTier) obj;
        return Utils.nullableEquals(min, other.min)
            && Utils.nullableEquals(max, other.max)
            && Utils.nullableEquals(computeSize, other.computeSize)
            && Utils.nullableEquals(cpu, other.cpu)
            && Utils.nullableEquals(memory, other.memory)
            && Utils.nullableEquals(instanceType, other.instanceType)
            && Utils.nullableEquals(ec2min, other.ec2min)
            && Utils.nullableEquals(ec2max, other.ec2max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, computeSize, cpu, memory, instanceType, ec2min, ec2max);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public abstract static class Builder {
        // TODO do validation on cpu/memory/computeSize
        private Integer min;
        private Integer max;
        private ComputeSize computeSize;
        private Integer cpu;
        private Integer memory;
        private String instanceType;
        private Integer ec2min;
        private Integer ec2max;

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

        public Builder ec2min(Integer ec2min) {
            this.ec2min = ec2min;
            return this;
        }

        public Builder ec2max(Integer ec2max) {
            this.ec2max = ec2max;
            return this;
        }

        public abstract AbstractComputeTier build();
    }
}
