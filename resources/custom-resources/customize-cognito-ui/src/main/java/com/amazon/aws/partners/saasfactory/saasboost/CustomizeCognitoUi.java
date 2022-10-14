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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.*;
import java.util.concurrent.*;

public class CustomizeCognitoUi implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomizeCognitoUi.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private final CognitoIdentityProviderClient cognito;
    private final S3Client s3;

    public CustomizeCognitoUi() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.cognito = Utils.sdkClient(CognitoIdentityProviderClient.builder(),
            CognitoIdentityProviderClient.SERVICE_NAME);
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        final Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String logoS3Bucket = (String) resourceProperties.get("LogoS3Bucket");
        final String logoS3Key = (String) resourceProperties.get("LogoS3Key");
        final String userPoolId = (String) resourceProperties.get("UserPoolId");

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    try {
                        // get bytes of logo from S3
                        ResponseInputStream<GetObjectResponse> responseInputStream = s3.getObject(request -> request
                                .bucket(logoS3Bucket)
                                .key(logoS3Key)
                                .build());
                        SdkBytes logoBytes = SdkBytes.fromInputStream(responseInputStream);
                        // set ui customization
                        cognito.setUICustomization(request -> request
                                .userPoolId(userPoolId)
                                .imageFile(logoBytes)
                                .css(".banner-customizable {background-color: blue;}")
                                .build());
                        // if the above call doesn't throw an exception, it succeeded
                        responseData.put("UserPool", userPoolId);
                        CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                    } catch (CognitoIdentityProviderException cidpe) {
                        LOGGER.error("cognito:setUiCustomization", cidpe.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(cidpe));
                        responseData.put("Reason", cidpe.awsErrorDetails().errorMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    } catch (S3Exception s3e) {
                        LOGGER.error("s3:getObject", s3e.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(s3e));
                        responseData.put("Reason", s3e.awsErrorDetails().errorMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    } catch (Exception e) {
                        LOGGER.error("Unexpected error", e);
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
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
