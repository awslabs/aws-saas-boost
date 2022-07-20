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

import com.amazon.aws.partners.saasfactory.saasboost.Database.RdsInstance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class RdsOptions implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RdsOptions.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private RdsClient rds;
    private DynamoDbClient ddb;

    public RdsOptions() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.rds = Utils.sdkClient(RdsClient.builder(), RdsClient.SERVICE_NAME);
        this.ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String table = (String) resourceProperties.get("Table");

        ExecutorService service = Executors.newSingleThreadExecutor();
        ObjectNode responseData = JsonNodeFactory.instance.objectNode();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");

                    // The same RDS engine version will be available for multiple EC2 instance types so we'll
                    // keep a working set of them to limit the number of describeDBEngineVersions calls
                    LOGGER.info("Building options for {}", AWS_REGION);
                    List<Object> engines = new ArrayList();

                    // For every RDS engine
                    for (Database.RdsEngine engine : Database.RdsEngine.values()) {
                        LOGGER.info("RDS engine {}", engine.name());
                        List<Object> versions = new ArrayList();
                        Set<String> seenVersions = new HashSet<>();

                        // load the versions
                        DescribeDbEngineVersionsResponse versionsResponse = null;
                        try {
                            versionsResponse = rds.describeDBEngineVersions(DescribeDbEngineVersionsRequest.builder()
                                    .engine(engine.getEngine())
                                    .build());
                        } catch (RdsException rdsException) {
                            if (rdsException.getMessage().contains("Unrecognized engine name")) {
                                LOGGER.info("{} is unrecognized when getting engine versions, skipping", engine);
                                continue;
                            }
                            LOGGER.error("rds:DescribeDbEngineVersions error {}", rdsException.getMessage());
                            String stackTrace = Utils.getFullStackTrace(rdsException);
                            LOGGER.error(stackTrace);
                            responseData.put("Reason", stackTrace);
                            sendResponse(event, "FAILED", responseData);
                        }

                        // for each version, find instances
                        for (DBEngineVersion dbVersion : versionsResponse.dbEngineVersions()) {
                            if (seenVersions.contains(dbVersion.engineVersion())) {
                                LOGGER.info("Skipping version we've already seen: {} {}", engine, dbVersion.engineVersion());
                                continue;
                            }
                            EnumMap<Database.RdsInstance, Map<String, String>> instanceMap = new EnumMap(Database.RdsInstance.class);
                            Integer maxRecords = 100;
                            String marker = null;
                            int requestNumber = 1;
                            do {
                                DescribeOrderableDbInstanceOptionsResponse orderableResponse = null;
                                try {
                                    LOGGER.info("orderableOptions request {} for {} {}", requestNumber, engine.getEngine(), dbVersion.engineVersion());
                                    orderableResponse = rds.describeOrderableDBInstanceOptions(DescribeOrderableDbInstanceOptionsRequest.builder()
                                            .engine(engine.getEngine())
                                            .engineVersion(dbVersion.engineVersion())
                                            .vpc(Boolean.TRUE)
                                            .marker(marker)
                                            .maxRecords(maxRecords)
                                            .build()
                                    );
                                } catch (RdsException rdsException) {
                                    LOGGER.error("rds:DescribeOrderableDBInstanceOptions error {}", rdsException.getMessage());
                                    String stackTrace = Utils.getFullStackTrace(rdsException);
                                    LOGGER.error(stackTrace);
                                    responseData.put("Reason", stackTrace);
                                    sendResponse(event, "FAILED", responseData);
                                }
                                LOGGER.info("{} {} {} has {} orderable instance options", AWS_REGION, engine.getEngine(), dbVersion.engineVersion(), orderableResponse.orderableDBInstanceOptions().size());
                                marker = orderableResponse.marker();

                                for (OrderableDBInstanceOption option : orderableResponse.orderableDBInstanceOptions()) {
                                    RdsInstance validInstance = RdsInstance.ofInstanceClass(option.dbInstanceClass());
                                    if (validInstance != null) {
                                        LOGGER.info("found {} instance option for {} {} ({})", validInstance, engine, dbVersion.engineVersion(), AWS_REGION);
                                        Map<String, String> instanceDetails = new HashMap<>();
                                        instanceDetails.put("class", validInstance.getInstanceClass());
                                        instanceDetails.put("description", validInstance.getDescription());
                                        instanceMap.put(validInstance, instanceDetails);
                                    } else {
                                        LOGGER.info("skipping option that doesn't match enum of instance types: {}", option);
                                    }
                                }
                                requestNumber++;
                            } while (marker != null && !marker.isEmpty());

                            if (!instanceMap.isEmpty()) {
                                Map<String, Object> versionDetails = new HashMap();
                                versionDetails.put("version", dbVersion.engineVersion());
                                versionDetails.put("description", dbVersion.dbEngineVersionDescription());
                                versionDetails.put("family", dbVersion.dbParameterGroupFamily());
                                versionDetails.put("instances", instanceMap);
                                LOGGER.info("Adding version {}", versionDetails.toString());
                                versions.add(versionDetails);
                                seenVersions.add(dbVersion.engineVersion());
                            } else {
                                LOGGER.info("Skipping version {} ({}) with no instances", dbVersion.engineVersion(), AWS_REGION);
                            }
                        }

                        if (!versions.isEmpty()) {
                            Map<String, Object> engineDetails = new HashMap<>();
                            engineDetails.put("name", engine.getEngine());
                            engineDetails.put("description", engine.getDescription());
                            engineDetails.put("versions", versions);
                            LOGGER.info("Adding engine {}", engineDetails);
                            engines.add(engineDetails);

                            // Save this engine's options for this region to our database for fast lookup
                            try {
                                Map<String, AttributeValue> item = new HashMap<>();
                                item.put("region", AttributeValue.builder().s(AWS_REGION).build());
                                item.put("engine", AttributeValue.builder().s(engine.name()).build());
                                item.put("options", AttributeValue.builder().m(toAttributeValueMap(engineDetails)).build());
                                ddb.putItem(request -> request.tableName(table).item(item));
                            } catch(SdkServiceException ddbError) {
                                LOGGER.error("dynamodb:putItem error {}", ddbError.getMessage());
                                String stackTrace = Utils.getFullStackTrace(ddbError);
                                LOGGER.error(stackTrace);
                                responseData.put("Reason", stackTrace);
                                sendResponse(event, "FAILED", responseData);
                            }
                        } else {
                            LOGGER.info("Skipping engine {} ({}) with no valid versions", engine.getEngine(), AWS_REGION);
                        }
                    }
                    // Tell CloudFormation we're done
                    sendResponse(event, "SUCCESS", responseData);
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
                    sendResponse(event, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    sendResponse(event, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    sendResponse(event, "FAILED", responseData);
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
            sendResponse(event, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    /**
     * Send a response to CloudFormation regarding progress in creating resource.
     *
     * @param event
     * @param responseStatus
     * @param responseData
     */
    public final void sendResponse(final Map<String, Object> event, final String responseStatus, ObjectNode responseData) {
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
    }

    // Convert our data to DynamoDB attribute values
    private static Map<String, AttributeValue> toAttributeValueMap(Map<String, Object> engineDetails) {
        Map<String, AttributeValue> options = new HashMap<>();
        options.put("name", AttributeValue.builder().s((String) engineDetails.get("name")).build());
        options.put("description", AttributeValue.builder().s((String) engineDetails.get("description")).build());

        LOGGER.info("attempting to convert {} to ddb attribute value map", engineDetails);

        List<AttributeValue> versions = new ArrayList<>();
        for (Map<String, Object> version : (List<Map<String, Object>>) engineDetails.get("versions")) {
            Map<String, AttributeValue> versionAttributeValue = new HashMap<>();
            versionAttributeValue.put("version", AttributeValue.builder().s((String) version.get("version")).build());
            versionAttributeValue.put("description", AttributeValue.builder().s((String) version.get("description")).build());
            versionAttributeValue.put("family", AttributeValue.builder().s((String) version.get("family")).build());

            Map<String, AttributeValue> instancesMap = new HashMap<>();
            // instances for the version AttributeValue is a map of RdsInstance -> map of String, String
            for (Map.Entry<RdsInstance, Map<String, String>> instanceEntry : ((EnumMap<RdsInstance, Map<String, String>>) version.get("instances")).entrySet()) {
                AttributeValue instanceDetails = AttributeValue.builder().m(
                        instanceEntry.getValue().entrySet().stream()
                            .collect(Collectors.toMap(
                                entry -> entry.getKey(),
                                entry -> AttributeValue.builder().s(entry.getValue()).build())))
                        .build();
                instancesMap.put(instanceEntry.getKey().toString(), instanceDetails);
            }

            versionAttributeValue.put("instances", AttributeValue.builder().m(instancesMap).build());
            versions.add(AttributeValue.builder().m(versionAttributeValue).build());
        }
        options.put("versions", AttributeValue.builder().l(versions).build());

        LOGGER.debug("created AttributeValue map for {}: {}", engineDetails, options);

        return options;
    }

}