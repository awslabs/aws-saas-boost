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

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TierDataAccessLayerTest {

    private static UUID tierId;

    @BeforeAll
    public static void setup() throws Exception {
        tierId = UUID.fromString("c9a437c5-68bc-47ab-a4d5-4e6bbd089914");
    }

    @Test
    public void testToAttributeValueMap() {
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime modified = LocalDateTime.now();

        String tierName = "default";
        String tierDescription = "Default Tier";
        boolean defaultTier = true;
        String bilingPlan = "Free Trial";

        Tier tier = new Tier();
        tier.setId(tierId);
        tier.setCreated(created);
        tier.setModified(modified);
        tier.setName(tierName);
        tier.setDescription(tierDescription);
        tier.setDefaultTier(defaultTier);
        tier.setBillingPlan(bilingPlan);

        Map<String, AttributeValue> expected = new HashMap<>();
        expected.put("id", AttributeValue.builder().s(tierId.toString()).build());
        expected.put("created", AttributeValue.builder().s(
                created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("modified", AttributeValue.builder().s(
                modified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("name", AttributeValue.builder().s(tierName).build());
        expected.put("description", AttributeValue.builder().s(tierDescription).build());
        expected.put("default_tier", AttributeValue.builder().bool(defaultTier).build());
        expected.put("billing_plan", AttributeValue.builder().s(bilingPlan).build());

        Map<String, AttributeValue> actual = TierDataAccessLayer.toAttributeValueMap(tier);

        // DynamoDB marshalling
        assertEquals(expected.size(), actual.size(),
                () -> "Expected size " + expected.size() + " != actual size " + actual.size());
        expected.keySet().stream().forEach(key -> {
            assertEquals(expected.get(key), actual.get(key), () -> "Value mismatch for '" + key + "'");
        });

        // Have we reflected all class properties we serialize for API calls in DynamoDB?
        Map<String, Object> json = Utils.fromJson(Utils.toJson(tier), LinkedHashMap.class);
        json.keySet().stream()
                .map(key -> Utils.toSnakeCase(key))
                .forEach(key -> {
                    assertTrue(actual.containsKey(key),
                            () -> "Class property '" + key + "' does not exist in DynamoDB attribute map");
                });
    }

}