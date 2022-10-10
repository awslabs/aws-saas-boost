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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonDeserialize(builder = AuthorizerResponse.Builder.class)
public class AuthorizerResponse {

    private final String principalId;
    private final PolicyDocument policyDocument;
    private final Map<String, String> context;

    private AuthorizerResponse(Builder builder) {
        this.principalId = builder.principalId;
        this.policyDocument = builder.policyDocument;
        this.context = builder.context;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public PolicyDocument getPolicyDocument() {
        return policyDocument;
    }

    public Map<String, String> getContext() {
        return context != null ? Map.copyOf(context) : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String principalId;
        private PolicyDocument policyDocument = PolicyDocument.builder().build();
        private Map<String, String> context = new HashMap<>();

        private Builder() {
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder policyDocument(PolicyDocument policyDocument) {
            this.policyDocument = policyDocument != null ? policyDocument : PolicyDocument.builder().build();
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context != null ? context : new HashMap<>();
            return this;
        }

        public AuthorizerResponse build() {
            return new AuthorizerResponse(this);
        }
    }
}
