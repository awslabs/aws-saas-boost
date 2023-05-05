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

import com.amazon.aws.partners.saasfactory.saasboost.ApiGatewayHelper;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public final class BillingUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillingUtils.class);

    public static String getBillingApiKey(ApiGatewayHelper api) {
        //invoke SaaS Boost private API to get API Key for Billing
        String apiKey = null;
        try {
            String responseBody = api.authorizedRequest("GET", "settings/BILLING_API_KEY/secret");
            Map<String, String> setting = Utils.fromJson(responseBody, HashMap.class);
            if (null == setting) {
                throw new RuntimeException("responseBody is invalid");
            }            
            apiKey = setting.get("value");
        } catch (NoSuchElementException nsee) {
            LOGGER.error("Error retrieving Stripe API key, AppConfig does not exist or has no api key");
            LOGGER.error(Utils.getFullStackTrace(nsee));
        } catch (Exception e) {
            LOGGER.error("getBillingApiKey: Error invoking API settings/BILLING_API/secret");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return apiKey;
    }
}
