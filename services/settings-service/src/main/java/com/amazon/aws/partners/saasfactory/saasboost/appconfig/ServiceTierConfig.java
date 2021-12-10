package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ServiceTierConfig.Builder.class)
public class ServiceTierConfig {
    private final Integer min;
    private final Integer max;
    private final Integer cpu;
    private final Integer memory;
    private final String instanceType;
    private final SharedFilesystem filesystem;
    private final Database database;

    private ServiceTierConfig(Builder builder) {
        this.min = builder.min;
        this.max = builder.max;
        this.cpu = builder.cpu;
        this.memory = builder.memory;
        this.instanceType = builder.instanceType;
        this.filesystem = builder.filesystem;
        this.database = builder.database;
    }

    public static ServiceTierConfig.Builder builder() {
        return new ServiceTierConfig.Builder();
    }

    public Integer getMin() {
        return min;
    }

    public Integer getMax() {
        return max;
    }

    public Integer getCpu() {
        return cpu;
    }

    public Integer getMemory() {
        return memory;
    }

    public String getInstanceType() {
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

        public Builder max(String max) {
            this.max = max != null && !max.isEmpty() ? Integer.valueOf(max) : null;
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
            return new ServiceTierConfig(this);
        }
    }
}
