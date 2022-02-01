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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CloudFormationResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudFormationResponse.class);

    private CloudFormationResponse() {
    }

    public static void send(Map<String, Object> event, Context context, String responseStatus,
                            Map<String, Object> responseData) {
        Map<String, Object> responseBody = buildResponseBody(event, context, responseStatus, responseData);
        String responseUrl = (String) event.get("ResponseURL");
        try {
            URL url = new URL(responseUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "");
            connection.setRequestMethod("PUT");
            try (OutputStreamWriter response = new OutputStreamWriter(connection.getOutputStream(),
                    StandardCharsets.UTF_8)) {
                response.write(Utils.toJson(responseBody));
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode > 299) {
                    LOGGER.error("Response from CFN S3 signed URL failed {}", responseUrl);
                    LOGGER.error("Response: {} {}", responseCode, connection.getResponseMessage());
                }
            } catch (IOException ioe) {
                LOGGER.error("Failed to complete HTTP request");
                LOGGER.error(Utils.getFullStackTrace(ioe));
            }
            connection.disconnect();
        } catch (IOException e) {
            LOGGER.error("Failed to open connection to CFN response URL");
            LOGGER.error(Utils.getFullStackTrace(e));
        }
    }

    protected static Map<String, Object> buildResponseBody(Map<String, Object> event, Context context,
                                                           String responseStatus, Map<String, Object> responseData) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("Status", responseStatus);
        responseBody.put("RequestId", event.get("RequestId"));
        responseBody.put("LogicalResourceId", event.get("LogicalResourceId"));
        responseBody.put("StackId", event.get("StackId"));
        // If the physical resource id changes between a CREATE and an UPDATE event, CloudFormation will DELETE
        // the previous physical resource. Usually this doesn't matter -- unless you're actually creating something
        // in your custom resource that you don't want deleted.
        responseBody.put("PhysicalResourceId", context.getAwsRequestId());
        if (!"FAILED".equals(responseStatus)) {
            responseBody.put("Data", responseData);
        } else {
            responseBody.put("Reason", responseData.get("Reason"));
        }
        LOGGER.info("Response Body: " + Utils.toJson(responseBody));
        return responseBody;
    }
}
