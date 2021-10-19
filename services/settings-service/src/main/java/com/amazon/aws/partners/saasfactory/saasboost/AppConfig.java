/**
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
package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = AppConfig.Builder.class)
public class AppConfig {

    private String name;
    private String domainName;
    private String sslCertArn;
    private Integer minCount;
    private Integer maxCount;
    private ComputeSize computeSize;
    private Integer defaultCpu;
    private Integer defaultMemory;
    private Integer containerPort;
    private String healthCheckURL;
    private OperatingSystem operatingSystem;
    private String instanceType;
    private SharedFilesystem filesystem;
    private Database database;
    private BillingProvider billing;

    private AppConfig(Builder builder) {
        this.name = builder.name;
        this.domainName = builder.domainName;
        this.sslCertArn = builder.sslCertArn;
        this.minCount = builder.minCount;
        this.maxCount = builder.maxCount;
        this.computeSize = builder.computeSize;
        this.defaultCpu = builder.defaultCpu;
        this.defaultMemory = builder.defaultMemory;
        this.containerPort = builder.containerPort;
        this.healthCheckURL = builder.healthCheckURL;
        this.operatingSystem = builder.operatingSystem;
        this.instanceType = builder.instanceType;
        this.filesystem = builder.filesystem;
        this.database = builder.database;
        this.billing = builder.billing;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getSslCertArn() {
        return sslCertArn;
    }

    public Integer getMinCount() {
        return minCount;
    }

    public Integer getMaxCount() {
        return maxCount;
    }

    public ComputeSize getComputeSize() {
        return computeSize;
    }

    public Integer getDefaultCpu() {
        Integer cpu = null;
        // Avoid weird NPE while using the ternary operator (probably some autoboxing issue)...
        if (getComputeSize() != null) {
            cpu = getComputeSize().getCpu();
        } else {
            cpu = defaultCpu;
        }
        return cpu;
    }

    public Integer getDefaultMemory() {
        Integer memory = null;
        // Avoid weird NPE while using the ternary operator (probably some autoboxing issue)...
        if (getComputeSize() != null) {
            memory = getComputeSize().getMemory();
        } else {
            memory = defaultMemory;
        }
        return memory;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public String getHealthCheckURL() {
        return healthCheckURL;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public String getInstanceType() {
        return computeSize != null ? computeSize.getInstanceType() : instanceType;
    }

    public SharedFilesystem getFilesystem() {
        return filesystem;
    }

    public BillingProvider getBilling() {
        return billing;
    }

    public Database getDatabase() {
        return database;
    }

    public boolean hasFilesystem() {
        return getFilesystem() != null;
    }

    public boolean hasDatabase() {
        return getDatabase() != null;
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
        final AppConfig other = (AppConfig) obj;
        return (
                ((name == null && other.name == null) || (name != null && name.equals(other.name)))
                && ((domainName == null && other.domainName == null) || (domainName != null && domainName.equals(other.domainName)))
                && ((sslCertArn == null && other.sslCertArn == null) || (sslCertArn != null && sslCertArn.equals(other.sslCertArn)))
                && ((minCount == null && other.minCount == null) || (minCount != null && minCount.equals(other.minCount)))
                && ((maxCount == null && other.maxCount == null) || (maxCount != null && maxCount.equals(other.maxCount)))
                && (computeSize == other.computeSize)
                && ((defaultCpu == null && other.defaultCpu == null) || (defaultCpu != null && defaultCpu.equals(other.defaultCpu)))
                && ((defaultMemory == null && other.defaultMemory == null) || (defaultMemory != null && defaultMemory.equals(other.defaultMemory)))
                && ((containerPort == null && other.containerPort == null) || (containerPort != null && containerPort.equals(other.containerPort)))
                && ((healthCheckURL == null && other.healthCheckURL == null) || (healthCheckURL != null && healthCheckURL.equals(other.healthCheckURL)))
                && (operatingSystem == other.operatingSystem)
                && ((instanceType == null && other.instanceType == null) || (instanceType != null && instanceType.equals(other.instanceType)))
                && ((filesystem == null && other.filesystem == null) || (filesystem != null && filesystem.equals(other.filesystem)))
                && ((database == null && other.database == null) || (database != null && database.equals(other.database)))
                && ((billing == null && other.billing == null) || (billing != null && billing.equals(other.billing)))
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, domainName, sslCertArn, minCount, maxCount, computeSize, defaultCpu, defaultMemory, containerPort,
                healthCheckURL, operatingSystem, instanceType, filesystem, database, billing);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String name;
        private String domainName;
        private String sslCertArn;
        private Integer minCount;
        private Integer maxCount;
        private ComputeSize computeSize;
        private Integer defaultCpu;
        private Integer defaultMemory;
        private Integer containerPort;
        private String healthCheckURL;
        private OperatingSystem operatingSystem;
        private String instanceType;
        private SharedFilesystem filesystem;
        private Database database;
        private BillingProvider billing;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder domainName(String domainName) {
            this.domainName = domainName;
            return this;
        }

        public Builder sslCertArn(String sslCertArn) {
            this.sslCertArn = sslCertArn;
            return this;
        }

        public Builder minCount(Integer minCount) {
            this.minCount = minCount;
            return this;
        }

        public Builder minCount(String minCount) {
            this.minCount = minCount != null && !minCount.isEmpty() ? Integer.valueOf(minCount) : null;
            return this;
        }

        public Builder maxCount(Integer maxCount) {
            this.maxCount = maxCount;
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

        public Builder containerPort(Integer containerPort) {
            this.containerPort = containerPort;
            return this;
        }

        public Builder containerPort(String containerPort) {
            this.containerPort = containerPort != null && !containerPort.isEmpty() ? Integer.valueOf(containerPort) : null;
            return this;
        }

        public Builder healthCheckURL(String healthCheckURL) {
            this.healthCheckURL = healthCheckURL;
            return this;
        }

        public Builder operatingSystem(String operatingSystem) {
            if (operatingSystem != null) {
                try {
                    this.operatingSystem = OperatingSystem.valueOf(operatingSystem);
                } catch (IllegalArgumentException e) {
                    OperatingSystem os = OperatingSystem.ofDescription(operatingSystem);
                    if (os == null) {
                        throw new RuntimeException(new IllegalArgumentException("Can't find OperatingSystem for value " + operatingSystem));
                    }
                    this.operatingSystem = os;
                }
            }
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

        public Builder billing(BillingProvider billing) {
            this.billing = billing;
            return this;
        }

        public AppConfig build() {
            return new AppConfig(this);
        }
    }
}
