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

public final class RequestContext {
    public final String requestType;
    public final String ecsCluster;
    public final String onboardingDdbTable;
    public final String capacityProvider;
    public final String tenantId;

    private RequestContext(Builder b) {
        this.requestType = b.requestType;
        this.ecsCluster = b.ecsCluster;
        this.onboardingDdbTable = b.onboardingDdbTable;
        this.capacityProvider = b.capacityProvider;
        this.tenantId = b.tenantId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(RequestContext requestContext) {
        return new Builder()
                .requestType(requestContext.requestType)
                .ecsCluster(requestContext.ecsCluster)
                .onboardingDdbTable(requestContext.onboardingDdbTable)
                .capacityProvider(requestContext.capacityProvider)
                .tenantId(requestContext.tenantId);
    }

    public static class Builder {
        private String requestType;
        private String ecsCluster;
        private String onboardingDdbTable;
        private String capacityProvider;
        private String tenantId;

        public Builder requestType(String requestType) {
            this.requestType = requestType;
            return this;
        }

        public Builder ecsCluster(String ecsCluster) {
            this.ecsCluster = ecsCluster;
            return this;
        }

        public Builder onboardingDdbTable(String onboardingDdbTable) {
            this.onboardingDdbTable = onboardingDdbTable;
            return this;
        }

        public Builder capacityProvider(String capacityProvider) {
            this.capacityProvider = capacityProvider;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }
}
