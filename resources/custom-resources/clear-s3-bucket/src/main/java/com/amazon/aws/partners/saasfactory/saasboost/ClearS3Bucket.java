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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ClearS3Bucket implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(ClearS3Bucket.class);
    private final S3Client s3;

    public ClearS3Bucket() throws URISyntaxException {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String bucket = (String) resourceProperties.get("Bucket");

        ExecutorService service = Executors.newSingleThreadExecutor();
        ObjectNode responseData = JsonNodeFactory.instance.objectNode();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    sendResponse(event, context, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");

                    // The list of objects in the bucket to delete
                    List<ObjectIdentifier> toDelete = new ArrayList<>();

                    // Is the bucket versioned?
                    GetBucketVersioningResponse versioningResponse = s3.getBucketVersioning(request -> request.bucket(bucket));
                    if (BucketVersioningStatus.ENABLED == versioningResponse.status() || BucketVersioningStatus.SUSPENDED == versioningResponse.status()) {
                        LOGGER.info("Bucket " + bucket + " is versioned (" + versioningResponse.status() + ")");
                        ListObjectVersionsResponse response;
                        String keyMarker = null;
                        String versionIdMarker = null;
                        do {
                            ListObjectVersionsRequest request;
                            if (Utils.isNotBlank(keyMarker) && Utils.isNotBlank(versionIdMarker)) {
                                request = ListObjectVersionsRequest.builder()
                                        .bucket(bucket)
                                        .keyMarker(keyMarker)
                                        .versionIdMarker(versionIdMarker)
                                        .build();
                            } else if (Utils.isNotBlank(keyMarker)) {
                                request = ListObjectVersionsRequest.builder()
                                        .bucket(bucket)
                                        .keyMarker(keyMarker)
                                        .build();
                            } else {
                                request = ListObjectVersionsRequest.builder()
                                        .bucket(bucket)
                                        .build();
                            }
                            response = s3.listObjectVersions(request);
                            keyMarker = response.nextKeyMarker();
                            versionIdMarker = response.nextVersionIdMarker();
                            response.versions()
                                    .stream()
                                    .map(version ->
                                        ObjectIdentifier.builder()
                                                .key(version.key())
                                                .versionId(version.versionId())
                                                .build()
                                    )
                                    .forEachOrdered(toDelete::add);
                        } while (response.isTruncated());
                    } else {
                        LOGGER.info("Bucket " + bucket + " is not versioned (" + versioningResponse.status() + ")");
                        ListObjectsV2Response response;
                        String token = null;
                        do {
                            ListObjectsV2Request request;
                            if (Utils.isNotBlank(token)) {
                                request = ListObjectsV2Request.builder()
                                        .bucket(bucket)
                                        .continuationToken(token)
                                        .build();
                            } else {
                                request = ListObjectsV2Request.builder()
                                        .bucket(bucket)
                                        .build();
                            }
                            response = s3.listObjectsV2(request);
                            token = response.nextContinuationToken();
                            response.contents()
                                    .stream()
                                    .map(obj ->
                                            ObjectIdentifier.builder()
                                                    .key(obj.key())
                                                    .build()
                                    )
                                    .forEachOrdered(toDelete::add);
                        } while (response.isTruncated());
                    }
                    if (!toDelete.isEmpty()) {
                        LOGGER.info("Deleting " + toDelete.size() + " objects");
                        final int maxBatchSize = 1000;
                        int batchStart = 0;
                        int batchEnd = 0;
                        while (batchEnd < toDelete.size()) {
                            batchStart = batchEnd;
                            batchEnd += maxBatchSize;
                            if (batchEnd > toDelete.size()) {
                                batchEnd = toDelete.size();
                            }
                            Delete delete = Delete.builder()
                                    .objects(toDelete.subList(batchStart, batchEnd))
                                    .build();
                            DeleteObjectsResponse deleteResponse = s3.deleteObjects(builder -> builder
                                    .bucket(bucket)
                                    .delete(delete)
                            );
                            LOGGER.info("Cleaned up " + deleteResponse.deleted().size() + " objects in bucket " + bucket);
                        }
                    } else {
                        LOGGER.info("Bucket " + bucket + " is empty. No objects to clean up.");
                    }
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
        LOGGER.info("ResponseURL: " + responseUrl + "\n");

        URL url;
        try {
            url = new URL(responseUrl);
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

            LOGGER.info("Response Code: " + connection.getResponseCode());
            connection.disconnect();
        } catch (IOException e) {
            LOGGER.error("Failed to open connection to CFN response URL");
            LOGGER.error(Utils.getFullStackTrace(e));
        }

        return null;
    }

}