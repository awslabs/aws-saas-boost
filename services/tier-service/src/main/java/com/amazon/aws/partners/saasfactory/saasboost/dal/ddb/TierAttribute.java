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

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.model.Tier;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.*;

public enum TierAttribute {
    id(tier -> AttributeValue.builder().s(tier.getId()).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.id(attributeValue.s())),
    created(tier -> AttributeValue.builder().s(
            tier.getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.created(
                LocalDateTime.parse(attributeValue.s(), DateTimeFormatter.ISO_DATE_TIME))),
    modified(tier -> AttributeValue.builder().s(
            tier.getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.modified(
                LocalDateTime.parse(attributeValue.s(), DateTimeFormatter.ISO_DATE_TIME))),
    name(tier -> AttributeValue.builder().s(tier.getName()).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.name(attributeValue.s())),
    description(tier -> AttributeValue.builder().s(tier.getDescription()).build(),
            attributeValue -> !Utils.isEmpty(attributeValue.s()),
            (tierBuilder, attributeValue) -> tierBuilder.description(attributeValue.s())),
    default_tier(tier -> AttributeValue.builder().bool(tier.defaultTier()).build(),
            attributeValue -> true,
            (tierBuilder, attributeValue) -> tierBuilder.defaultTier(attributeValue.bool()));

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
