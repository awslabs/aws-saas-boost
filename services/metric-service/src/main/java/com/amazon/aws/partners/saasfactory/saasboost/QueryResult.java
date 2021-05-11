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
package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    private String id;
    private List<MetricResultItem> metrics= new ArrayList<>();
    private List<String> periods = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getPeriods() {
        return periods;
    }

    public void setPeriods(List<String> periodList) {
        this.periods = periodList;
    }

    public List<MetricResultItem> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<MetricResultItem> metricResultItemList) {
        this.metrics = metricResultItemList;
    }

    private List<MetricValue> tenantTaskMaxCapacity = new ArrayList<>();
    public List<MetricValue> getTenantTaskMaxCapacity() {
        return tenantTaskMaxCapacity;
    }

    public void setTenantTaskMaxCapacity(List<MetricValue> tenantTaskMaxCapacity) {
        this.tenantTaskMaxCapacity = tenantTaskMaxCapacity;
    }

}
