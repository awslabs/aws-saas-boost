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

public class TenantServiceDAL {

    private final static Logger LOGGER = LoggerFactory.getLogger(TenantServiceDAL.class);
    private static final String TENANTS_TABLE = System.getenv("TENANTS_TABLE");
    private final DynamoDbClient ddb;

    public TenantServiceDAL() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(TENANTS_TABLE)) {
            throw new IllegalStateException("Missing required environment variable TENANTS_TABLE");
        }
        this.ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(request -> request.tableName(TENANTS_TABLE));
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public List<Tenant> getOnboardedTenants() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::getTenants");
        List<Tenant> tenants = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request
                    .tableName(TENANTS_TABLE)
                    .filterExpression("attribute_exists(onboarding) AND onboarding IN (:status1, :status2)")
                    .expressionAttributeValues(Stream
                            .of(
                                    new AbstractMap.SimpleEntry<>(":status1", AttributeValue.builder().s("succeeded").build()),
                                    new AbstractMap.SimpleEntry<>(":status2", AttributeValue.builder().s("updated").build())
                            )
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
            );
            LOGGER.info("TenantServiceDAL::getTenants returning " + response.items().size() + " onboarded tenants");
            response.items().forEach(item ->
                    tenants.add(fromAttributeValueMap(item))
            );
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::getTenants " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::getTenants exec " + totalTimeMillis);
        return tenants;
    }

    public List<Tenant> getProvisionedTenants() {
        return getProvisionedTenants(null);
    }

    public List<Tenant> getProvisionedTenants(Boolean customizedTenants) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::getProvisionedTenants");

        // Get all tenants who haven't just started provisioning (created)
        // or who had an error during provisioning (failed)
        String filter = "attribute_exists(onboarding) AND onboarding <> :created AND onboarding <> :failed AND onboarding <> :deleted";
        Map<String, AttributeValue> expressions = new HashMap<>();
        expressions.put(":created", AttributeValue.builder().s("created").build());
        expressions.put(":failed", AttributeValue.builder().s("failed").build());
        expressions.put(":deleted", AttributeValue.builder().s("deleted").build());
        // Also, only get tenants who have (or have not) overridden the default
        // compute settings for memory, CPU, and auto scaling group bounds
        if (customizedTenants != null) {
            filter = filter + " AND attribute_exists(overrideDefaults) AND overrideDefaults = :overrideDefaults";
            expressions.put(":overrideDefaults", AttributeValue.builder().bool(customizedTenants).build());
        }
        List<Tenant> tenants = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(ScanRequest.builder()
                    .tableName(TENANTS_TABLE)
                    .filterExpression(filter)
                    .expressionAttributeValues(expressions)
                    .build()
            );
            LOGGER.info("TenantServiceDAL::getProvisionedTenants returning {} provisioned{} tenants", response.items().size(), (customizedTenants != null ? (customizedTenants ? " and customized" : " and not customized") : ""));
            response.items().forEach(item ->
                    tenants.add(fromAttributeValueMap(item))
            );
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::getProvisionedTenants " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::getProvisionedTenants exec " + totalTimeMillis);
        return tenants;
    }

    public List<Tenant> getAllTenants() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::getAllTenants");
        List<Tenant> tenants = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request.tableName(TENANTS_TABLE));
            response.items().forEach(item ->
                    tenants.add(fromAttributeValueMap(item))
            );
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::getAllTenants " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::getAllTenants exec " + totalTimeMillis);
        return tenants;
    }

    public Tenant getTenant(UUID tenantId) {
        return getTenant(tenantId.toString());
    }

    public Tenant getTenant(String tenantId) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::getTenant {}", tenantId);
        Map<String, AttributeValue> item = null;
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tenantId).build());
            GetItemResponse response = ddb.getItem(request -> request.tableName(TENANTS_TABLE).key(key));
            item = response.item();
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::getTenant " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        Tenant tenant = fromAttributeValueMap(item);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::getTenant exec {}", totalTimeMillis);
        return tenant;
    }

    // Choosing to do a replacement update as you might do in a RDBMS by
    // setting columns = NULL when they do not exist in the updated value
    public Tenant updateTenant(Tenant tenant) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::updateTenant {}", tenant.getId());
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            tenant.setModified(LocalDateTime.now());
            Map<String, AttributeValue> item = toAttributeValueMap(tenant);
            PutItemResponse response = ddb.putItem(request -> request.tableName(TENANTS_TABLE).item(item));
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::updateTenant " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::updateTenant exec " + totalTimeMillis);
        return tenant;
    }

    public Tenant updateTenantOnboarding(UUID tenantId, String onboardingStatus) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::updateTenantOnboarding {} {}", tenantId.toString(), onboardingStatus);
        Tenant updated = new Tenant();
        updated.setId(tenantId);
        updated.setOnboardingStatus(onboardingStatus);
        updated.setModified(LocalDateTime.now());
        String modified = updated.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tenantId.toString()).build());
            UpdateItemResponse response = ddb.updateItem(request -> request
                    .tableName(TENANTS_TABLE)
                    .key(key)
                    .updateExpression("SET onboarding = :onboarding, modified = :modified")
                    .expressionAttributeValues(Stream
                            .of(
                                    new AbstractMap.SimpleEntry<String, AttributeValue>(":onboarding", AttributeValue.builder().s(onboardingStatus).build()),
                                    new AbstractMap.SimpleEntry<String, AttributeValue>(":modified", AttributeValue.builder().s(modified).build())
                            )
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
                    .returnValues(ReturnValue.ALL_NEW)
            );
            updated = fromAttributeValueMap(response.attributes());
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::updateTenantOnboarding {}", Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::updateTenantOnboarding exec {}", totalTimeMillis);
        return updated;
    }

    public Tenant disableTenant(String tenantId) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::disableTenant");
        Tenant disabled = setStatus(tenantId, Boolean.FALSE);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::disableTenant exec {}", totalTimeMillis);
        return disabled;
    }

    public Tenant enableTenant(String tenantId) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::disableTenant");
        Tenant enabled = setStatus(tenantId, Boolean.TRUE);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::disableTenant exec {}", totalTimeMillis);
        return enabled;
    }

    private Tenant setStatus(String tenantId, Boolean active) {
        Tenant updated = null;
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tenantId).build());
            UpdateItemResponse response = ddb.updateItem(request -> request
                    .tableName(TENANTS_TABLE)
                    .key(key)
                    .updateExpression("SET active = :active, modified = :modified")
                    .expressionAttributeValues(Stream
                            .of(
                                    new AbstractMap.SimpleEntry<String, AttributeValue>(":active", AttributeValue.builder().bool(active).build()),
                                    new AbstractMap.SimpleEntry<String, AttributeValue>(":modified", AttributeValue.builder().s(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build())
                            )
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
                    .returnValues(ReturnValue.ALL_NEW)
            );
            updated = fromAttributeValueMap(response.attributes());
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::setStatus {}", Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return updated;
    }

    public Tenant insertTenant(Tenant tenant) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::insertTenant {}", tenant.getName());
        UUID tenantId = UUID.randomUUID();
        tenant.setId(tenantId);
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            LocalDateTime now = LocalDateTime.now();
            tenant.setCreated(now);
            tenant.setModified(now);
            Map<String, AttributeValue> item = toAttributeValueMap(tenant);
            PutItemResponse response = ddb.putItem(request -> request.tableName(TENANTS_TABLE).item(item));
            long putItemTimeMillis = System.currentTimeMillis() - startTimeMillis;
            LOGGER.info("TenantServiceDAL::insertTenant PutItem exec " + putItemTimeMillis);
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::insertTenant " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::insertTenant exec " + totalTimeMillis);
        return tenant;
    }

    public void deleteTenant(UUID tenantId) {
        deleteTenant(tenantId.toString());
    }

    public void deleteTenant(String tenantId) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::deleteTenant");
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tenantId).build());
            DeleteItemResponse response = ddb.deleteItem(request -> request.tableName(TENANTS_TABLE).key(key));
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::deleteTenant " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::deleteTenant exec " + totalTimeMillis);
        return;
    }

    public static Map<String, AttributeValue> toAttributeValueMap(Tenant tenant) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(tenant.getId().toString()).build());
        if (tenant.getCreated() != null) {
            item.put("created", AttributeValue.builder().s(tenant.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (tenant.getModified() != null) {
            item.put("modified", AttributeValue.builder().s(tenant.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (tenant.getActive() != null) {
            item.put("active", AttributeValue.builder().bool(tenant.getActive()).build());
        }
        if (Utils.isNotBlank(tenant.getOnboardingStatus())) {
            item.put("onboarding", AttributeValue.builder().s(tenant.getOnboardingStatus()).build());
        }
        if (Utils.isNotBlank(tenant.getName())) {
            item.put("name", AttributeValue.builder().s(tenant.getName()).build());
        }
        if (Utils.isNotBlank(tenant.getSubdomain())) {
            item.put("subdomain", AttributeValue.builder().s(tenant.getSubdomain()).build());
        }
        if (Utils.isNotBlank(tenant.getTier())) {
            item.put("tier", AttributeValue.builder().s(tenant.getTier()).build());
        }
        if (Utils.isNotBlank(tenant.getPlanId())) {
            item.put("planId", AttributeValue.builder().s(tenant.getPlanId()).build());
        }
        if (tenant.getResources() != null) {
            item.put("resources", AttributeValue.builder().m(tenant.getResources().entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey(),
                            entry -> AttributeValue.builder().m(
                                    Map.of(
                                            "name", AttributeValue.builder().s(entry.getValue().getName()).build(),
                                            "arn", AttributeValue.builder().s(entry.getValue().getArn()).build(),
                                            "consoleUrl", AttributeValue.builder().s(entry.getValue().getConsoleUrl()).build()
                                    )).build()
                            ))
                    ).build()
            );
//            item.put("resources", AttributeValue.builder().m(tenant.getResources().entrySet()
//                    .stream()
//                    .collect(Collectors.toMap(
//                            entry -> entry.getKey(),
//                            entry -> AttributeValue.builder().s(entry.getValue()).build()
//                    ))).build());
        }
        return item;
    }

    public static Tenant fromAttributeValueMap(Map<String, AttributeValue> item) {
        Tenant tenant = null;
        if (item != null && !item.isEmpty()) {
            tenant = new Tenant();
            if (item.containsKey("id")) {
                try {
                    tenant.setId(UUID.fromString(item.get("id").s()));
                } catch (IllegalArgumentException e) {
                    LOGGER.error("Failed to parse UUID from database: " + item.get("id").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("created")) {
                try {
                    LocalDateTime created = LocalDateTime.parse(item.get("created").s(), DateTimeFormatter.ISO_DATE_TIME);
                    tenant.setCreated(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("created").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("modified")) {
                try {
                    LocalDateTime modified = LocalDateTime.parse(item.get("modified").s(), DateTimeFormatter.ISO_DATE_TIME);
                    tenant.setModified(modified);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("modified").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("active")) {
                tenant.setActive(item.get("active").bool());
            }
            if (item.containsKey("tier")) {
                tenant.setTier(item.get("tier").s());
            }
            if (item.containsKey("onboarding")) {
                tenant.setOnboardingStatus(item.get("onboarding").s());
            }
            if (item.containsKey("name")) {
                tenant.setName(item.get("name").s());
            }
            if (item.containsKey("subdomain")) {
                tenant.setSubdomain(item.get("subdomain").s());
            }
            if (item.containsKey("planId")) {
                tenant.setPlanId(item.get("planId").s());
            }
            if (item.containsKey("resources")) {
                try {
                    tenant.setResources(item.get("resources").m().entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    entry -> entry.getKey(),
                                    entry -> new Tenant.Resource(entry.getValue().m().get("name").s(),
                                            entry.getValue().m().get("arn").s(),
                                            entry.getValue().m().get("consoleUrl").s())
                            ))
                    );
                } catch (Exception e) {
                    LOGGER.error("Failed to parse resources from database: {}", item.get("resources").m());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
        }
        return tenant;
    }
}
