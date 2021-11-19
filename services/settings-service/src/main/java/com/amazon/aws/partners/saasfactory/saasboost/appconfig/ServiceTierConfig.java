package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ServiceTierConfig.Builder.class)
public class ServiceTierConfig {
    private final Integer minCount;
    private final Integer maxCount;
    private final ComputeSize computeSize;
    private final Integer defaultCpu;
    private final Integer defaultMemory;
    private final String instanceType;
    private final SharedFilesystem filesystem;
    private final Database database;

    private ServiceTierConfig(Builder builder) {
        this.minCount = builder.minCount;
        this.maxCount = builder.maxCount;
        this.computeSize = builder.computeSize;
        this.defaultCpu = builder.defaultCpu;
        this.defaultMemory = builder.defaultMemory;
        this.instanceType = builder.instanceType;
        this.filesystem = builder.filesystem;
        this.database = builder.database;
    }

    public Database getDatabase() {
        return database;
    }

    public boolean hasDatabase() {
        return database != null;
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private Integer minCount;
        private Integer maxCount;
        private ComputeSize computeSize;
        private Integer defaultCpu;
        private Integer defaultMemory;
        private String instanceType;
        private SharedFilesystem filesystem;
        private Database database;

        private Builder() {
        }

        public Builder minCount(String minCount) {
            this.minCount = minCount != null && !minCount.isEmpty() ? Integer.valueOf(minCount) : null;
            return this;
        }

        public Builder maxCount(String maxCount) {
            this.maxCount = maxCount != null && !maxCount.isEmpty() ? Integer.valueOf(maxCount) : null;
            return this;
        }

        public Builder computeSize(String computeSize) {
            if (computeSize != null && !computeSize.isEmpty()) {
                try {
                    this.computeSize = ComputeSize.valueOf(computeSize);
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException(new IllegalArgumentException("Can't find ComputeSize for value " + computeSize));
                }
            }
            return this;
        }

        public Builder defaultCpu(Integer defaultCpu) {
            this.defaultCpu = defaultCpu;
            return this;
        }

        public Builder defaultCpu(String defaultCpu) {
            this.defaultCpu = defaultCpu != null && !defaultCpu.isEmpty() ? Integer.valueOf(defaultCpu) : null;
            return this;
        }

        public Builder defaultMemory(Integer defaultMemory) {
            this.defaultMemory = defaultMemory;
            return this;
        }

        public Builder defaultMemory(String defaultMemory) {
            this.defaultMemory = defaultMemory != null && !defaultMemory.isEmpty() ? Integer.valueOf(defaultMemory) : null;
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
            return new ServiceTierConfig(this);
        }
    }
}
