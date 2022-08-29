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

import java.util.HashMap;

public class KeyCloakAuthorizer implements Authorizer {

    @Override
    public AuthorizerResponse handleRequest(TokenAuthorizerRequest event, Context context) {
        return AuthorizerResponse.builder()
                .principalId(event.getAccountId())
                .policyDocument(PolicyDocument.builder()
                        .statement(Statement.builder()
                                .effect("Deny")
                                .resource(ApiGatewayAuthorizer.apiGatewayResource(event))
                                .build()
                        )
                        .build()
                )
                .context(new HashMap<>())
                .build();
    }

    @Override
    public boolean verifyToken(TokenAuthorizerRequest request) {
        return false;
    }
}