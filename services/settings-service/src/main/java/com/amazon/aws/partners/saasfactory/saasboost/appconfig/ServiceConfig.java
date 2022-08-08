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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = ServiceConfig.Builder.class)
public class ServiceConfig {

    @JsonProperty("public")
    private final Boolean publiclyAddressable;
    private final String name;
    private final String description;
    private final String path;
    private final Map<String, ServiceTierConfig> tiers;
    private final Integer containerPort;
    private final String containerRepo;
    private final String containerTag;
    private final String healthCheckUrl;
    private final OperatingSystem operatingSystem;
    private final Database database;
    private final EcsLaunchType ecsLaunchType;

    private ServiceConfig(Builder builder) {
        this.publiclyAddressable = builder.publiclyAddressable;
        this.name = builder.name;
        this.description = builder.description;
        this.path = builder.path;
        this.containerPort = builder.containerPort;
        this.containerRepo = builder.containerRepo;
        this.containerTag = builder.containerTag;
        this.healthCheckUrl = builder.healthCheckUrl;
        this.operatingSystem = builder.operatingSystem;
        this.tiers = builder.tiers;
        this.database = builder.database;
        this.ecsLaunchType = builder.ecsLaunchType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ServiceConfig other) {
        return new Builder()
                .publiclyAddressable(other.isPublic())
                .name(other.getName())
                .description(other.getDescription())
                .path(other.getPath())
                .tiers(other.getTiers())
                .containerPort(other.getContainerPort())
                .containerRepo(other.getContainerRepo())
                .containerTag(other.getContainerTag())
                .healthCheckUrl(other.getHealthCheckUrl())
                .operatingSystem(other.getOperatingSystem())
                .database(other.getDatabase())
                .ecsLaunchType(other.getEcsLaunchType());
    }

    public Boolean isPublic() {
        return publiclyAddressable;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
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

    public String getHealthCheckUrl() {
        return healthCheckUrl;
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public Map<String, ServiceTierConfig> getTiers() {
        return tiers != null ? Map.copyOf(tiers) : null;
    }

    public Database getDatabase() {
        return database;
    }

    public boolean hasDatabase() {
        return database != null;
    }

    public EcsLaunchType getEcsLaunchType() {
        return ecsLaunchType;
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

        final ServiceConfig other = (ServiceConfig) obj;

        boolean tiersEqual = tiers != null && other.tiers != null;
        if (tiersEqual) {
            tiersEqual = tiers.size() == other.tiers.size();
            if (tiersEqual) {
                for (Map.Entry<String, ServiceTierConfig> tier : tiers.entrySet()) {
                    tiersEqual = tier.getValue().equals(other.tiers.get(tier.getKey()));
                    if (!tiersEqual) {
                        break;
                    }
                }
            }
        }

        return Utils.nullableEquals(name, other.name)
            && Utils.nullableEquals(description, other.description)
            && Utils.nullableEquals(path, other.path)
            && Utils.nullableEquals(publiclyAddressable, other.publiclyAddressable)
            && Utils.nullableEquals(containerPort, other.containerPort)
            && Utils.nullableEquals(containerRepo, other.containerRepo)
            && Utils.nullableEquals(containerTag, other.containerTag)
            && Utils.nullableEquals(healthCheckUrl, other.healthCheckUrl)
            && Utils.nullableEquals(operatingSystem, other.operatingSystem)
            && Utils.nullableEquals(tiers, other.tiers) && tiersEqual
            && Utils.nullableEquals(database, other.database)
            && Utils.nullableEquals(ecsLaunchType, other.ecsLaunchType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, path, publiclyAddressable, containerPort, containerRepo, containerTag,
                healthCheckUrl, operatingSystem, database, ecsLaunchType)
                + Arrays.hashCode(tiers != null ? tiers.keySet().toArray(new String[0]) : null)
                + Arrays.hashCode(tiers != null ? tiers.values().toArray(new Object[0]) : null);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        @JsonProperty("public")
        private Boolean publiclyAddressable;
        private String name;
        private String description;
        private String path;
        private Integer containerPort;
        private String containerRepo;
        private String containerTag;
        private String healthCheckUrl;
        private OperatingSystem operatingSystem;
        private Map<String, ServiceTierConfig> tiers = new HashMap<>();
        private Database database;
        private EcsLaunchType ecsLaunchType;

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

        public Builder description(String description) {
            this.description = description;
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
            this.containerPort = Utils.isNotEmpty(containerPort) ? Integer.valueOf(containerPort) : null;
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

        public Builder healthCheckUrl(String healthCheckUrl) {
            this.healthCheckUrl = healthCheckUrl;
            return this;
        }

        public Builder operatingSystem(String operatingSystem) {
            if (operatingSystem != null) {
                try {
                    this.operatingSystem = OperatingSystem.valueOf(operatingSystem);
                } catch (IllegalArgumentException e) {
                    OperatingSystem os = OperatingSystem.ofDescription(operatingSystem);
                    if (os == null) {
                        throw new RuntimeException(
                                new IllegalArgumentException("Can't find OperatingSystem for value " + operatingSystem)
                        );
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
            this.tiers = tiers != null ? tiers : new HashMap<>();
            return this;
        }

        public Builder database(Database database) {
            this.database = database;
            return this;
        }

        public Builder ecsLaunchType(String ecsLaunchType) {
            if (ecsLaunchType != null) {
                try {
                    this.ecsLaunchType = EcsLaunchType.valueOf(ecsLaunchType);
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException(
                        new IllegalArgumentException("Can't find EcsLaunchType for value " + ecsLaunchType)
                    );
                }
            }
            return this;
        }

        public Builder ecsLaunchType(EcsLaunchType ecsLaunchType) {
            this.ecsLaunchType = ecsLaunchType;
            return this;
        }

        public ServiceConfig build() {
            return new ServiceConfig(this);
        }
    }
}
