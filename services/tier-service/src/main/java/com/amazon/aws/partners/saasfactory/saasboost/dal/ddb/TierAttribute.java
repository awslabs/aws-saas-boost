package com.amazon.aws.partners.saasfactory.saasboost.dal.ddb;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.model.Tier;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.function.*;

public enum TierAttribute {
    id(tier -> AttributeValue.builder().s(tier.getId()).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.id(attributeValue.s())),
    name(tier -> AttributeValue.builder().s(tier.getName()).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.name(attributeValue.s())),
    description(tier -> AttributeValue.builder().s(tier.getDescription()).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.description(attributeValue.s()));

    // used to convert the Attribute from a Tier to a DDB AttributeValue
    private final Function<Tier, AttributeValue> fromTierFunction;
    // used to determine if the provided AttributeValue is valid for this Attribute
    private final Predicate<AttributeValue> validAttributeValueFunction;
    // takes an existing Tier.Builder and adds this Attribute to it
    private final BiConsumer<Tier.Builder, AttributeValue> addToTierBuilderFunction;

    TierAttribute(
            Function<Tier, AttributeValue> fromTierFunction,
            Predicate<AttributeValue> validAttributeValueFunction,
            BiConsumer<Tier.Builder, AttributeValue> addToTierBuilderFunction) {
        this.fromTierFunction = fromTierFunction;
        this.validAttributeValueFunction = validAttributeValueFunction;
        this.addToTierBuilderFunction = addToTierBuilderFunction;
    }

    public AttributeValue fromTier(Tier tier) {
        return fromTierFunction.apply(tier);
    }

    public void toTier(Tier.Builder tierBuilderInProgress, AttributeValue attributeValue) {
        if (!validAttributeValueFunction.test(attributeValue)) {
            throw new IllegalArgumentException(attributeValue.toString() + " is not applicable to " + this);
        }
        addToTierBuilderFunction.accept(tierBuilderInProgress, attributeValue);
    }
}
