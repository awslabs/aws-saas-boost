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

import java.util.Map;
import java.util.UUID;

// TODO Make a marker interface of SaaSBoostEvent?
public enum TenantEvent {
    TENANT_ONBOARDING_STATUS_CHANGED("Tenant Onboarding Status Changed"),
    TENANT_RESOURCES_CHANGED("Tenant Resources Changed"),
    TENANT_HOSTNAME_CHANGED("Tenant Hostname Changed"),
    TENANT_TIER_CHANGED("Tenant Tier Changed"),
    TENANT_ENABLED("Tenant Enabled"),
    TENANT_DISABLED("Tenant Disabled"),
    TENANT_DELETED("Tenant Deleted")
    ;

    private final String detailType;

    TenantEvent(String detailType) {
        this.detailType = detailType;
    }

    public String detailType() {
        return detailType;
    }

    public static TenantEvent fromDetailType(String detailType) {
        TenantEvent event = null;
        for (TenantEvent tenantEvent : TenantEvent.values()) {
            if (tenantEvent.detailType().equals(detailType)) {
                event = tenantEvent;
                break;
            }
        }
        return event;
    }

    public static boolean validate(Map<String, Object> event) {
        return validate(event, null);
    }

    public static boolean validate(Map<String, Object> event, String... requiredKeys) {
        if (event == null || !event.containsKey("detail")) {
            return false;
        }
        try {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            if (detail == null || !detail.containsKey("tenantId")) {
                return false;
            }
            try {
                UUID.fromString(String.valueOf(detail.get("tenantId")));
            } catch (IllegalArgumentException iae) {
                return false;
            }
            if (requiredKeys != null) {
                for (String requiredKey : requiredKeys) {
                    if (!detail.containsKey(requiredKey)) {
                        return false;
                    }
                }
            }
        } catch (ClassCastException cce) {
            return false;
        }
        return true;
    }
}
