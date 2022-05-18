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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.util.*;
import java.util.stream.Collectors;

public class TenantService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String EVENT_SOURCE = "saas-boost";
    private final TenantServiceDAL dal;
    private final EventBridgeClient eventBridge;

    public TenantService() {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable TENANTS_TABLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new TenantServiceDAL();
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        //logRequestEvent(event);
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent getTenants(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::getTenants");
        //Utils.logRequestEvent(event);
        List<Tenant> tenants = new ArrayList<>();
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        if (queryParams != null && queryParams.containsKey("status")) {
            if ("provisioned".equalsIgnoreCase(queryParams.get("status"))) {
                tenants.addAll(dal.getProvisionedTenants());
            } else if ("onboarded".equalsIgnoreCase(queryParams.get("status"))) {
                tenants.addAll(dal.getOnboardedTenants());
            }
        } else {
            tenants.addAll(dal.getAllTenants());
        }
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody(Utils.toJson(tenants));
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::getTenants exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getTenant(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::getTenant");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String tenantId = params.get("id");
        Tenant tenant = dal.getTenant(tenantId);
        if (tenant != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(tenant));
        } else {
            response = new APIGatewayProxyResponseEvent().withStatusCode(404).withHeaders(CORS);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::getTenant exec {}", totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent updateTenant(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::updateTenant");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String tenantId = params.get("id");
        Tenant tenant = Utils.fromJson((String) event.get("body"), Tenant.class);
        if (tenant == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS);
        } else {
            if (tenant.getId() == null || !tenant.getId().toString().equals(tenantId)) {
                LOGGER.error("Can't update tenant {} at resource {}", tenant.getId(), tenantId);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS);
            } else {
                Tenant existing = dal.getTenant(tenantId);
                tenant = dal.updateTenant(tenant);
                if (existing.isProvisioned()) {
                    // Need to trigger an update for this tenant's provisioned resources
                    LOGGER.info("Triggering provisioned tenant update for {}", tenantId);
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, "Tenant Updated",
                            Map.of("tenantId", tenant.getId()));
                }

                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(tenant));
            }
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::updateTenant exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent enableTenant(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::enableTenant");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String tenantId = params.get("id");
        LOGGER.info("TenantService::enableTenant " + tenantId);
        if (tenantId == null || tenantId.isEmpty()) {
            response = new APIGatewayProxyResponseEvent().withStatusCode(400).withHeaders(CORS);
        } else {
            Tenant tenant = dal.enableTenant(tenantId);

            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, "Tenant Enabled",
                    Map.of("tenantId", tenantId));

            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(tenant));
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::enableTenant exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent disableTenant(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::disableTenant");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String tenantId = params.get("id");
        LOGGER.info("TenantService::disableTenant " + tenantId);
        if (tenantId == null || tenantId.isEmpty()) {
            response = new APIGatewayProxyResponseEvent().withStatusCode(400).withHeaders(CORS);
        } else {
            Tenant tenant = dal.disableTenant(tenantId);

            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, "Tenant Disabled",
                    Map.of("tenantId", tenantId));

            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(tenant));
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::disableTenant exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent insertTenant(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::insertTenant");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        Tenant tenant = null;
        // Were we called from Step Functions or API Gateway?
        if (event.containsKey("body")) {
            tenant = Utils.fromJson((String) event.get("body"), Tenant.class);
        }
        if (tenant == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid request body\"}");
        } else {
            // Create a new Tenant record in the database
            tenant = dal.insertTenant(tenant);
            LOGGER.info("TenantService::insertTenant tenant id {}", tenant.getId().toString());

            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(tenant));
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::insertTenant exec {}", totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent deleteTenant(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::deleteTenant");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String tenantId = params.get("id");
        Tenant tenant = Utils.fromJson((String) event.get("body"), Tenant.class);
        if (tenant == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid request body\"}");
        } else {
            if (tenant.getId() == null || !tenant.getId().toString().equals(tenantId)) {
                LOGGER.error("Can't delete tenant {} at resource {}", tenant.getId(), tenantId);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Invalid request for specified resource\"}");
            } else {
                dal.disableTenant(tenantId);
                //dal.deleteTenant(tenantId);
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(200);

                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                        TenantEvent.TENANT_DELETED.detailType(),
                        Map.of("tenantId", tenantId));
            }
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::deleteTenant exec {}", totalTimeMillis);
        return response;
    }

    public void handleTenantEvent(Map<String, Object> event, Context context) {
        if ("saas-boost".equals(event.get("source"))) {
            TenantEvent tenantEvent = TenantEvent.fromDetailType((String) event.get("detail-type"));
            if (tenantEvent != null) {
                switch (tenantEvent) {
                    case TENANT_HOSTNAME_CHANGED:
                        LOGGER.info("Handling Tenant Hostname Changed");
                        handleTenantHostnameChanged(event, context);
                        break;
                    case TENANT_ONBOARDING_STATUS_CHANGED:
                        LOGGER.info("Handling Tenant Onboarding Status Changed");
                        handleTenantOnboardingStatusChanged(event, context);
                        break;
                    case TENANT_RESOURCES_CHANGED:
                        LOGGER.info("Handling Tenant Resources Changed");
                        handleTenantResourcesChanged(event, context);
                        break;
                }
            } else {
                LOGGER.error("Can't find tenant event for detail-type {}", event.get("detail-type"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Unknown event source " + event.get("source"));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleTenantOnboardingStatusChanged(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        if (TenantEvent.validate(event, "onboardingStatus")) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            String tenantId = (String) detail.get("tenantId");
            String onboardingStatus = (String) detail.get("onboardingStatus");
            Tenant tenant = dal.getTenant(tenantId);
            if (tenant != null) {
                LOGGER.info("Updating tenant {} onboarding status from {} to {}", tenantId,
                        tenant.getOnboardingStatus(), onboardingStatus);
                dal.updateTenantOnboardingStatus(tenant.getId(), onboardingStatus);
            } else {
                // Can't find an tenant record for this id
                LOGGER.error("Can't find tenant record for {}", tenantId);
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Missing tenantId or onboardingStatus in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleTenantHostnameChanged(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        if (TenantEvent.validate(event, "hostname")) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            String tenantId = (String) detail.get("tenantId");
            String hostname = (String) detail.get("hostname");
            Tenant tenant = dal.getTenant(tenantId);
            if (tenant != null) {
                LOGGER.info("Updating tenant {} hostname to {}", tenantId, hostname);
                tenant.setHostname(hostname);
                dal.updateTenant(tenant);
            } else {
                // Can't find an tenant record for this id
                LOGGER.error("Can't find tenant record for {}", tenantId);
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Missing tenantId or onboardingStatus in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleTenantResourcesChanged(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        if (TenantEvent.validate(event, "resources")) {
            Map<String, Object> detail = (Map<String, Object>) event.get("detail");
            String tenantId = (String) detail.get("tenantId");
            Tenant tenant = dal.getTenant(tenantId);
            if (tenant != null) {
                Map<String, Tenant.Resource> updatedResources = fromTenantResourcesChangedEvent(event);
                if (updatedResources != null) {
                    Map<String, Tenant.Resource> tenantResources = tenant.getResources();
                    // Merge the updated resources with the existing ones. This helps the calling code not have to
                    // pull the current tenant before invoking this method. If you want to replace/delete resources
                    // from a tenant, you'll have to build the resources map you want and call updateTenant.
                    tenantResources.putAll(updatedResources);
                    tenant.setResources(tenantResources);
                    dal.updateTenant(tenant);
                    LOGGER.info("Resources updated for tenant: {}", tenantId);
                }
            } else {
                // Can't find an tenant record for this id
                LOGGER.error("Can't find tenant record for {}", tenantId);
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Missing tenantId or resources in event detail {}", Utils.toJson(event.get("detail")));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected static Map<String, Tenant.Resource> fromTenantResourcesChangedEvent(Map<String, Object> event) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        Map<String, Object> resources = Utils.fromJson((String) detail.get("resources"), LinkedHashMap.class);
        if (resources != null) {
            return resources.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey(),
                            entry -> {
                                Map<String, String> res = (Map<String, String>) entry.getValue();
                                return new Tenant.Resource(res.get("name"), res.get("arn"),
                                        res.get("consoleUrl"));
                            }
                    ));
        } else {
            LOGGER.error("Resources is invalid Json");
        }
        return null;
    }

}