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
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BillingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillingService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String API_APP_CLIENT = System.getenv("API_APP_CLIENT");
    private static final String BILLING_TABLE = System.getenv("BILLING_TABLE");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String EVENT_SOURCE = "saas-boost";
    private static final String BILLING_PROVIDER_CONFIG = System.getenv("BILLING_PROVIDER_CONFIG");
    private final BillingDataAccessLayer dal;
    private final EventBridgeClient eventBridge;
    private ApiGatewayHelper api;

    public BillingService() {
        this(new DefaultDependencyFactory());
    }

    // Facilitates testing by being able to mock out AWS SDK dependencies
    public BillingService(BillingServiceDependencyFactory init) {
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        if (Utils.isBlank(BILLING_TABLE)) {
            throw new IllegalStateException("Missing environment variable BILLING_TABLE");
        }
        if (Utils.isBlank(BILLING_PROVIDER_CONFIG)) {
            throw new IllegalStateException("Missing environment variable BILLING_PROVIDER_CONFIG");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = init.dal();
        this.eventBridge = init.eventBridge();
    }

    /**
     * Get billing plans for the active provider. Integration for GET /billing/plans endpoint
     * @param event API Gateway proxy request event
     * @param context Lambda function context
     * @return List of billing plan objects
     */
    public APIGatewayProxyResponseEvent getPlans(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        //Utils.logRequestEvent(event);
        List<BillingPlan> plans = dal.getPlans();
        APIGatewayProxyResponseEvent response;
        response = new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(HttpURLConnection.HTTP_OK)
                .withBody(Utils.toJson(plans));
        return response;
    }

    /**
     * Get available billing providers as config templates. Integration for GET /billing/providers endpoint
     * @param event API Gateway proxy request event
     * @param context Lambda function context
     * @return List of onboarding objects
     */
    public APIGatewayProxyResponseEvent getProviders(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        List<BillingProviderConfig> providers = dal.getAvailableProviders();
        response = new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(HttpURLConnection.HTTP_OK)
                .withBody(Utils.toJson(providers));

        return response;
    }

    /**
     * Returns the active Billing Provider configuration. Integration for GET /billing/provider endpoint.
     * @param event API Gateway proxy request event
     * @param context Lambda function context
     * @return
     */
    public APIGatewayProxyResponseEvent getProvider(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        BillingProviderConfig providerConfig = dal.getProviderConfig();
        if (providerConfig != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_OK)
                    .withBody(Utils.toJson(providerConfig));
        } else {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
        }

        return response;
    }

    /**
     * Sets the active Billing Provider configuration. Integration for POST /billing/provider endpoint.
     * @param event API Gateway proxy request event
     * @param context Lambda function context
     * @return
     */
    public APIGatewayProxyResponseEvent setProvider(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        //Utils.logRequestEvent(event);
        BillingProviderConfig providerConfig = Utils.fromJson(event.getBody(), BillingProviderConfig.class);
        APIGatewayProxyResponseEvent response;
        if (providerConfig == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(Map.of("message", "Invalid request body")));
        } else {
            dal.setProviderConfig(providerConfig);
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_CREATED);
        }
        return response;
    }

    /**
     * Event listener (EventBridge Rule target) for Billing Events. The single
     * public entry point both reduces the number of rules and simplifies debug logging.
     * @param event the EventBridge event
     */
    public void handleBillingEvent(Map<String, Object> event) {
        Utils.logRequestEvent(event);
        if (BillingEvent.validate(event)) {
            String detailType = (String) event.get("detail-type");
            BillingEvent billingEvent = BillingEvent.fromDetailType(detailType);
            if (billingEvent != null) {
                switch (billingEvent) {
                    default:
                        LOGGER.error("Unknown Billing Event!");
                }
            } else if (detailType.startsWith("Tenant ")) {
                // Tenant events that trigger billing system workflows
                // Use this entry point for consolidated logging of the billing system
                LOGGER.info("Handling Tenant Event");
                handleTenantEvent(event);
            } else {
                LOGGER.error("Can't find billing event for detail-type {}", event.get("detail-type"));
                // TODO Throw here? Would end up in DLQ.
            }
        } else {
            LOGGER.error("Invalid SaaS Boost Billing Event " + Utils.toJson(event));
            // TODO Throw here? Would end up in DLQ.
        }
    }

    protected void handleTenantEvent(Map<String, Object> event) {
        String detailType = (String) event.get("detail-type");
        if ("Tenant Onboarding Status Changed".equals(detailType)) {
            LOGGER.info("Handling Tenant Onboarding Status Changed Event");
            handleTenantOnboardingStatusChangedEvent(event);
        }
    }

    protected void handleTenantOnboardingStatusChangedEvent(Map<String, Object> event) {
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String onboardingStatus = (String) detail.get("onboardingStatus");
        if ("deployed".equals(onboardingStatus)) {
            String tenantId = (String) detail.get("tenantId");
            if (Utils.isNotBlank(tenantId)) {
                Map<String, Object> tenant = getTenant(tenantId);
                if (tenant != null) {
                    String tierName = (String) tenant.getOrDefault("tier", "");
                    Map<String, Object> tier = getTier(tierName);
                    if (tier != null && tier.containsKey("billingPlan")) {
                        // TODO wire-up actual billing provider subscription for a given plan
                    } else {
                        LOGGER.info("Tenant {} is subscribed to Tier {} with no billing plan", tenant, tierName);
                    }
                    // Let everyone know this tenant is subscribed
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            BillingEvent.BILLING_SUBSCRIBED.detailType(),
                            Map.of("tenantId", tenantId)
                    );
                } else {
                    LOGGER.error("Can't fetch tenant from TenantService {}", detail.get("tenantId"));
                }
            } else {
                LOGGER.error("Missing tenantId in event detail {}", Utils.toJson(event.get("detail")));
            }
        }
    }

    protected Map<String, Object> getTenant(String tenantId) {
        LOGGER.info("Calling tenant service to fetch tenant by id {}", tenantId);
        LinkedHashMap<String, Object> tenant = null;
        if (Utils.isNotBlank(tenantId)) {
            ApiGatewayHelper api = getApiGatewayHelper();
            String getTenantResponseBody = api.authorizedRequest("GET", "tenants/" + tenantId);
            tenant = Utils.fromJson(getTenantResponseBody, LinkedHashMap.class);
        }
        return tenant;
    }

    protected Map<String, Object> getTier(String tierName) {
        LOGGER.info("Calling tier service to fetch tier by id {}", tierName);
        LinkedHashMap<String, Object> tier = null;
        if (Utils.isNotBlank(tierName)) {
            ApiGatewayHelper api = getApiGatewayHelper();
            String getTierResponseBody = api.authorizedRequest("GET", "tiers?name="
                    + URLEncoder.encode(tierName, StandardCharsets.UTF_8));
            tier = Utils.fromJson(getTierResponseBody, LinkedHashMap.class);
        }
        return tier;
    }

    private ApiGatewayHelper getApiGatewayHelper() {
        if (this.api == null) {
            this.api = ApiGatewayHelper.clientCredentialsHelper(API_APP_CLIENT);
        }
        return this.api;
    }

    interface BillingServiceDependencyFactory {

        BillingDataAccessLayer dal();

        EventBridgeClient eventBridge();
    }

    private static final class DefaultDependencyFactory implements BillingServiceDependencyFactory {

        @Override
        public BillingDataAccessLayer dal() {
            return new BillingDataAccessLayer(
                    Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME),
                    BILLING_TABLE,
                    Utils.sdkClient(SecretsManagerClient.builder(), SecretsManagerClient.SERVICE_NAME),
                    BILLING_PROVIDER_CONFIG);
        }

        @Override
        public EventBridgeClient eventBridge() {
            return Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        }
    }
}
