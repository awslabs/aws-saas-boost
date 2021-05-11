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

import com.amazon.aws.partners.saasfactory.domain.metrics.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.regions.Region;

import java.util.Map;

@Controller
public class MetricsController {

    private String tenantId = System.getenv("TENANT_ID");
    private final static Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));
    private final String streamName = System.getenv("METRICS_STREAM");
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsController.class);

    @GetMapping("/metric.html")
    @ResponseBody
    public String getMetric(@RequestParam String type, @RequestParam String tier, Map<String, Object> model) {
        if (tenantId == null || tenantId.isEmpty()) {
            tenantId = "Unknown";
        }

        LOGGER.info("getMetric: log metric event");

        if (null == streamName || streamName.isEmpty() || "N/A".equalsIgnoreCase(streamName)) {
            return "metric";
        }

        String metricName = "";
        long value = 0l;
        String unit = "";
        if ("storage".equalsIgnoreCase(type)) {
            metricName="storage";
            value = 20l;
            unit = "MB";
        } else if ("transfer".equalsIgnoreCase(type)) {
            metricName="transfer";
            value = 1000l;
            unit = "mb";
        }

        LOGGER.info("getMetric: Metric Name: " + metricName);

        long startTimeMillis = System.currentTimeMillis();

        MetricEvent event = new MetricEventBuilder()
                .withType(MetricEvent.Type.Application)
                .withWorkload("Application")
                .withContext("TestMetrics")
                .withMetric(new MetricBuilder()
                        .withName(metricName)
                        .withUnit(unit)  //update to the desired unit for the metric
                        .withValue(value)
                        .build()
                )
                .withTenant(new TenantBuilder()
                        .withId(tenantId)
                        .withName(tenantId)
                        .withTier(tier)
                        .build())
                .addMetaData("user", "111")  //update with your application user info
                .addMetaData("resource", "metrics")  //update with your application resource info
                .build();

        model.put("type", type);
        try {
            logSingleEvent(event);
            model.put("result", "Success!");
            LOGGER.info("getMetric: Event logged for " + metricName);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error with event", e);
            model.put("result", "Error: " + e.getMessage());
            throw new RuntimeException(e);
        }

        long milli = System.currentTimeMillis() - startTimeMillis;

        model.put("executionTime", (milli / 1000));
        return "metric";
    }


    public void logSingleEvent(MetricEvent event) throws JsonProcessingException {
        MetricEventLogger logger = MetricEventLogger.getLoggerFor(streamName, AWS_REGION);
        logger.log(event);
        logger.shutdown();
    }
}
