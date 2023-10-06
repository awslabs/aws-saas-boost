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

import java.util.HashMap;
import java.util.Map;

public class MetricContext extends HashMap<String, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricContext.class);
    private static final String TENANT_ID = "TenantId";
    private static final String USER_ID = "UserId";
    private static final String ACTION = "Action";
    private static final String APPLICATION = "Application";
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put(TENANT_ID, "");
        DEFAULTS.put(USER_ID, "");
        DEFAULTS.put(ACTION, "");
        DEFAULTS.put(APPLICATION, "");
    }

    public MetricContext() {
        super(DEFAULTS);
    }

    public String getTenantId() {
        return get(TENANT_ID);
    }

    public void setTenantId(String tenantId) {
        put(TENANT_ID, tenantId);
        System.out.println("Default tenantId = " + DEFAULTS.get(TENANT_ID));
    }

    public String getUserId() {
        return get(USER_ID);
    }

    public void setUserId(String userId) {
        put(USER_ID, userId);
    }

    public String getAction() {
        return get(ACTION);
    }

    public void setAction(String action) {
        put(ACTION, action);
    }

    public String getApplication() {
        return get(APPLICATION);
    }

    public void setApplication(String application) {
        put(APPLICATION, application);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object clone() {
        MetricContext clone = new MetricContext();
        clone.putAll(Map.copyOf((Map<String, String>) super.clone()));
        return clone;
    }

    @Override
    public String remove(Object key) {
        if (DEFAULTS.containsKey(key)) {
            throw new UnsupportedOperationException("Can't remove key " + key);
        }
        return super.remove(key);
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (DEFAULTS.containsKey(key)) {
            throw new UnsupportedOperationException("Can't remove key " + key);
        }
        return super.remove(key, value);
    }
}
