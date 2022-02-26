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
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TenantService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantService.class);
    private static final Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<String, String>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String SYSTEM_API_CALL_DETAIL_TYPE = "System API Call";
    private static final String TENANT_STATUS_CHANGE_DETAIL_TYPE = "Tenant Status Update";
    private static final String EVENT_SOURCE = "saas-boost";
    private final TenantServiceDAL dal;
    private final EventBridgeClient eventBridge;

    public TenantService() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable TENANTS_TABLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new TenantServiceDAL();
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
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
        List<Tenant> tenants = dal.getOnboardedTenants();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody(Utils.toJson(tenants));
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::getTenants exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getProvisionedTenants(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::getProvisionedTenants");
        //Utils.logRequestEvent(event);

        List<Tenant> tenants;
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        if (queryParams != null && queryParams.containsKey("overrideDefaults")) {
            Boolean customizedTenants = Boolean.valueOf(queryParams.get("overrideDefaults"));
            tenants = dal.getProvisionedTenants(customizedTenants);
        } else {
            tenants = dal.getProvisionedTenants();
        }
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody(Utils.toJson(tenants));
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::getProvisionedTenants exec {}", totalTimeMillis);
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

    public APIGatewayProxyResponseEvent updateTenantOnboarding(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::updateTenantOnboarding");
        //Utils.logRequestEvent(event);
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
                LOGGER.error("Can't update onboarding status for tenant {} at resource {}", tenant.getId(), tenantId);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS);
            } else if (tenant.getOnboardingStatus() == null || tenant.getOnboardingStatus().isEmpty()) {
                LOGGER.error("Missing onboarding status for tenant {}", tenant.getId());
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS);
            } else {
                tenant = dal.updateTenantOnboarding(tenant.getId(), tenant.getOnboardingStatus());
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(tenant));
            }
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::updateTenantOnboarding exec " + totalTimeMillis);
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

            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, "Tenant Enabled",
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
                    .withHeaders(CORS);
        } else {
            if (tenant.getId() == null || !tenant.getId().toString().equals(tenantId)) {
                LOGGER.error("Can't delete tenant {} at resource {}", tenant.getId(), tenantId);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS);
            } else {
                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, "Tenant Deleted",
                        Map.of("tenantId", tenantId));

                //**TODO set status to deleting or disable?
                dal.disableTenant(tenantId);
                //dal.deleteTenant(tenantId);
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(200);
            }
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TenantService::deleteTenant exec {}", totalTimeMillis);
        return response;
    }

    public void updateTenantResources(Map<String, Object> event, Context context) {
        if ("saas-boost".equals(event.get("source")) && "Tenant Resources Updated".equals(event.get("detail-type"))) {
            //Utils.logRequestEvent(event);
            Tenant updatedTenant = parseTenantUpdateResourcesEvent(event);
            Tenant tenant = dal.getTenant(updatedTenant.getId());
            Map<String, Tenant.Resource> resources = tenant.getResources();
            // Merge the updated resources with the existing ones. This helps the calling code not have to pull the
            // current tenant before invoking this method. If you want to replace/delete resources from a tenant,
            // you'll have to build the resources map you want and call updateTenant.
            resources.putAll(updatedTenant.getResources());
            tenant.setResources(resources);
            dal.updateTenant(tenant);
            LOGGER.info("Resources updated for tenant: {}", updatedTenant.getId());
        } else {
            LOGGER.error("Unknown event {}", Utils.toJson(event));
            throw new IllegalArgumentException("Unknown event");
        }
    }

    protected static Tenant parseTenantUpdateResourcesEvent(Map<String, Object> event) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String tenantId = (String) detail.get("tenantId");
        LOGGER.info("Processing Tenant Update Resources event for tenant {}", tenantId);
        if (Utils.isBlank(tenantId)) {
            LOGGER.error("Event detail is missing tenantId attribute");
            throw new RuntimeException(new IllegalArgumentException("Event detail is missing tenantId attribute"));
        }
        if (!detail.containsKey("resources")) {
            LOGGER.error("Event detail is missing resources attribute");
            throw new RuntimeException(new IllegalArgumentException("Event detail is missing resources attribute"));
        }
        Tenant tenant = new Tenant();
        tenant.setId(UUID.fromString(tenantId));
        Map<String, Object> updatedResources = Utils.fromJson((String) detail.get("resources"), LinkedHashMap.class);
        if (null == updatedResources) {
            throw new RuntimeException("Resources is invalid Json");
        }
        Map<String, Tenant.Resource> resources = new HashMap<>();
        for (Map.Entry<String, Object> resource : updatedResources.entrySet()) {
            Map<String, String> res = (Map<String, String>) resource.getValue();
            resources.put(resource.getKey(), new Tenant.Resource(res.get("name"), res.get("arn"),
                    res.get("consoleUrl")));
        }
        tenant.setResources(resources);
        return tenant;
    }

}