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

public enum MeteredProduct {
    product_requests("Requests",10);

    private final long amount;
    private String label;

    MeteredProduct(String label, long amount) {
        this.amount = amount;
        this.label = label;
    }

    public long getAmount() {
        return this.amount;
    }

    public String getLabel() {return this.label; }

}
