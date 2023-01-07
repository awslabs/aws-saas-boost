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

import com.amazon.aws.partners.saasfactory.saasboost.Database.RdsInstance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class RdsOptions implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RdsOptions.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private final RdsClient rds;
    private final DynamoDbClient ddb;

    public RdsOptions() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.rds = Utils.sdkClient(RdsClient.builder(), RdsClient.SERVICE_NAME);
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
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");
                    // For every RDS engine we support via the Database enum
                    for (Database.RdsEngine engine : Database.RdsEngine.values()) {
                        LOGGER.info("RDS engine {}", engine.name());
                        List<Object> versions = new ArrayList();
                        // The same RDS engine version will be available for multiple EC2 instance types so we'll
                        // keep a working set of them to limit the number of describeDBEngineVersions calls
                        Set<String> seenVersions = new HashSet<>();

                        // load the versions
                        DescribeDbEngineVersionsResponse versionsResponse = null;
                        try {
                            versionsResponse = rds.describeDBEngineVersions(DescribeDbEngineVersionsRequest.builder()
                                    .engine(engine.getEngine())
                                    .build());
                        } catch (RdsException rdsException) {
                            if (rdsException.getMessage().contains("Unrecognized engine name")) {
                                LOGGER.warn("Unrecognized engine {}", engine);
                                continue;
                            }
                            LOGGER.error("rds:DescribeDbEngineVersions error {}", rdsException);
                            throw rdsException;
                        }

                        // for each version, find instances
                        for (DBEngineVersion dbVersion : versionsResponse.dbEngineVersions()) {
                            if (seenVersions.contains(dbVersion.engineVersion())) {
                                LOGGER.info("Skipping duplicate version: {} {}", engine, dbVersion.engineVersion());
                                continue;
                            }
                            EnumMap<Database.RdsInstance, Map<String, String>> instanceMap = new EnumMap(Database.RdsInstance.class);
                            Integer maxRecords = 100;
                            String marker = null;
                            int requestNumber = 1;
                            do {
                                DescribeOrderableDbInstanceOptionsResponse orderableResponse = null;
                                try {
                                    LOGGER.info("orderableOptions request {} for {} {}",
                                            requestNumber, engine.getEngine(), dbVersion.engineVersion());
                                    orderableResponse = rds.describeOrderableDBInstanceOptions(
                                            DescribeOrderableDbInstanceOptionsRequest.builder()
                                                    .engine(engine.getEngine())
                                                    .engineVersion(dbVersion.engineVersion())
                                                    .vpc(Boolean.TRUE)
                                                    .marker(marker)
                                                    .maxRecords(maxRecords)
                                                    .build()
                                    );
                                    marker = orderableResponse.marker();
                                    String size = Objects.toString(orderableResponse.orderableDBInstanceOptions().size());
                                    String numOrderableOptions = (Utils.isNotEmpty(marker) ? size + "+" : size);
                                    LOGGER.info("{} {} {} has {} orderable instance options",
                                            AWS_REGION, engine.getEngine(), dbVersion.engineVersion(), numOrderableOptions
                                    );

                                    Set<String> unknownInstanceTypes = new HashSet<>();
                                    for (OrderableDBInstanceOption option : orderableResponse.orderableDBInstanceOptions()) {
                                        RdsInstance validInstance = RdsInstance.ofInstanceClass(option.dbInstanceClass());
                                        if (validInstance != null) {
                                            if (!instanceMap.containsKey(validInstance)) {
                                                LOGGER.info("Found {} instance option for {} {} ({})",
                                                        validInstance, engine, dbVersion.engineVersion(), AWS_REGION);
                                                instanceMap.put(validInstance, Map.of(
                                                        "class", validInstance.getInstanceClass(),
                                                        "description", validInstance.getDescription()
                                                ));
                                            } else {
                                                LOGGER.debug("Skipping duplicate orderable instance type {}", validInstance);
                                            }
                                        } else {
                                            if (!unknownInstanceTypes.contains(option.dbInstanceClass())) {
                                                unknownInstanceTypes.add(option.dbInstanceClass());
                                                LOGGER.debug("Skipping unknown orderable instance type {}",
                                                        option.dbInstanceClass());
                                            }
                                        }
                                    }
                                } catch (RdsException rdsException) {
                                    String message = Objects.toString(rdsException.getMessage(), "");
                                    if (message.startsWith("Cannot find version " +  dbVersion.engineVersion())) {
                                        LOGGER.warn("Skipping non orderable version {}", dbVersion.engineVersion());
                                    } else {
                                        LOGGER.error("rds:DescribeOrderableDBInstanceOptions error", rdsException);
                                        throw rdsException;
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

                            // Save this engine's options for this region to our database for fast lookup
                            try {
                                Map<String, AttributeValue> item = new HashMap<>();
                                item.put("region", AttributeValue.builder().s(AWS_REGION).build());
                                item.put("engine", AttributeValue.builder().s(engine.name()).build());
                                item.put("options", AttributeValue.builder().m(toAttributeValueMap(engineDetails)).build());
                                ddb.putItem(request -> request.tableName(table).item(item));
                            } catch (SdkServiceException ddbError) {
                                LOGGER.error("dynamodb:putItem error {}", ddbError);
                                throw ddbError;
                            }
                        } else {
                            LOGGER.info("Skipping engine {} ({}) with no valid versions", engine.getEngine(), AWS_REGION);
                        }
                    }
                    // Tell CloudFormation we're done
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
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

    // Convert our data to DynamoDB attribute values
    private static Map<String, AttributeValue> toAttributeValueMap(Map<String, Object> engineDetails) {
        Map<String, AttributeValue> options = new HashMap<>();
        options.put("name", AttributeValue.builder().s((String) engineDetails.get("name")).build());
        options.put("description", AttributeValue.builder().s((String) engineDetails.get("description")).build());

        List<AttributeValue> versions = new ArrayList<>();
        for (Map<String, Object> version : (List<Map<String, Object>>) engineDetails.get("versions")) {
            Map<String, AttributeValue> versionAttributeValue = new HashMap<>();
            versionAttributeValue.put("version", AttributeValue.builder().s((String) version.get("version")).build());
            versionAttributeValue.put("description", AttributeValue.builder().s((String) version.get("description")).build());
            versionAttributeValue.put("family", AttributeValue.builder().s((String) version.get("family")).build());

            Map<String, AttributeValue> instancesMap = new HashMap<>();
            // instances for the version AttributeValue is a map of RdsInstance -> map of String, String
            for (Map.Entry<RdsInstance, Map<String, String>> instanceEntry :
                    ((EnumMap<RdsInstance, Map<String, String>>) version.get("instances")).entrySet()) {
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

        return options;
    }

}