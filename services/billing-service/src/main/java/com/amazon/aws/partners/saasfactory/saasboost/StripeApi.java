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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

public class StripeApi implements BillingProviderApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeApi.class);
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");

    @Override
    public Collection<BillingPlan> getPlans() {
        // Stripe Product & Pricing Model
        return Collections.emptyList();
    }
}
