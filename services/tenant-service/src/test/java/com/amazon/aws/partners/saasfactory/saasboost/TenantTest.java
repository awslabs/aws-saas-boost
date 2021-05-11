/**
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

import static org.junit.Assert.*;

public class TenantTest {

    @Test
    public void testIsProvisioned() {
        Tenant tenant = new Tenant();
        assertFalse("Null onboarding status tenants are not provisioned", tenant.isProvisioned());

        tenant.setOnboardingStatus("created");
        assertFalse("Created tenants are not provisioned", tenant.isProvisioned());

        tenant.setOnboardingStatus("failed");
        assertFalse("Failed tenants are not provisioned", tenant.isProvisioned());

        tenant.setOnboardingStatus("succeeded");
        assertTrue("Succeeded tenants are provisioned", tenant.isProvisioned());

        String json = Utils.toJson(tenant);
        assertTrue("Serialized tenant has provisioned property", json.indexOf("\"provisioned\":true") != -1);

        Tenant deserialized = Utils.fromJson(json, Tenant.class);
        assertTrue("Deserialized tenant is provisioned", deserialized.isProvisioned());
    }
}
