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

import com.amazon.aws.partners.saasfactory.saasboost.io.AuthPolicy;
import com.amazon.aws.partners.saasfactory.saasboost.io.TokenAuthorizerContext;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class OidcAuthorizer implements RequestHandler<TokenAuthorizerContext, AuthPolicy> {
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcAuthorizer.class);
    private static final String OIDC_ISSUER = System.getenv("OIDC_ISSUER");

    static TokenVerifier tokenVerifier = TokenVerifier.getInstance(OIDC_ISSUER);

    @Override
    public AuthPolicy handleRequest(TokenAuthorizerContext input, Context context) {

        String methodArn = input.getMethodArn();
        LOGGER.info("methodArn: {}", methodArn);

        String[] arnPartials = methodArn.split(":");
        String region = arnPartials[3];
        String awsAccountId = arnPartials[4];
        String[] apiGatewayArnPartials = arnPartials[5].split("/");
        String restApiId = apiGatewayArnPartials[0];
        String stage = apiGatewayArnPartials[1];
        String httpMethod = apiGatewayArnPartials[2];
        String resource = String.join("/", Arrays.copyOfRange(apiGatewayArnPartials, 3, apiGatewayArnPartials.length));

        LOGGER.info("httpMethod: {}", httpMethod);
        LOGGER.info("resource: {}", resource);

        String token = input.getAuthorizationToken();
        LOGGER.info(token);
        String principalId = "user";
        Claims claims = null;
        try {
            claims = tokenVerifier.verify(token, new Resource(httpMethod, resource));
        } catch (IllegalTokenException e) {
            LOGGER.error(e.getMessage());
            return new AuthPolicy(principalId,
                    AuthPolicy.PolicyDocument.getDenyAllPolicy(region, awsAccountId, restApiId, stage));
        }

        principalId = claims.getSubject();
        AuthPolicy authPolicy = new AuthPolicy(principalId,
                AuthPolicy.PolicyDocument.getAllowAllPolicy(region, awsAccountId, restApiId, stage));
        authPolicy.setContext(getContext(claims));
        return authPolicy;
    }

    private Map<String, String> getContext(Claims claims) {
        Map<String, String> context = new HashMap<>();
        Arrays.asList("issuer", "id", "sub", "email", "name", "scope", "scp", "groups")
                .forEach(k -> {
                            if (claims.get(k) != null) {
                                context.put(k, claims.get(k).toString());
                                if (k.equals("scp")) {
                                    context.put("scope", claims.get(k).toString());
                                }
                            }
                        }
                );

        return context;
    }

}
