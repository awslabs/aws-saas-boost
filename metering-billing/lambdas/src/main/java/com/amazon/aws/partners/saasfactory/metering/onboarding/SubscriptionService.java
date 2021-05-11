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

import com.amazon.aws.partners.saasfactory.metering.common.SubscriptionPlan;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SubscriptionService {
    private final static Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<String, String>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private final static Logger LOGGER = LoggerFactory.getLogger(SubscriptionService.class);

    public SubscriptionService() {
        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
    }
    public APIGatewayProxyResponseEvent getPlans(Map<String, Object> event, Context context) throws JsonProcessingException {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("SubscriptionService::getPlans starting");

        // create an array of key-value pairs
        //ObjectMapper MAPPER = new ObjectMapper();

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        ArrayNode plans = JsonNodeFactory.instance.arrayNode();
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            ObjectNode planN = JsonNodeFactory.instance.objectNode();
            planN.put("planId", plan.name());
            planN.put("planName", plan.getLabel());
            plans.add(planN);
        }

        response = new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(plans));

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("SubscriptionService::getPlans exec " + totalTimeMillis);

        return response;
    }
}
