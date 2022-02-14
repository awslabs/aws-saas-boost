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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

public class OnboardingRequest {

    private String name;
    private String tier;
    private String subdomain;
    private String billingPlan;
    private Map<String, String> attributes = new LinkedHashMap<>();

    public OnboardingRequest(String name) {
        this(name, "default");
    }

    public OnboardingRequest(String name, String tier) {
        this(name, tier, null);
    }

    @JsonCreator
    public OnboardingRequest(@JsonProperty("name") String name, @JsonProperty("tier") String tier,
                             @JsonProperty("subdomain") String subdomain) {
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = name;
        this.tier = tier != null ? tier : "default";
        this.subdomain = subdomain;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getBillingPlan() {
        return billingPlan;
    }

    public void setBillingPlan(String billingPlan) {
        this.billingPlan = billingPlan;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? attributes : new LinkedHashMap<>();
    }
}
