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

import com.amazonaws.services.lambda.runtime.Context;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class CognitoAuthorizer implements Authorizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoAuthorizer.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String USER_POOL_ID = System.getenv("USER_POOL_ID");

    public CognitoAuthorizer() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(USER_POOL_ID)) {
            throw new IllegalStateException("Missing required environment variable USER_POOL_ID");
        }
    }

    @Override
    public boolean verifyToken(TokenAuthorizerRequest request) {
        // TODO add aud claim and pass client id in to the Lambda as an env variable
        JWTVerifier verifier = JWT
                .require(Algorithm.RSA256(new CognitoKeyProvider()))
                .acceptLeeway(5L) // Allowed seconds of clock skew between token issuer and verifier
                .withClaim("token_use", (claim, token) -> (
                        // Per Cognito documentation, make sure we got an Access or Identity token
                        // (not a refresh token)
                        "access".equals(claim.asString()) || "id".equals(claim.asString()))
                )
                .build();
        boolean valid = false;
        try {
            verifier.verify(request.tokenPayload());
            valid = true;
        } catch (JWTVerificationException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
        }
        return valid;
    }
}
