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

import java.time.Instant;
import java.util.*;

public class MetricQuery {
    private String id;
    private String stat;
    private List<Dimension> dimensions = new ArrayList<>();
    private Integer period;
    private Instant startDate;
    private Instant endDate;
    private String timeRangeName;
    private int tzOffset = 0;
    private List<String> tenants = new ArrayList<>();
    private boolean singleTenant = false;
    private boolean topTenants = false;
    private boolean statsMap = false;
    private boolean tenantTaskMaxCapacity = false;

    @Override
    public String toString() {
        return Utils.toJson(this);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public List<Dimension> getDimensions() {
        return List.copyOf(dimensions);
    }

    public void setDimensions(List<Dimension> dimensions) {
        this.dimensions = dimensions != null ? dimensions : new ArrayList<>();
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public Instant getStartDate() {
        if (startDate == null) {
            return null;
        } else {
            return Instant.from(startDate);
        }
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate != null ? Instant.from(startDate) : null;
    }

    public Instant getEndDate() {
        if (endDate == null) {
            return null;
        } else {
            return Instant.from(endDate);
        }
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate != null ? Instant.from(endDate) : null;
    }

    public String getTimeRangeName() {
        return timeRangeName;
    }

    public void setTimeRangeName(String timeRangeName) {
        this.timeRangeName = timeRangeName;
    }

    public int getTzOffset() {
        return tzOffset;
    }

    public void setTzOffset(int tzOffset) {
        this.tzOffset = tzOffset;
    }

    public List<String> getTenants() {
        return List.copyOf(tenants);
    }

    public void setTenants(List<String> tenants) {
        this.tenants = tenants != null ? tenants : new ArrayList<>();
    }

    public void addTenant(String tenantId) {
        if (!this.tenants.contains(tenantId)) {
            this.tenants.add(tenantId);
        }
    }

    public boolean isSingleTenant() {
        return singleTenant;
    }

    public void setSingleTenant(boolean singleTenant) {
        this.singleTenant = singleTenant;
    }

    public boolean isTopTenants() {
        return topTenants;
    }

    public void setTopTenants(boolean topTenants) {
        this.topTenants = topTenants;
    }

    public boolean isStatsMap() {
        return statsMap;
    }

    public void setStatsMap(boolean statsMap) {
        this.statsMap = statsMap;
    }

    public boolean isTenantTaskMaxCapacity() {
        return tenantTaskMaxCapacity;
    }

    public void setTenantTaskMaxCapacity(boolean tenantTaskMaxCapacity) {
        this.tenantTaskMaxCapacity = tenantTaskMaxCapacity;
    }

    public static class Dimension {
        private String metricName;
        private String nameSpace;

        //need this for JSON deserializer
        public Dimension() {
        }

        public Dimension(String metricName, String nameSpace) {
            this.metricName = metricName;
            this.nameSpace = nameSpace;
        }

        public String getMetricName() {
            return metricName;
        }

        public void setMetricName(String metricName) {
            this.metricName = metricName;
        }

        public String getNameSpace() {
            return nameSpace;
        }

        public void setNameSpace(String nameSpace) {
            this.nameSpace = nameSpace;
        }
    }
}
