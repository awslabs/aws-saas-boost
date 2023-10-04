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
import java.util.stream.Collectors;

public class OnboardingDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingDataAccessLayer.class);
    private final String onboardingTable;
    private final DynamoDbClient ddb;

    public OnboardingDataAccessLayer(DynamoDbClient ddb, String onboardingTable) {
        final long startTimeMillis = System.currentTimeMillis();
        this.ddb = ddb;
        this.onboardingTable = onboardingTable;
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(r -> r.tableName(this.onboardingTable));
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public List<Onboarding> getOnboardings() {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::getOnboardings");
        List<Onboarding> onboardings = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request.tableName(onboardingTable));
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::getOnboarding");
        Map<String, AttributeValue> item;
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(onboardingId).build());
            GetItemResponse response = ddb.getItem(request -> request.tableName(onboardingTable).key(key));
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::getOnboardingByTenantId");
        Onboarding onboarding = null;
        try {
            final int uuidLength = 36;
            String filter;
            if (tenantId.length() < uuidLength) {
                filter = "begins_with(tenant_id, :tenantId)";
            } else {
                filter = "tenant_id = :tenantId";
            }
            ScanResponse scan = ddb.scan(ScanRequest.builder()
                    .tableName(onboardingTable)
                    .filterExpression(filter)
                    .expressionAttributeValues(
                            Map.of(":tenantId", AttributeValue.builder().s(tenantId).build())
                    )
                    .build()
            );
            if (1 == scan.items().size()) {
                LOGGER.info("Scanning onboarding for tenant id " + tenantId);
                onboarding = fromAttributeValueMap(scan.items().get(0));
            } else {
                LOGGER.info("Onboarding scan for tenant id " + tenantId + " returned "
                        + scan.items().size() + " results");
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::updateOnboarding");
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            onboarding.setModified(LocalDateTime.now());
            Map<String, AttributeValue> item = toAttributeValueMap(onboarding);
            ddb.putItem(request -> request.tableName(onboardingTable).item(item));
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
        final long startTimeMillis = System.currentTimeMillis();
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
                    .tableName(onboardingTable)
                    .key(key)
                    .updateExpression("SET #status = :status, modified = :modified")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":status", AttributeValue.builder().s(status.toString()).build(),
                            ":modified", AttributeValue.builder().s(modified).build())
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::insertOnboarding");

        // Unique identifier is owned by the DAL
        if (onboarding.getId() != null) {
            throw new IllegalArgumentException("Can't insert a new onboarding record that already has an id");
        }
        UUID onboardingId = UUID.randomUUID();
        onboarding.setId(onboardingId);

        // We start in a created state
        onboarding.setStatus(OnboardingStatus.created);

        // Created and Modified are owned by the DAL since they reflect when the
        // object was persisted
        LocalDateTime now = LocalDateTime.now();
        onboarding.setCreated(now);
        onboarding.setModified(now);
        Map<String, AttributeValue> item = toAttributeValueMap(onboarding);
        try {
            ddb.putItem(request -> request.tableName(onboardingTable).item(item));
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

    public Onboarding deleteOnboarding(Onboarding onboarding) {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::deleteOnboarding");

        try {
            ddb.deleteItem(request -> request
                    .tableName(onboardingTable)
                    .key(Map.of("id", AttributeValue.builder().s(onboarding.getId().toString()).build()))
            );
            long deleteItemTimeMillis = System.currentTimeMillis() - startTimeMillis;
            LOGGER.info("OnboardingServiceDAL::deleteOnboarding DeleteItem exec " + deleteItemTimeMillis);
        } catch (DynamoDbException e) {
            LOGGER.error("OnboardingServiceDAL::deleteOnboarding " + Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("OnboardingServiceDAL::deleteOnboarding exec " + totalTimeMillis);
        return onboarding;
    }

    public static Map<String, AttributeValue> toAttributeValueMap(Onboarding onboarding) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(onboarding.getId().toString()).build());
        if (onboarding.getCreated() != null) {
            item.put("created", AttributeValue.builder().s(
                    onboarding.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (onboarding.getModified() != null) {
            item.put("modified", AttributeValue.builder().s(
                    onboarding.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (onboarding.getStatus() != null) {
            item.put("status", AttributeValue.builder().s(onboarding.getStatus().toString()).build());
        }
        if (onboarding.getTenantId() != null) {
            item.put("tenant_id", AttributeValue.builder().s(onboarding.getTenantId().toString()).build());
        }
        if (onboarding.getZipFile() != null) {
            item.put("zip_file", AttributeValue.builder().s(onboarding.getZipFile()).build());
        }
        if (onboarding.getRequest() != null) {
            OnboardingRequest request = onboarding.getRequest();
            Map<String, AttributeValue> requestMap = new HashMap<>();
            if (Utils.isNotBlank(request.getName())) {
                requestMap.put("name", AttributeValue.builder().s(request.getName()).build());
            }
            if (Utils.isNotBlank(request.getTier())) {
                requestMap.put("tier", AttributeValue.builder().s(request.getTier()).build());
            }
            if (Utils.isNotBlank(request.getSubdomain())) {
                requestMap.put("subdomain", AttributeValue.builder().s(request.getSubdomain()).build());
            }
            if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
                requestMap.put("attributes", AttributeValue.builder().m(request.getAttributes().entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> AttributeValue.builder().s(entry.getValue()).build())
                        )
                ).build());
            }
            if (request.getAdminUsers() != null && !request.getAdminUsers().isEmpty()) {
                List<AttributeValue> userMaps = new ArrayList<>();
                request.getAdminUsers()
                        .stream()
                        .map(userMap -> AttributeValue.builder().m(
                                userMap.entrySet()
                                        .stream()
                                        .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                entry -> AttributeValue.builder().s(
                                                        String.valueOf(entry.getValue())).build()
                                        ))
                        ).build())
                        .forEach(userMaps::add);
                requestMap.put("admin_users", AttributeValue.builder().l(userMaps).build());
            }
            item.put("request", AttributeValue.builder().m(requestMap).build());
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
                    LocalDateTime created = LocalDateTime.parse(item.get("created").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
                    onboarding.setCreated(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("created").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("modified")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("modified").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
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
            if (item.containsKey("zip_file")) {
                onboarding.setZipFile(item.get("zip_file").s());
            }
            if (item.containsKey("request")) {
                Map<String, AttributeValue> requestMap = item.get("request").m();
                String name = null;
                if (requestMap.containsKey("name")) {
                    name = requestMap.get("name").s();
                }
                String tier = null;
                if (requestMap.containsKey("tier")) {
                    tier = requestMap.get("tier").s();
                }
                String subdomain = null;
                if (requestMap.containsKey("subdomain")) {
                    subdomain = requestMap.get("subdomain").s();
                }
                Map<String, String> attributes = null;
                if (requestMap.containsKey("attributes")) {
                    attributes = requestMap.get("attributes").m().entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().s(),
                                    (valForKey, valForDupKey) -> valForKey,
                                    LinkedHashMap::new
                            ));
                }
                Set<Map<String, Object>> adminUsers = new LinkedHashSet<>();
                if (requestMap.containsKey("admin_users")) {
                    List<AttributeValue> adminUsersList = requestMap.get("admin_users").l();
                    for (AttributeValue adminUserMap : adminUsersList) {
                        Map<String, Object> adminUser = adminUserMap.m().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> entry.getValue().s(),
                                        (valForKey, valForDupKey) -> valForKey,
                                        LinkedHashMap::new
                                ));
                        adminUsers.add(adminUser);
                    }
                }
                OnboardingRequest request = OnboardingRequest.builder()
                        .name(name)
                        .tier(tier)
                        .subdomain(subdomain)
                        .attributes(attributes)
                        .adminUsers(adminUsers)
                        .build();
                onboarding.setRequest(request);
            }

        }
        return onboarding;
    }
}
