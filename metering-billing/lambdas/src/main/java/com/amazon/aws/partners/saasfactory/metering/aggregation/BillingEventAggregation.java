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
package com.amazon.aws.partners.saasfactory.metering.aggregation;

import com.amazon.aws.partners.saasfactory.metering.common.BillingEvent;
import com.amazon.aws.partners.saasfactory.metering.common.TenantConfiguration;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.amazon.aws.partners.saasfactory.metering.common.Constants.*;

public class BillingEventAggregation implements RequestStreamHandler {

    private final DynamoDbClient ddb;
    private final Logger LOGGER = LoggerFactory.getLogger(BillingEventAggregation.class);
    private final static String TABLE_NAME = System.getenv(TABLE_ENV_VARIABLE);

    public BillingEventAggregation() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(TABLE_NAME)) {
            throw new IllegalStateException("Missing required environment variable " + TABLE_ENV_VARIABLE);
        }
        // Used by TenantConfiguration
        if (Utils.isBlank(System.getenv("DYNAMODB_CONFIG_INDEX_NAME"))) {
            throw new IllegalStateException("Missing required environment variable DYNAMODB_CONFIG_INDEX_NAME");
        }
        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
        ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    private List<BillingEvent> getBillingEventsForTenant(String tenantID) {
        HashMap<String,String> expressionNames = new HashMap<>();
        expressionNames.put(PRIMARY_KEY_EXPRESSION_NAME, PRIMARY_KEY_NAME);
        expressionNames.put(SORT_KEY_EXPRESSION_NAME, SORT_KEY_NAME);

        HashMap<String,AttributeValue> queryValues = new HashMap<>();

        AttributeValue tenantIDValue = AttributeValue.builder()
                .s(tenantID)
                .build();

        AttributeValue eventPrefixValue = AttributeValue.builder()
                .s(EVENT_PREFIX)
                .build();

        queryValues.put(TENANT_ID_EXPRESSION_VALUE, tenantIDValue);
        queryValues.put(EVENT_PREFIX_ATTRIBUTE_VALUE, eventPrefixValue);

        QueryResponse result = null;
        List<BillingEvent> billingEvents = new ArrayList<>();
        do {

            QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression(String.format("%s = %s and begins_with(%s, %s)",
                                                PRIMARY_KEY_EXPRESSION_NAME,
                                                TENANT_ID_EXPRESSION_VALUE,
                                                SORT_KEY_EXPRESSION_NAME,
                                                EVENT_PREFIX_ATTRIBUTE_VALUE))
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(queryValues)
                    .build();
            if (result != null && !result.lastEvaluatedKey().isEmpty()) {
                request = request.toBuilder()
                            .exclusiveStartKey(result.lastEvaluatedKey())
                            .build();
            }
            try {
                result = this.ddb.query(request);
            } catch (ResourceNotFoundException e) {
                this.LOGGER.error("Table {} does not exist", TABLE_NAME);
            } catch (InternalServerErrorException e) {
                this.LOGGER.error(e.getMessage());
                // if there's a failure, return an empty array list rather than a partial array list
                return new ArrayList<>();
            }
            for (Map<String, AttributeValue> item : result.items()) {
                String eventEntry = item.get(SORT_KEY_NAME).s();
                Long eventTimeInMilliseconds = Long.valueOf(eventEntry.split(ATTRIBUTE_DELIMITER)[EVENT_TIME_ARRAY_INDEX]);
                String nonce = eventEntry.split(ATTRIBUTE_DELIMITER)[NONCE_ARRAY_INDEX];
                Instant eventTime = Instant.ofEpochMilli(eventTimeInMilliseconds);
                String productCode = item.get(PRODUCT_CODE_ATTRIBUTE_NAME).s();
                Long quantity = Long.valueOf(item.get(QUANTITY_ATTRIBUTE_NAME).n());
                BillingEvent billingEvent = new BillingEvent(tenantID, eventTime, productCode, quantity, nonce);
                billingEvents.add(billingEvent);
            }
        } while (!result.lastEvaluatedKey().isEmpty());
        return billingEvents;
    }

    private Map<ZonedDateTime, List<BillingEvent>> categorizeEvents(TenantConfiguration tenant, List<BillingEvent> billingEvents) {
        if (billingEvents.size() == 0) {
            return null;
        }
        // Figure out the lowest and highest date (the range)
        Instant earliestBillingEvent = Collections.min(billingEvents).getEventTime();
        this.LOGGER.info("Earliest event for tenant {} at {}",
                                    tenant.getTenantID(),
                                    earliestBillingEvent.toString());
        Instant latestBillingEvent = Collections.max(billingEvents).getEventTime();
        this.LOGGER.info("Latest event for tenant {} at {}",
                                    tenant.getTenantID(),
                                    latestBillingEvent.toString());
        // Create a map with each element as a key based on the frequency (e.g. a day for a key with frequency for a day)
        Map<ZonedDateTime, List<BillingEvent>> eventCounts = new HashMap<>();
        for (BillingEvent event : billingEvents) {
            ZonedDateTime eventTime = event.getEventTime().atZone(ZoneId.of("UTC"));
            ZonedDateTime startOfEventTimePeriod = eventTime.truncatedTo(TRUNCATION_UNIT);
            ZonedDateTime startOfCurrentTimePeriod = Instant.now().atZone(ZoneId.of("UTC")).truncatedTo(TRUNCATION_UNIT);
            // Skip over this time period and future time period events because there may eventually be more events
            if (!(startOfCurrentTimePeriod.compareTo(startOfEventTimePeriod) <= 0)) {
                List<BillingEvent> eventList = eventCounts.getOrDefault(startOfEventTimePeriod, new ArrayList<>());
                eventList.add(event);
                eventCounts.put(startOfEventTimePeriod, eventList);
            }
        }
        return eventCounts;
    }

    private void initializeItem(Map<String, AttributeValue> compositeKey, String productCode, ZonedDateTime time) {
        // Format the statements
        Map<String, AttributeValue> productToValueMap = new HashMap<>();
        AttributeValue initialEventValue = AttributeValue.builder()
                .n(EVENT_COUNT_INITIALIZATION_VALUE)
                .build();
        productToValueMap.put(productCode, initialEventValue);

        AttributeValue initialMapValue = AttributeValue.builder()
                .m(productToValueMap)
                .build();

        compositeKey.put(QUANTITY_ATTRIBUTE_NAME, initialMapValue);

        AttributeValue idempotencyKeyValue = AttributeValue.builder()
                .s(UUID.randomUUID().toString().split(UUID_DELIMITER)[SELECTED_UUID_INDEX])
                .build();
        compositeKey.put(IDEMPOTENTCY_KEY_ATTRIBUTE_NAME, idempotencyKeyValue);

        AttributeValue submittedValue = AttributeValue.builder()
                .bool(false)
                .build();
        compositeKey.put(SUBMITTED_KEY_ATTRIBUTE_NAME, submittedValue);

        String conditionalStatement = String.format("attribute_not_exists(%s)", QUANTITY_ATTRIBUTE_NAME);

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(compositeKey)
                .conditionExpression(conditionalStatement)
                .build();

        try {
            ddb.putItem(putItemRequest);
        } catch (ResourceNotFoundException|InternalServerErrorException e) {
            this.LOGGER.error("{}", e.toString());
        } catch (ConditionalCheckFailedException e) {
            // Repeat the transaction and see if it works
            this.LOGGER.error("Entry for {} at {} already exists",
                    productCode,
                    time.toInstant().toString());
        }
    }

    private void putRequestsAsTransaction(Update updateRequest, List<Delete> deleteRequests) {
        List<TransactWriteItem> transaction = new ArrayList<>();
        TransactWriteItem updateTransactionItem = TransactWriteItem.builder()
                .update(updateRequest)
                .build();
        transaction.add(updateTransactionItem);
        List<TransactWriteItem> deleteRequestItems = deleteRequests.stream().map(deleteRequest -> TransactWriteItem.builder()
            .delete(deleteRequest)
            .build()).collect(Collectors.toList());
        transaction.addAll(deleteRequestItems);
        this.LOGGER.info("Transaction contains {} actions", transaction.size());

        TransactWriteItemsRequest transactWriteItemsRequest = TransactWriteItemsRequest.builder()
                .transactItems(transaction)
                .build();

        try {
            ddb.transactWriteItems(transactWriteItemsRequest);
        } catch (ResourceNotFoundException|InternalServerErrorException|TransactionCanceledException e) {
            this.LOGGER.error("{}", e.toString());
        }
    }

    private Map<String, Long> countEventsByProductCode(List<BillingEvent> billingEvents) {
        Map<String, Long> countByProductCode = new HashMap<>();
        this.LOGGER.info("Counting events by product code");
        for (BillingEvent event : billingEvents) {
            Long currentCount = countByProductCode.getOrDefault(event.getProductCode(), Long.valueOf(0));
            Long updatedCount = event.getQuantity() + currentCount;
            countByProductCode.put(event.getProductCode(), updatedCount);
        }
        // There's now a map in the format of
        // product-code : count
        return countByProductCode;
    }

    private Update buildUpdate(Map<String,Long> countByProductCode, Map<String, AttributeValue> compositeKey) {
        List<String> updateStatements = new ArrayList<>();
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put(QUANTITY_EXPRESSION_NAME, QUANTITY_ATTRIBUTE_NAME);
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        Integer index = 0;
        for (String productCode : countByProductCode.keySet()) {
            this.LOGGER.info("Count for {} is {}", productCode, countByProductCode.get(productCode));
            // Appended to the ADD_TO_AGGREGATION_ATTRIBUTE_VALUE for identification in the expression
            // attribute names/values. There could be more than one product code to aggregate
            String aggregationAttributeName = ADD_TO_AGGREGATION_EXPRESSION_NAME + index.toString();
            String aggregationAttributeValue = ADD_TO_AGGREGATION_EXPRESSION_VALUE + index.toString();
            String updateStatement = String.format("SET %s.%s = %s.%s + %s",
                    QUANTITY_EXPRESSION_NAME,
                    aggregationAttributeName,
                    QUANTITY_EXPRESSION_NAME,
                    aggregationAttributeName,
                    aggregationAttributeValue);
            updateStatements.add(updateStatement);
            expressionAttributeNames.put(aggregationAttributeName, productCode);
            AttributeValue countByProductionCodeValue = AttributeValue.builder()
                .n(countByProductCode.get(productCode).toString())
                .build();
            expressionAttributeValues.put(aggregationAttributeValue, countByProductionCodeValue);
            index++;
        }

        return Update.builder()
                .tableName(TABLE_NAME)
                .key(compositeKey)
                .updateExpression(String.join(",", updateStatements))
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

    }

    private List<Delete> buildDeletes(List<BillingEvent> billingEvents, TenantConfiguration tenant) {
        List<Delete> deleteRequests = new ArrayList<>();
        for (BillingEvent event : billingEvents) {
            Map<String, AttributeValue> keyToDelete = new HashMap<>();
            Long eventTime = event.getEventTime().toEpochMilli();
            String nonce = event.getNonce();
            AttributeValue tenantIDValue = AttributeValue.builder()
                    .s(tenant.getTenantID())
                    .build();
            keyToDelete.put(PRIMARY_KEY_NAME, tenantIDValue);

            AttributeValue eventValue = AttributeValue.builder()
                    .s(String.format("%s%s%d%s%s",
                        EVENT_PREFIX,
                        ATTRIBUTE_DELIMITER,
                        eventTime,
                        ATTRIBUTE_DELIMITER,
                        nonce))
                    .build();

            keyToDelete.put(SORT_KEY_NAME, eventValue);

            Delete delete = Delete.builder()
                    .tableName(TABLE_NAME)
                    .key(keyToDelete)
                    .build();

            deleteRequests.add(delete);
        }
        return deleteRequests;
    }

    private void performTransaction(List<BillingEvent> billingEvents, Map<String, AttributeValue> compositeKey, ZonedDateTime time, TenantConfiguration tenant) {
        Map<String, Long> countByProductCode = new HashMap<>();
        Update updateRequest = null;
        List<Delete> deleteRequests = null;

        countByProductCode = countEventsByProductCode(billingEvents);
        this.LOGGER.info("Counting the quantity of entries for each product code");
        // Initialize the item for this time slot if necessary
        for (String productCode : countByProductCode.keySet()) {
            this.LOGGER.debug("Initializing count for product code {} for tenant {} at time {}",
                    productCode,
                    tenant.getTenantID(),
                    time.toInstant().toString());
            // TODO: This doesn't have to be done each time; keep track of what is already initialized
            // Pass in a copy of compositeKey because initializeItem makes modifications to it
            initializeItem(new HashMap<>(compositeKey), productCode, time);
        }
        this.LOGGER.debug("Batched {} events, performing transaction", billingEvents.size());
        updateRequest = buildUpdate(countByProductCode, compositeKey);
        deleteRequests = buildDeletes(billingEvents, tenant);
        putRequestsAsTransaction(updateRequest, deleteRequests);
    }

    private void aggregateEntries(Map<ZonedDateTime, List<BillingEvent>> eventsByDate, TenantConfiguration tenant) {
        Map<String, AttributeValue> compositeKey = new HashMap<>();
        List<BillingEvent> eventsToCount = new ArrayList<>();

        for (ZonedDateTime time : eventsByDate.keySet()) {
            AttributeValue tenantIDValue = AttributeValue.builder()
                    .s(tenant.getTenantID())
                    .build();
            compositeKey.put(PRIMARY_KEY_NAME, tenantIDValue);

            AttributeValue aggregationEntryValue = AttributeValue.builder()
                    .s(formatAggregationEntry(time.toInstant().toEpochMilli()))
                    .build();
            compositeKey.put(SORT_KEY_NAME, aggregationEntryValue);

            List<BillingEvent> billingEvents = eventsByDate.get(time);

            for (BillingEvent event : billingEvents) {
                eventsToCount.add(event);
                // 24 purges, 1 update
                // Minus one because I need to leave room for the update statement
                if (eventsToCount.size() == MAXIMUM_BATCH_SIZE - 1) {
                    performTransaction(eventsToCount, compositeKey, time, tenant);
                    eventsToCount.clear();
                }
            }
            // Submit the last batch of items
            // If the number of requests lands on an increment of 25, need to make sure that no attempt is made to put
            // an empty list, which is why I'm checking for a size greater than zero
            if (eventsToCount.size() > 0) {
                performTransaction(eventsToCount, compositeKey, time, tenant);
                eventsToCount.clear();
            }
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) {
        this.LOGGER.info("Resolving tenant IDs in table {}", TABLE_NAME);
        List<TenantConfiguration> tenants = TenantConfiguration.getTenantConfigurations(TABLE_NAME, this.ddb, this.LOGGER);
        this.LOGGER.info("Resolved tenant IDs in table {}", TABLE_NAME);
        if (tenants == null) {
            this.LOGGER.info("No tenants found");
            return;
        }
        for (TenantConfiguration tenant : tenants) {
            List<BillingEvent> billingEvents = getBillingEventsForTenant(tenant.getTenantID());
            if (billingEvents.isEmpty()) {
                this.LOGGER.info("No events for {}", tenant.getTenantID());
                continue;
            }
            // Count the number of events - this step is necessary to make the transactions work; they need
            // to be grouped together
            Map<ZonedDateTime, List<BillingEvent>> categorizedEvents = categorizeEvents(tenant, billingEvents);
            if (categorizedEvents == null) {
                this.LOGGER.info("No aggregation entries for {}", tenant.getTenantID());
            } else {
                // Put those results back into the table
                aggregateEntries(categorizedEvents, tenant);
            }
        }
    }
}