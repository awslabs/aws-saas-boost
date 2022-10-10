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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

public final class GitVersionInfoTest {

    private static final String VALID_TAG = "v2.0.0";
    private static final String VALID_COMMIT = "9ac3fbe";
    private static final String VALID_DESC = "v2.0.0-4-dirty";
    private static final String VALID_JSON = "{\"tag\":\"" + VALID_TAG
            + "\",\"commit\":\"" + VALID_COMMIT
            + "\",\"describe\":\"" + VALID_DESC + "\"}";
    private static final GitVersionInfo VALID_INFO = GitVersionInfo.builder()
            .tag(VALID_TAG).commit(VALID_COMMIT).describe(VALID_DESC).build();

    private Properties properties;

    @Before
    public void setup() {
        properties = new Properties();
        properties.setProperty(GitVersionInfo.TAG_NAME_PROPERTY, VALID_TAG);
        properties.setProperty(GitVersionInfo.COMMIT_HASH_PROPERTY, VALID_COMMIT);
        properties.setProperty(GitVersionInfo.DESCRIPTION_PROPERTY, VALID_DESC);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFromProperties_empty() {
        properties.clear();
        GitVersionInfo.fromProperties(properties);
    }

    @Test(expected = NullPointerException.class)
    public void testFromProperties_null() {
        GitVersionInfo.fromProperties(null);
    }

    @Test
    public void testFromProperties_basic() {
        GitVersionInfo info = GitVersionInfo.fromProperties(properties);
        assertNotNull(info);
        assertEquals(VALID_TAG, info.getTag());
        assertEquals(VALID_COMMIT, info.getCommit());
        assertEquals(VALID_DESC, info.getDescribe());
    }

    @Test
    public void testJsonConfig_toJson() {
        assertEquals(VALID_JSON, Utils.toJson(VALID_INFO));
    }

    @Test
    public void testJsonConfig_fromJson() {
        assertEquals(VALID_INFO, Utils.fromJson(VALID_JSON, GitVersionInfo.class));
    }
}
