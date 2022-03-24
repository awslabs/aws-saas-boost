package com.amazon.aws.partners.saasfactory.saasboost.dal.ddb;

import com.amazon.aws.partners.saasfactory.saasboost.dal.TierDataStore;
import com.amazon.aws.partners.saasfactory.saasboost.dal.exception.TierNotFoundException;
import com.amazon.aws.partners.saasfactory.saasboost.model.Tier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.stream.Collectors;

public class DynamoTierDataStore implements TierDataStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoTierDataStore.class);
    private final String tableName;
    private final DynamoDbClient ddb;

    public DynamoTierDataStore(DynamoDbClient ddb, String tableName) {
        this.ddb = ddb;
        this.tableName = tableName;
    }

    @Override
    public Tier getTier(String id) throws TierNotFoundException {
        String tierNotFoundMessage = String.format("No Tier found with id: %s", id);
        if (id == null) {
            throw new TierNotFoundException(tierNotFoundMessage);
        }
        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(tableName)
                .key(DynamoTier.primaryKey(id))
                .consistentRead(true)
                .build();
        GetItemResponse getItemResponse = ddb.getItem(getItemRequest);
        if (getItemResponse.hasItem()) {
            return DynamoTier.fromAttributes(getItemResponse.item());
        }
        throw new TierNotFoundException(tierNotFoundMessage);
    }

    @Override
    public List<Tier> listTiers() {
        // TODO this doesn't do any ddb error checking
        return ddb.scan(request -> request
                        .tableName(tableName)).items().stream()
                .map(DynamoTier::fromAttributes)
                .collect(Collectors.toList());
    }

    @Override
    public Tier createTier(Tier tier) {
        if (tier == null) {
            throw new NullPointerException("Cannot create null Tier");
        }
        // TODO this doesn't do any ddb error checking
        final PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(DynamoTier.fromTier(tier).attributes)
                .build();
        ddb.putItem(putItemRequest);
        return tier;
    }

    @Override
    public void deleteTier(String id) {
        // TODO this doesn't do any ddb error checking
        // deleteItem has no problem with deleting non-existent items
        DeleteItemRequest deleteItemRequest = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(DynamoTier.primaryKey(id))
                .build();
        ddb.deleteItem(deleteItemRequest);
    }

    @Override
    public void updateTier(Tier tier) throws TierNotFoundException {
        // TODO this doesn't do any ddb error checking
        DynamoTier dynamoTier = DynamoTier.fromTier(tier);

        // TODO conditional on whether it exists
        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .key(dynamoTier.primaryKey())
                .tableName(tableName)
                .updateExpression(dynamoTier.updateExpression())
                .expressionAttributeValues(dynamoTier.updateAttributes())
                .expressionAttributeNames(dynamoTier.updateAttributeNames())
                .build();
        LOGGER.debug("{}", updateItemRequest);
        ddb.updateItem(updateItemRequest);
    }
}
