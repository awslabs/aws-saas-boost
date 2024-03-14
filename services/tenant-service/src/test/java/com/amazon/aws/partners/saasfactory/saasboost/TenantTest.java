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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TenantTest {

    @Test
    public void testIsProvisioned() {
        Tenant tenant = new Tenant();
        assertFalse(tenant.isProvisioned(), "Null onboarding status tenants are not provisioned");

        Collection<String> unProvisionedStates = Arrays.asList("created", "validating", "validated",
                "provisioning", "failed", "deleting", "deleted", "uknown");

        for (String onboardingStatus : Tenant.PROVISIONED_STATES) {
            tenant.setOnboardingStatus(onboardingStatus);
            assertTrue(tenant.isProvisioned(), onboardingStatus + " tenants are provisioned");
            assertTrue(Utils.toJson(tenant).contains("\"provisioned\":true"),
                    "Serialized tenant has provisioned property");
        }
        for (String onboardingStatus : unProvisionedStates) {
            tenant.setOnboardingStatus(onboardingStatus);
            assertFalse(tenant.isProvisioned(), onboardingStatus + " tenants are not provisioned");
            assertTrue(Utils.toJson(tenant).contains("\"provisioned\":false"),
                    "Serialized tenant has provisioned property");
        }

        String json = "{\"id\":\"" + UUID.randomUUID() + "\""
                + ", \"active\":true"
                + ", \"name\":\"Unit Test\""
                + ", \"provisioned\":true"
                + "}";
        assertFalse(Utils.fromJson(json, Tenant.class).isProvisioned(),
                "Deserialized tenant doesn't write provisioned");

        json = "{\"id\":\"" + UUID.randomUUID() + "\""
                + ", \"active\":true"
                + ", \"name\":\"Unit Test\""
                + ", \"provisioned\":false"
                + ", \"onboardingStatus\": \"deployed\""
                + "}";
        assertTrue(Utils.fromJson(json, Tenant.class).isProvisioned(),
                "Deserialized tenant doesn't write provisioned");
    }

}
