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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class TierDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TierDataAccessLayer.class);
    private final String tiersTable;
    private final DynamoDbClient ddb;

    public TierDataAccessLayer(DynamoDbClient ddb, String tiersTable) {
        final long startTimeMillis = System.currentTimeMillis();
        this.ddb = ddb;
        this.tiersTable = tiersTable;
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(r -> r.tableName(this.tiersTable));
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public List<Tier> getTiers() {
        List<Tier> tiers = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request.tableName(tiersTable));
            response.items().forEach(item ->
                    tiers.add(fromAttributeValueMap(item))
            );
        } catch (DynamoDbException e) {
            LOGGER.error(e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        return tiers;
    }

    public Tier getTier(UUID tierId) {
        return getTier(tierId.toString());
    }

    public Tier getTier(String tierId) {
        Map<String, AttributeValue> item;
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tierId).build());
            GetItemResponse response = ddb.getItem(request -> request.tableName(tiersTable).key(key));
            item = response.item();
        } catch (DynamoDbException e) {
            LOGGER.error(e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        Tier tier = fromAttributeValueMap(item);
        return tier;
    }

    public Tier getTierByName(String name) {
        Map<String, AttributeValue> item = null;
        try {
            ScanResponse scan = ddb.scan(ScanRequest.builder()
                    .tableName(tiersTable)
                    .filterExpression("#name = :name")
                    .expressionAttributeNames(Map.of("#name", "name"))
                    .expressionAttributeValues(
                            Map.of(":name", AttributeValue.builder().s(name).build())
                    )
                    .build()
            );
            if (1 == scan.items().size()) {
                LOGGER.info("Scanning tiers for tier name {}", name);
                item = scan.items().get(0);
            } else {
                LOGGER.error("Tiers scan for name " + name + " returned "
                        + scan.items().size() + " results");
            }
        } catch (DynamoDbException e) {
            LOGGER.error(e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        Tier tier = fromAttributeValueMap(item);
        return tier;
    }

    // Choosing to do a replacement update as you might do in a RDBMS by
    // setting columns = NULL when they do not exist in the updated value
    public Tier updateTier(Tier tier) {
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            tier.setModified(LocalDateTime.now());
            Map<String, AttributeValue> item = toAttributeValueMap(tier);
            ddb.putItem(request -> request.tableName(tiersTable).item(item));
        } catch (DynamoDbException e) {
            LOGGER.error(e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        return tier;
    }

    public Tier insertTier(Tier tier) {
        // Unique identifier is owned by the DAL
        if (tier.getId() != null) {
            throw new IllegalArgumentException("Can't insert a new tier that already has an id");
        }
        UUID tierId = UUID.randomUUID();
        tier.setId(tierId);

        // Created and Modified are owned by the DAL since they reflect when the
        // object was persisted
        LocalDateTime now = LocalDateTime.now();
        tier.setCreated(now);
        tier.setModified(now);
        Map<String, AttributeValue> item = toAttributeValueMap(tier);
        try {
            ddb.putItem(request -> request.tableName(tiersTable).item(item));
        } catch (DynamoDbException e) {
            LOGGER.error(e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        return tier;
    }

    public Tier deleteTier(Tier tier) {
        try {
            ddb.deleteItem(request -> request
                    .tableName(tiersTable)
                    .key(Map.of("id", AttributeValue.builder().s(tier.getId().toString()).build()))
            );
        } catch (DynamoDbException e) {
            LOGGER.error(e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return tier;
    }

    public static Map<String, AttributeValue> toAttributeValueMap(Tier tier) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(tier.getId().toString()).build());
        item.put("default_tier", AttributeValue.builder().bool(tier.isDefaultTier()).build());
        if (tier.getCreated() != null) {
            item.put("created", AttributeValue.builder().s(
                    tier.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (tier.getModified() != null) {
            item.put("modified", AttributeValue.builder().s(
                    tier.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (tier.getName() != null) {
            item.put("name", AttributeValue.builder().s(tier.getName()).build());
        }
        if (tier.getDescription() != null) {
            item.put("description", AttributeValue.builder().s(tier.getDescription()).build());
        }
        if (tier.getBillingPlan() != null) {
            item.put("billing_plan", AttributeValue.builder().s(tier.getBillingPlan()).build());
        }
        return item;
    }

    public static Tier fromAttributeValueMap(Map<String, AttributeValue> item) {
        Tier tier = null;
        if (item != null && !item.isEmpty()) {
            tier = new Tier();
            if (item.containsKey("id")) {
                try {
                    tier.setId(UUID.fromString(item.get("id").s()));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to parse UUID from database: " + item.get("id").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("default_tier")) {
                tier.setDefaultTier(item.get("default_tier").bool());
            }
            if (item.containsKey("created")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("created").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
                    tier.setCreated(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("created").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("modified")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("modified").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
                    tier.setModified(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("modified").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("name")) {
                tier.setName(item.get("name").s());
            }
            if (item.containsKey("description")) {
                tier.setDescription(item.get("description").s());
            }
            if (item.containsKey("billing_plan")) {
                tier.setBillingPlan(item.get("billing_plan").s());
            }
        }
        return tier;
    }
}
