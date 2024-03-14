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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

// TODO Make a marker interface of SaaSBoostEvent?
public enum OnboardingEvent {
    ONBOARDING_INITIATED("Onboarding Initiated"), // Produce
    ONBOARDING_VALIDATED("Onboarding Validated"), // Consume
    ONBOARDING_TENANT_ASSIGNED("Onboarding Tenant Assigned"), // Produce
    ONBOARDING_PROVISIONING("Onboarding Provisioning"), // Consume (optional)
    ONBOARDING_PROVISIONED("Onboarding Provisioned"), // Consume (optional)
    ONBOARDING_DEPLOYING("Onboarding Deploying"), // Consume (optional)
    ONBOARDING_DEPLOYED("Onboarding Deployed"), // Consume
    ONBOARDING_APPLICATION_READY("Onboarding Application Ready"), // Consume
    ONBOARDING_COMPLETED("Onboarding Completed"), // Produce
    ONBOARDING_FAILED("Onboarding Failed") // Produce/Consume
    ;

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingEvent.class);
    private final String detailType;

    OnboardingEvent(String detailType) {
        this.detailType = detailType;
    }

    public String detailType() {
        return detailType;
    }

    public static OnboardingEvent fromDetailType(String detailType) {
        OnboardingEvent event = null;
        for (OnboardingEvent onboardingEvent : OnboardingEvent.values()) {
            if (onboardingEvent.detailType().equals(detailType)) {
                event = onboardingEvent;
                break;
            }
        }
        return event;
    }

    public static boolean validate(Map<String, Object> event) {
        return validate(event, null);
    }

    public static boolean validate(Map<String, Object> event, String... requiredKeys) {
        if (event == null || !event.containsKey("detail") || !event.containsKey("source")) {
            LOGGER.error("Event is null or is missing 'detail' or 'source' attributes");
            return false;
        }
        if (!"saas-boost".equals(event.get("source"))) {
            LOGGER.error("Event 'source' != saas-boost");
            return false;
        }
        try {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            if (detail == null) {
                LOGGER.error("Event is missing 'detail'");
                return false;
            }
            if (detail.containsKey("onboardingId")) {
                try {
                    UUID.fromString(String.valueOf(detail.get("onboardingId")));
                } catch (IllegalArgumentException iae) {
                    LOGGER.error("Event onboardingId can't be parsed to UUID");
                    return false;
                }
            }
            if (requiredKeys != null) {
                for (String requiredKey : requiredKeys) {
                    if (!detail.containsKey(requiredKey)) {
                        LOGGER.error("Event 'detail' is missing required key '" + requiredKey + "'");
                        return false;
                    }
                }
            }
        } catch (ClassCastException cce) {
            LOGGER.error("Event detail is not a Map " + cce.getMessage());
            return false;
        }
        return true;
    }
}
