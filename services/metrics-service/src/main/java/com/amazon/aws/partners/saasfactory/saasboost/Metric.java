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

public class Metric {

    private String stat;
    private String nameSpace;
    private SortedMap<Instant, PriorityQueue<MetricValue>> timeValMap = new TreeMap<>();
    private String metricName;
    private double period;
    private List<Double> metricValues = new ArrayList<>();
    private List<Instant> metricTimes = new ArrayList<>();

    public void addMetricValue(Double val) {
        this.metricValues.add(val);
    }

    public List<Double> getMetricValues() {
        return List.copyOf(this.metricValues);
    }

    public List<Instant> getMetricTimes() {
        return List.copyOf(metricTimes);
    }

    public void addSortTime(Instant sortTime) {
        this.metricTimes.add(sortTime);
    }

    public double getPeriod() {
        return period;
    }

    public void setPeriod(double period) {
        this.period = period;
    }

    public String getStat() {
        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }

    public String getNameSpace() {
        return nameSpace;
    }

    public void setNameSpace(String nameSpace) {
        this.nameSpace = nameSpace;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getMetricName() {
        return metricName;
    }

    public void addQueueValue(Instant time, MetricValue mv) {
        PriorityQueue<MetricValue> pq = timeValMap.computeIfAbsent(time, k -> new PriorityQueue<>());
        pq.add(mv);
    }

    public SortedMap<Instant, PriorityQueue<MetricValue>> getTimeValMap() {
        return new TreeMap<>(timeValMap);
    }

    @Override
    public String toString() {
        return Utils.toJson(this);
    }

}
