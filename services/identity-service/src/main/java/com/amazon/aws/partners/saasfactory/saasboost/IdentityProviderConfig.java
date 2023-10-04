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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Properties;

public class IdentityProviderConfig {

    private final IdentityProvider.ProviderType type;
    private final Properties metadata;

    @JsonCreator
    public IdentityProviderConfig(@JsonProperty("type") IdentityProvider.ProviderType type) {
        this.type = type;
        this.metadata = new Properties();
        switch (this.type) {
            case COGNITO:
                this.metadata.putAll(CognitoIdentityProvider.DEFAULTS);
                break;
            default:
                break;
        }
    }

    public IdentityProvider.ProviderType getType() {
        return this.type;
    }

    public Properties getMetadata() {
        return (Properties) metadata.clone();
    }

    public void setMetadata(Map<String, String> properties) {
        if (properties != null) {
            this.metadata.putAll(properties);
        }
    }
}
