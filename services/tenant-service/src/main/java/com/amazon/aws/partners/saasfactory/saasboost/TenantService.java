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

    private final static Logger LOGGER = LoggerFactory.getLogger(TenantService.class);
    private final static Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<String, String>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String SYSTEM_API_CALL_DETAIL_TYPE = "System API Call";
    private static final String TENANT_STATUS_CHANGE_DETAIL_TYPE = "Tenant Status Update";
    private static final String EVENT_SOURCE = "saas-boost";
    private final TenantServiceDAL dal;
    private final EventBridgeClient eventBridge;

    public TenantService() {
        long startTimeMillis = System.currentTimeMillis();
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

        long startTimeMillis = System.currentTimeMillis();
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

        long startTimeMillis = System.currentTimeMillis();
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

        long startTimeMillis = System.currentTimeMillis();
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

        long startTimeMillis = System.currentTimeMillis();
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
                    Map<String, Object> systemApiRequest = new HashMap<>();
                    systemApiRequest.put("resource", "onboarding/update/tenant");
                    systemApiRequest.put("method", "PUT");
                    systemApiRequest.put("body", Utils.toJson(tenant));
                    fireEvent(SYSTEM_API_CALL_DETAIL_TYPE, systemApiRequest);
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

        long startTimeMillis = System.currentTimeMillis();
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

        long startTimeMillis = System.currentTimeMillis();
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

            // Send EventBridge message so we can take action on enable/disable
            Map<String, Object> tenantStatusChangeDetails = new HashMap<>();
            tenantStatusChangeDetails.put("tenantId", tenantId);
            tenantStatusChangeDetails.put("status", Boolean.TRUE);
            LOGGER.info("Publishing tenant status change event for {} to {}", tenantStatusChangeDetails.get("tenantId"), tenantStatusChangeDetails.get("status"));
            fireEvent(TENANT_STATUS_CHANGE_DETAIL_TYPE, tenantStatusChangeDetails);

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

        long startTimeMillis = System.currentTimeMillis();
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

            // Send EventBridge message so we can take action on enable/disable
            Map<String, Object> tenantStatusChangeDetails = new HashMap<>();
            tenantStatusChangeDetails.put("tenantId", tenantId);
            tenantStatusChangeDetails.put("status", Boolean.FALSE);
            LOGGER.info("Publishing tenant status change event for {} to {}", tenantStatusChangeDetails.get("tenantId"), tenantStatusChangeDetails.get("status"));
            fireEvent(TENANT_STATUS_CHANGE_DETAIL_TYPE, tenantStatusChangeDetails);

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

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::insertTenant");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        Tenant tenant = null;
        // Were we called from Step Functions or API Gateway?
        if (event.containsKey("body")) {
            tenant = Utils.fromJson((String) event.get("body"), Tenant.class);
            if (null == tenant) {
                throw new RuntimeException("Body is not a valid tenant json");
            }
        } else if (event.containsKey("tenant")) {
            // It's already been deserialized to a Map by Lambda so we'd have
            // to serialize to a string and then back out to a Tenant object
            // if we wanted to use Jackson...
            Map<String, Object> input = (Map<String, Object>) event.get("tenant");
            tenant = new Tenant();
            tenant.setName((String) input.get("name"));
            tenant.setActive((Boolean) input.get("active"));
            tenant.setOnboardingStatus((String) input.get("onboardingStatus"));
            tenant.setSubdomain((String) input.get("subdomain"));
            tenant.setPlanId((String) input.get("planId"));
            tenant.setOverrideDefaults((Boolean) input.get("overrideDefaults"));
            if (tenant.getOverrideDefaults()) {
                tenant.setComputeSize((String) input.get("computeSize"));
                if (input.get("memory") != null) {
                    try {
                        tenant.setMemory((Integer) input.get("memory"));
                    } catch (NumberFormatException nfe) {
                        LOGGER.error("Can't set memory to value {}", input.get("memory"));
                        LOGGER.error(Utils.getFullStackTrace(nfe));
                    }
                }
                if (input.get("cpu") != null) {
                    try {
                        tenant.setCpu((Integer) input.get("cpu"));
                    } catch (NumberFormatException nfe) {
                        LOGGER.error("Can't set CPU to value {}", input.get("cpu"));
                        LOGGER.error(Utils.getFullStackTrace(nfe));
                    }
                }
                if (input.get("minCount") != null) {
                    try {
                        tenant.setMinCount((Integer) input.get("minCount"));
                    } catch (NumberFormatException nfe) {
                        LOGGER.error("Can't set min task count to value {}", input.get("minCount"));
                        LOGGER.error(Utils.getFullStackTrace(nfe));
                    }
                }
                if (input.get("maxCount") != null) {
                    try {
                        tenant.setMaxCount((Integer) input.get("maxCount"));
                    } catch (NumberFormatException nfe) {
                        LOGGER.error("Can't set max task count to value {}", input.get("maxCount"));
                        LOGGER.error(Utils.getFullStackTrace(nfe));
                    }
                }
            }
        }

        if (tenant == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS);
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

        long startTimeMillis = System.currentTimeMillis();
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

                //fire event to onboarding service to delete the stack
                LOGGER.info("Triggering tenant stack delete event");
                Map<String, Object> deleteTenantEventDetail = new HashMap<>();
                deleteTenantEventDetail.put("tenantId", tenantId);
                publishEvent(deleteTenantEventDetail, "Delete Tenant");

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

    private void publishEvent(Map<String, Object> eventBridgeDetail, String detailType) {
        try {
            PutEventsRequestEntry systemEvent = PutEventsRequestEntry.builder()
                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                    .detailType(detailType)
                    .source(EVENT_SOURCE)
                    .detail(Utils.toJson(eventBridgeDetail))
                    .build();
            PutEventsResponse eventBridgeResponse = eventBridge.putEvents(r -> r
                    .entries(systemEvent)
            );
            for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                    LOGGER.info("Put event success {} {}", entry.toString(), systemEvent.toString());
                } else {
                    LOGGER.error("Put event failed {}", entry.toString());
                }
            }
        } catch (SdkServiceException eventBridgeError) {
            LOGGER.error("events::PutEvents", eventBridgeError);
            LOGGER.error(Utils.getFullStackTrace(eventBridgeError));
            throw eventBridgeError;
        }
    }

    //handles the event for 'Tenant Update Resources'
    public Object updateTenantResources(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("TenantService::updateTenantResources");
        //Utils.logRequestEvent(event);
        Tenant tenant = parseTenantUpdateResourcesEvent(event);
        //load current tenant record and update the resources field
        Tenant currentTenant = dal.getTenant(tenant.getId());
        currentTenant.setResources(tenant.getResources());
        dal.updateTenant(currentTenant);
        LOGGER.info("TenantService::updateTenantResources - Updated resources for tenant: {}", tenant.getId());
        return null;
    }

    static Tenant parseTenantUpdateResourcesEvent(Map<String, Object> event) {
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
        Map<String, String> resourcesMap = Utils.fromJson((String) detail.get("resources"), HashMap.class);
        if (null == resourcesMap) {
            throw new RuntimeException("Resources is invalid Json");
        }
        tenant.setResources(resourcesMap);
        return tenant;
    }

    private void fireEvent(String type, Map<String, Object> detail) {
        try {
            PutEventsRequestEntry event = PutEventsRequestEntry.builder()
                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                    .source("saas-boost")
                    .detailType(type)
                    .detail(Utils.toJson(detail))
                    .build();
            PutEventsResponse eventBridgeResponse = eventBridge.putEvents(request -> request
                    .entries(event)
            );
            for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                    LOGGER.info("Put event success {} {}", entry.toString(), event.toString());
                } else {
                    LOGGER.error("Put event failed {}", entry.toString());
                }
            }
        } catch (SdkServiceException eventBridgeError) {
            LOGGER.error("events::PutEvents");
            LOGGER.error(Utils.getFullStackTrace(eventBridgeError));
            throw eventBridgeError;
        }
    }
}