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
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.regions.Region;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class Constants {

    private Constants() {}

    // This is used for pulling a section of a UUID to add unique token in certain situations
    public static final ChronoUnit TRUNCATION_UNIT = ChronoUnit.MINUTES;
    public static final Integer EVENT_TIME_ARRAY_INDEX = 1;
    public static final Integer MAXIMUM_BATCH_SIZE = 25;
    public static final Integer NONCE_ARRAY_INDEX = 2;
    public static final Integer PERIOD_START_ARRAY_LOCATION = 2;
    public static final Integer SELECTED_UUID_INDEX = 4;
    public static final String ADD_TO_AGGREGATION_EXPRESSION_NAME = "#aggregationName";
    public static final String ADD_TO_AGGREGATION_EXPRESSION_VALUE = ":aggregationValue";
    public static final String AGGREGATION_ENTRY_PREFIX = "AGGREGATE";
    public static final String AGGREGATION_EXPRESSION_VALUE = ":aggregate";
    public static final String ATTRIBUTE_DELIMITER = "#";
    public static final String CONFIG_EXPRESSION_NAME = "#configurationAttributeName";
    public static final String CONFIG_EXPRESSION_VALUE = ":config";
    public static final String CONFIG_INDEX_NAME_ENV_VARIABLE = "DYNAMODB_CONFIG_INDEX_NAME";
    public static final String CONFIG_SORT_KEY_VALUE = "CONFIG";
    public static final String EVENT_COUNT_INITIALIZATION_VALUE = "0";
    public static final String EVENT_PREFIX = "EVENT";
    public static final String EVENT_PREFIX_ATTRIBUTE_VALUE = ":event";
    public static final String EXTERNAL_PRODUCT_CODE_EXPRESSION_VALUE = ":externalProductCode";
    public static final String IDEMPOTENTCY_KEY_ATTRIBUTE_NAME = "idempotency_key";
    public static final String INTERNAL_PRODUCT_CODE_EXPRESSION_NAME = "#internalProductCode";
    public static final String INTERNAL_PRODUCT_CODE_INITIALIZATION_VALUE = "";
    public static final String KEY_SUBMITTED_EXPRESSION_VALUE = ":confirmPublished";
    public static final String PRIMARY_KEY_EXPRESSION_NAME = "#datatype";
    public static final String PRIMARY_KEY_NAME = "data_type";
    public static final String PRODUCT_CODE_ATTRIBUTE_NAME = "product_code";
    public static final String QUANTITY_ATTRIBUTE_NAME = "quantity";
    public static final String QUANTITY_EXPRESSION_NAME = "#quantityName";
    public static final String SORT_KEY_EXPRESSION_NAME = "#subtype";
    public static final String SORT_KEY_NAME = "sub_type";
    public static final String STRIPE_IDEMPOTENCY_REPLAYED = "idempotent-replayed";
    public static final String STRIPE_SECRET_ARN_ENV_VARIABLE = "STRIPE_SECRET_ARN";
    public static final String SUBMITTED_KEY_ATTRIBUTE_NAME = "published_to_billing_provider";
    public static final String SUBMITTED_KEY_EXPRESSION_NAME = "#publishName";
    public static final String SUBSCRIPTION_MAPPING_ATTRIBUTE_NAME = "subscription_mapping";
    public static final String SUBSCRIPTION_MAPPING_EXPRESSION_NAME = "#subscriptionMapping";
    public static final String TABLE_ENV_VARIABLE = "DYNAMODB_TABLE_NAME";
    public static final String TENANT_ID_EXPRESSION_VALUE = ":tenantID";
    public static final String TENANT_PREFIX = "TENANT";
    public static final String UUID_DELIMITER = "-";
    private final static Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));

    public static String getEnvVariable(String envVariableName, Logger logger) {
        String envVariableValue = System.getenv(envVariableName);
        if (envVariableValue == null) {
            logger.error("Environment variable {} not present", envVariableName);
            return "";
        }
        logger.debug("Resolved {} to {}", envVariableName, envVariableValue);
        return envVariableValue;
    }

    public static String formatAggregationEntry(long aggregationTime) {
        return String.format("%s%s%s%s%d",
                AGGREGATION_ENTRY_PREFIX,
                ATTRIBUTE_DELIMITER,
                TRUNCATION_UNIT.toString().toUpperCase(),
                ATTRIBUTE_DELIMITER,
                aggregationTime);
    }

    public static String formatTenantEntry(String tenantID) {
        return String.format("%s%s%s",
                TENANT_PREFIX,
                ATTRIBUTE_DELIMITER,
                tenantID);
    }

    public static String formatEventEntry(Instant timeOfEvent) {
        return String.format("%s%s%d%s%s",
                EVENT_PREFIX,
                ATTRIBUTE_DELIMITER,
                timeOfEvent.toEpochMilli(),
                ATTRIBUTE_DELIMITER,
                UUID.randomUUID().toString().split("-")[SELECTED_UUID_INDEX]);
    }
}
