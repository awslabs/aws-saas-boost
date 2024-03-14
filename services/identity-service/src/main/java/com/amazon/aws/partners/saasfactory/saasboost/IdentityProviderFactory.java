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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentityProviderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityProviderFactory.class);

    private IdentityProviderFactory() {
    }

    public static IdentityProviderFactory getInstance() {
        return IdentityProviderFactoryInstance.instance;
    }

    public IdentityProvider getProvider(IdentityProviderConfig providerConfig) {
        switch (providerConfig.getType()) {
            case COGNITO:
                return new CognitoIdentityProvider(providerConfig.getMetadata());
            default:
                return null;
        }
    }

    private static class IdentityProviderFactoryInstance {
        public static final IdentityProviderFactory instance = new IdentityProviderFactory();
    }
}
