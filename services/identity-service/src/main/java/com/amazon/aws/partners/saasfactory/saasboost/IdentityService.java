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
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

public class IdentityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String IDENTITY_PROVIDER_CONFIG = System.getenv("IDENTITY_PROVIDER_CONFIG");
    private final IdentityDataAccessLayer dal;

    public IdentityService() {
        this(new DefaultDependencyFactory());
    }

    public IdentityService(IdentityServiceDependencyFactory init) {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_ENV");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = init.dal();
    }

    /**
     * Get available identity providers as config templates. Integration for GET /identity/providers endpoint
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
        List<IdentityProviderConfig> providers = dal.getAvailableProviders();
        response = new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(HttpURLConnection.HTTP_OK)
                .withBody(Utils.toJson(providers));

        return response;
    }

    /**
     * Returns the active Identity Provider configuration. Integration for GET /identity endpoint.
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
        IdentityProviderConfig providerConfig = dal.getProviderConfig();
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
     * Sets the active Identity Provider configuration. Integration for POST /identity endpoint.
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
        IdentityProviderConfig providerConfig = Utils.fromJson(event.getBody(), IdentityProviderConfig.class);
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
     * Event listener (EventBridge Rule target) for Events. The single public
     * entry point both reduces the number of EventBridge rules and simplifies debug logging.
     * @param event the EventBridge event
     */
    public void handleEvent(Map<String, Object> event) {
        Utils.logRequestEvent(event);
        String detailType = (String) event.get("detail-type");
        if (detailType.startsWith("Onboarding ")) {
            // Onboarding events that trigger identity system workflows
            // Use this entry point for consolidated logging of the identity service
            LOGGER.info("Handling Onboarding Event");
            handleOnboardingEvent(event);
        } else {
            LOGGER.error("Unknown event for detail-type {}", event.get("detail-type"));
        }
    }

    protected void handleOnboardingEvent(Map<String, Object> event) {
        String detailType = (String) event.get("detail-type");
        if ("Onboarding Tenant Assigned".equals(detailType)) {
            LOGGER.info("Handling Onboarding Tenant Assigned Event");
            handleOnboardingTenantAssignedEvent(event);
        }
    }

    protected void handleOnboardingTenantAssignedEvent(Map<String, Object> event) {
        IdentityProviderConfig providerConfig = dal.getProviderConfig();
        if (providerConfig != null) {
            IdentityProvider provider = IdentityProviderFactory.getInstance().getProvider(providerConfig);
            try {
                // First make sure our custom user attributes are set
                provider.getApi().addUserAttribute(UserAttribute.builder().name("tenant_id").build());
                provider.getApi().addUserAttribute(UserAttribute.builder().name("tier").build());

                Map<String, Object> detail = (Map<String, Object>) event.get("detail");
                Map<String, Object> tenant = (Map<String, Object>) detail.get("tenant");
                String tenantId = (String) tenant.getOrDefault("id", "");
                String tier = (String) tenant.getOrDefault("tier", "");
                List<Map<String, Object>> tenantUsers = (List<Map<String, Object>>) tenant.get("adminUsers");
                for (Map<String, Object> tenantUser : tenantUsers) {
                    User adminUser = provider.getApi().createUser(User.builder()
                            .tenantId(tenantId)
                            .tier(tier)
                            .username((String) tenantUser.get("username"))
                            .email((String) tenantUser.getOrDefault("email", null))
                            .phoneNumber((String) tenantUser.getOrDefault("phoneNumber", null))
                            .givenName((String) tenantUser.getOrDefault("givenName", null))
                            .familyName((String) tenantUser.getOrDefault("familyName", null))
                            .password(Utils.generatePassword(12))
                            .build()
                    );
                    LOGGER.info("Tenant Admin User Created");
                    LOGGER.info(Utils.toJson(adminUser));
                }
            } catch (Exception e) {
                LOGGER.error(Utils.getFullStackTrace(e));
                throw new IllegalArgumentException("Can't process tenant assigned event");
            }
        } else {
            LOGGER.warn("No Identity Provider has been set");
        }
    }

    interface IdentityServiceDependencyFactory {

        IdentityDataAccessLayer dal();
    }

    private static final class DefaultDependencyFactory implements IdentityServiceDependencyFactory {

        @Override
        public IdentityDataAccessLayer dal() {
            return new IdentityDataAccessLayer(Utils.sdkClient(SecretsManagerClient.builder(),
                    SecretsManagerClient.SERVICE_NAME), IDENTITY_PROVIDER_CONFIG);
        }

    }
}