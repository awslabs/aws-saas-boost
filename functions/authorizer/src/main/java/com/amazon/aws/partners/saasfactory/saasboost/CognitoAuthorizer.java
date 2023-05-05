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

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CognitoAuthorizer implements Authorizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoAuthorizer.class);

    @Override
    public DecodedJWT verifyToken(TokenAuthorizerRequest request) {
        JWTVerifier verifier = JWT
                .require(Algorithm.RSA256(new CognitoKeyProvider()))
                .acceptLeeway(5L) // Allowed seconds of clock skew between token issuer and verifier
                .withClaim("token_use", "access")
                .withClaim("client_id", (claim, token) -> (
                        List.of(ApiGatewayAuthorizer.ADMIN_WEB_APP_CLIENT_ID, ApiGatewayAuthorizer.API_APP_CLIENT_ID,
                                ApiGatewayAuthorizer.PRIVATE_API_APP_CLIENT_ID).contains(claim.asString())
                        )
                )
                .build();
        DecodedJWT token = null;
        try {
            token = verifier.verify(request.tokenPayload());
        } catch (JWTVerificationException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
        }
        return token;
    }

    @Override
    public String getClientId(DecodedJWT token) {
        return token.getClaim("client_id").asString();
    }

    @Override
    public List<String> getGroups(DecodedJWT token) {
        return token.getClaim("cognito:groups").asList(String.class);
    }
}
