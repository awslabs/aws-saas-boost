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

import java.time.Instant;
import java.util.*;

/*
Use to setup the query to use for the CW metrics
- tenants is the list of tenants
- MetricType is the type of metric

 */

public class MetricQuery {
    private String id;
    private List<String> tenants = new ArrayList<>();
    private Instant startDate, endDate;
    private String stat;
    private Integer period;
    private boolean topTenants = false;
    private boolean statsMap = false;
    private boolean tenantTaskMaxCapacity = false;
    private int tzOffset = 0;

    public int getTzOffset() {
        return tzOffset;
    }

    public void setTzOffset(int tzOffset) {
        this.tzOffset = tzOffset;
    }

    public boolean isTenantTaskMaxCapacity() {
        return tenantTaskMaxCapacity;
    }

    public void setTenantTaskMaxCapacity(boolean tenantTaskMaxCapacity) {
        this.tenantTaskMaxCapacity = tenantTaskMaxCapacity;
    }

    public String getTimeRangeName() {
        return timeRangeName;
    }

    public void setTimeRangeName(String timeRangeName) {
        this.timeRangeName = timeRangeName;
    }

    private String timeRangeName;

    private List<Dimension> dimensions = new ArrayList<>();

    private boolean singleTenant = false;

    public boolean isSingleTenant() {
        return this.singleTenant;
    }

    public void setSingleTenant(boolean singleTenant) {
        this.singleTenant = singleTenant;
    }

    public static class Dimension {
        private String metricName;
        private String nameSpace;

        //need this for JSON deserializer
        public Dimension() {
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

        public Dimension(String metricName, String nameSpace) {
            this.metricName = metricName;
            this.nameSpace = nameSpace;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Dimension> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<Dimension> dimensions) {
        this.dimensions = dimensions;
    }

    public boolean isTopTenants() {
        return topTenants;
    }

    public boolean isStatsMap() {
        return statsMap;
    }

    public void setStatsMap(boolean statsMap) {
        this.statsMap = statsMap;
    }

    public void setTopTenants(boolean topTenants) {
        this.topTenants = topTenants;
    }


    @Override
    public String toString() {
        return "MetricQuery{" +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", dimensions='" + dimensions.size() + '\'' +
                ", stat='" + stat + '\'' +
                ", period=" + period +
                ", topList=" + topTenants +
                ", statsMap=" + statsMap +
                '}';
    }

    public void addTenant(String tenantId) {
        this.tenants.add(tenantId);
    }

    public List<String> getTenants() {
        return tenants;
    }

    public void setTenants(List<String> tenants) {
        this.tenants = tenants;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }
}
