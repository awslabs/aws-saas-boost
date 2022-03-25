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

public enum TimeRange {
    HOUR_24(24),
    HOUR_12(12),
    HOUR_10(10),
    HOUR_8(8),
    HOUR_4(4),
    HOUR_2(2),
    HOUR_1(1),
    TODAY(0),
    DAY_7(7),
    THIS_WEEK(0),
    THIS_MONTH(0),
    DAY_30(30);

    private final int valueToSubtract;

    TimeRange(int valueToSubtract) {
        this.valueToSubtract = valueToSubtract;
    }

    public int getValueToSubtract() {
        return this.valueToSubtract;
    }

}