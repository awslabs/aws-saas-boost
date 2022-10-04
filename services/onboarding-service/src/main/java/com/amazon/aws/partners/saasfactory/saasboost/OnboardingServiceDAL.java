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

public class OnboardingServiceDAL {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingServiceDAL.class);
    private static final String ONBOARDING_TABLE = System.getenv("ONBOARDING_TABLE");
    private static final String CIDR_BLOCK_TABLE = System.getenv("CIDR_BLOCK_TABLE");
    private final DynamoDbClient ddb;

    public OnboardingServiceDAL() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(ONBOARDING_TABLE)) {
            throw new IllegalStateException("Missing required environment variable ONBOARDING_TABLE");
        }
        this.ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(r -> r.tableName(ONBOARDING_TABLE));
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public List<Onboarding> getOnboardings() {
        final long startTimeMillis = System.currentTimeMillis();
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("OnboardingServiceDAL::getOnboarding");
        Map<String, AttributeValue> item;
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
                    .tableName(ONBOARDING_TABLE)
                    .filterExpression(filter)
                    .expressionAttributeValues(
                            Collections.singletonMap(":tenantId", AttributeValue.builder().s(tenantId).build())
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
        final long startTimeMillis = System.currentTimeMillis();
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
                    .tableName(ONBOARDING_TABLE)
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

    public String getCidrBlock(UUID tenantId) {
        return getCidrBlock(tenantId.toString());
    }

    public String getCidrBlock(String tenantId) {
        if (Utils.isBlank(CIDR_BLOCK_TABLE)) {
            throw new IllegalStateException("Missing required environment variable CIDR_BLOCK_TABLE");
        }
        String cidrBlock = null;
        try {
            ScanResponse scan = ddb.scan(r -> r.tableName(CIDR_BLOCK_TABLE));
            if (!scan.items().isEmpty()) {
                for (Map<String, AttributeValue> item : scan.items()) {
                    if (item.containsKey("tenant_id") && item.get("tenant_id").s().equals(tenantId)) {
                        cidrBlock = item.get("cidr_block").s();
                    }
                }
            }
        } catch (DynamoDbException ddbError) {
            LOGGER.error("dynamodb:Scan error", ddbError);
            LOGGER.error(Utils.getFullStackTrace(ddbError));
            throw ddbError;
        } catch (Exception e) {
            LOGGER.error("Unexpected error", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return cidrBlock;
    }

    public boolean availableCidrBlock() {
        if (Utils.isBlank(CIDR_BLOCK_TABLE)) {
            throw new IllegalStateException("Missing required environment variable CIDR_BLOCK_TABLE");
        }
        boolean available;
        try {
            ScanResponse scan = ddb.scan(r -> r
                    .tableName(CIDR_BLOCK_TABLE)
                    .filterExpression("attribute_not_exists(tenant_id)")
            );
            available = scan.hasItems() && !scan.items().isEmpty();
        } catch (DynamoDbException ddbError) {
            LOGGER.error("dynamodb:Scan error", ddbError);
            LOGGER.error(Utils.getFullStackTrace(ddbError));
            throw ddbError;
        }
        return available;
    }

    public String assignCidrBlock(String tenantId) {
        if (Utils.isBlank(CIDR_BLOCK_TABLE)) {
            throw new IllegalStateException("Missing required environment variable CIDR_BLOCK_TABLE");
        }
        String cidrBlock;
        try {
            long scanStartTimeMillis = System.currentTimeMillis();
            List<String> availableCidrBlocks = new ArrayList<>();
            ScanResponse fullScan = ddb.scan(r -> r.tableName(CIDR_BLOCK_TABLE));
            if (!fullScan.items().isEmpty()) {
                for (Map<String, AttributeValue> item : fullScan.items()) {
                    // Make sure we're not trying to assign a CIDR block to a tenant that already has one
                    if (item.containsKey("tenant_id") && tenantId.equals(item.get("tenant_id").s())) {
                        throw new RuntimeException("CIDR block already assigned for tenant " + tenantId);
                    }
                    if (!item.containsKey("tenant_id")) {
                        availableCidrBlocks.add(item.get("cidr_block").s());
                    }
                }
                // Make sure we have an open CIDR block left to assign
                if (availableCidrBlocks.isEmpty()) {
                    throw new RuntimeException("No remaining CIDR blocks");
                }
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
                    .expressionAttributeValues(
                            Collections.singletonMap(":tenantId", AttributeValue.builder().s(tenantId).build())
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
            item.put("request", AttributeValue.builder().m(requestMap).build());
        }
        if (!onboarding.getStacks().isEmpty()) {
            item.put("stacks", AttributeValue.builder().l(onboarding.getStacks()
                    .stream()
                    .map(stack -> {
                        Map<String, AttributeValue> stackItem = new HashMap<>();
                        if (stack.getName() != null) {
                            stackItem.put("name", AttributeValue.builder().s(stack.getName()).build());
                        }
                        if (stack.getArn() != null) {
                            stackItem.put("arn", AttributeValue.builder().s(stack.getArn()).build());
                        }
                        stackItem.put("baseStack", AttributeValue.builder().bool(stack.isBaseStack()).build());
                        if (stack.getStatus() != null) {
                            stackItem.put("status", AttributeValue.builder().s(stack.getStatus()).build());
                        }
                        if (stack.getPipeline() != null) {
                            stackItem.put("pipeline", AttributeValue.builder().s(stack.getPipeline()).build());
                        }
                        if (stack.getPipelineStatus() != null) {
                            stackItem.put("pipelineStatus", AttributeValue.builder().s(stack.getPipelineStatus()).build());
                        }
                        return AttributeValue.builder().m(stackItem).build();
                    })
                    .collect(Collectors.toList())
                    ).build()
            );
        }
        item.put("ecs_cluster_locked", AttributeValue.builder().bool(onboarding.isEcsClusterLocked()).build());
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
            if (item.containsKey("zip_file")) {
                onboarding.setZipFile(item.get("zip_file").s());
            }
            if (item.containsKey("request")) {
                Map<String, AttributeValue> requestMap = item.get("request").m();
                OnboardingRequest request = new OnboardingRequest(requestMap.get("name").s());
                if (requestMap.containsKey("tier")) {
                    request.setTier(requestMap.get("tier").s());
                }
                if (requestMap.containsKey("subdomain")) {
                    request.setSubdomain(requestMap.get("subdomain").s());
                }
                if (requestMap.containsKey("attributes")) {
                    request.setAttributes(requestMap.get("attributes").m().entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> entry.getValue().s(),
                                    (valForKey, valForDupKey) -> valForKey,
                                    LinkedHashMap::new
                            ))
                    );
                }
                onboarding.setRequest(request);
            }
            if (item.containsKey("stacks")) {
                onboarding.setStacks(item.get("stacks").l()
                        .stream()
                        .map(stackItem -> {
                            Map<String, AttributeValue> stack = stackItem.m();
                            return OnboardingStack.builder()
                                    .name(stack.containsKey("name") ? stack.get("name").s() : null)
                                    .arn(stack.containsKey("arn") ? stack.get("arn").s() : null)
                                    .baseStack(stack.containsKey("baseStack") ? stack.get("baseStack").bool() : false)
                                    .status(stack.containsKey("status") ? stack.get("status").s() : null)
                                    .pipeline(stack.containsKey("pipeline") ? stack.get("pipeline").s() : null)
                                    .pipelineStatus(stack.containsKey("pipelineStatus") ? stack.get("pipelineStatus").s() : null)
                                    .build();
                        })
                        .collect(Collectors.toList())
                );
            }
            if (item.containsKey("ecs_cluster_locked")) {
                onboarding.setEcsClusterLocked(item.get("ecs_cluster_locked").bool());
            }
        }
        return onboarding;
    }
}
