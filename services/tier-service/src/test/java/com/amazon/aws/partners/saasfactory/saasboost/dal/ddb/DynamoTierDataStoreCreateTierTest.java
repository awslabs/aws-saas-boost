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

package com.amazon.aws.partners.saasfactory.saasboost.dal.ddb;

import com.amazon.aws.partners.saasfactory.saasboost.model.Tier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DynamoTierDataStoreCreateTierTest {
    private static final String TABLE_NAME = "test-table";
    private static final String VALID_ID = "abc-123-id-456";
    private static final String VALID_DESC = "gold tier description\n123";
    private static final String VALID_NAME = "gold-tier";
    private static final Tier TIER_WITH_ID = Tier.builder()
            .name(VALID_NAME).id(VALID_ID).description(VALID_DESC).defaultTier(false).build();
    private static final Tier TIER_NO_ID = Tier.builder()
            .name(VALID_NAME).description(VALID_DESC).defaultTier(false).build();

    private DynamoDbClient mockDdb;
    private DynamoTierDataStore dynamoTierDataStore;
    private ArgumentCaptor<PutItemRequest> requestArgumentCaptor;

    @Before
    public void setup() {
        mockDdb = mock(DynamoDbClient.class);
        final PutItemResponse validResponse = PutItemResponse.builder().build();
        when(mockDdb.putItem(any(PutItemRequest.class))).thenReturn(validResponse);

        requestArgumentCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        dynamoTierDataStore = new DynamoTierDataStore(mockDdb, TABLE_NAME);
    }

    public void verifyPutItemRequest(Map<String, AttributeValue> expectedAttributes) {
        // verify putItem was called at least once
        verify(mockDdb).putItem(requestArgumentCaptor.capture());
        // assert the passed getItem request matches expectations
        PutItemRequest capturedRequest = requestArgumentCaptor.getValue();
        assertEquals("Requested table name should match configuration.",
                TABLE_NAME, capturedRequest.tableName());
        assertTrue(capturedRequest.hasItem());
        assertTrue("Put item attributes must contain ID.",
                capturedRequest.item().containsKey(DynamoTierAttribute.id.name()));
        assertTrue("Put item attributes must contain Created.",
                capturedRequest.item().containsKey(DynamoTierAttribute.created.name()));
        assertTrue("Put item attributes must contain Modified.",
                capturedRequest.item().containsKey(DynamoTierAttribute.modified.name()));

        // we might need to modify expectedAttributes, and it might be unmodifiable. so make a quick copy.
        expectedAttributes = new HashMap<>(expectedAttributes);
        if (!expectedAttributes.containsKey(DynamoTierAttribute.id.name())) {
            // we aren't expecting any ID in particular, so just set the expected ID to be the actual
            expectedAttributes.put(DynamoTierAttribute.id.name(), capturedRequest.item().get(DynamoTierAttribute.id.name()));
        }
        if (!expectedAttributes.containsKey(DynamoTierAttribute.created.name())) {
            // it's unreasonable to expect an exact create time, so just set the expected to be the actual
            expectedAttributes.put(DynamoTierAttribute.created.name(), capturedRequest.item().get(DynamoTierAttribute.created.name()));
        }
        if (!expectedAttributes.containsKey(DynamoTierAttribute.modified.name())) {
            // it's unreasonable to expect an exact modified time, so just set the expected to be the actual
            expectedAttributes.put(DynamoTierAttribute.modified.name(), capturedRequest.item().get(DynamoTierAttribute.modified.name()));
        }
        assertEquals("Put item attributes should match expected.",
                expectedAttributes,
                capturedRequest.item());
    }

    @Test
    public void withoutId() {
        dynamoTierDataStore.createTier(TIER_NO_ID);
        verifyPutItemRequest(Map.of(
                DynamoTierAttribute.description.name(), AttributeValue.builder().s(VALID_DESC).build(),
                DynamoTierAttribute.name.name(), AttributeValue.builder().s(VALID_NAME).build(),
                DynamoTierAttribute.default_tier.name(), AttributeValue.builder().bool(false).build()
        ));
    }

    @Test
    public void withId() {
        dynamoTierDataStore.createTier(TIER_WITH_ID);
        verifyPutItemRequest(Map.of(
                DynamoTierAttribute.description.name(), AttributeValue.builder().s(VALID_DESC).build(),
                DynamoTierAttribute.name.name(), AttributeValue.builder().s(VALID_NAME).build(),
                DynamoTierAttribute.id.name(), AttributeValue.builder().s(VALID_ID).build(),
                DynamoTierAttribute.default_tier.name(), AttributeValue.builder().bool(false).build()
        ));
    }

    @Test(expected = NullPointerException.class)
    public void nullTier() {
        dynamoTierDataStore.createTier(null);
    }
}
