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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.DescribeFileSystemsRequest;
import software.amazon.awssdk.services.fsx.model.DescribeFileSystemsResponse;
import software.amazon.awssdk.services.fsx.model.FSxException;
import software.amazon.awssdk.services.fsx.model.FileSystem;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class FsxDnsName implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(FsxDnsName.class);
    private FSxClient fsx;

    public FsxDnsName() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.fsx = Utils.sdkClient(FSxClient.builder(), FSxClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }
    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String fsxId = (String) resourceProperties.get("FsxFileSystemId");

        ExecutorService service = Executors.newSingleThreadExecutor();
        ObjectNode responseData = JsonNodeFactory.instance.objectNode();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    try {
                        DescribeFileSystemsResponse response = fsx.describeFileSystems(DescribeFileSystemsRequest.builder()
                                .fileSystemIds(fsxId)
                                .build());
                        FileSystem fs = response.fileSystems().get(0);
                        LOGGER.info("DnsName is {}", fs.dnsName());
                        responseData.put("DnsName", fs.dnsName());
                        LOGGER.info("DnsName is {}", fs.dnsName());
                    } catch (FSxException e) {
                        LOGGER.error("Fsx::Error " + e.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", "Fsx Error " + e.getMessage());
                        sendResponse(event, context, "FAILED", responseData);
                    }
                    LOGGER.info("responseDate: " +  Utils.toJson(responseData));
                    sendResponse(event, context, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    sendResponse(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    sendResponse(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            String stackTrace = Utils.getFullStackTrace(e);
            LOGGER.error(stackTrace);
            responseData.put("Reason", stackTrace);
            sendResponse(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    /**
     * Send a response to CloudFormation regarding progress in creating resource.
     *
     * @param event
     * @param context
     * @param responseStatus
     * @param responseData
     * @return
     */
    public final Object sendResponse(final Map<String, Object> event, final Context context, final String responseStatus, ObjectNode responseData) {
        String responseUrl = (String) event.get("ResponseURL");
        LOGGER.info("ResponseURL: {}", responseUrl);

        try {
            URL url = new URL(responseUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "");
            connection.setRequestMethod("PUT");

            ObjectNode responseBody = JsonNodeFactory.instance.objectNode();
            responseBody.put("Status", responseStatus);
            responseBody.put("RequestId", (String) event.get("RequestId"));
            responseBody.put("LogicalResourceId", (String) event.get("LogicalResourceId"));
            responseBody.put("StackId", (String) event.get("StackId"));
            responseBody.put("PhysicalResourceId", (String) event.get("LogicalResourceId"));
            if (!"FAILED".equals(responseStatus)) {
                responseBody.set("Data", responseData);
            } else {
                responseBody.put("Reason", responseData.get("Reason").asText());
            }
            LOGGER.info("Response Body: " + responseBody.toString());

            try (OutputStreamWriter response = new OutputStreamWriter(connection.getOutputStream())) {
                response.write(responseBody.toString());
            } catch (IOException ioe) {
                LOGGER.error("Failed to call back to CFN response URL");
                LOGGER.error(Utils.getFullStackTrace(ioe));
            }

            LOGGER.info("Response Code: {}", connection.getResponseCode());
            connection.disconnect();
        } catch (IOException e) {
            LOGGER.error("Failed to open connection to CFN response URL");
            LOGGER.error(Utils.getFullStackTrace(e));
        }

        return null;
    }

}