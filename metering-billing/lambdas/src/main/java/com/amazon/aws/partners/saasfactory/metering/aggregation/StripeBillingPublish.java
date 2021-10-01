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


import com.amazon.aws.partners.saasfactory.metering.common.AggregationEntry;
import com.amazon.aws.partners.saasfactory.metering.common.TenantConfiguration;
import com.amazon.aws.partners.saasfactory.saasboost.ApiGatewayHelper;
import com.amazon.aws.partners.saasfactory.saasboost.ApiRequest;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.UsageRecord;
import com.stripe.net.RequestOptions;
import com.stripe.param.UsageRecordCreateOnSubscriptionItemParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.amazon.aws.partners.saasfactory.metering.common.Constants.*;

public class StripeBillingPublish implements RequestStreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeBillingPublish.class);
    private final static String TABLE_NAME = System.getenv(TABLE_ENV_VARIABLE);
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private final DynamoDbClient ddb;

    public StripeBillingPublish() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(TABLE_NAME)) {
            throw new IllegalStateException("Missing required environment variable " + TABLE_ENV_VARIABLE);
        }
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        // Used by TenantConfiguration
        if (Utils.isBlank(System.getenv("DYNAMODB_CONFIG_INDEX_NAME"))) {
            throw new IllegalStateException("Missing required environment variable DYNAMODB_CONFIG_INDEX_NAME");
        }
        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
        ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    private List<AggregationEntry> getAggregationEntries(String tenantID) {
        HashMap<String,String> expressionNames = new HashMap<>();
        expressionNames.put(PRIMARY_KEY_EXPRESSION_NAME, PRIMARY_KEY_NAME);
        expressionNames.put(SORT_KEY_EXPRESSION_NAME, SORT_KEY_NAME);
        expressionNames.put(SUBMITTED_KEY_EXPRESSION_NAME, SUBMITTED_KEY_ATTRIBUTE_NAME);

        HashMap<String, AttributeValue> expressionValues = new HashMap<>();
        AttributeValue tenantIDValue = AttributeValue.builder()
                .s(tenantID)
                .build();
        expressionValues.put(TENANT_ID_EXPRESSION_VALUE, tenantIDValue);

        AttributeValue aggregationEntryPrefixValue = AttributeValue.builder()
                .s(AGGREGATION_ENTRY_PREFIX)
                .build();
        expressionValues.put(AGGREGATION_EXPRESSION_VALUE, aggregationEntryPrefixValue);

        // Filter for those entries that have not yet been submitted to the billing provider
        AttributeValue keySubmittedValue = AttributeValue.builder()
                .bool(false)
                .build();
        expressionValues.put(KEY_SUBMITTED_EXPRESSION_VALUE, keySubmittedValue);

        QueryResponse result = null;
        List<AggregationEntry> aggregationEntries = new ArrayList<>();
        do {
            QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression(String.format("%s = %s and begins_with(%s, %s)",
                                                PRIMARY_KEY_EXPRESSION_NAME,
                                                TENANT_ID_EXPRESSION_VALUE,
                                                SORT_KEY_EXPRESSION_NAME,
                                                AGGREGATION_EXPRESSION_VALUE))
                    .filterExpression(String.format("%s = %s", SUBMITTED_KEY_EXPRESSION_NAME, KEY_SUBMITTED_EXPRESSION_VALUE))
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues)
                    .build();
            if (result != null && !result.lastEvaluatedKey().isEmpty()) {
                request = request.toBuilder()
                        .exclusiveStartKey(result.lastEvaluatedKey())
                        .build();
            }
            try {
                result = this.ddb.query(request);
            } catch (ResourceNotFoundException e) {
                LOGGER.error("Table {} does not exist", TABLE_NAME);
                return null;
            } catch (InternalServerErrorException e) {
                LOGGER.error(e.getMessage());
                return null;
            }
            for (Map<String, AttributeValue> item : result.items()) {
                String[] aggregationInformation = item.get(SORT_KEY_NAME).s().split(ATTRIBUTE_DELIMITER);
                Instant periodStart = Instant.ofEpochMilli(Long.valueOf(aggregationInformation[PERIOD_START_ARRAY_LOCATION]));
                Map<String, AttributeValue> quantityByProductCode = item.get(QUANTITY_ATTRIBUTE_NAME).m();
                String idempotencyKey = item.get(IDEMPOTENTCY_KEY_ATTRIBUTE_NAME).s();
                for (Map.Entry<String, AttributeValue> mapEntry : quantityByProductCode.entrySet()) {
                    AggregationEntry entry = new AggregationEntry(tenantID,
                            periodStart,
                            mapEntry.getKey(),
                            Integer.valueOf(mapEntry.getValue().n()),
                            idempotencyKey);
                    aggregationEntries.add(entry);
                }
            }
        } while (!result.lastEvaluatedKey().isEmpty());
        return aggregationEntries;
    }

    private String getStripeAPIKey() {

        //invoke SaaS Boost private API to get API Key for Billing
        String apiKey;
        try {
            // CloudFormation needs the Parameter Store reference key (version number) to properly
            // decode secure string parameters... So we need to call the private API to get it.
            ApiRequest paramStoreRef = ApiRequest.builder()
                    .resource("settings/BILLING_API_KEY/secret")
                    .method("GET")
                    .build();
            SdkHttpFullRequest apiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, paramStoreRef);
            String responseBody = null;
            try {
                responseBody = ApiGatewayHelper.signAndExecuteApiRequest(apiRequest, API_TRUST_ROLE, "BillingIntegration");
                Map<String, String> refsMap = Utils.fromJson(responseBody, HashMap.class);
                if (null == refsMap) {
                    throw new RuntimeException("responseBody is invalid");
                }                
                apiKey = refsMap.get("value");
            } catch (Exception e) {
                LOGGER.error("getStripeAPIKey: Error invoking API settings/BILLING_API/ref");
                LOGGER.error(Utils.getFullStackTrace(e));
                throw new RuntimeException(e);
            }

        } catch (Exception e) {
            LOGGER.error("getStripeAPIKey: Can't invoke {} lambda", System.getenv("PARAM_STORE_REF_FUNCTION"));
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        return apiKey;
    }

    private void addUsageToSubscriptionItem(String subscriptionItemId, AggregationEntry aggregationEntry) {
        UsageRecord usageRecord = null;

        UsageRecordCreateOnSubscriptionItemParams params =
                UsageRecordCreateOnSubscriptionItemParams.builder()
                    .setQuantity(Long.valueOf(aggregationEntry.getQuantity()))
                    .setTimestamp(aggregationEntry.getPeriodStart().truncatedTo(ChronoUnit.SECONDS).getEpochSecond())
                    .build();

        RequestOptions requestOptions = RequestOptions
                .builder()
                .setIdempotencyKey(aggregationEntry.getIdempotencyKey())
                .build();

        try {
            usageRecord = UsageRecord.createOnSubscriptionItem(subscriptionItemId, params, requestOptions);
        } catch(StripeException e) {
            LOGGER.error("Stripe exception:\n{}", e.getMessage());
            LOGGER.error("Timestamp: {}", aggregationEntry.getPeriodStart());
            return;
        }
        Map<String, List<String>> responseHeaders = usageRecord.getLastResponse().headers().map();
        // Check for idempotency key in use; if it is, then this is likely a situation where the
        // item was already submitted, but not marked as published
        if (responseHeaders.containsKey(STRIPE_IDEMPOTENCY_REPLAYED)) {
            LOGGER.info("Aggregation entry {} for tenant {} already published; marking as published",
                            formatAggregationEntry(aggregationEntry.getPeriodStart().toEpochMilli()),
                            aggregationEntry.getTenantID());
        }
        markAggregationRecordAsSubmitted(aggregationEntry);
    }

    private void markAggregationRecordAsSubmitted(AggregationEntry aggregationEntry) {
        // Update the attribute that marks an item as submitted
        Map<String, AttributeValue> aggregationEntryKey = new HashMap<>();
        AttributeValue tenantIDValue = AttributeValue.builder()
                .s(aggregationEntry.getTenantID())
                .build();
        aggregationEntryKey.put(PRIMARY_KEY_NAME, tenantIDValue);

        AttributeValue aggregationStringValue = AttributeValue.builder()
                .s(formatAggregationEntry(aggregationEntry.getPeriodStart().toEpochMilli()))
                .build();
        aggregationEntryKey.put(SORT_KEY_NAME, aggregationStringValue);

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put(SUBMITTED_KEY_EXPRESSION_NAME, SUBMITTED_KEY_ATTRIBUTE_NAME);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

        AttributeValue keySubmittedValue = AttributeValue.builder()
                .bool(true)
                .build();
        expressionAttributeValues.put(KEY_SUBMITTED_EXPRESSION_VALUE, keySubmittedValue);

        String updateExpression = String.format("SET %s = %s",
                                                SUBMITTED_KEY_EXPRESSION_NAME,
                                                KEY_SUBMITTED_EXPRESSION_VALUE);

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(aggregationEntryKey)
                .updateExpression(updateExpression)
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        try {
            ddb.updateItem(updateRequest);
        } catch (ResourceNotFoundException|InternalServerErrorException|TransactionCanceledException e) {
            LOGGER.error(e.getMessage());
        }

        LOGGER.info("Marked aggregation record {} for tenant {} as published",
                aggregationEntry.getTenantID(),
                formatAggregationEntry(aggregationEntry.getPeriodStart().toEpochMilli()));
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) {
        Stripe.apiKey = getStripeAPIKey();
        LOGGER.info("Fetching tenant IDs in table {}", TABLE_NAME);
        List<TenantConfiguration> tenantConfigurations = TenantConfiguration.getTenantConfigurations(TABLE_NAME, ddb, LOGGER);
        if (tenantConfigurations == null || tenantConfigurations.isEmpty()) {
            LOGGER.info("No tenant configurations found in table {}", TABLE_NAME);
            return;
        }
        LOGGER.info("Resolved tenant IDs in table {}", TABLE_NAME);
        for (TenantConfiguration tenant: tenantConfigurations) {
            List<AggregationEntry> aggregationEntries = getAggregationEntries(tenant.getTenantID());
            if (aggregationEntries == null || aggregationEntries.isEmpty()) {
                LOGGER.info("No unpublished aggregation entries found for tenant {}",
                                tenant.getTenantID());
            } else {
                if (aggregationEntries.size() == 1) {
                    LOGGER.info("Found {} an unpublished aggregation entry for tenant {}",
                            aggregationEntries.size(),
                            tenant.getTenantID());
                } else{
                    LOGGER.info("Found {} unpublished aggregation entries for tenant {}",
                            aggregationEntries.size(),
                            tenant.getTenantID());
                }
                for (AggregationEntry entry : aggregationEntries) {
                    String subscriptionID = tenant.getSubscriptionMapping().get(entry.getProductCode());
                    if (subscriptionID == null) {
                        LOGGER.error("No subscription ID for product code {} found associated with tenant {}",
                                            entry.getProductCode(),
                                            tenant.getTenantID());
                        LOGGER.error("Unable to publish aggregation entry {} associated with tenant {}",
                                            formatAggregationEntry(entry.getPeriodStart().toEpochMilli()),
                                            tenant.getTenantID());
                        continue;
                    }
                    addUsageToSubscriptionItem(tenant.getSubscriptionMapping().get(entry.getProductCode()), entry);
                }
            }
        }
    }
}
