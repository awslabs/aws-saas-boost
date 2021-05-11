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
package com.amazon.aws.partners.saasfactory.metering.onboarding;

import com.amazon.aws.partners.saasfactory.metering.common.OnboardingEvent;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

import static com.amazon.aws.partners.saasfactory.metering.common.Constants.*;

public class OnboardTenantProduct implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient ddb;
    private final static Logger LOGGER = LoggerFactory.getLogger(OnboardTenantProduct.class);
    private final static String TABLE_NAME = System.getenv(TABLE_ENV_VARIABLE);

    public OnboardTenantProduct() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(TABLE_NAME)) {
            throw new IllegalStateException("Missing required environment variable " + TABLE_ENV_VARIABLE);
        }
        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
        ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    private void initializeMap(Map<String, AttributeValue> compositeKey, String internalProductCode) {
        // Format the statements
        Map<String, AttributeValue> initialValue = new HashMap<>();
        AttributeValue internalProductCodeValue = AttributeValue.builder()
                .s(INTERNAL_PRODUCT_CODE_INITIALIZATION_VALUE)
                .build();
        initialValue.put(internalProductCode, internalProductCodeValue);

        AttributeValue initialMapValue = AttributeValue.builder()
                .m(initialValue)
                .build();

        compositeKey.put(SUBSCRIPTION_MAPPING_ATTRIBUTE_NAME, initialMapValue);
        String conditionalStatement = String.format("attribute_not_exists(%s)", SUBSCRIPTION_MAPPING_ATTRIBUTE_NAME);

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(compositeKey)
                .conditionExpression(conditionalStatement)
                .build();

        try {
            ddb.putItem(putItemRequest);
        } catch (ConditionalCheckFailedException e) {
            // Repeat the transaction and see if it works
            LOGGER.error("Entry for {} for tenant {}} already exists",
                    internalProductCode,
                    compositeKey.get(PRIMARY_KEY_NAME).s());
        }  catch (SdkServiceException dynamodbError) {
            LOGGER.error("dynamodb::PutItem error", dynamodbError);
            LOGGER.error(Utils.getFullStackTrace(dynamodbError));
            throw dynamodbError;
        }
    }

    private UpdateItemRequest buildUpdateStatement(OnboardingEvent onboardingEvent) {
        HashMap<String, AttributeValue> compositeKey = new HashMap<>();

        AttributeValue primaryKeyValue = AttributeValue.builder()
                .s(String.format("%s%s%s",
                        TENANT_PREFIX,
                        ATTRIBUTE_DELIMITER,
                        onboardingEvent.getTenantId()))
                .build();

        compositeKey.put(PRIMARY_KEY_NAME, primaryKeyValue);

        AttributeValue sortKeyValue = AttributeValue.builder()
                .s(CONFIG_SORT_KEY_VALUE)
                .build();
        compositeKey.put(SORT_KEY_NAME, sortKeyValue);

        HashMap<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put(INTERNAL_PRODUCT_CODE_EXPRESSION_NAME, onboardingEvent.getInternalProductCode());
        expressionAttributeNames.put(SUBSCRIPTION_MAPPING_EXPRESSION_NAME, SUBSCRIPTION_MAPPING_ATTRIBUTE_NAME);

        HashMap<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        AttributeValue externalProductCodeValue = AttributeValue.builder()
                .s(onboardingEvent.getExternalProductCode())
                .build();

        expressionAttributeValues.put(EXTERNAL_PRODUCT_CODE_EXPRESSION_VALUE, externalProductCodeValue);

        String updateStatement = String.format("SET %s.%s = %s",
                SUBSCRIPTION_MAPPING_EXPRESSION_NAME,
                INTERNAL_PRODUCT_CODE_EXPRESSION_NAME,
                EXTERNAL_PRODUCT_CODE_EXPRESSION_VALUE);

        return UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(compositeKey)
                .updateExpression(updateStatement)
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

    }

    private void putTenant(OnboardingEvent onboardingEvent) {
        // This needs to be an update; there may already be an existing value in the subscription mapping attribute
        UpdateItemRequest subscriptionMappingUpdate = buildUpdateStatement(onboardingEvent);

        // Initialize the map; a copy is required because the original map returned by key() is immutable
        initializeMap(new HashMap<>(subscriptionMappingUpdate.key()), onboardingEvent.getInternalProductCode());

        try {
            ddb.updateItem(subscriptionMappingUpdate);
        } catch (SdkServiceException e) {
            LOGGER.error("Error updating Dynamo", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
    }

    //HANDLES request for Event bridge detail type BILLING_PRODUCT_ONBOARD
    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);
        if (!event.containsKey("detail")) {
            throw new RuntimeException("Event detail is null");
        }
        Map<String, String> detail = (Map<String, String>) event.get("detail");

        // Create onboarding event
        OnboardingEvent onboardingEvent = new OnboardingEvent(detail.get("tenantId"), detail.get("internalProductCode"), detail.get("externalProductCode"));

        // Put the onboarding event into DynamoDB
        putTenant(onboardingEvent);
        LOGGER.info("Created tenant with ID %s", onboardingEvent.getTenantId());

        return null;
    }
}
