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
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class CloudFormationResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudFormationResponse.class);
    private static final int MAX_RESPONSE_ATTEMPTS = 5;
    private static final HttpClient http = HttpClient.newBuilder().build();
    private static final RetryConfig exponentialBackoffWithJitter = RetryConfig.custom()
            .maxAttempts(MAX_RESPONSE_ATTEMPTS)
            .retryExceptions(CloudFormationResponseException.class)
            .intervalFunction(
                    // 1s, 2s, 4s, 8s, 16s retries with random jitter
                    IntervalFunction.ofExponentialRandomBackoff(1000L, 2.0d, 0.6d)
            )
            .build();

    private CloudFormationResponse() {
    }

    public static void signal(String waitHandle, boolean success, String uniqueId, String data, String reason) {
        String responseBody = Utils.toJson(Map.of(
                "Status", (success ? "SUCCESS" : "FAILURE"),
                "UniqueId", uniqueId,
                "Data", Objects.toString(data),
                "Reason", Objects.toString(reason)
        ));
        send(waitHandle, responseBody);
    }

    public static void send(Map<String, Object> event, Context context, String responseStatus,
                            Map<String, Object> responseData) {
        send(event, context, responseStatus, responseData, false);
    }

    public static void send(Map<String, Object> event, Context context, String responseStatus,
                            Map<String, Object> responseData, boolean noEcho) {
        String responseBody = buildResponseBody(event, context, responseStatus, responseData, noEcho);
        String responseUrl = (String) event.get("ResponseURL");
        send(responseUrl, responseBody);
    }

    protected static void send(String responseUrl, String responseBody) {
        // Super helpful command line equivalent to copy/paste from CloudWatch if things are stuck
        LOGGER.info("curl -H 'Content-Type: \"\"' -X PUT -d '" + responseBody + "' \"" + responseUrl + "\"");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(responseUrl))
                .setHeader("Content-Type", "")
                .PUT(HttpRequest.BodyPublishers.ofString(responseBody, StandardCharsets.UTF_8))
                .build();

        // We've noticed some random S3 timeouts after switching to the newer java.net.http.HttpClient
        // vs the old school java.net.HttpURLConnection (they occurred in the past, just not as frequently)
        // Apply a retry with exponential backoff and jitter to the response HTTP PUT to CloudFormation's S3
        // bucket since things seem to work if we invoke the PUT manually using the cURL command logged above
        Retry retry = Retry.of("CloudFormationResponse", exponentialBackoffWithJitter);
        Function<HttpRequest, String> cloudFormationCall = Retry.decorateFunction(retry,
                CloudFormationResponse::callCloudFormation);
        cloudFormationCall.apply(request);
    }

    protected static String callCloudFormation(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK != response.statusCode()) {
                throw new RuntimeException(response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to complete HTTP request");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new CloudFormationResponseException(e.getMessage());
        }
        return null;
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
        // the previous physical resource. Usually this doesn't matter -- unless you're actually *creating* something
        // in your custom resource that you don't want replaced on an update to the Custom::CustomResource resource
        // in the template. Also, when you call stack DELETE, if the physical resource id isn't the same, then
        // CloudFormation won't remove the Custom::CustomResource resource.
        if (Utils.isBlank((String) event.get("PhysicalResourceId"))) {
            // Stack CREATE won't pass a physical id in for a custom resource
            responseBody.put("PhysicalResourceId", physicalResourceId(event));
        } else {
            // Stack DELETE expects the same physical id as currently exists so it can remove the resource
            // Stack UPDATE will update-in-place existing physical resources and will replace (delete old,
            // replace with new) different physical resources
            responseBody.put("PhysicalResourceId", event.get("PhysicalResourceId"));
        }

        if (!"FAILED".equals(responseStatus)) {
            responseBody.put("Data", responseData != null ? responseData : Collections.EMPTY_MAP);
        } else {
            // CloudFormation will blow up if the failure response string is longer than 256 chars
            String error = Objects.toString(responseData.getOrDefault("Reason", ""), "");
            if (error.length() > 256) {
                error = error.substring(0, 256);
            }
            responseBody.put("Reason", error);
        }
        return Utils.toJson(responseBody);
    }

    protected static String physicalResourceId(Map<String, Object> event) {
        // Don't blindly use CloudWatch Logs log stream or the Request ID
        // bind the physical resource to the stack and the logical id to maintain idempotency
        return ((String) event.get("StackId")).split("/")[1]  + "-" + event.get("LogicalResourceId")
                + "-" + Utils.randomString(8);
    }

}
