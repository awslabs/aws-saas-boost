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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OnboardingServiceDAL {

    private final static Logger LOGGER = LoggerFactory.getLogger(OnboardingServiceDAL.class);
    private static final String ONBOARDING_TABLE = System.getenv("ONBOARDING_TABLE");
    private static final String CIDR_BLOCK_TABLE = System.getenv("CIDR_BLOCK_TABLE");
    private final DynamoDbClient ddb;

    public OnboardingServiceDAL() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(ONBOARDING_TABLE)) {
            throw new IllegalStateException("Missing required environment variable ONBOARDING_TABLE");
        }
        this.ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(r -> r.tableName(ONBOARDING_TABLE));
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public List<Onboarding> getOnboardings() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::getOnboardings");
        List<Onboarding> onboardings = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request.tableName(ONBOARDING_TABLE));
            response.items().forEach(item ->
                    onboardings.add(fromAttributeValueMap(item))
            );
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::getOnboardings " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingServiceDAL::getOnboardings exec " + totalTimeMillis);
        return onboardings;
    }

    public Onboarding getOnboarding(UUID onboardingId) {
        return getOnboarding(onboardingId.toString());
    }

    public Onboarding getOnboarding(String onboardingId) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::getOnboarding");
        Map<String, AttributeValue> item = null;
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(onboardingId).build());
            GetItemResponse response = ddb.getItem(request -> request.tableName(ONBOARDING_TABLE).key(key));
            item = response.item();
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::getOnboarding " + Utils.getFullStackTrace(e));
            return null;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        Onboarding onboarding = fromAttributeValueMap(item);
        LOGGER.info("OnboardingServiceDAL::getOnboarding exec " + totalTimeMillis);
        return onboarding;
    }

    public Onboarding getOnboardingByTenantId(String tenantId) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::getOnboardingByTenantId");
        Onboarding onboarding = null;
        try {
            final int UUID_LENGTH = 36;
            String filter = null;
            if (tenantId.length() < UUID_LENGTH) {
                filter = "begins_with(tenant_id, :tenantId)";
            } else {
                filter = "tenant_id = :tenantId";
            }
            ScanResponse scan = ddb.scan(ScanRequest.builder()
                    .tableName(ONBOARDING_TABLE)
                    .filterExpression(filter)
                    .expressionAttributeValues(Stream
                            .of(new AbstractMap.SimpleEntry<String, AttributeValue>(":tenantId", AttributeValue.builder().s(tenantId).build()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
                    .build()
            );
            if (1 == scan.items().size()) {
                LOGGER.info("Scanning onboarding for tenant id " + tenantId);
                onboarding = fromAttributeValueMap(scan.items().get(0));
            } else {
                LOGGER.info("Onboarding scan for tenant id " + tenantId + " returned " + scan.items().size() + " results");
            }
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::getOnboardingByTenantId " + Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;

        LOGGER.info("OnboardingServiceDAL::getOnboardingByTenantId exec " + totalTimeMillis);
        return onboarding;
    }

    // Choosing to do a replacement update as you might do in a RDBMS by
    // setting columns = NULL when they do not exist in the updated value
    public Onboarding updateOnboarding(Onboarding onboarding) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::updateOnboarding");
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            onboarding.setModified(LocalDateTime.now());
            Map<String, AttributeValue> item = toAttributeValueMap(onboarding);
            ddb.putItem(request -> request.tableName(ONBOARDING_TABLE).item(item));
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::updateOnboarding " + Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingServiceDAL::updateOnboarding exec " + totalTimeMillis);
        return onboarding;
    }

    public Onboarding updateStatus(String onboardingId, String status) {
        return updateStatus(UUID.fromString(onboardingId), OnboardingStatus.valueOf(status));
    }

    public Onboarding updateStatus(UUID onboardingId, OnboardingStatus status) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::updateStatus");
        Onboarding updated = new Onboarding();
        updated.setId(onboardingId);
        updated.setStatus(status);
        updated.setModified(LocalDateTime.now());
        String modified = updated.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(onboardingId.toString()).build());
            UpdateItemResponse response = ddb.updateItem(request -> request
                    .tableName(ONBOARDING_TABLE)
                    .key(key)
                    .updateExpression("SET #status = :status, modified = :modified")
                    .expressionAttributeNames(Stream
                            .of(new AbstractMap.SimpleEntry<String, String>("#status", "status"))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
                    .expressionAttributeValues(Stream
                            .of(
                                new AbstractMap.SimpleEntry<String, AttributeValue>(":status", AttributeValue.builder().s(status.toString()).build()),
                                new AbstractMap.SimpleEntry<String, AttributeValue>(":modified", AttributeValue.builder().s(modified).build())
                            )
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
                    .returnValues(ReturnValue.ALL_NEW)
            );
            updated = fromAttributeValueMap(response.attributes());
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::updateStatus " + Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingServiceDAL::updateStatus exec " + totalTimeMillis);
        return updated;
    }

    public Onboarding insertOnboarding(Onboarding onboarding) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::insertOnboarding");
        UUID onboardingId = UUID.randomUUID();
        onboarding.setId(onboardingId);
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            LocalDateTime now = LocalDateTime.now();
            onboarding.setCreated(now);
            onboarding.setModified(now);
            Map<String, AttributeValue> item = toAttributeValueMap(onboarding);
            ddb.putItem(request -> request.tableName(ONBOARDING_TABLE).item(item));
            long putItemTimeMillis = System.currentTimeMillis() - startTimeMillis;
            LOGGER.info("OnboardingServiceDAL::insertOnboarding PutItem exec " + putItemTimeMillis);
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::insertOnboarding " + Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingServiceDAL::insertOnboarding exec " + totalTimeMillis);
        return onboarding;
    }

    public String assignCidrBlock(String tenantId) {
        if (Utils.isBlank(CIDR_BLOCK_TABLE)) {
            throw new IllegalStateException("Missing required environment variable CIDR_BLOCK_TABLE");
        }
        String cidrBlock = null;
        try {
            // Do both checks at once
            long scanStartTimeMillis = System.currentTimeMillis();
            boolean cidrBlockAvailable = true;
            List<String> availableCidrBlocks = new ArrayList<>();
            ScanResponse fullScan = ddb.scan(r -> r.tableName(CIDR_BLOCK_TABLE));
            if (!fullScan.items().isEmpty()) {
                for (Map<String, AttributeValue> item : fullScan.items()) {
                    // Make sure we're not trying to assign a CIDR block to a tenant that already has one
                    if (item.containsKey("tenant_id") && tenantId.equals(item.get("tenant_id").s())) {
                        cidrBlockAvailable = false;
                        break;
                    }
                    if (!item.containsKey("tenant_id")) {
                        availableCidrBlocks.add(item.get("cidr_block").s());
                    }
                }
                // Make sure we have an open CIDR block left to assign
                if (availableCidrBlocks.isEmpty()) {
                    cidrBlockAvailable = false;
                }
            }
            if (!cidrBlockAvailable) {
                // We're out of CIDR blocks that we can assign to tenant VPCs
                throw new RuntimeException("No remaining CIDR blocks");
            }
            long scanTotalTimeMillis = System.currentTimeMillis() - scanStartTimeMillis;
            LOGGER.info("OnboardingServiceDAL::assignCidrBlock scan " + scanTotalTimeMillis);

            long updateStartTimeMillis = System.currentTimeMillis();
            String cidr = availableCidrBlocks.get((int) (Math.random() * availableCidrBlocks.size()));
            // Claim this one for this tenant
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("cidr_block", AttributeValue.builder().s(cidr).build());
            UpdateItemResponse update = ddb.updateItem(r -> r
                    .tableName(CIDR_BLOCK_TABLE)
                    .key(key)
                    .updateExpression("SET tenant_id = :tenantId")
                    .expressionAttributeValues(Stream
                            .of(new AbstractMap.SimpleEntry<String, AttributeValue>(":tenantId", AttributeValue.builder().s(tenantId).build()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
                    .conditionExpression("attribute_not_exists(tenant_id)")
                    .returnValues(ReturnValue.ALL_NEW)
            );
            Map<String, AttributeValue> updated = update.attributes();
            cidrBlock = updated.get("cidr_block").s();
            long updateTotalTimeMillis = System.currentTimeMillis() - updateStartTimeMillis;
            LOGGER.info("OnboardingServiceDAL::assignCidrBlock update " + updateTotalTimeMillis);
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::assignCidrBlock " + Utils.getFullStackTrace(e));
            throw e;
        }

        return cidrBlock;
    }

    public static Map<String, AttributeValue> toAttributeValueMap(Onboarding onboarding) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(onboarding.getId().toString()).build());
        if (onboarding.getCreated() != null) {
            item.put("created", AttributeValue.builder().s(onboarding.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (onboarding.getModified() != null) {
            item.put("modified", AttributeValue.builder().s(onboarding.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (onboarding.getStatus() != null) {
            item.put("status", AttributeValue.builder().s(onboarding.getStatus().toString()).build());
        }
        if (onboarding.getTenantId() != null) {
            item.put("tenant_id", AttributeValue.builder().s(onboarding.getTenantId().toString()).build());
        }
        if (Utils.isNotBlank(onboarding.getTenantName())) {
            item.put("tenant_name", AttributeValue.builder().s(onboarding.getTenantName()).build());
        }
        if (Utils.isNotBlank(onboarding.getStackId())) {
            item.put("stack_id", AttributeValue.builder().s(onboarding.getStackId()).build());
        }
        return item;
    }

    public static Onboarding fromAttributeValueMap(Map<String, AttributeValue> item) {
        Onboarding onboarding = null;
        if (item != null && !item.isEmpty()) {
            onboarding = new Onboarding();
            if (item.containsKey("id")) {
                try {
                    onboarding.setId(UUID.fromString(item.get("id").s()));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to parse UUID from database: " + item.get("id").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("status")) {
                try {
                    onboarding.setStatus(OnboardingStatus.valueOf(item.get("status").s()));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to parse status from database: " + item.get("status").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("created")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("created").s(), DateTimeFormatter.ISO_DATE_TIME);
                    onboarding.setCreated(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("created").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("modified")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("modified").s(), DateTimeFormatter.ISO_DATE_TIME);
                    onboarding.setModified(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("modified").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("tenant_id")) {
                try {
                    onboarding.setTenantId(UUID.fromString(item.get("tenant_id").s()));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to parse UUID from database: " + item.get("tenant_id").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("tenant_name")) {
                onboarding.setTenantName(item.get("tenant_name").s());
            }
            if (item.containsKey("stack_id")) {
                onboarding.setStackId(item.get("stack_id").s());
            }
        }
        return onboarding;
    }
}
