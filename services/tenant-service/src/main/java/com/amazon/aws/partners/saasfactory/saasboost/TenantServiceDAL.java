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

public class TenantServiceDAL {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantServiceDAL.class);
    private static final String TENANTS_TABLE = System.getenv("TENANTS_TABLE");
    private final DynamoDbClient ddb;

    public TenantServiceDAL() {
        if (Utils.isBlank(TENANTS_TABLE)) {
            throw new IllegalStateException("Missing required environment variable TENANTS_TABLE");
        }
        this.ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(request -> request.tableName(TENANTS_TABLE));
    }

    public List<Tenant> getOnboardedTenants() {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::getTenants");

        // Get all tenants that have the workload deployed and are ready to use the system
        // or who have had the workload deployed and are in an update/deployment cycle
        List<Tenant> tenants = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request
                    .tableName(TENANTS_TABLE)
                    .filterExpression("attribute_exists(onboarding_status) "
                            + "AND onboarding_status IN (:updating, :updated, :deploying, :deployed)")
                    .expressionAttributeValues(Map.of(
                            ":updating", AttributeValue.builder().s("updating").build(),
                            ":updated", AttributeValue.builder().s("updated").build(),
                            ":deploying", AttributeValue.builder().s("deploying").build(),
                            ":deployed", AttributeValue.builder().s("deployed").build()
                    ))
                    .build()
            );
            LOGGER.info("TenantServiceDAL::getTenants returning {} onboarded tenants", response.items().size());
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::getProvisionedTenants");

        // Get all tenants that have infrastructure running or being created
        List<Tenant> tenants = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(ScanRequest.builder()
                    .tableName(TENANTS_TABLE)
                    .filterExpression("attribute_exists(onboarding_status) "
                            + "AND onboarding_status <> :failed "
                            + "AND onboarding_status <> :deleting "
                            + "AND onboarding_status <> :deleted") // Can't use NOT IN (...) in DynamoDB
                    .expressionAttributeValues(Map.of(
                            ":failed", AttributeValue.builder().s("failed").build(),
                            ":deleting", AttributeValue.builder().s("deleting").build(),
                            ":deleted", AttributeValue.builder().s("deleted").build()
                    ))
                    .build()
            );
            LOGGER.info("TenantServiceDAL::getProvisionedTenants returning {} provisioned tenants", response.items().size());
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
        final long startTimeMillis = System.currentTimeMillis();
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
        final long startTimeMillis = System.currentTimeMillis();
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::updateTenant {}", tenant.getId());
        try {
            // Created and Modified are owned by the DAL since they reflect when the
            // object was persisted
            tenant.setModified(LocalDateTime.now());
            Map<String, AttributeValue> item = toAttributeValueMap(tenant);
            ddb.putItem(request -> request.tableName(TENANTS_TABLE).item(item));
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::updateTenant " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::updateTenant exec " + totalTimeMillis);
        return tenant;
    }

    public Tenant updateTenantOnboardingStatus(UUID tenantId, String onboardingStatus) {
        final long startTimeMillis = System.currentTimeMillis();
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
                    .updateExpression("SET onboarding_status = :onboarding, modified = :modified")
                    .expressionAttributeValues(Map.of(
                            ":onboarding", AttributeValue.builder().s(onboardingStatus).build(),
                            ":modified", AttributeValue.builder().s(modified).build())
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

    public Tenant updateTenantHostname(UUID tenantId, String hostname) {
        Tenant updated = new Tenant();
        updated.setId(tenantId);
        updated.setHostname(hostname);
        updated.setModified(LocalDateTime.now());
        String modified = updated.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tenantId.toString()).build());
            UpdateItemResponse response = ddb.updateItem(request -> request
                    .tableName(TENANTS_TABLE)
                    .key(key)
                    .updateExpression("SET hostname = :hostname, modified = :modified")
                    .expressionAttributeValues(Map.of(
                            ":hostname", AttributeValue.builder().s(hostname).build(),
                            ":modified", AttributeValue.builder().s(modified).build())
                    )
                    .returnValues(ReturnValue.ALL_NEW)
            );
            updated = fromAttributeValueMap(response.attributes());
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::updateTenantHostname {}", e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        return updated;
    }

    public Tenant disableTenant(String tenantId) {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::disableTenant");
        Tenant disabled = setStatus(tenantId, Boolean.FALSE);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::disableTenant exec {}", totalTimeMillis);
        return disabled;
    }

    public Tenant enableTenant(String tenantId) {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::disableTenant");
        Tenant enabled = setStatus(tenantId, Boolean.TRUE);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::disableTenant exec {}", totalTimeMillis);
        return enabled;
    }

    private Tenant setStatus(String tenantId, Boolean active) {
        Tenant updated;
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tenantId).build());
            UpdateItemResponse response = ddb.updateItem(request -> request
                    .tableName(TENANTS_TABLE)
                    .key(key)
                    .updateExpression("SET active = :active, modified = :modified")
                    .expressionAttributeValues(Map.of(
                            ":active", AttributeValue.builder().bool(active).build(),
                            ":modified", AttributeValue.builder().s(
                                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build()
                            )
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
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::insertTenant {}", tenant.getName());
        UUID tenantId = UUID.randomUUID();
        tenant.setId(tenantId);
        // Created and Modified are owned by the DAL since they reflect when the object was persisted
        LocalDateTime now = LocalDateTime.now();
        tenant.setCreated(now);
        tenant.setModified(now);
        try {
            ddb.putItem(request -> request.tableName(TENANTS_TABLE).item(toAttributeValueMap(tenant)));
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::insertTenant " + Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::insertTenant exec " + totalTimeMillis);
        return tenant;
    }

    public void deleteTenant(UUID tenantId) {
        deleteTenant(tenantId.toString());
    }

    public void deleteTenant(String tenantId) {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::deleteTenant");
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(tenantId).build());
            ddb.deleteItem(request -> request.tableName(TENANTS_TABLE).key(key));
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::deleteTenant " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::deleteTenant exec " + totalTimeMillis);
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
            item.put("onboarding_status", AttributeValue.builder().s(tenant.getOnboardingStatus()).build());
        }
        if (Utils.isNotBlank(tenant.getName())) {
            item.put("name", AttributeValue.builder().s(tenant.getName()).build());
        }
        if (Utils.isNotBlank(tenant.getHostname())) {
            item.put("hostname", AttributeValue.builder().s(tenant.getHostname()).build());
        }
        if (Utils.isNotBlank(tenant.getSubdomain())) {
            item.put("subdomain", AttributeValue.builder().s(tenant.getSubdomain()).build());
        }
        if (Utils.isNotBlank(tenant.getTier())) {
            item.put("tier", AttributeValue.builder().s(tenant.getTier()).build());
        }
        if (Utils.isNotBlank(tenant.getBillingPlan())) {
            item.put("billing_plan", AttributeValue.builder().s(tenant.getBillingPlan()).build());
        }
        if (tenant.getAttributes() != null) {
            item.put("attributes", AttributeValue.builder().m(tenant.getAttributes().entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    entry -> entry.getKey(),
                                    entry -> AttributeValue.builder().s(entry.getValue()).build()
                            ))
                    ).build()
            );
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
            if (item.containsKey("onboarding_status")) {
                tenant.setOnboardingStatus(item.get("onboarding_status").s());
            }
            if (item.containsKey("name")) {
                tenant.setName(item.get("name").s());
            }
            if (item.containsKey("hostname")) {
                tenant.setHostname(item.get("hostname").s());
            }
            if (item.containsKey("subdomain")) {
                tenant.setSubdomain(item.get("subdomain").s());
            }
            if (item.containsKey("billing_plan")) {
                tenant.setBillingPlan(item.get("billing_plan").s());
            }
            if (item.containsKey("attributes")) {
                try {
                    tenant.setAttributes(item.get("attributes").m().entrySet()
                            .stream()
                            .collect(Collectors.toMap(
                                    entry -> entry.getKey(),
                                    entry -> entry.getValue().s()
                            ))
                    );
                } catch (Exception e) {
                    LOGGER.error("Failed to parse resources from database: {}", item.get("resources").m());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
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
