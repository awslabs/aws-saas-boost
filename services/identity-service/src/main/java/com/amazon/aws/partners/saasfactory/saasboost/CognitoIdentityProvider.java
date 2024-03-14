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

import software.amazon.awssdk.services.sts.StsClient;

import java.util.Properties;

public class CognitoIdentityProvider implements IdentityProvider {

    static final Properties DEFAULTS = new Properties();

    static {
        DEFAULTS.put("userPoolId", "");
        DEFAULTS.put("assumedRole", "");
    }

    private final Properties metadata;
    private final StsClient sts;

    public CognitoIdentityProvider(Properties metadata) {
        this(metadata, new DefaultDependencyFactory());
    }

    public CognitoIdentityProvider(Properties metadata, CognitoIdentityProviderDependencyFactory init) {
        this.metadata = new Properties(DEFAULTS);
        this.metadata.putAll(metadata);
        this.sts = init.sts();
    }

    @Override
    public ProviderType type() {
        return ProviderType.COGNITO;
    }

    public Properties getMetadata() {
        return (Properties) metadata.clone();
    }

    public IdentityProviderApi getApi() {
        IamCredentials credentials = new IamCredentials(sts, metadata.getProperty("assumedRole"));
        CognitoApi api = new CognitoApi(credentials, metadata.getProperty("userPoolId"));
        return api;
    }

    interface CognitoIdentityProviderDependencyFactory {

        StsClient sts();
    }

    private static final class DefaultDependencyFactory implements CognitoIdentityProviderDependencyFactory {

        @Override
        public StsClient sts() {
            return Utils.sdkClient(StsClient.builder(), StsClient.SERVICE_NAME);
        }
    }
}
