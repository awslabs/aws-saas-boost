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
package com.amazon.aws.partners.saasfactory.metering.billing;

import com.amazon.aws.partners.saasfactory.metering.common.BillingEvent;
import com.amazon.aws.partners.saasfactory.metering.common.ProcessBillingEventException;
import com.amazon.aws.partners.saasfactory.metering.common.TenantConfiguration;
import com.amazon.aws.partners.saasfactory.metering.onboarding.OnboardTenantProduct;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.amazon.aws.partners.saasfactory.metering.common.Constants.*;

public class ProcessBillingEvent implements RequestHandler<Map<String, Object>, Object> {

    private final DynamoDbClient ddb;
    private final static Logger LOGGER = LoggerFactory.getLogger(OnboardTenantProduct.class);
    private final static String TABLE_NAME = System.getenv(TABLE_ENV_VARIABLE);

    public ProcessBillingEvent() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(TABLE_NAME)) {
            throw new IllegalStateException("Missing required environment variable " + TABLE_ENV_VARIABLE);
        }
        LOGGER.info("Version Info: " + Utils.version(this.getClass()));
        ddb = Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    private boolean putEvent(BillingEvent billingEvent) {
        HashMap<String,AttributeValue> item= new HashMap<>();

        AttributeValue primaryKeyValue = AttributeValue.builder()
                .s(formatTenantEntry(billingEvent.getTenantID()))
                .build();

        AttributeValue sortKeyValue = AttributeValue.builder()
                .s(formatEventEntry(billingEvent.getEventTime()))
                .build();

        AttributeValue productCodeValue = AttributeValue.builder()
                .s(billingEvent.getProductCode())
                .build();

        AttributeValue quantityAttributeValue = AttributeValue.builder()
                .n(billingEvent.getQuantity().toString())
                .build();

        item.put(PRIMARY_KEY_NAME, primaryKeyValue);
        item.put(SORT_KEY_NAME, sortKeyValue);
        item.put(PRODUCT_CODE_ATTRIBUTE_NAME, productCodeValue);
        item.put(QUANTITY_ATTRIBUTE_NAME, quantityAttributeValue);

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        try {
            this.ddb.putItem(request);
        } catch (ResourceNotFoundException e) {
            LOGGER.error("Table {} does not exist", TABLE_NAME);
            return false;
        } catch (InternalServerErrorException e) {
            LOGGER.error(e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");

        // Verify the existence of the tenant ID
        TenantConfiguration tenant = TenantConfiguration.getTenantConfiguration(
                (String) detail.get("TenantId"),
                TABLE_NAME,
                ddb,
                LOGGER);

        if (tenant == null) {
            LOGGER.info("TenantId with ID {} not found", detail.get("TenantId"));
            return null;
        }

        LOGGER.info("Found TenantId {}", detail.get("TenantId"));
        BillingEvent billingEvent;
        try {
            billingEvent = new BillingEvent((String) detail.get("TenantId"),
                    Instant.now(),
                    (String) detail.get("ProductCode"),
                    (Long) detail.get("Quantity"));
        } catch (NullPointerException npe) {
            LOGGER.error("Billing event not created because a component of the billing event was missing.");
            throw new ProcessBillingEventException("Billing event not created because a component of the billing event was missing.");
        }
        LOGGER.debug("Billing event time is: {}", billingEvent.getEventTime());
        boolean result = putEvent(billingEvent);
        if (result) {
            LOGGER.info("{} | {} | {} | {}",
                    billingEvent.getTenantID(),
                    billingEvent.getEventTime().toString(),
                    billingEvent.getProductCode(),
                    billingEvent.getQuantity());
        } else {
            LOGGER.error("{} | {} | {} | {}",
                    billingEvent.getTenantID(),
                    billingEvent.getEventTime().toString(),
                    billingEvent.getProductCode(),
                    billingEvent.getQuantity());
            throw new ProcessBillingEventException("Failure to put item into DyanmoDB");
        }
        return null;
    }
}