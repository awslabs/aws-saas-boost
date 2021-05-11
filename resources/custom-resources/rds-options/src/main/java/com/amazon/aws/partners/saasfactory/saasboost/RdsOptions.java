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
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
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

    private final static Logger LOGGER = LoggerFactory.getLogger(RdsOptions.class);
    private final static String AWS_REGION = System.getenv("AWS_REGION");
//    private final static Region[] SAAS_BOOST_REGIONS = new Region[] {Region.US_EAST_1, Region.US_EAST_2, Region.US_WEST_1,
//            Region.US_WEST_2, Region.CA_CENTRAL_1, Region.EU_WEST_1, Region.EU_WEST_2, Region.EU_WEST_3, Region.EU_CENTRAL_1,
//            Region.EU_NORTH_1, Region.AP_SOUTH_1, Region.AP_NORTHEAST_1, Region.AP_NORTHEAST_2, Region.AP_SOUTHEAST_1,
//            Region.AP_SOUTHEAST_2, Region.SA_EAST_1
//    };
    private RdsClient rds;
    private DynamoDbClient ddb;

    public RdsOptions() {
        long startTimeMillis = System.currentTimeMillis();
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

                    // For every RDS engine
                    for (Database.RDS_ENGINE engine : Database.RDS_ENGINE.values()) {
                        Map<String, Map<String, Object>> engineVersionCache = new HashMap<>();
                    //for (Database.RDS_ENGINE engine : new Database.RDS_ENGINE[] {Database.RDS_ENGINE.AURORA_PG}) {
                        LOGGER.info("RDS engine {}", engine.name());

                        // Each engine needs its own copy of the list of instance types because
                        // they can be different by engine by region
                        EnumMap<Database.RDS_INSTANCE, Map<String, Object>> instanceMap = new EnumMap<>(Database.RDS_INSTANCE.class);
                        for (Database.RDS_INSTANCE inst : Database.RDS_INSTANCE.values()) {
                        //for (Database.RDS_INSTANCE inst : new Database.RDS_INSTANCE[] {Database.RDS_INSTANCE.T3_MEDIUM}) {
                            Map<String, Object> instanceDetails = new HashMap<>();
                            instanceDetails.put("class", inst.getInstanceClass());
                            instanceDetails.put("description", inst.getDescription());
                            instanceDetails.put("versions", new ArrayList<Map<String, Object>>());
                            instanceMap.put(inst, instanceDetails);
                        }

                        Map<String, Object> engineDetails = new HashMap<>();
                        engineDetails.put("name", engine.getEngine());
                        engineDetails.put("description", engine.getDescription());
                        engineDetails.put("instances", instanceMap);

                        // For every RDS database instance type
                        for (Map.Entry<Database.RDS_INSTANCE, Map<String, Object>> instance : instanceMap.entrySet()) {
                            String instanceClass = (String) instance.getValue().get("class");
                            LOGGER.info("RDS instance class {}", instanceClass);

                            List<Map<String, Object>> versions = new ArrayList<>();
                            Integer maxRecords = 100;
                            String marker = null;
                            do {
                                try {
                                    DescribeOrderableDbInstanceOptionsResponse orderableResponse = rds.describeOrderableDBInstanceOptions(DescribeOrderableDbInstanceOptionsRequest.builder()
                                            .engine(engine.getEngine())
                                            .dbInstanceClass(instanceClass)
                                            .vpc(Boolean.TRUE)
                                            .marker(marker)
                                            .maxRecords(maxRecords)
                                            .build()
                                    );
                                    LOGGER.info("{} {} {} has {} orderable instance options", AWS_REGION.toString(), engine.getEngine(), instanceClass, orderableResponse.orderableDBInstanceOptions().size());
                                    marker = orderableResponse.marker();

                                    // We can get more orderable instances than versions due to storage, IOPs,
                                    // security, networking, and other options. We are just interested in the
                                    // unique set of engine versions per instance type.
                                    //LOGGER.info("Processing {} instance options", orderableResponse.orderableDBInstanceOptions().size());
                                    for (OrderableDBInstanceOption orderable : orderableResponse.orderableDBInstanceOptions()) {
                                        String orderableVersion = orderable.engineVersion();

                                        // Have we already seen this version for this instance type?
                                        boolean newVersion = versions.stream()
                                                .filter(m -> orderableVersion.equals(m.get("version")))
                                                .collect(Collectors.toList())
                                                .isEmpty();

                                        // If we haven't seen this version yet for this instance type
                                        if (newVersion) {
                                            // Maybe we've seen this same version for other instance types
                                            boolean cachedVersion = engineVersionCache.containsKey(orderableVersion);

                                            if (!cachedVersion) {
                                                // Nope... make the call to get the details for this version
                                                //LOGGER.info("Engine version {}", orderableVersion);
                                                DescribeDbEngineVersionsResponse versionsResponse = rds.describeDBEngineVersions(DescribeDbEngineVersionsRequest.builder()
                                                        .engine(engine.getEngine())
                                                        .engineVersion(orderableVersion)
                                                        .build()
                                                );
                                                //LOGGER.info("Processing {} engine versions", versionsResponse.dbEngineVersions().size());
                                                for (DBEngineVersion dbVersion : versionsResponse.dbEngineVersions()) {
                                                    // describeDBEngineVersions brings back multiple results with the same
                                                    // version for at least db.t3.medium aurora-postgresql (bug?)
                                                    boolean uniqueVersionResponse = versions.stream()
                                                            .filter(m -> dbVersion.engineVersion().equals(m.get("version")))
                                                            .collect(Collectors.toList())
                                                            .isEmpty();
                                                    if (uniqueVersionResponse) {
                                                        Map<String, Object> version = new HashMap<>();
                                                        version.put("version", dbVersion.engineVersion());
                                                        version.put("description", dbVersion.dbEngineVersionDescription());
                                                        version.put("family", dbVersion.dbParameterGroupFamily());
                                                        //LOGGER.info("Adding version {}", version.toString());
                                                        versions.add(version);

                                                        // Be sure to cache the results
                                                        //LOGGER.info("Caching version {}", dbVersion.engineVersion());
                                                        engineVersionCache.put(dbVersion.engineVersion(), version);
                                                    } else {
                                                        //LOGGER.info("Skipping duplicate db version response");
                                                    }
                                                }
                                            } else {
                                                // We haven't seen this version for this instance type, but we have for
                                                // other instance types for this RDS engine, so we can use our cached copy
                                                // of the version response.
                                                //LOGGER.info("Engine version {} (cached)", orderableVersion);
                                                versions.add(engineVersionCache.get(orderableVersion));
                                            }
                                        }
                                    }
                                } catch (SdkServiceException rdsError) {
                                    LOGGER.error("rds:describeOrderableDBInstanceOptions | rds:DescribeDbEngineVersions error {}", rdsError.getMessage());
                                    String stackTrace = Utils.getFullStackTrace(rdsError);
                                    LOGGER.error(stackTrace);
                                    responseData.put("Reason", stackTrace);
                                    sendResponse(event, context, "FAILED", responseData);
                                }
                            } while (marker != null && !marker.isEmpty());

                            // Now we have all the unique versions for this instance type
                            LOGGER.info("{} {} {} has {} available versions", AWS_REGION.toString(), engine.getEngine(), instanceClass, versions.size());
                            instanceMap.get(instance.getKey()).put("versions", versions);
                        }

                        // Remove instances that don't have any orderable versions
                        for (Database.RDS_INSTANCE instance : instanceMap.keySet()) {
                            if (((List) instanceMap.get(instance).get("versions")).isEmpty()) {
                                LOGGER.info("Removing unavailable instance class {} for {} in {}", instance.getInstanceClass(), engine.getEngine(), AWS_REGION.toString());
                                instanceMap.remove(instance);
                            }
                        }

                        if (((Map) engineDetails.get("instances")).isEmpty()) {
                            LOGGER.info("Removing unavailable engine {} in {}", engine.getEngine(), AWS_REGION.toString());
                        } else {
                            // Save this engine's options for this region to our database for fast lookup
                            try {
                                Map<String, AttributeValue> item = new HashMap<>();
                                item.put("region", AttributeValue.builder().s(AWS_REGION).build());
                                item.put("engine", AttributeValue.builder().s(engine.name()).build());
                                item.put("options", AttributeValue.builder().m(toAttributeValueMap(engineDetails)).build());
                                PutItemResponse response = ddb.putItem(request -> request.tableName(table).item(item));
                            } catch(SdkServiceException ddbError) {
                                LOGGER.error("dynamodb:putItem error {}", ddbError.getMessage());
                                String stackTrace = Utils.getFullStackTrace(ddbError);
                                LOGGER.error(stackTrace);
                                responseData.put("Reason", stackTrace);
                                sendResponse(event, context, "FAILED", responseData);
                            }
                        }
                    }
                    // Tell CloudFormation we're done
                    sendResponse(event, context, "SUCCESS", responseData);
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
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

    // Convert our data to DynamoDB attribute values
    private static Map<String, AttributeValue> toAttributeValueMap(Map<String, Object> engineDetails) {
        Map<String, AttributeValue> options = new HashMap<>();
        options.put("name", AttributeValue.builder().s((String) engineDetails.get("name")).build());
        options.put("description", AttributeValue.builder().s((String) engineDetails.get("description")).build());

        Map<String, AttributeValue> instances = new HashMap<>();
        for (Map.Entry<Database.RDS_INSTANCE, Map<String, Object>> instance : ((EnumMap<Database.RDS_INSTANCE, Map<String, Object>>) engineDetails.get("instances")).entrySet()) {
            Map<String, Object> instanceDetails = instance.getValue();

            Map<String, AttributeValue> versions = new HashMap<>();
            versions.put("class", AttributeValue.builder().s((String) instanceDetails.get("class")).build());
            versions.put("description", AttributeValue.builder().s((String) instanceDetails.get("description")).build());

            List<AttributeValue> instanceVersions = new ArrayList<>();
            for (Map<String, String> instanceVersion : (List<Map<String, String>>) instanceDetails.get("versions")) {
                instanceVersions.add(AttributeValue.builder().m(
                        instanceVersion.entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                        entry -> entry.getKey(),
                                        entry -> AttributeValue.builder().s(entry.getValue()).build()
                                ))
                    ).build()
                );
            }
            versions.put("versions", AttributeValue.builder().l(instanceVersions).build());

            instances.put(instance.getKey().name(), AttributeValue.builder().m(versions).build());
        }
        options.put("instances", AttributeValue.builder().m(instances).build());

        return options;
    }

}