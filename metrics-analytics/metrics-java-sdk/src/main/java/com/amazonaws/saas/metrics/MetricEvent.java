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
package com.amazonaws.saas.metrics;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MetricEvent {

    public static enum Type {
        Application,
        System;
    }

    private MetricEvent.Type type;
    private String workload;
    private String context;
    private Tenant tenant;
    private Map<String, String> metaData;
    private Metric metric;
    private Long timestamp;

    public MetricEvent() {
        this.type = MetricEvent.Type.Application;
        this.timestamp = Instant.now().getEpochSecond();
        this.metaData = new HashMap<>();
        this.tenant = new Tenant();
        this.metric = new Metric();
    }

    @JsonIgnore
    public boolean isValid() {
        return !this.getWorkload().isEmpty() && this.tenant.isValid() && this.metric.isValid();
    }

    public void setType(MetricEvent.Type type) {
        this.type = type;
    }

    @JsonGetter("type")
    public MetricEvent.Type getType() {
        return this.type;
    }

    @JsonGetter("workload")
    public String getWorkload() {
        return this.workload;
    }

    public void setWorkload(String workload) {
        this.workload = workload;
    }

    @JsonGetter("context")
    public String getContext() {
        return this.context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    @JsonGetter("tenant")
    public Tenant getTenant() {
        return this.tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    @JsonGetter("meta-data")
    public Map<String, String> getMetaData() {
        return this.metaData;
    }

    public void setMetaData(Map<String, String> metaData) {
        this.metaData = metaData;
    }

    @JsonGetter("metric")
    public Metric getMetric() {
        return this.metric;
    }

    public void setMetric(Metric metric) {
        this.metric = metric;
    }

    @JsonGetter("timestamp")
    public Long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "MetricEvent{" +
                "type=" + type +
                ", workload='" + workload + '\'' +
                ", context='" + context + '\'' +
                ", tenant=" + tenant +
                ", metaData=" + metaData +
                ", metric=" + metric +
                ", timestamp=" + timestamp +
                '}';
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            MetricEvent that = (MetricEvent)o;
            return this.type == that.type && Objects.equals(this.workload, that.workload) && Objects.equals(this.context, that.context) && Objects.equals(this.tenant, that.tenant) && Objects.equals(this.metaData, that.metaData) && Objects.equals(this.metric, that.metric) && Objects.equals(this.timestamp, that.timestamp);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(new Object[]{this.type, this.workload, this.context, this.tenant, this.metaData, this.metric, this.timestamp});
    }

}

