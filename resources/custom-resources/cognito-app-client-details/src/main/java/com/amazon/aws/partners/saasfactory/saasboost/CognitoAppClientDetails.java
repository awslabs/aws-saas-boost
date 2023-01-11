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
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeUserPoolClientResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolClientType;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class CognitoAppClientDetails implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoAppClientDetails.class);
    private final CognitoIdentityProviderClient cognito;

    public CognitoAppClientDetails() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.cognito = Utils.sdkClient(CognitoIdentityProviderClient.builder(),
                CognitoIdentityProviderClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        final Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String userPoolId = (String) resourceProperties.get("UserPoolId");
        final String clientId = (String) resourceProperties.get("ClientId");

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    try {
                        DescribeUserPoolClientResponse clientDetails = cognito.describeUserPoolClient(request -> request
                                .userPoolId(userPoolId)
                                .clientId(clientId)
                        );
                        UserPoolClientType client = clientDetails.userPoolClient();
                        if (client != null) {
                            responseData.put("ClientId", client.clientId());
                            responseData.put("ClientName", client.clientName());
                            responseData.put("ClientSecret", client.clientSecret());
                            // We're returning sensitive data, so be sure to use NoEcho = true
                            // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/crpg-ref-responses.html
                            CloudFormationResponse.send(event, context, "SUCCESS", responseData, true);
                        } else {
                            throw new RuntimeException("UserPoolClient is NULL!");
                        }
                    } catch (SdkServiceException cognitoError) {
                        LOGGER.error("cognito-idp:DescribeUserPoolClient error", cognitoError);
                        throw cognitoError;
                    }
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

}
