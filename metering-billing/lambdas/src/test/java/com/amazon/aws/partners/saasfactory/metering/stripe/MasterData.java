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
package com.amazon.aws.partners.saasfactory.metering.stripe;

import com.amazon.aws.partners.saasfactory.metering.common.EventBridgeEvent;
import com.amazon.aws.partners.saasfactory.metering.common.MeteredProduct;
import com.amazon.aws.partners.saasfactory.metering.common.SubscriptionPlan;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class MasterData {

    public static void main(String[] args) throws Exception {
        Stripe.apiKey = "sk_test_51H4VPdJdbgnT4OooRD5fI9AVKpDGk4ObZDELk7JmYcoCHLm4hu8LZF6yYr8ATtStp7OpjuwwZoQWV0gWbe8573Da000Bxf0RIH";
        try {
            Product productFetch = Product.retrieve(MeteredProduct.product_requests.name());
            System.out.println("Product found");
        } catch (StripeException e) {
            System.out.println("Exception");
        }
        System.exit(1);
        String myJson = "{\n" +
                "    \"version\": \"0\",\n" +
                "    \"id\": \"8a023fbc-2460-a26c-d822-595b2764c21b\",\n" +
                "    \"detail-type\": \"Tenant Product Onboard\",\n" +
                "    \"source\": \"saas-boost\",\n" +
                "    \"account\": \"573838506705\",\n" +
                "    \"time\": \"2020-08-26T00:10:36Z\",\n" +
                "    \"region\": \"us-west-2\",\n" +
                "    \"resources\": [],\n" +
                "    \"detail\": {\n" +
                "        \"tenantId\": \"de4123307-a7e8-48ca-a9c4-d457042a7198\",\n" +
                "        \"internalProductCode\": \"product_requests\",\n" +
                "        \"externalProductCode\": \"si_Hu0EoSmoyEhGnk\",\n" +
                "        \"timestamp\": 1598400635770\n" +
                "    }\n" +
                "}";

        EventBridgeEvent event = Utils.fromJson(myJson, EventBridgeEvent.class);
        System.out.println(Utils.toJson(event));

        List<String> planMap = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        ArrayNode plans = JsonNodeFactory.instance.arrayNode();
        for (SubscriptionPlan plan : SubscriptionPlan.values()) {
            builder.append("{\"planId\":\"");
            builder.append(plan.name());
            builder.append("\", \"planName\":\"");
            builder.append(plan.getLabel());
            builder.append("\"},");
            planMap.add("{planId:" + plan.name() + ", \"planName\":\"" + plan.getLabel() + "\"}");
            ObjectNode planN = JsonNodeFactory.instance.objectNode();
            planN.put("planId", plan.name());
            planN.put("planName", plan.getLabel());
            plans.add(planN);
        }

        System.out.println("from plans:" + Utils.toJson(plans));

        Map<String, Object> x = new HashMap<>();
        x.put("plans", builder.toString());
        System.out.println(Utils.toJson(x));

        System.out.println("plan Map as string\n" + Utils.toJson(planMap));
        System.exit(0);

        // Update the onboarding status from provisioning to provisioned

        String tenantId = "tenant1122q22";
        String stackStatus = "succeeded";

        StringBuffer resourcesSb = new StringBuffer();
            resourcesSb
            .append("{")
            .append("\"")
            .append("keyVal1")
            .append("\":\"")
            .append("urlval1")
            .append("\"");
        resourcesSb
                .append(",")
                .append("\"")
                .append("keyVal2")
                .append("\":\"")
                .append("urlval2")
                .append("\"}");

/*        resourcesSb
                .append("{")
                .append("keyVal1")
                .append(":")
                .append("urlval1");
        resourcesSb
                .append(",")
                .append("keyVal2")
                .append(":")
                .append("urlval2")
                .append("ß}");*/
        String resources = resourcesSb.toString();
            String physicalResourceId = "/ecs/afdadfddsfdf";

            System.out.println(physicalResourceId.replaceAll("/", Matcher.quoteReplacement("$252F")));
            Map<String, Object> systemApiRequest = new HashMap<>();
//            systemApiRequest.put("resource", "onboarding/status");
 //           systemApiRequest.put("method", "PUT");
            systemApiRequest.put("resources", resources);
            String jsonText = Utils.toJson(systemApiRequest);
            System.out.println("systemApiRequest = " + jsonText);
            System.out.println("resources = " + Utils.toJson(resources));
/*
            Map<String,String> map = Utils.fromJson(systemApiRequest.get("resources").textValue(), Map.class);
            Map<String,String> map2 = Utils.fromJson(resourcesSb.toString() , Map.class);
*/
            Map<String, Object> map3 = Utils.fromJson(jsonText, HashMap.class);

            System.out.println("test");
    }

}
