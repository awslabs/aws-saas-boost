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
            .name(VALID_NAME).id(VALID_ID).description(VALID_DESC).build();
    private static final Tier TIER_NO_ID = Tier.builder()
            .name(VALID_NAME).description(VALID_DESC).build();

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
                capturedRequest.item().containsKey(TierAttribute.id.name()));
        if (!expectedAttributes.containsKey(TierAttribute.id.name())) {
            // we aren't expecting any ID in particular, so just set the expected ID to be the actual
            expectedAttributes = new HashMap<>(expectedAttributes);
            expectedAttributes.put(TierAttribute.id.name(), capturedRequest.item().get(TierAttribute.id.name()));
        }
        assertEquals("Put item attributes should match expected.",
                expectedAttributes,
                capturedRequest.item());
    }

    @Test
    public void withoutId() {
        dynamoTierDataStore.createTier(TIER_NO_ID);
        verifyPutItemRequest(Map.of(
                TierAttribute.description.name(), AttributeValue.builder().s(VALID_DESC).build(),
                TierAttribute.name.name(), AttributeValue.builder().s(VALID_NAME).build()
        ));
    }

    @Test
    public void withId() {
        dynamoTierDataStore.createTier(TIER_WITH_ID);
        verifyPutItemRequest(Map.of(
                TierAttribute.description.name(), AttributeValue.builder().s(VALID_DESC).build(),
                TierAttribute.name.name(), AttributeValue.builder().s(VALID_NAME).build(),
                TierAttribute.id.name(), AttributeValue.builder().s(VALID_ID).build()
        ));
    }

    @Test(expected = NullPointerException.class)
    public void nullTier() {
        dynamoTierDataStore.createTier(null);
    }
}
