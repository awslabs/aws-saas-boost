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

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TierService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TierService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String TIERS_TABLE = System.getenv("TIERS_TABLE");
    private final TierDataAccessLayer dal;

    public TierService() {
        this(new DefaultDependencyFactory());
    }

    // Facilitates testing by being able to mock out AWS SDK dependencies
    public TierService(TierServiceDependencyFactory init) {
        if (Utils.isBlank(TIERS_TABLE)) {
            throw new IllegalStateException("Missing environment variable TIERS_TABLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = init.dal();
    }

    /**
     * Get tiers. Integration for GET /tiers endpoint.
     * Can be filtered to search for tier by name using GET /tiers?name={name}
     * @param event API Gateway proxy request event
     * @param context
     * @return List of tier objects
     */
    public APIGatewayProxyResponseEvent getTiers(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        Map<String, String> params = event.getQueryStringParameters();
        if (params != null && params.containsKey("name")) {
            Tier tier = dal.getTierByName(params.get("name"));
            if (tier ==  null) {
                return new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
            } else {
                return new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(HttpURLConnection.HTTP_OK)
                        .withBody(Utils.toJson(tier));
            }
        } else {
            List<Tier> tiers = dal.getTiers();
            if (tiers.isEmpty()) {
                // We want to ensure there is always at least a default tier.
                Tier defaultTier = new Tier();
                defaultTier.setDefaultTier(true);
                defaultTier.setName("default");
                defaultTier.setDescription("Default Tier");
                tiers.add(dal.insertTier(defaultTier));
            }
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_OK)
                    .withBody(Utils.toJson(tiers));
        }
    }

    /**
     * Get tier by id. Integration for GET /tiers/{id} endpoint.
     * @param event API Gateway proxy request event containing an id path parameter
     * @param context
     * @return Tier object for id or HTTP 404 if not found
     */
    public APIGatewayProxyResponseEvent getTier(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        APIGatewayProxyResponseEvent response;
        Map<String, String> params = event.getPathParameters();
        String tierId = params.get("id");
        Tier tier = dal.getTier(tierId);
        if (tier != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_OK)
                    .withBody(Utils.toJson(tier));
        } else {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
        }

        return response;
    }

    /**
     * Update a tier by id. Integration for PUT /tiers/{id} endpoint.
     * @param event API Gateway proxy request event containing an id path parameter
     * @param context
     * @return HTTP 200 if updated, HTTP 400 on failure
     */
    public APIGatewayProxyResponseEvent updateTier(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = event.getPathParameters();
        String tierId = params.get("id");
        Tier tier = Utils.fromJson(event.getBody(), Tier.class);
        if (tier == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(Map.of("message", "Invalid request body")));
        } else {
            if (tier.getId() == null || !tier.getId().toString().equals(tierId)) {
                LOGGER.error("Can't update tier {} at resource {}", tier.getId(), tierId);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(Map.of("message", "Request body must include id")));
            } else {
                Tier existing = dal.getTier(tierId);
                if (existing == null) {
                    LOGGER.error("Can't update tier non-existent tier {}", tierId);
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(Map.of("message", "No tier with id " + tierId)));
                } else {
                    if (!existing.isDefaultTier() && tier.isDefaultTier()) {
                        // we weren't default but now we are, this means all other default
                        // Tiers should be updated to no longer be default,
                        // as we need to enforce only one default Tier at a given time
                        enforceSingleDefaultTier(tier);
                    }
                    tier = dal.updateTier(tier);
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpURLConnection.HTTP_OK)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(tier));
                }
            }
        }

        return response;
    }

    /**
     * Inserts a new tier. Integration for POST /tiers endpoint
     * @param event API Gateway proxy request event containing a Tier object in the request body
     * @param context
     * @return Tier object in a created state or HTTP 400 if the request does not contain a name
     */
    public APIGatewayProxyResponseEvent insertTier(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        Tier tier = Utils.fromJson(event.getBody(), Tier.class);
        if (null == tier) {
            LOGGER.error("Tier request is invalid");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid request body.\"}");
        }
        if (Utils.isBlank(tier.getName())) {
            LOGGER.error("Tier is missing name");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Tier name is required.\"}");
        }

        tier = dal.insertTier(tier);
        if (tier.isDefaultTier()) {
            enforceSingleDefaultTier(tier);
        }
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(HttpURLConnection.HTTP_CREATED)
                .withBody(Utils.toJson(tier));
    }

    /**
     * Delete a tier by id. Integration for DELETE /tier/{id} endpoint.
     * @param event API Gateway proxy request event containing an id path parameter
     * @param context
     * @return HTTP 204 if deleted, HTTP 400 on failure
     */
    public APIGatewayProxyResponseEvent deleteTier(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = event.getPathParameters();
        String tierId = params.get("id");
        Tier tier = dal.getTier(tierId);
        if (tier == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(Map.of("message", "Invalid tier id")));
        } else {
            try {
                dal.deleteTier(tier);
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(HttpURLConnection.HTTP_NO_CONTENT); // No content
            } catch (Exception e) {
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                        .withBody(Utils.toJson(Map.of("message", "Failed to delete tier " + tierId)));
            }
        }
        return response;
    }

    public void enforceSingleDefaultTier(Tier defaultTier) {
        List<Tier> defaultTiers = dal.getTiers().stream()
                .filter(tier -> tier.isDefaultTier())
                .collect(Collectors.toList());
        for (Tier t : defaultTiers) {
            if (!t.getId().equals(defaultTier.getId())) {
                // TODO in the event that multiple users try to update tiers at the same time, different
                // TODO lambda invocations may step on each other. to get around this use DDB TransactWriteItems
                t.setDefaultTier(false);
                dal.updateTier(t);
            }
        }
    }

    interface TierServiceDependencyFactory {

        TierDataAccessLayer dal();
    }

    private static final class DefaultDependencyFactory implements TierServiceDependencyFactory {

        @Override
        public TierDataAccessLayer dal() {
            return new TierDataAccessLayer(Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME),
                    TIERS_TABLE);
        }
    }
}