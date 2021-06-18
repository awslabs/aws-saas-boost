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

import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TenantServiceDALTest {

    private static UUID tenantId;
    private static HashMap<String, String> resources;

    @BeforeClass
    public static void setup() throws Exception {
        tenantId = UUID.fromString("d1c1e3cc-962f-4f03-b4a8-d8a7c1f986c3");

        resources = new HashMap<>();
        resources.put("alb", "http://my.alb2.com");
        resources.put("ecs", "http://new.ecs2.com");
        resources.put("rds", "http://new.rds2.com");
    }

    @Test
    public void testToAttributeValueMap() {
        Tenant tenant = new Tenant();
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime modified = LocalDateTime.now();

        tenant.setId(tenantId);
        tenant.setActive(Boolean.TRUE);
        tenant.setCreated(created);
        tenant.setModified(modified);
        tenant.setName("Test Tenant");
        tenant.setOnboardingStatus("succeeded");
        tenant.setPlanId("Billing Plan");
        tenant.setSubdomain("test-tenant");
        tenant.setResources(resources);
        tenant.setOverrideDefaults(Boolean.FALSE);

        Map<String, AttributeValue> expected = new HashMap<>();
        expected.put("id", AttributeValue.builder().s(tenantId.toString()).build());
        expected.put("active", AttributeValue.builder().bool(Boolean.TRUE).build());
        expected.put("created", AttributeValue.builder().s(created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("modified", AttributeValue.builder().s(modified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("name", AttributeValue.builder().s("Test Tenant").build());
        expected.put("onboarding", AttributeValue.builder().s("succeeded").build());
        expected.put("subdomain", AttributeValue.builder().s("test-tenant").build());
        expected.put("planId", AttributeValue.builder().s("Billing Plan").build());
        expected.put("overrideDefaults", AttributeValue.builder().bool(Boolean.FALSE).build());
        expected.put("resources", AttributeValue.builder().m(resources.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> AttributeValue.builder().s(entry.getValue()).build()
                ))).build());

        Map<String, AttributeValue> actual = TenantServiceDAL.toAttributeValueMap(tenant);

        assertEquals("Size unequal", expected.size(), actual.size());
        expected.keySet().stream().forEach((key) -> {
            assertEquals("Value mismatch for '" + key + "'", expected.get(key), actual.get(key));
        });

        Integer min = 2;
        Integer max = 4;
        tenant.setOverrideDefaults(Boolean.TRUE);
        tenant.setComputeSize("XL");
        tenant.setMinCount(min);
        tenant.setMaxCount(max);

        expected.put("overrideDefaults", AttributeValue.builder().bool(Boolean.TRUE).build());
        expected.put("computeSize", AttributeValue.builder().s("XL").build());
        expected.put("minCount", AttributeValue.builder().n(min.toString()).build());
        expected.put("maxCount", AttributeValue.builder().n(max.toString()).build());

        Map<String, AttributeValue> customized = TenantServiceDAL.toAttributeValueMap(tenant);

        assertEquals("Size unequal", expected.size(), customized.size());
        expected.keySet().stream().forEach((key) -> {
            assertEquals("Value mismatch for '" + key + "'", expected.get(key), customized.get(key));
        });
    }
}
