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

public class OnboardingEvent {

    private final String tenantId;
    private final String internalProductCode;
    private final String externalProductCode;

    public OnboardingEvent(String tenantId, String internalProductCode, String externalProductCode) {
        this.tenantId = tenantId;
        this.internalProductCode = internalProductCode;
        this.externalProductCode = externalProductCode;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public String getInternalProductCode() {
        return this.internalProductCode;
    }

    public String getExternalProductCode() {
        return this.externalProductCode;
    }
}
