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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.*;

public class TenantTest {

    @Test
    public void testIsProvisioned() {
        Tenant tenant = new Tenant();
        assertFalse("Null onboarding status tenants are not provisioned", tenant.isProvisioned());

        Collection<String> provisionedStates = Arrays.asList("created", "validating", "validated", "provisioning",
                "provisioned", "updating", "updated", "deploying", "deployed");
        Collection<String> unProvisionedStates = Arrays.asList("failed", "deleting", "deleted");

        for (String onboardingStatus : provisionedStates) {
            tenant.setOnboardingStatus(onboardingStatus);
            assertTrue(onboardingStatus + " tenants are provisioned", tenant.isProvisioned());
            assertTrue("Serialized tenant has provisioned property",
                    Utils.toJson(tenant).contains("\"provisioned\":true"));
        }
        for (String onboardingStatus : unProvisionedStates) {
            tenant.setOnboardingStatus(onboardingStatus);
            assertFalse(onboardingStatus + " tenants are not provisioned", tenant.isProvisioned());
            assertTrue("Serialized tenant has provisioned property",
                    Utils.toJson(tenant).contains("\"provisioned\":false"));
        }

        String json = "{\"id\":\"" + UUID.randomUUID() + "\""
                + ", \"active\":true"
                + ", \"name\":\"Unit Test\""
                + ", \"provisioned\":true"
                + "}";
        assertFalse("Deserialized tenant doesn't write provisioned",
                Utils.fromJson(json, Tenant.class).isProvisioned());

        json = "{\"id\":\"" + UUID.randomUUID() + "\""
                + ", \"active\":true"
                + ", \"name\":\"Unit Test\""
                + ", \"provisioned\":false"
                + ", \"onboardingStatus\": \"deployed\""
                + "}";
        assertTrue("Deserialized tenant doesn't write provisioned",
                Utils.fromJson(json, Tenant.class).isProvisioned());
    }

}
