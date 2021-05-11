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

public class MetricValue implements Comparable<MetricValue> {

    //private Instant metricTime;
    private double value;
    private String id;
    public MetricValue(double value, String id) {
       // this.metricTime = time;
        this.value = value;
        this.id = id;
    }

    public double getValue() {
        return value;
    }

    public String getId() {
        return id;
    }

    public int compareTo(MetricValue o) {
        return Double.compare(value, o.value);
    }

    public void setValue(double value) {
        this.value = value;
    }


    @Override
    public String toString() {
        return "MetricValue{" +
                "value=" + value +
                ", id='" + id + '\'' +
                '}';
    }
}
