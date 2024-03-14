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

import java.util.*;

@JsonDeserialize(builder = OnboardingRequest.Builder.class)
public class OnboardingRequest {

    private final String name;
    private final String tier;
    private final String subdomain;
    private final Map<String, String> attributes;
    private final Set<Map<String, Object>> adminUsers;

    private OnboardingRequest(Builder builder) {
        this.name = builder.name;
        this.tier = builder.tier;
        this.subdomain = builder.subdomain;
        this.attributes = builder.attributes;
        this.adminUsers = builder.adminUsers;
    }

    public String getName() {
        return name;
    }

    public String getTier() {
        return tier;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public Map<String, String> getAttributes() {
        return Map.copyOf(attributes);
    }

    public Set<Map<String, Object>> getAdminUsers() {
        return Set.copyOf(adminUsers);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private String name;
        private String tier;
        private String subdomain;
        private Map<String, String> attributes = new LinkedHashMap<>();
        private Set<Map<String, Object>> adminUsers = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder tier(String tier) {
            this.tier = tier;
            return this;
        }

        public Builder subdomain(String subdomain) {
            this.subdomain = subdomain;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public Builder adminUsers(Collection<Map<String, Object>> adminUsers) {
            if (adminUsers != null) {
                this.adminUsers.addAll(adminUsers);
            }
            return this;
        }

        public OnboardingRequest build() {
            return new OnboardingRequest(this);
        }
    }
}
