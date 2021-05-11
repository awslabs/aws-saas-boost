/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazon.aws.partners.saasfactory.metering.common;

public enum SubscriptionPlan {

    //You can customize the metered products for a plan
    product_none("None", 0, null),
    product_basic("Basic", 3000, MeteredProduct.values()),
    product_standard("Standard", 10000, MeteredProduct.values()),
    product_premium("Premium", 20000, MeteredProduct.values());

    private final long amount;
    private String label;
    private MeteredProduct[] meteredProducts;

    SubscriptionPlan(String label, long amount, MeteredProduct[] values) {
        this.amount = amount;
        this.label = label;
        this.meteredProducts = values;
    }

    public long getAmount() {
        return this.amount;
    }

    public String getLabel() {
        return this.label;
    }

    public MeteredProduct[] getMeteredProducts() {
        return this.meteredProducts;
    }
}

