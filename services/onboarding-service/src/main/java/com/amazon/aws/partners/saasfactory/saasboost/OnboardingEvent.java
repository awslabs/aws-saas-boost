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

// TODO Make a marker interface of SaaSBoostEvent?
public enum OnboardingEvent {
    ONBOARDING_INITIATED("Onboarding Initiated"),
    ONBOARDING_VALID("Onboarding Validated"),
    ONBOARDING_TENANT_ASSIGNED("Onboarding Tenant Assigned"),
    ONBOARDING_STACK_STATUS_CHANGED("Onboarding Stack Status Changed"),
    ONBOARDING_BASE_PROVISIONED("Onboarding Base Provisioned"),
    ONBOARDING_PROVISIONED("Onboarding Provisioned"),
    ONBOARDING_DEPLOYMENT_PIPELINE_CHANGED("Onboarding Deployment Pipeline Change"),
    ONBOARDING_DEPLOYED("Onboarding Deployed"),
    ONBOARDING_COMPLETED("Onboarding Completed"),
    ONBOARDING_FAILED("Onboarding Failed")
    ;

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
}
