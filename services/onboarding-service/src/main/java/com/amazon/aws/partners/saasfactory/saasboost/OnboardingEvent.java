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
    ONBOARDING_CREATED("Onboarding request created"),
    ONBOARDING_VALID("Onboarding request validated"),
    ONBOARDING_STARTED("Onboarding request started"),
    ONBOARDING_IN_PROGRESS("Onboarding request in progress"),
    ONBOARDING_FAILED("Onboarding request failed"),
    ONBOARDING_COMPLETE("Onboarding request completed"),
    ONBOARDING_STATUS_CHANGE("Onboarding request state changed"),
    ONBOARDING_INVALID("Onboarding request failed validation"),
    ONBOARDING_LIMITS_EXCEEDED("Onboarding request exceeds account quotas"),
    ONBOARDING_LIMITS_OK("Onboarding request does not exceed account quotas"),
    ONBOARDING_CIDR_BLOCK_ASSIGNED("Tenant CIDR block assigned"),
    ONBOARDING_PROVISIONING_STARTED("Tenant base provisioning started"),
    ONBOARDING_PROVISIONING_IN_PROGRESS("Tenant base provisioning in progress"),
    ONBOARDING_PROVISIONING_FAILED("Tenant base provisioning failed"),
    ONBOARDING_PROVISIONING_COMPLETE("Tenant base provisioning completed"),
    ONBOARDING_APP_PROVISIONING_STARTED("Tenant app service provisioning started"),
    ONBOARDING_APP_PROVISIONING_IN_PROGRESS("Tenant app service provisioning in progress"),
    ONBOARDING_APP_PROVISIONING_FAILED("Tenant app service provisioning failed"),
    ONBOARDING_APP_PROVISIONING_COMPLETE("Tenant app service provisioning completed")
    ;

    private final String detailType;

    private OnboardingEvent(String detailType) {
        this.detailType = detailType;
    }
}
