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

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.ArrayList;
import java.util.List;

public class IdentityDataAccessLayer {

    private final String providerConfigSecretId;
    private final SecretsManagerClient secrets;

    public IdentityDataAccessLayer(SecretsManagerClient secrets, String providerConfigSecretId) {
        this.secrets = secrets;
        this.providerConfigSecretId = providerConfigSecretId;
    }

    public List<IdentityProviderConfig> getAvailableProviders() {
        List<IdentityProviderConfig> providers = new ArrayList<>();
        IdentityProviderConfig activeProvider = getProviderConfig();
        for (IdentityProvider.ProviderType type : IdentityProvider.ProviderType.values()) {
            if (activeProvider != null && type == activeProvider.getType()) {
                providers.add(activeProvider);
            } else {
                providers.add(new IdentityProviderConfig(type));
            }
        }
        return providers;
    }

    public IdentityProviderConfig getProviderConfig() {
        GetSecretValueResponse response = secrets.getSecretValue(request -> request
                .secretId(providerConfigSecretId)
        );
        return Utils.fromJson(response.secretString(), IdentityProviderConfig.class);
    }

    public void setProviderConfig(IdentityProviderConfig providerConfig) {
        secrets.putSecretValue(request -> request
                .secretId(providerConfigSecretId)
                .secretString(Utils.toJson(providerConfig))
        );
    }
}
