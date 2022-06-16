package com.amazon.aws.partners.saasfactory.saasboost.dal.ddb;

import static org.junit.Assert.assertEquals;

import com.amazon.aws.partners.saasfactory.saasboost.model.Tier;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public class DynamoTierAttributeTest {
    // TODO migrate to ParameterizedTest in JUnit5
    private static final String VALID_ID = UUID.randomUUID().toString();
    private static final String VALID_NAME = "Ultra-Platinum";
    private static final String VALID_DESC = "The best tier, wow!";
    private static final LocalDateTime VALID_DATETIME = LocalDateTime.now();
    private static final Boolean VALID_ISDEFAULT = Boolean.TRUE;
    private static final Tier VALID_TIER = Tier.builder()
            .created(VALID_DATETIME)
            .defaultTier(VALID_ISDEFAULT)
            .description(VALID_DESC)
            .id(VALID_ID)
            .modified(VALID_DATETIME)
            .name(VALID_NAME)
            .build();
    private static final Map<String, AttributeValue> VALID_ATTRIBUTES = Map.of(
        DynamoTierAttribute.id.name(), AttributeValue.builder().s(VALID_ID).build(),
        DynamoTierAttribute.created.name(), AttributeValue.builder().s(VALID_DATETIME.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build(),
        DynamoTierAttribute.modified.name(), AttributeValue.builder().s(VALID_DATETIME.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build(),
        DynamoTierAttribute.name.name(), AttributeValue.builder().s(VALID_NAME).build(),
        DynamoTierAttribute.description.name(), AttributeValue.builder().s(VALID_DESC).build(),
        DynamoTierAttribute.default_tier.name(), AttributeValue.builder().bool(VALID_ISDEFAULT).build()
    );

    // Test fromTier for all DynamoTierAttributes

    @Test
    public void id_fromTier() {
        assertEquals(VALID_ATTRIBUTES.get(DynamoTierAttribute.id.name()), DynamoTierAttribute.id.fromTier(VALID_TIER));
    }

    @Test
    public void id_fromTier_null() {
        Tier nullIdTier = Tier.builder(VALID_TIER).id(null).build();
        AttributeValue nullIdAttributeValue = AttributeValue.builder().s(null).build();
        assertEquals(nullIdAttributeValue, DynamoTierAttribute.id.fromTier(nullIdTier));
    }

    @Test
    public void id_fromTier_empty() {
        Tier emptyIdTier = Tier.builder(VALID_TIER).id("").build();
        AttributeValue emptyIdAttributeValue = AttributeValue.builder().s("").build();
        assertEquals(emptyIdAttributeValue, DynamoTierAttribute.id.fromTier(emptyIdTier));
    }

    @Test
    public void created_fromTier() {
        assertEquals(VALID_ATTRIBUTES.get(DynamoTierAttribute.created.name()), DynamoTierAttribute.created.fromTier(VALID_TIER));
    }

    @Test(expected = NullPointerException.class)
    public void created_fromTier_null() {
        Tier nullCreatedTier = Tier.builder(VALID_TIER).created(null).build();
        AttributeValue nullCreatedAttributeValue = AttributeValue.builder().s(null).build();
        assertEquals(nullCreatedAttributeValue, DynamoTierAttribute.created.fromTier(nullCreatedTier));
    }

    @Test
    public void modified_fromTier() {
        assertEquals(VALID_ATTRIBUTES.get(DynamoTierAttribute.modified.name()), DynamoTierAttribute.modified.fromTier(VALID_TIER));
    }

    @Test(expected = NullPointerException.class)
    public void modified_fromTier_null() {
        Tier nullModifiedTier = Tier.builder(VALID_TIER).modified(null).build();
        AttributeValue nullModifiedAttributeValue = AttributeValue.builder().s(null).build();
        assertEquals(nullModifiedAttributeValue, DynamoTierAttribute.modified.fromTier(nullModifiedTier));
    }

    @Test
    public void name_fromTier() {
        assertEquals(VALID_ATTRIBUTES.get(DynamoTierAttribute.name.name()), DynamoTierAttribute.name.fromTier(VALID_TIER));
    }

    // Tier Builder disallows a null and empty names

    @Test
    public void description_fromTier() {
        assertEquals(VALID_ATTRIBUTES.get(DynamoTierAttribute.description.name()), DynamoTierAttribute.description.fromTier(VALID_TIER));
    }

    @Test
    public void description_fromTier_null() {
        Tier nullDescriptionTier = Tier.builder(VALID_TIER).description(null).build();
        AttributeValue nullDescriptionAttributeValue = AttributeValue.builder().s(null).build();
        assertEquals(nullDescriptionAttributeValue, DynamoTierAttribute.description.fromTier(nullDescriptionTier));
    }

    @Test
    public void description_fromTier_empty() {
        Tier emptyDescriptionTier = Tier.builder(VALID_TIER).description("").build();
        AttributeValue emptyDescriptionAttributeValue = AttributeValue.builder().s("").build();
        assertEquals(emptyDescriptionAttributeValue, DynamoTierAttribute.description.fromTier(emptyDescriptionTier));
    }

    @Test
    public void defaultTier_fromTier() {
        assertEquals(VALID_ATTRIBUTES.get(DynamoTierAttribute.default_tier.name()), DynamoTierAttribute.default_tier.fromTier(VALID_TIER));
    }

    @Test
    public void defaultTier_fromTier_null() {
        Tier nullDefaultTier = Tier.builder(VALID_TIER).defaultTier(null).build();
        AttributeValue nullDefaultAttributeValue = AttributeValue.builder().s(null).build();
        assertEquals(nullDefaultAttributeValue, DynamoTierAttribute.default_tier.fromTier(nullDefaultTier));
    }

    // Test toTier for all DynamoTierAttributes

    @Test
    public void id_toTier_valid() {
        assertEquals(VALID_ID.toString(), toTierTest(
                DynamoTierAttribute.id, VALID_ATTRIBUTES.get(DynamoTierAttribute.id.name()))
            .getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void id_toTier_null() {
        toTierTest(DynamoTierAttribute.id, AttributeValue.builder().s(null).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void id_toTier_empty() {
        toTierTest(DynamoTierAttribute.id, AttributeValue.builder().s("").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void id_toTier_nonexistent() {
        toTierTest(DynamoTierAttribute.id, AttributeValue.builder().build());
    }

    @Test
    public void created_toTier_valid() {
        assertEquals(VALID_DATETIME.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), toTierTest(
                DynamoTierAttribute.created, VALID_ATTRIBUTES.get(DynamoTierAttribute.created.name()))
            .getCreated().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Test(expected = IllegalArgumentException.class)
    public void created_toTier_null() {
        toTierTest(DynamoTierAttribute.created, AttributeValue.builder().s(null).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void created_toTier_empty() {
        toTierTest(DynamoTierAttribute.created, AttributeValue.builder().s("").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void created_toTier_nonexistent() {
        toTierTest(DynamoTierAttribute.created, AttributeValue.builder().build());
    }

    @Test
    public void modified_toTier_valid() {
        assertEquals(VALID_DATETIME.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), toTierTest(
                DynamoTierAttribute.modified, VALID_ATTRIBUTES.get(DynamoTierAttribute.modified.name()))
            .getModified().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Test(expected = IllegalArgumentException.class)
    public void modified_toTier_null() {
        toTierTest(DynamoTierAttribute.modified, AttributeValue.builder().s(null).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void modified_toTier_empty() {
        toTierTest(DynamoTierAttribute.modified, AttributeValue.builder().s("").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void modified_toTier_nonexistent() {
        toTierTest(DynamoTierAttribute.modified, AttributeValue.builder().build());
    }

    @Test
    public void name_toTier_valid() {
        assertEquals(VALID_NAME, toTierTest(
                DynamoTierAttribute.name, VALID_ATTRIBUTES.get(DynamoTierAttribute.name.name()))
            .getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void name_toTier_null() {
        toTierTest(DynamoTierAttribute.name, AttributeValue.builder().s(null).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void name_toTier_empty() {
        toTierTest(DynamoTierAttribute.name, AttributeValue.builder().s("").build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void name_toTier_nonexistent() {
        toTierTest(DynamoTierAttribute.name, AttributeValue.builder().build());
    }

    @Test
    public void description_toTier_valid() {
        assertEquals(VALID_DESC, toTierTest(
                DynamoTierAttribute.description, VALID_ATTRIBUTES.get(DynamoTierAttribute.description.name()))
            .getDescription());
    }

    @Test(expected = IllegalArgumentException.class)
    public void description_toTier_null() {
        toTierTest(DynamoTierAttribute.description, AttributeValue.builder().s(null).build());
    }

    public void description_toTier_empty() {
        assertEquals("", toTierTest(
                DynamoTierAttribute.description, AttributeValue.builder().s("").build())
            .getDescription());
    }

    @Test(expected = IllegalArgumentException.class)
    public void description_toTier_nonexistent() {
        toTierTest(DynamoTierAttribute.description, AttributeValue.builder().build());
    }

    @Test
    public void defaultTier_toTier_valid() {
        assertEquals(VALID_ISDEFAULT, toTierTest(
                DynamoTierAttribute.default_tier, VALID_ATTRIBUTES.get(DynamoTierAttribute.default_tier.name()))
            .defaultTier());
    }

    @Test(expected = IllegalArgumentException.class)
    public void defaultTier_toTier_null() {
        toTierTest(DynamoTierAttribute.default_tier, AttributeValue.builder().bool(null).build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void defaultTier_toTier_nonexistent() {
        toTierTest(DynamoTierAttribute.default_tier, AttributeValue.builder().build());
    }

    @Test(expected = IllegalArgumentException.class)
    public void defaultTier_toTier_invalid() {
        toTierTest(DynamoTierAttribute.default_tier, AttributeValue.builder().s("").build());
    }

    private Tier toTierTest(DynamoTierAttribute testedAttribute, AttributeValue testAttributeValue) {
        Tier.Builder testBuilder = Tier.builder(VALID_TIER);
        testedAttribute.toTier(testBuilder, testAttributeValue);
        return testBuilder.build();
    }
}
