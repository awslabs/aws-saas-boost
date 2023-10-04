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

public class TenantDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantDataAccessLayer.class);
    private final String tenantsTable;
    private final DynamoDbClient ddb;

    public TenantDataAccessLayer(DynamoDbClient ddb, String tenantsTable) {
        this.tenantsTable = tenantsTable;
        this.ddb = ddb;
        // Cold start performance hack -- take the TLS hit for the client in the constructor
        this.ddb.describeTable(request -> request.tableName(tenantsTable));
    }

    public List<Tenant> getOnboardedTenants() {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantServiceDAL::getTenants");

        // Get all tenants that have the workload deployed and are ready to use the system
        // or who have had the workload deployed and are in an update/deployment cycle
        List<Tenant> tenants = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request
                    .tableName(tenantsTable)
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
                    .tableName(tenantsTable)
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
            LOGGER.info("Returning {} provisioned tenants", response.items().size());
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
            ScanResponse response = ddb.scan(request -> request.tableName(tenantsTable));
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
            GetItemResponse response = ddb.getItem(request -> request.tableName(tenantsTable).key(key));
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
            ddb.putItem(request -> request.tableName(tenantsTable).item(item));
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
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            String modified = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            key.put("id", AttributeValue.builder().s(tenantId.toString()).build());
            UpdateItemResponse response = ddb.updateItem(request -> request
                    .tableName(tenantsTable)
                    .key(key)
                    .updateExpression("SET onboarding_status = :onboarding, modified = :modified")
                    .expressionAttributeValues(Map.of(
                            ":onboarding", AttributeValue.builder().s(onboardingStatus).build(),
                            ":modified", AttributeValue.builder().s(modified).build())
                    )
                    .returnValues(ReturnValue.ALL_NEW)
            );
            long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
            LOGGER.info("TenantServiceDAL::updateTenantOnboarding exec {}", totalTimeMillis);
            return fromAttributeValueMap(response.attributes());
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::updateTenantOnboarding {}", Utils.getFullStackTrace(e));
            throw e;
        }
    }

    public Tenant updateTenantHostname(String tenantId, String hostname) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            String modified = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            key.put("id", AttributeValue.builder().s(tenantId).build());
            UpdateItemResponse response = ddb.updateItem(request -> request
                    .tableName(tenantsTable)
                    .key(key)
                    .updateExpression("SET hostname = :hostname, modified = :modified")
                    .expressionAttributeValues(Map.of(
                            ":hostname", AttributeValue.builder().s(hostname).build(),
                            ":modified", AttributeValue.builder().s(modified).build())
                    )
                    .returnValues(ReturnValue.ALL_NEW)
            );
            return fromAttributeValueMap(response.attributes());
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::updateTenantHostname {}", e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
    }

    public Tenant updateTenantResources(String tenantId, Map<String, Tenant.Resource> resources) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            Map<String, String> expressionAttributeNames = new HashMap<>();
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

            StringBuilder updateExpression = new StringBuilder("SET modified = :modified");
            String modified = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            expressionAttributeValues.put(":modified", AttributeValue.builder().s(modified).build());

            for (Map.Entry<String, Tenant.Resource> tenantResource : resources.entrySet()) {
                final String resourceKey = tenantResource.getKey();
                final Tenant.Resource resourceValue = tenantResource.getValue();

                updateExpression.append(", ");
                updateExpression.append(mapAttributeUpdateExpression("resources", resourceKey, resourceKey));
                expressionAttributeNames.put(mapAttributeExpressionName(resourceKey), resourceKey);
                expressionAttributeValues.put(
                        mapAttributeExpressionValue(resourceKey),
                        AttributeValue.builder().m(
                                Map.of(
                                    "name", AttributeValue.builder().s(resourceValue.getName()).build(),
                                    "arn", AttributeValue.builder().s(resourceValue.getArn()).build(),
                                    "consoleUrl", AttributeValue.builder().s(resourceValue.getConsoleUrl()).build()
                                )
                        ).build()
                );
            }

            key.put("id", AttributeValue.builder().s(tenantId).build());
            //LOGGER.debug(updateExpression.toString());
            //LOGGER.debug(Utils.toJson(expressionAttributeNames));
            //LOGGER.debug(Utils.toJson(expressionAttributeValues));
            UpdateItemResponse response = ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(tenantsTable)
                    .key(key)
                    .updateExpression(updateExpression.toString())
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .returnValues(ReturnValue.ALL_NEW)
                    .build()
            );
            return fromAttributeValueMap(response.attributes());
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::updateTenantResources {}", e.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
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
                    .tableName(tenantsTable)
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
        if (Utils.isEmpty(tenant.getOnboardingStatus())) {
            tenant.setOnboardingStatus("unknown");
        }
        try {
            ddb.putItem(request -> request.tableName(tenantsTable).item(toAttributeValueMap(tenant)));
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
            ddb.deleteItem(request -> request.tableName(tenantsTable).key(key));
        } catch (DynamoDbException e) {
            LOGGER.error("TenantServiceDAL::deleteTenant " + Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantServiceDAL::deleteTenant exec " + totalTimeMillis);
    }

    protected static Map<String, AttributeValue> toAttributeValueMap(Tenant tenant) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(tenant.getId().toString()).build());
        if (tenant.getCreated() != null) {
            item.put("created", AttributeValue.builder().s(
                    tenant.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        }
        if (tenant.getModified() != null) {
            item.put("modified", AttributeValue.builder().s(
                    tenant.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
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
                                                    "name", AttributeValue.builder().s(
                                                            entry.getValue().getName()).build(),
                                                    "arn", AttributeValue.builder().s(
                                                            entry.getValue().getArn()).build(),
                                                    "consoleUrl", AttributeValue.builder().s(
                                                            entry.getValue().getConsoleUrl()).build()
                                            )).build()
                            ))
                    ).build()
            );
        }
        if (tenant.getAdminUsers() != null) {
            item.put("admin_users", AttributeValue.builder().l(tenant.getAdminUsers()
                    .stream().map(user -> AttributeValue.builder().m(
                            toAttributeValueMap(user)
                    ).build())
                    .collect(Collectors.toSet())
            ).build());
        }
        LOGGER.debug("DynamoDB Item");
        LOGGER.debug(Utils.toJson(item));
        return item;
    }

    protected static Map<String, AttributeValue> toAttributeValueMap(TenantAdminUser adminUser) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        if (adminUser != null) {
            if (Utils.isNotBlank(adminUser.getUsername())) {
                item.put("username", AttributeValue.builder().s(adminUser.getUsername()).build());
            }
            if (Utils.isNotBlank(adminUser.getEmail())) {
                item.put("email", AttributeValue.builder().s(adminUser.getEmail()).build());
            }
            if (Utils.isNotBlank(adminUser.getPhoneNumber())) {
                item.put("phone_number", AttributeValue.builder().s(adminUser.getPhoneNumber()).build());
            }
            if (Utils.isNotBlank(adminUser.getGivenName())) {
                item.put("given_name", AttributeValue.builder().s(adminUser.getGivenName()).build());
            }
            if (Utils.isNotBlank(adminUser.getFamilyName())) {
                item.put("family_name", AttributeValue.builder().s(adminUser.getFamilyName()).build());
            }
        }
        return item;
    }

    protected static Tenant fromAttributeValueMap(Map<String, AttributeValue> item) {
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
                    LocalDateTime created = LocalDateTime.parse(item.get("created").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
                    tenant.setCreated(created);
                } catch (DateTimeParseException e) {
                    LOGGER.error("Failed to parse created date from database: " + item.get("created").s());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
            if (item.containsKey("modified")) {
                try {
                    LocalDateTime modified = LocalDateTime.parse(item.get("modified").s(),
                            DateTimeFormatter.ISO_DATE_TIME);
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
            if (item.containsKey("admin_users")) {
                try {
                    tenant.setAdminUsers(item.get("admin_users").l().stream()
                            .map(entry -> fromAdminUserAttributeValueMap(entry.m()))
                            .collect(Collectors.toSet())
                    );
                } catch (Exception e) {
                    LOGGER.error("Failed to parse admin users from database: {}", item.get("admin_users").l());
                    LOGGER.error(Utils.getFullStackTrace(e));
                }
            }
        }
        return tenant;
    }

    protected static TenantAdminUser fromAdminUserAttributeValueMap(Map<String, AttributeValue> item) {
        TenantAdminUser adminUser = null;
        if (item != null && !item.isEmpty()) {
            String username = null;
            String email = null;
            String phoneNumber = null;
            String givenName = null;
            String familyName = null;
            if (item.containsKey("username")) {
                username = item.get("username").s();
            }
            if (item.containsKey("email")) {
                email = item.get("email").s();
            }
            if (item.containsKey("phone_number")) {
                phoneNumber = item.get("phone_number").s();
            }
            if (item.containsKey("given_name")) {
                givenName = item.get("given_name").s();
            }
            if (item.containsKey("family_name")) {
                familyName = item.get("family_name").s();
            }
            adminUser = TenantAdminUser.builder()
                    .username(username)
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .givenName(givenName)
                    .familyName(familyName)
                    .build();
        }
        return adminUser;
    }

    protected static String mapAttributeExpressionName(String keyName) {
        if (Utils.isBlank(keyName)) {
            throw new IllegalArgumentException("Missing arguments");
        }
        return "#" + keyName;
    }

    protected static String mapAttributeExpressionValue(String valueName) {
        return ":" + valueName;
    }

    protected static String mapAttributeUpdateExpression(String mapAttribute, String keyName, String valueName) {
        return mapAttribute + "." + mapAttributeExpressionName(keyName)
                + " = " + mapAttributeExpressionValue(valueName);
    }
}
