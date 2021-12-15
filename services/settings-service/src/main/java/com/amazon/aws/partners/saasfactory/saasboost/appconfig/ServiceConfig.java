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
package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = ServiceConfig.Builder.class)
public class ServiceConfig {

    @JsonProperty("public")
    private final Boolean publiclyAddressable;
    private final String name;
    private final String path;
    private final Map<String, ServiceTierConfig> tiers;
    private final Integer containerPort;
    private final String containerRepo;
    private final String containerTag;
    private final String healthCheckURL;
    private final OperatingSystem operatingSystem;

    private ServiceConfig(Builder builder) {
        this.publiclyAddressable = builder.publiclyAddressable;
        this.name = builder.name;
        this.path = builder.path;
        this.containerPort = builder.containerPort;
        this.containerRepo = builder.containerRepo;
        this.containerTag = builder.containerTag;
        this.healthCheckURL = builder.healthCheckURL;
        this.operatingSystem = builder.operatingSystem;
        this.tiers = builder.tiers;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ServiceConfig other) {
        return new Builder()
                .publiclyAddressable(other.isPublic())
                .name(other.getName())
                .path(other.getPath())
                .tiers(other.getTiers())
                .containerPort(other.getContainerPort())
                .containerRepo(other.getContainerRepo())
                .containerTag(other.getContainerTag())
                .healthCheckURL(other.getHealthCheckURL())
                .operatingSystem(other.getOperatingSystem());
    }

    public Boolean isPublic() { return publiclyAddressable; }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    public String getContainerRepo() {
        return containerRepo;
    }

    public String getContainerTag() {
        return containerTag;
    }

    public String getHealthCheckURL() {
        return healthCheckURL;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public Map<String, ServiceTierConfig> getTiers() {
        return tiers;
    }

    @Override
    public boolean equals(Object obj) {
        // TODO POEPPT update equals and hashCode
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
        final ServiceConfig other = (ServiceConfig) obj;
        return (
                ((name == null && other.name == null) || (name != null && name.equals(other.name)))
                        && ((path == null && other.path == null) || (path != null && path.equals(other.path)))
                        && ((containerPort == null && other.containerPort == null) || (containerPort != null && containerPort.equals(other.containerPort)))
                        && ((healthCheckURL == null && other.healthCheckURL == null) || (healthCheckURL != null && healthCheckURL.equals(other.healthCheckURL)))
                        && (operatingSystem == other.operatingSystem)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path, containerPort, healthCheckURL, operatingSystem);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        @JsonProperty("public")
        private Boolean publiclyAddressable;
        private String name;
        private String path;
        private Integer containerPort;
        private String containerRepo;
        private String containerTag;
        private String healthCheckURL;
        private OperatingSystem operatingSystem;
        private Map<String, ServiceTierConfig> tiers;

        private Builder() {
        }

        public Builder publiclyAddressable(Boolean publiclyAddressable) {
            this.publiclyAddressable = publiclyAddressable;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
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

        public Builder containerRepo(String containerRepo) {
            this.containerRepo = containerRepo;
            return this;
        }

        public Builder containerTag(String containerTag) {
            this.containerTag = containerTag;
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

        public Builder operatingSystem(OperatingSystem operatingSystem) {
            this.operatingSystem = operatingSystem;
            return this;
        }

        public Builder tiers(Map<String, ServiceTierConfig> tiers) {
            this.tiers = tiers;
            return this;
        }

        public ServiceConfig build() {
            return new ServiceConfig(this);
        }
    }
}
