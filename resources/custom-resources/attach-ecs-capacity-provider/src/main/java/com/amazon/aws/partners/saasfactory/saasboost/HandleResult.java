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

import java.util.HashMap;
import java.util.Map;

public class HandleResult {
    private boolean success = false;
    private Map<String, Object> responseData = new HashMap<String, Object>();

    public void setSucceeded() {
        this.success = true;
    }

    public void setFailed() {
        this.success = false;
    }

    public void setResponseData(Map<String, Object> responseData) {
        this.responseData = responseData;
    }

    public void putResponseData(String key, Object value) {
        this.responseData.put(key, value);
    }

    public void putFailureReason(String reason) {
        putResponseData("Reason", reason);
    }

    public boolean succeeded() {
        return this.success;
    }

    public Map<String, Object> getResponseData() {
        return this.responseData;
    }
}