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

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public class CognitoKeyProvider implements RSAKeyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoKeyProvider.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String USER_POOL_ID = System.getenv("USER_POOL_ID");
    private final JwkProvider keyProvider;

    public CognitoKeyProvider() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(USER_POOL_ID)) {
            throw new IllegalStateException("Missing required environment variable USER_POOL_ID");
        }
        keyProvider = new JwkProviderBuilder(jwksUrl()).build();
    }

    @Override
    public RSAPublicKey getPublicKeyById(String kid) {
        try {
            return (RSAPublicKey) keyProvider.get(kid).getPublicKey();
        } catch (JwkException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return null;
    }

    @Override
    public String getPrivateKeyId() {
        return null;
    }

    // https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html
    protected static URL jwksUrl() {
        URL url = null;
        try {
            url = new URL("https://cognito-idp." + AWS_REGION + ".amazonaws.com/" + USER_POOL_ID
                    + "/.well-known/jwks.json");
        } catch (MalformedURLException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
        }
        return url;
    }
}
