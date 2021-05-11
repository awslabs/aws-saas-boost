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

import java.time.Instant;

public class BillingEvent implements Comparable<BillingEvent> {

    private final Instant eventTime;
    private final String productCode;
    private final String tenantID;
    private final Long quantity;
    private final String nonce;

    public BillingEvent(String tenantID, Instant eventTime, String productCode, Long quantity, String nonce) {
        if (tenantID == null || productCode == null || quantity == null) {
            throw new NullPointerException();
        }
        this.eventTime = eventTime != null ? eventTime : Instant.now();
        this.productCode = productCode;
        this.tenantID = tenantID;
        this.quantity = quantity;
        this.nonce = nonce;
    }

    public BillingEvent(String tenantID, Instant eventTime, String productCode, Long quantity) {
        this(tenantID, eventTime, productCode, quantity, null);
    }

    public String getProductCode() { return this.productCode; }

    public Instant getEventTime() { return this.eventTime; }

    public String getTenantID() { return this.tenantID; }

    public Long getQuantity() { return this.quantity; }

    public String getNonce() { return this.nonce; }

    @Override
    public int compareTo(BillingEvent event) {
        return this.eventTime.compareTo(event.eventTime);
    }
}
