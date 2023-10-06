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

import java.util.Properties;

public class CloudWatchMetricsProvider implements MetricsProvider {

    static final Properties DEFAULTS = new Properties();

    static {
        //DEFAULTS.put("logGroupName", "");
        //DEFAULTS.put("logStreamName", "");
    }

    private final Properties properties;

    public CloudWatchMetricsProvider(Properties properties) {
        this.properties = new Properties(DEFAULTS);
        this.properties.putAll(properties);
    }

    @Override
    public ProviderType type() {
        return ProviderType.CLOUDWATCH;
    }

    @Override
    public Properties getProperties() {
        return (Properties) properties.clone();
    }

    @Override
    public MetricsProviderApi api() {
        return new CloudWatchApi();
    }
}
