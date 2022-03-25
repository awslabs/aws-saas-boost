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

import java.util.ArrayList;
import java.util.List;

public class QueryResult {

    private String id;
    private List<MetricResultItem> metrics = new ArrayList<>();
    private List<String> periods = new ArrayList<>();
    private List<MetricValue> tenantTaskMaxCapacity = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getPeriods() {
        return List.copyOf(periods);
    }

    public void setPeriods(List<String> periods) {
        this.periods = periods != null ? periods : new ArrayList<>();
    }

    public List<MetricResultItem> getMetrics() {
        return List.copyOf(metrics);
    }

    public void setMetrics(List<MetricResultItem> metrics) {
        this.metrics = metrics != null ? metrics : new ArrayList<>();
    }

    public List<MetricValue> getTenantTaskMaxCapacity() {
        return List.copyOf(tenantTaskMaxCapacity);
    }

    public void setTenantTaskMaxCapacity(List<MetricValue> tenantTaskMaxCapacity) {
        this.tenantTaskMaxCapacity = tenantTaskMaxCapacity != null ? tenantTaskMaxCapacity : new ArrayList<>();
    }

}
