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
import java.util.stream.Collectors;

public class TierService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TierService.class);
    private static final String TIERS_TABLE = System.getenv("TIERS_TABLE");
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");

    private final TierDataStore store;

    public TierService() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isEmpty(TIERS_TABLE)) {
            throw new IllegalStateException("Missing environment variable TIERS_TABLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));

        this.store = new DynamoTierDataStore(
                Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME),
                TIERS_TABLE);

        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent getTiers(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);
        List<Tier> tiers = store.listTiers();
        if (tiers.isEmpty()) {
            // we want to ensure there is always at least a default tier.
            tiers.add(
                    store.createTier(Tier.builder()
                            .name("default")
                            .description("Default Tier")
                            .defaultTier(true)
                            .build()
                    )
            );
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
        if (createdTier.defaultTier()) {
            enforceSingleDefaultTier(createdTier);
        }
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
        Tier providedTier = Utils.fromJson((String) event.get("body"), Tier.class);
        if (providedTier == null) {
            // Utils.fromJson swallows and logs any exceptions coming from deserialization attempts
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Body should represent a Tier.\"}");
        }
        Map<String, String> pathParams = (Map<String, String>) event.get("pathParameters");
        if (!pathParams.get("id").equals(providedTier.getId())) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Tier IDs are immutable: body Tier ID must match path parameter.\"}");
        }
        Tier updatedTier = providedTier;
        try {
            Tier oldTier = store.getTier(providedTier.getId());
            // TODO validate that user isn't trying to update fields that should not be updated, e.g. created, id
            updatedTier = store.updateTier(providedTier);
            if (!oldTier.defaultTier() && updatedTier.defaultTier()) {
                // we weren't default but now we are, this means all other default
                // Tiers should be updated to no longer be default,
                // as we need to enforce only one default Tier at a given time
                enforceSingleDefaultTier(updatedTier);
            }
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
                .withStatusCode(200)
                .withBody(Utils.toJson(updatedTier));
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

    public void enforceSingleDefaultTier(Tier defaultTier) {
        List<Tier> defaultTiers = store.listTiers().stream()
                .filter(tier -> tier.defaultTier())
                .collect(Collectors.toList());
        for (Tier t : defaultTiers) {
            if (!t.getId().equals(defaultTier.getId())) {
                try {
                    // TODO in the event that multiple users try to update tiers at the same time, different
                    // TODO lambda invocations may step on each other. to get around this use DDB TransactWriteItems
                    store.updateTier(Tier.builder(t).defaultTier(false).build());
                } catch (TierNotFoundException tnfe) {
                    // race condition between the list we just pulled and the update
                    LOGGER.error("Could not enforce a single default tier."
                            + " Found {} default tiers but {} does not exist.", defaultTiers, t);
                }
            }
        }
    }
}