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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.OperatingSystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.ecs.EcsCompute;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Map;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @Type(value = EcsCompute.class, name = AbstractCompute.ECS)
})
public abstract class AbstractCompute {
    protected static final String ECS = "ECS";

    private final Integer containerPort;
    private final String containerRepo;
    private final String containerTag;
    private final String healthCheckUrl;
    private final OperatingSystem operatingSystem;
    
    protected AbstractCompute(Builder b) {
        this.containerPort = b.containerPort;
        this.containerRepo = b.containerRepo;
        this.containerTag = b.containerTag;
        this.healthCheckUrl = b.healthCheckUrl;
        this.operatingSystem = b.operatingSystem;
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

    public abstract Map<String, ? extends AbstractComputeTier> getTiers();

    protected Builder fillBuilder(Builder b) {
        return b.containerPort(containerPort)
                .containerRepo(containerRepo)
                .containerTag(containerTag)
                .healthCheckUrl(healthCheckUrl)
                .operatingSystem(operatingSystem);
    }

    public abstract Builder builder();

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

        AbstractCompute other = (AbstractCompute) obj;
        return (super.equals(other)
                && Utils.nullableEquals(containerPort, other.containerPort)
                && Utils.nullableEquals(containerRepo, other.containerRepo)
                && Utils.nullableEquals(containerTag, other.containerTag)
                && Utils.nullableEquals(healthCheckUrl, other.healthCheckUrl)
                && Utils.nullableEquals(operatingSystem, other.operatingSystem));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), containerPort, containerRepo, containerTag,
                healthCheckUrl, operatingSystem);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public abstract static class Builder {
        private Integer containerPort;
        private String containerRepo;
        private String containerTag;
        private String healthCheckUrl;
        private OperatingSystem operatingSystem;

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

        public abstract AbstractCompute build();
    }
}   
