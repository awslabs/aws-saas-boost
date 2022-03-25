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

import java.util.Objects;

public class MetricValue implements Comparable<MetricValue> {

    private double value;
    private String id;

    public MetricValue(double value, String id) {
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
        return Utils.toJson(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        // Same reference?
        if (this == obj) {
            return true;
        }
        // Same type?
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MetricValue other = (MetricValue) obj;
        return (Utils.nullableEquals(id, other.id) && (value == other.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, value);
    }
}
