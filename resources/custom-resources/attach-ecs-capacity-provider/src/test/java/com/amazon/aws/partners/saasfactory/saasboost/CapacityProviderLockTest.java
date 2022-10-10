package com.amazon.aws.partners.saasfactory.saasboost;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public final class CapacityProviderLockTest {
    private static final String ONBOARDING_DDB_TABLE = "onboarding";
    private static final String TENANT_ID = "123-456";
    private static final String ONBOARDING_ID = "onb-123-456";
    private static final RequestContext TEST_CONTEXT = RequestContext.builder()
            .requestType("Create")
            .ecsCluster("ecsCluster")
            .onboardingDdbTable(ONBOARDING_DDB_TABLE)
            .capacityProvider("capacityProvider")
            .tenantId(TENANT_ID)
            .build();

    private DynamoDbClient mockDdb;
    private CapacityProviderLock testLock;

    @Before
    public void setup() {
        mockDdb = mock(DynamoDbClient.class);
        testLock = new CapacityProviderLock(mockDdb);
    }

    /**
     * trylock something already locked
     * tryunlock something not locked
     * trylock happy case
     * tryunlock happy case
     * verify each test does a scan
     * verify each test does an update
     * verify a conditional update fail means false
     */

    @Test
    public void getOnboardingId_basic() {
        final ArgumentCaptor<ScanRequest> scanCaptor = ArgumentCaptor.forClass(ScanRequest.class);
        final AttributeValue onboardingId = AttributeValue.builder().s("onb-123-456").build();
        final AttributeValue tenantIdAttributeValue = AttributeValue.builder().s(TENANT_ID).build();
        doReturn(ScanResponse.builder().items(List.of(Map.of("id", onboardingId))).build())
                .when(mockDdb).scan(scanCaptor.capture());
        AttributeValue foundOnboardingId = testLock.currentOnboardingId(TEST_CONTEXT);
        assertEquals(onboardingId, foundOnboardingId);
        assertTrue("scan for onboarding ID should include the tenant id passed in request context", 
                scanCaptor.getValue().expressionAttributeValues().values().contains(tenantIdAttributeValue));
        
        doReturn(ScanResponse.builder().build()).when(mockDdb).scan(any(ScanRequest.class));
        // assert that we cache onboardingId, since it should not change for the lifetime of the lambda
        assertEquals(onboardingId, testLock.currentOnboardingId(TEST_CONTEXT));
        verify(mockDdb, times(1)).scan(any(ScanRequest.class));
    }

    @Test(expected = RuntimeException.class)
    public void getOnboardingId_scanFailure() {
        doThrow(ResourceNotFoundException.builder().build()).when(mockDdb).scan(any(ScanRequest.class));
        testLock.currentOnboardingId(TEST_CONTEXT);
    }

    @Test
    public void tryLockUnlock_basic() {
        final AttributeValue onboardingId = AttributeValue.builder().s(ONBOARDING_ID).build();
        doReturn(ScanResponse.builder().items(List.of(Map.of("id", onboardingId))).build())
                .when(mockDdb).scan(any(ScanRequest.class));
        
        final ArgumentCaptor<UpdateItemRequest> updateCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        doReturn(UpdateItemResponse.builder().build()).when(mockDdb).updateItem(updateCaptor.capture());
        
        boolean success = testLock.tryLockUnlock(TEST_CONTEXT, true);
        UpdateItemRequest actualRequest = updateCaptor.getValue();
        assertEquals(onboardingId, actualRequest.key().get("id"));
        assertEquals("ecs_cluster_locked = :lock_expected", actualRequest.conditionExpression());
        assertEquals("SET ecs_cluster_locked = :new_lock", actualRequest.updateExpression());
        assertEquals(AttributeValue.builder().bool(false).build(), actualRequest.expressionAttributeValues().get(":lock_expected"));
        assertEquals(AttributeValue.builder().bool(true).build(), actualRequest.expressionAttributeValues().get(":new_lock"));
        assertTrue(success);
        
        success = testLock.tryLockUnlock(TEST_CONTEXT, false);
        actualRequest = updateCaptor.getValue();
        assertEquals(onboardingId, actualRequest.key().get("id"));
        assertEquals("ecs_cluster_locked = :lock_expected", actualRequest.conditionExpression());
        assertEquals("SET ecs_cluster_locked = :new_lock", actualRequest.updateExpression());
        assertEquals(AttributeValue.builder().bool(true).build(), actualRequest.expressionAttributeValues().get(":lock_expected"));
        assertEquals(AttributeValue.builder().bool(false).build(), actualRequest.expressionAttributeValues().get(":new_lock"));
        assertTrue(success);
    }

    @Test
    public void tryLockUnlock_conditionNotMet() {
        final AttributeValue onboardingId = AttributeValue.builder().s(ONBOARDING_ID).build();
        doReturn(ScanResponse.builder().items(List.of(Map.of("id", onboardingId))).build())
                .when(mockDdb).scan(any(ScanRequest.class));
        
        doThrow(ConditionalCheckFailedException.builder().build()).when(mockDdb).updateItem(any(UpdateItemRequest.class));
        
        boolean success = testLock.tryLockUnlock(TEST_CONTEXT, true);
        assertFalse(success);
    }

    @Test(expected = RuntimeException.class)
    public void tryLockUnlock_unexpectedException() {
        final AttributeValue onboardingId = AttributeValue.builder().s(ONBOARDING_ID).build();
        doReturn(ScanResponse.builder().items(List.of(Map.of("id", onboardingId))).build())
                .when(mockDdb).scan(any(ScanRequest.class));
        
        doThrow(InternalServerErrorException.builder().build()).when(mockDdb).updateItem(any(UpdateItemRequest.class));
        testLock.tryLockUnlock(TEST_CONTEXT, true);
    }
}
