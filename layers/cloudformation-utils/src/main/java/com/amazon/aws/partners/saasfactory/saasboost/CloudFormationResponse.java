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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CloudFormationResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudFormationResponse.class);
    private static final HttpClient http = HttpClient.newBuilder().build();

    private CloudFormationResponse() {
    }

    public static void send(Map<String, Object> event, Context context, String responseStatus,
                            Map<String, Object> responseData) {
        send(event, context, responseStatus, responseData, false);
    }

    public static void send(Map<String, Object> event, Context context, String responseStatus,
                            Map<String, Object> responseData, boolean noEcho) {
        String responseBody = buildResponseBody(event, context, responseStatus, responseData, noEcho);
        String responseUrl = (String) event.get("ResponseURL");
        LOGGER.info("curl -H 'Content-Type: \"\"' -X PUT -d '" + responseBody + "' \"" + responseUrl + "\"");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .setHeader("Content-Type", "")
                .PUT(HttpRequest.BodyPublishers.ofString(responseBody, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK != response.statusCode()) {
                LOGGER.error("Response from CFN S3 signed URL failed {} {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to complete HTTP request");
            LOGGER.error(Utils.getFullStackTrace(e));
        }
    }

    protected static String buildResponseBody(Map<String, Object> event, Context context, String responseStatus,
                                              Map<String, Object> responseData, boolean noEcho) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("Status", responseStatus);
        responseBody.put("RequestId", event.get("RequestId"));
        responseBody.put("LogicalResourceId", event.get("LogicalResourceId"));
        responseBody.put("StackId", event.get("StackId"));
        responseBody.put("NoEcho", noEcho);
        // If the physical resource id changes between a CREATE and an UPDATE event, CloudFormation will DELETE
        // the previous physical resource. Usually this doesn't matter -- unless you're actually creating something
        // in your custom resource that you don't want deleted.
        responseBody.put("PhysicalResourceId", context.getAwsRequestId());
        if (!"FAILED".equals(responseStatus)) {
            if (responseData != null && !responseData.isEmpty()) {
                responseBody.put("Data", responseData);
            }
        } else {
            responseBody.put("Reason", responseData.get("Reason"));
        }
        return Utils.toJson(responseBody);
    }
}
