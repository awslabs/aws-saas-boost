package com.amazon.aws.partners.saasfactory.saasboost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;

public class CapacityProviderLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityProviderLock.class);

    private final DynamoDbClient ddb;
    private AttributeValue onboardingId = null;

    public CapacityProviderLock(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    /**
     * Locks the distributed lock for reading/writing CapacityProviders.
     * 
     * This function blocks indefinitely until the operation is successful, relying on outside
     * timeouts to prevent us from actually blocking forever.
     */
    public void lock(RequestContext requestContext) {
        boolean locked = false;
        while (!locked) {
            locked = tryLockUnlock(requestContext, true);
            if (!locked) {
                // self-throttle so we don't blow up DDB trying to attain the lock
                try {
                    Thread.sleep(5 * 1000); // 5 seconds
                } catch (InterruptedException ie) {
                    // do nothing, keep trying
                }
            }
        }
    }

    /**
     * Unlocks the distributed lock for reading/writing CapacityProviders.
     * 
     * This function blocks indefinitely until the operation is successful, relying on outside
     * timeouts to prevent us from actually blocking forever. We don't allow unlocking an unlocked
     * lock, since it is an invalid operation: we should only be unlocking after our own lock.
     */
    public void unlock(RequestContext requestContext) {
        boolean success = false;
        while (!success) {
            success = tryLockUnlock(requestContext, false);
            if (!success) {
                // self-throttle so we don't blow up DDB trying to relinquish the lock
                try {
                    Thread.sleep(5 * 1000); // 5 seconds
                } catch (InterruptedException ie) {
                    // do nothing, keep trying
                }
            }
        }
    }

    // VisibleForTesting
    protected AttributeValue currentOnboardingId(RequestContext requestContext) {
        if (onboardingId == null) {
            try {
                ScanRequest scanRequest = ScanRequest.builder()
                        .tableName(requestContext.onboardingDdbTable)
                        .filterExpression("tenant_id = :tenantid")
                        .expressionAttributeValues((Map<String, AttributeValue>) Map.of(
                                ":tenantid", AttributeValue.builder().s(requestContext.tenantId).build()))
                        .build();
                LOGGER.debug("sending scan with ScanRequest {}", scanRequest);
                ScanResponse scanResponse = ddb.scan(ScanRequest.builder()
                        .tableName(requestContext.onboardingDdbTable)
                        .filterExpression("tenant_id = :tenantid")
                        .expressionAttributeValues((Map<String, AttributeValue>) Map.of(
                                ":tenantid", AttributeValue.builder().s(requestContext.tenantId).build()))
                        .build());
                this.onboardingId = scanResponse.items().get(0).get("id");
            } catch (DynamoDbException ddbe) {
                LOGGER.error("Error trying to scan for current onboarding id: {}", ddbe.getMessage());
                LOGGER.error(Utils.getFullStackTrace(ddbe));
                throw new RuntimeException(ddbe);
            }
        }
        return this.onboardingId;
    }

    // VisibleForTesting
    protected boolean tryLockUnlock(RequestContext requestContext, boolean tryLock) {
        try {
            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                    .tableName(requestContext.onboardingDdbTable)
                    .key(Map.of("id", currentOnboardingId(requestContext)))
                    .conditionExpression("ecs_cluster_locked = :lock_expected")
                    .updateExpression("SET ecs_cluster_locked = :new_lock")
                    .expressionAttributeValues(Map.of(
                            ":lock_expected", AttributeValue.builder().bool(!tryLock).build(),
                            ":new_lock", AttributeValue.builder().bool(tryLock).build()))
                    .build();
            LOGGER.debug("trying to {} with updateItemRequest {}", tryLock ? "lock" : "unlock", updateItemRequest);
            ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(requestContext.onboardingDdbTable)
                    .key(Map.of("id", currentOnboardingId(requestContext)))
                    .conditionExpression("ecs_cluster_locked = :lock_expected")
                    .updateExpression("SET ecs_cluster_locked = :new_lock")
                    .expressionAttributeValues(Map.of(
                            ":lock_expected", AttributeValue.builder().bool(!tryLock).build(),
                            ":new_lock", AttributeValue.builder().bool(tryLock).build()))
                    .build());
        } catch (ConditionalCheckFailedException ccfe) {
            LOGGER.error("Could not {} ecs_cluster_locked, conditional check failed: {}",
                    tryLock ? "lock" : "unlock", ccfe.getMessage());
            return false;
        } catch (DynamoDbException ddbe) {
            LOGGER.error("Error trying to update lock for current onboarding id: {}", ddbe.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ddbe));
            throw new RuntimeException(ddbe);
        }
        return true;
    }
}
