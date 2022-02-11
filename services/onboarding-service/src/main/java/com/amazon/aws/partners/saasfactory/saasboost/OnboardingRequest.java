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

    @JsonCreator
    public OnboardingRequest(@JsonProperty("name") String name) {
        this(name, "default");
    }

    @JsonCreator
    public OnboardingRequest(@JsonProperty("name") String name, @JsonProperty("tier") String tier) {
        this(name, tier, null);
    }

    @JsonCreator
    public OnboardingRequest(@JsonProperty("name") String name, @JsonProperty("tier") String tier,
                             @JsonProperty("subdomain") String subdomain) {
        this.name = name;
        this.tier = tier;
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
