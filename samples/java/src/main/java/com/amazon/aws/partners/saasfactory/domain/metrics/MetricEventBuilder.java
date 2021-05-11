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
package com.amazon.aws.partners.saasfactory.domain.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class MetricEventBuilder {
    private static final Logger logger = LoggerFactory.getLogger(MetricEventBuilder.class);
    private MetricEvent metricEvent;

    public MetricEventBuilder() {
        metricEvent = new MetricEvent();
    }

    public MetricEventBuilder withType(MetricEvent.Type type) {
        this.metricEvent.setType(MetricEvent.Type.Application);
        return this;
    }

    public MetricEventBuilder withWorkload(String workload) {
        this.metricEvent.setWorkload(workload);
        return this;
    }

    public MetricEventBuilder withContext(String context) {
        this.metricEvent.setContext(context);
        return this;
    }

    public MetricEventBuilder withTenant(Tenant tenant) {
        this.metricEvent.setTenant(tenant);
        return this;
    }

    public MetricEventBuilder withMetaData(Map<String, String> metaData) {
        this.metricEvent.setMetaData(metaData);
        return this;
    }

    public MetricEventBuilder addMetaData(String key, String value) {
        this.metricEvent.getMetaData().put(key, value);
        return this;
    }

    public MetricEventBuilder withMetric(Metric metric) {
        this.metricEvent.setMetric(metric);
        return this;
    }

    public MetricEvent build() {
        if (this.metricEvent.isValid()) {
            return this.metricEvent;
        } else {
            logger.debug("Error: MetricEvent is missing required data");
            return null;
        }
    }
}

