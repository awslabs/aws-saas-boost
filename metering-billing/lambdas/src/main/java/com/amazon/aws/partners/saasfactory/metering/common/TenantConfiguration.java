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
package com.amazon.aws.partners.saasfactory.metering.common;

import org.slf4j.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.amazon.aws.partners.saasfactory.metering.common.Constants.CONFIG_EXPRESSION_NAME;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.CONFIG_EXPRESSION_VALUE;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.CONFIG_INDEX_NAME_ENV_VARIABLE;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.CONFIG_SORT_KEY_VALUE;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.PRIMARY_KEY_NAME;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.SORT_KEY_NAME;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.SUBSCRIPTION_MAPPING_ATTRIBUTE_NAME;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.formatTenantEntry;
import static com.amazon.aws.partners.saasfactory.metering.common.Constants.getEnvVariable;

public class TenantConfiguration {

    private final String tenantID;
    private final Map<String, String> SubscriptionMapping;

    private TenantConfiguration(String tenantID, Map<String, String> subscriptionMapping) {
       this.tenantID = tenantID;
       this.SubscriptionMapping = subscriptionMapping;
    }

    public String getTenantID() {
        return tenantID;
    }

    public Map<String, String> getSubscriptionMapping() { return SubscriptionMapping; }

    private static Map<String, String> parseSubscriptionMapping(Map<String, AttributeValue> item) {
        Map<String, String> subscriptionMapping = new HashMap<>();
        for (String internalSubscriptionName : item.get(SUBSCRIPTION_MAPPING_ATTRIBUTE_NAME).m().keySet()) {
            subscriptionMapping.put(internalSubscriptionName, item.get(SUBSCRIPTION_MAPPING_ATTRIBUTE_NAME).m().get(internalSubscriptionName).s());
        }
        return subscriptionMapping;
    }

    public static List<TenantConfiguration> getTenantConfigurations(String tableName, DynamoDbClient ddb, Logger logger) {
        List<TenantConfiguration> tenantIDs = new ArrayList<>();
        String configIndexName = getEnvVariable(CONFIG_INDEX_NAME_ENV_VARIABLE, logger);

        if (configIndexName.equals("")) {
            return tenantIDs;
        }

        HashMap<String,String> expressionNames = new HashMap<>();
        expressionNames.put(CONFIG_EXPRESSION_NAME, SORT_KEY_NAME);

        HashMap<String,AttributeValue> expressionValues = new HashMap<>();
        AttributeValue sortKeyValue = AttributeValue.builder()
                .s(CONFIG_SORT_KEY_VALUE)
                .build();
        expressionValues.put(CONFIG_EXPRESSION_VALUE, sortKeyValue);
        QueryResponse result = null;
        do {
            QueryRequest request = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(configIndexName)
                    .keyConditionExpression(String.format("%s = %s", CONFIG_EXPRESSION_NAME, CONFIG_EXPRESSION_VALUE))
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues)
                    .build();
            if (result != null && !result.lastEvaluatedKey().isEmpty()) {
                request = request.toBuilder()
                        .exclusiveStartKey(result.lastEvaluatedKey())
                        .build();
            }
            try {
                result = ddb.query(request);
            } catch (ResourceNotFoundException e) {
                logger.error("Table {} does not exist", tableName);
                return null;
            } catch (InternalServerErrorException e) {
                logger.error(e.getMessage());
                return null;
            }
            for (Map<String, AttributeValue> item : result.items()) {
                String tenantID = item.get(PRIMARY_KEY_NAME).s();
                Map<String, String> subscriptionMapping = parseSubscriptionMapping(item);
                TenantConfiguration tenant = new TenantConfiguration(tenantID, subscriptionMapping);
                logger.info("Found tenant ID {}", tenantID);
                tenantIDs.add(tenant);
            }
        } while (!result.lastEvaluatedKey().isEmpty());
        return tenantIDs;
    }

    public static TenantConfiguration getTenantConfiguration(String tenantID, String tableName, DynamoDbClient ddb, Logger logger) {

        Map<String, AttributeValue> compositeKey = new HashMap<>();
        AttributeValue primaryKeyValue = AttributeValue.builder()
                .s(formatTenantEntry(tenantID))
                .build();
        compositeKey.put(PRIMARY_KEY_NAME, primaryKeyValue);
        AttributeValue sortKeyValue = AttributeValue.builder()
                .s(CONFIG_SORT_KEY_VALUE)
                .build();
        compositeKey.put(SORT_KEY_NAME, sortKeyValue);

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(compositeKey)
                .build();

        Map<String, AttributeValue> item;
        try {
            item = ddb.getItem(request).item();
        } catch (ResourceNotFoundException e) {
            logger.error("Table {} does not exist", tableName);
            return null;
        } catch (InternalServerErrorException e) {
            logger.error(e.getMessage());
            return null;
        }

        TenantConfiguration tenant;
        if (!item.isEmpty()) {
            Map<String, String> subscriptionMapping = parseSubscriptionMapping(item);
            tenant = new TenantConfiguration(tenantID, subscriptionMapping);
        } else {
            return null;
        }

        return tenant;
    }
}
