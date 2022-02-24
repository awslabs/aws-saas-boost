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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.concurrent.*;

public class CidrDynamoDB implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CidrDynamoDB.class);
    private final DynamoDbClient ddb;

    public CidrDynamoDB() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String table = (String) resourceProperties.get("Table");

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    try {
                        ScanResponse scan = ddb.scan(request -> request.tableName(table));
                        // ScanResponse::hasItems will return true even with an empty list
                        if (scan.hasItems() && !scan.items().isEmpty()) {
                            LOGGER.info("CIDR table {} is already populated with {} items", table, scan.count());
                        } else {
                            LOGGER.info("Populating CIDR table");
                            List<List<WriteRequest>> batches = generateBatches();
                            for (List<WriteRequest> batch : batches) {
                                try {
                                    ddb.batchWriteItem(request -> request.requestItems(Map.of(table, batch)));
                                } catch (DynamoDbException e) {
                                    LOGGER.error(Utils.getFullStackTrace(e));
                                    responseData.put("Reason", e.awsErrorDetails().errorMessage());
                                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                                }
                            }
                        }
                        CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                    } catch (DynamoDbException e) {
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.awsErrorDetails().errorMessage());
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
            LOGGER.error("FAILED unexpected error or request timed out", e);
            String stackTrace = Utils.getFullStackTrace(e);
            LOGGER.error(stackTrace);
            responseData.put("Reason", stackTrace);
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    protected static List<List<WriteRequest>> generateBatches() {
        final int batchWriteItemLimit = 25;
        final int maxOctet = 255;
        int octet = -1;
        List<List<WriteRequest>> batches = new ArrayList<>();
        List<WriteRequest> batch = new ArrayList<>();
        while (octet <= maxOctet) {
            octet++;
            if (batch.size() == batchWriteItemLimit || octet > maxOctet) {
                batches.add(new ArrayList<>(batch)); // shallow copy is ok here
                batch.clear(); // clear out our working batch so we can fill it up again to the limit
            }
            String cidr = String.format("10.%d.0.0", octet);
            WriteRequest putRequest = WriteRequest.builder()
                    .putRequest(PutRequest.builder()
                            .item(Map.of("cidr_block", AttributeValue.builder().s(cidr).build()))
                            .build())
                    .build();
            batch.add(putRequest);
        }
        return batches;
    }
}