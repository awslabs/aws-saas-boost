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

import com.amazon.aws.partners.saasfactory.saasboost.dal.TierDataStore;
import com.amazon.aws.partners.saasfactory.saasboost.dal.ddb.DynamoTierDataStore;
import com.amazon.aws.partners.saasfactory.saasboost.dal.exception.TierNotFoundException;
import com.amazon.aws.partners.saasfactory.saasboost.model.Tier;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.Map;

public class TierService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TierService.class);
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    // private static final String DATASTORE_IMPL = System.getenv("DATASTORE_IMPL");

    // private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    // private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    // private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    // private static final String SYSTEM_API_CALL_DETAIL_TYPE = "System API Call";
    // private static final String SYSTEM_API_CALL_SOURCE = "saas-boost";

    private final TierDataStore store;

    // TODO EventBridge will eventually be needed to send tier definition change events
    // private final EventBridgeClient eventBridge;

    /*
     * getTiers, getTier, createTier, updateTier, deleteTier
     * each requires access to the backing DDB datastore
     *
     * DDB table needs to be passed as environment variable
     *
     * getTiers, getTier, createTier, updateTier, deleteTier
     * are all kind of ONLY DDB datastore actions
     * except maybe update.
     * either way, we should have a Tier object. JSON serializable. and DDB serializable.
     */
    public TierService() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isEmpty(TABLE_NAME)) {
            throw new IllegalStateException("Missing environment variable TIERS_TABLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));

        // this.store = TierDataStoreFactory.getInstance(DATASTORE_IMPL); // creates required clients in factory method
        this.store = new DynamoTierDataStore(
                Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME),
                TABLE_NAME);

        // this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent getTiers(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);
        List<Tier> tiers = store.listTiers();
        if (tiers.isEmpty()) {
            // we want to ensure there is always at least a default tier.
            tiers.add(store.createTier(Tier.builder().name("default").description("Default Tier").build()));
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TierService::getTiers exec " + totalTimeMillis);
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(tiers));
    }

    public APIGatewayProxyResponseEvent getTier(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);
        Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");
        Tier foundTier = null;
        try {
            foundTier = store.getTier(pathParams.get("id"));
        } catch (TierNotFoundException tnfe) {
            LOGGER.error("Tier not found", tnfe);
            long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
            LOGGER.info("TierService::getTier exec " + totalTimeMillis);
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(404)
                    .withBody("{\"message\":\"Tier not found.\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TierService::getTier exec " + totalTimeMillis);
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(foundTier));
    }

    public APIGatewayProxyResponseEvent createTier(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        final long startTimeMillis = System.currentTimeMillis();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(CORS);
        Utils.logRequestEvent(event);
        Tier newTier = Utils.fromJson((String) event.get("body"), Tier.class);
        if (newTier == null) {
            // Utils.fromJson swallows and logs any exceptions coming from deserialization attempts
            return response.withStatusCode(400).withBody("{\"message\":\"Body should represent a Tier.\"}");
        }
        Tier createdTier = store.createTier(newTier);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TierService::createTier exec " + totalTimeMillis);
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(Utils.toJson(createdTier));
    }

    public APIGatewayProxyResponseEvent updateTier(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);
        Tier updatedTier = Utils.fromJson((String) event.get("body"), Tier.class);
        if (updatedTier == null) {
            // Utils.fromJson swallows and logs any exceptions coming from deserialization attempts
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Body should represent a Tier.\"}");
        }
        Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");
        if (!pathParams.get("id").equals(updatedTier.getId())) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Tier IDs are immutable: body Tier ID must match path parameter.\"}");
        }
        try {
            store.updateTier(updatedTier);
        } catch (TierNotFoundException tnfe) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(404)
                    .withBody("{\"message\":\"Tier not found.\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TierService::updateTier exec " + totalTimeMillis);
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent deleteTier(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);
        Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");
        try {
            store.deleteTier(pathParams.get("id"));
        } catch (TierNotFoundException tnfe) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(404)
                    .withBody("{\"message\":\"Tier not found.\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("TierService::deleteTier exec " + totalTimeMillis);
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200);
    }
}