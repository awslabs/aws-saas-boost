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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Properties;

public class MetricsProviderConfig {

    private final MetricsProvider.ProviderType type;
    private final Properties properties;

    public MetricsProviderConfig(@JsonProperty("type") MetricsProvider.ProviderType type) {
        this.type = type;
        this.properties = new Properties();
        switch (this.type) {
            case CLOUDWATCH:
                this.properties.putAll(CloudWatchMetricsProvider.DEFAULTS);
                break;
            default:
                break;
        }
    }

    public Properties getProperties() {
        return (Properties) properties.clone();
    }

    public void setProperties(Map<String, String> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public MetricsProvider.ProviderType getType() {
        return type;
    }
}
