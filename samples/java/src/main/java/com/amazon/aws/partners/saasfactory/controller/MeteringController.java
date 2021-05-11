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
package com.amazon.aws.partners.saasfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.time.Instant;
import java.util.Map;

@Controller
public class MeteringController {

    private final static ObjectMapper MAPPER = new ObjectMapper();
    private static final String PRODUCT_CODE = "ProductCode";
    private static final String TENANT_ID = "TenantId";
    private static final String QUANTITY = "Quantity";
    private static final String TIMESTAMP = "Timestamp";
    private String tenantId = System.getenv("TENANT_ID");
    private static final Logger LOGGER = LoggerFactory.getLogger(MeteringController.class);

    @GetMapping("/meter.html")
    @ResponseBody
    public String getMeter(@RequestParam String count, @RequestParam String productCode, Map<String, Object> model) {
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "Unknown";
        }

        LOGGER.info("getMeter: add meter value");

        //NB:  productCode is the internal product code. For each tenant, the DDB billing table has a config
        //     item to map internal product code to the Billing system subscription item.
        if (productCode == null) {
            productCode = "product_requests";
        }
        int meterVal = Integer.valueOf(count);
        long startTimeMillis = System.currentTimeMillis();

        try {
            ObjectNode systemApiRequest = MAPPER.createObjectNode();
            systemApiRequest.put(TENANT_ID, tenantId);
            systemApiRequest.put(PRODUCT_CODE, productCode);
            systemApiRequest.put(QUANTITY, meterVal);
            systemApiRequest.put(TIMESTAMP, Instant.now().toEpochMilli());     //epoch time in UTC
            putMeteringEvent(MAPPER.writeValueAsString(systemApiRequest));
            model.put("product", productCode);
            model.put("result", "Success");
        } catch (Exception e) {
            //LOGGER.error("JSON processing failed");
            //LOGGER.error(getFullStackTrace(ioe));
            model.put("result", "Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
        long milli = System.currentTimeMillis() - startTimeMillis;
        model.put("executionTime", (milli / 1000));
        return "meter";
    }

    /*
Put metering event on EventBridge
 */
    private void putMeteringEvent(String eventBridgeDetail) {
        try {
            final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
            if (SAAS_BOOST_EVENT_BUS == null || SAAS_BOOST_EVENT_BUS.isEmpty()) {
                throw new RuntimeException("Unable to put metering event, missing environment variable SAAS_BOOST_EVENT_BUS");
            }

            EventBridgeClient eventBridgeClient = null;
            try {
                eventBridgeClient = EventBridgeClient.builder()
                        .credentialsProvider(ContainerCredentialsProvider.builder().build())
                        .build();
            } catch (Exception e) {
                eventBridgeClient = EventBridgeClient.builder()
                        .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                        .build();
            }

            PutEventsRequestEntry systemApiCallEvent = PutEventsRequestEntry.builder()
                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                    .detailType("BILLING")
                    .source("saas-boost-" + tenantId)
                    .detail(eventBridgeDetail)
                    .build();
            PutEventsResponse eventBridgeResponse = eventBridgeClient.putEvents(r -> r
                    .entries(systemApiCallEvent)
            );
            for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                    System.out.println(String.format("Put event success ", entry.toString(), systemApiCallEvent.toString()));
                } else {
                    System.err.println(String.format("Put event failed {}", entry.toString()));
                }
            }
        } catch (SdkServiceException eventBridgeError) {
            // LOGGER.error("events::PutEvents");
            // LOGGER.error(getFullStackTrace(eventBridgeError));
            throw eventBridgeError;
        }
    }
}
