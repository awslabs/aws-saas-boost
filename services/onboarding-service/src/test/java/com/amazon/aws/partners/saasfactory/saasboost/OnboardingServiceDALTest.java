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

import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class OnboardingServiceDALTest {

    private static UUID onboardingId;
    private static UUID tenantId;
    private static List<OnboardingStack> stacks;

    @BeforeClass
    public static void setup() throws Exception {
        onboardingId = UUID.fromString("f11cadd8-9c3c-40be-9106-4d64e2478daf");
        tenantId = UUID.fromString("c9a437c5-68bc-47ab-a4d5-4e6bbd089914");
        stacks = new ArrayList<>();
        stacks.add(OnboardingStack.builder().baseStack(true).name("BaseStack").build());
        stacks.add(OnboardingStack.builder().baseStack(false).name("AppStack").build());
    }
    @Test
    public void testToAttributeValueMap() {
        Onboarding onboarding = new Onboarding();
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime modified = LocalDateTime.now();

        onboarding.setId(onboardingId);
        onboarding.setCreated(created);
        onboarding.setModified(modified);
        onboarding.setStatus(OnboardingStatus.created);
        onboarding.setTenantId(tenantId);
        onboarding.setRequest(new OnboardingRequest("Unit Test", "default"));
        onboarding.setStacks(stacks);
        onboarding.setZipFile("foobar");
        onboarding.setEcsClusterLocked(false);

        Map<String, AttributeValue> expected = new HashMap<>();
        expected.put("id", AttributeValue.builder().s(onboardingId.toString()).build());
        expected.put("created", AttributeValue.builder().s(created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("modified", AttributeValue.builder().s(modified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("status", AttributeValue.builder().s(OnboardingStatus.created.name()).build());
        expected.put("tenant_id", AttributeValue.builder().s(tenantId.toString()).build());
        expected.put("zip_file", AttributeValue.builder().s("foobar").build());
        expected.put("request", AttributeValue.builder().m(Map.of(
                "name", AttributeValue.builder().s("Unit Test").build(),
                "tier", AttributeValue.builder().s("default").build())
        ).build());
        expected.put("stacks", AttributeValue.builder().l(stacks.stream()
                .map(stack -> AttributeValue.builder().m(Map.of(
                        "name", AttributeValue.builder().s(stack.getName()).build(),
                        "baseStack", AttributeValue.builder().bool(stack.isBaseStack()).build()
                )).build())
                .collect(Collectors.toList())
        ).build());
        expected.put("ecs_cluster_locked", AttributeValue.builder().bool(false).build());

        Map<String, AttributeValue> actual = OnboardingServiceDAL.toAttributeValueMap(onboarding);

        // DynamoDB marshalling
        assertEquals("Size unequal", expected.size(), actual.size());
        expected.keySet().stream().forEach(key -> {
            assertEquals("Value mismatch for '" + key + "'", expected.get(key), actual.get(key));
        });

        // Have we reflected all class properties we serialize for API calls in DynamoDB?
        Map<String, Object> json = Utils.fromJson(Utils.toJson(onboarding), LinkedHashMap.class);
        json.keySet().stream()
                .map(key -> Utils.toSnakeCase(key))
                .forEach(key -> {
                    assertTrue("Class property '" + key + "' does not exist in DynamoDB attribute map", actual.containsKey(key));
                });
    }
}
