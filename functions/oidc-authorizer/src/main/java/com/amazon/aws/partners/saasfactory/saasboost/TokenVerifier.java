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

import com.amazon.aws.partners.saasfactory.saasboost.impl.DefaultOidcTokenVerifier;
import io.jsonwebtoken.Claims;

public interface TokenVerifier {
    static TokenVerifier getInstance(String issuer) {
        return new DefaultOidcTokenVerifier(new OIDCConfig(issuer));
    }

    /**
     * Verify OIDC token
     *
     * @param token: OIDC token, must start with 'Bearer '
     * @param resource: http resource, HttpMethod and resource path
     * @return claims in the token
     * @throws IllegalTokenException, if token is illegal, this exception will be thrown
     */
    Claims verify(String token, Resource resource) throws IllegalTokenException;

}
