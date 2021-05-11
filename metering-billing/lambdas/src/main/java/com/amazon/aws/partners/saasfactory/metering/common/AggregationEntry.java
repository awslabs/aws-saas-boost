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

public class AggregationEntry {

    private final String tenantID;
    private final Instant periodStart;
    private final String productCode;
    private final Integer quantity;
    private final String idempotencyKey;

    public AggregationEntry(String tenantID, Instant periodStart, String productCode, Integer quantity, String idempotencyKey) {
       this.tenantID = tenantID;
       this.periodStart = periodStart;
       this.productCode = productCode;
       this.quantity = quantity;
       this.idempotencyKey = idempotencyKey;
    }

    public String getTenantID() { return tenantID; }

    public Instant getPeriodStart() { return periodStart; }

    public String getProductCode() { return productCode; }

    public Integer getQuantity() { return quantity; }

    public String getIdempotencyKey() { return idempotencyKey; }
}
