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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetricResultItem {
    private List<MetricValue> topTenants = new ArrayList<>();
    private Map<String, List<Double>> stats = new LinkedHashMap<>();
    private MetricDimension dimension;

    public MetricDimension getDimension() {
        return dimension;
    }

    public void setDimension(MetricDimension dimension) {
        this.dimension = dimension;
    }

    public Map<String, List<Double>> getStats() {
        return stats;
    }

    public List<MetricValue> getTopTenants() {
        return topTenants;
    }

    public void setTopTenant(List<MetricValue> mvList) {
        topTenants = mvList;
    }

    public List<Double> getStat(String key) {
        return stats.get(key);
    }

    public void putStat(String key, List<Double> valueList) {
        stats.put(key, valueList);
    }

}
