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

package com.amazon.aws.partners.saasfactory.metering.onboarding;

import com.amazon.aws.partners.saasfactory.metering.common.BillingUtils;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.model.ProductCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SubscriptionService {
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionService.class);
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");

    public SubscriptionService() {
        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
    }

    public APIGatewayProxyResponseEvent getPlans(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);

        APIGatewayProxyResponseEvent response;
        Stripe.apiKey = BillingUtils.getBillingApiKey(API_GATEWAY_HOST, API_GATEWAY_STAGE, API_TRUST_ROLE);
        if (Stripe.apiKey != null) {
            try {
                ArrayNode plans = JsonNodeFactory.instance.arrayNode();
                ProductCollection products = Product.list(new HashMap<>());
                for (Product product : products.getData()) {
                    // TODO in the eventual refactor the Plan returned by this function should be a POJO we construct
                    if (product.getActive()) {
                        ObjectNode productNode = JsonNodeFactory.instance.objectNode();
                        productNode.put("planId", product.getId());
                        productNode.put("planName", product.getName());
                        plans.add(productNode);
                    }
                }
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(200)
                        .withBody(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(plans));
            } catch (StripeException se) {
                LOGGER.error("Error listing products {}", se);
                LOGGER.error(Utils.getFullStackTrace(se));
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(Map.of("message", "Error listing products from Stripe")));
            } catch (JsonProcessingException jpe) {
                LOGGER.error("Unable to generate JSON list of plans {}", jpe);
                LOGGER.error(Utils.getFullStackTrace(jpe));
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(500)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(Map.of("message", "Error retrieving products, view logs for details.")));
            }
        } else {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(404)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(Map.of("message", "No Stripe API key configured")));
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SubscriptionService::getPlans exec " + totalTimeMillis);

        return response;
    }
}
