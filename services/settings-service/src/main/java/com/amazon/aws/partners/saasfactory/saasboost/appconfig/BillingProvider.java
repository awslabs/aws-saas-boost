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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = BillingProvider.Builder.class)
public class BillingProvider {

    private String apiKey;

    private BillingProvider(Builder builder) {
        this.apiKey = builder.apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean hasApiKey() {
        return getApiKey() != null && !getApiKey().isEmpty();
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
        final BillingProvider other = (BillingProvider) obj;
        return (
                (apiKey == null && other.apiKey == null) || (apiKey != null && apiKey.equals(other.apiKey))
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String apiKey;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public BillingProvider build() {
            return new BillingProvider(this);
        }
    }
}
