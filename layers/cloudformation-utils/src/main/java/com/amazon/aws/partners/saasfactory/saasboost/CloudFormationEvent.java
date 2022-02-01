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

package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.LinkedHashMap;
import java.util.Map;

public class CloudFormationEvent {

    private final String stackId;
    private final String timestamp;
    private final String eventId;
    private final String logicalResourceId;
    private final String namespace;
    private final String physicalResourceId;
    private final String principalId;
    private final Map<String, Object> resourceProperties;
    private final String resourceStatus;
    private final String resourceStatusReason;
    private final String resourceType;
    private final String stackName;
    private final String clientRequestToken;

    private CloudFormationEvent(Builder builder) {
        this.stackId = builder.stackId;
        this.timestamp = builder.timestamp;
        this.eventId = builder.eventId;
        this.logicalResourceId = builder.logicalResourceId;
        this.namespace = builder.namespace;
        this.physicalResourceId = builder.physicalResourceId;
        this.principalId = builder.principalId;
        this.resourceProperties = builder.resourceProperties;
        this.resourceStatus = builder.resourceStatus;
        this.resourceStatusReason = builder.resourceStatusReason;
        this.resourceType = builder.resourceType;
        this.stackName = builder.stackName;
        this.clientRequestToken = builder.clientRequestToken;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getStackId() {
        return stackId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public String getLogicalResourceId() {
        return logicalResourceId;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPhysicalResourceId() {
        return physicalResourceId;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public Map<String, Object> getResourceProperties() {
        return resourceProperties != null ? Map.copyOf(resourceProperties) : null;
    }

    public String getResourceStatus() {
        return resourceStatus;
    }

    public String getResourceStatusReason() {
        return resourceStatusReason;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getStackName() {
        return stackName;
    }

    public String getClientRequestToken() {
        return clientRequestToken;
    }

    public static final class Builder {
        private String stackId;
        private String timestamp;
        private String eventId;
        private String logicalResourceId;
        private String namespace;
        private String physicalResourceId;
        private String principalId;
        private Map<String, Object> resourceProperties;
        private String resourceStatus;
        private String resourceStatusReason;
        private String resourceType;
        private String stackName;
        private String clientRequestToken;

        private Builder() {
            this.resourceProperties = new LinkedHashMap<>();
        }

        public Builder stackId(String stackId) {
            this.stackId = stackId;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder logicalResourceId(String logicalResourceId) {
            this.logicalResourceId = logicalResourceId;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder physicalResourceId(String physicalResourceId) {
            this.physicalResourceId = physicalResourceId;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder resourceProperties(Map<String, Object> resourceProperties) {
            this.resourceProperties = resourceProperties != null ? resourceProperties : new LinkedHashMap<>();
            return this;
        }

        public Builder resourceStatus(String resourceStatus) {
            this.resourceStatus = resourceStatus;
            return this;
        }

        public Builder resourceStatusReason(String resourceStatusReason) {
            this.resourceStatusReason = resourceStatusReason;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder stackName(String stackName) {
            this.stackName = stackName;
            return this;
        }

        public Builder clientRequestToken(String clientRequestToken) {
            this.clientRequestToken = clientRequestToken;
            return this;
        }

        public CloudFormationEvent build() {
            return new CloudFormationEvent(this);
        }
    }
}
