package com.amazon.aws.partners.saasfactory.saasboost.model;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;
import java.util.UUID;

@JsonDeserialize(builder = Tier.Builder.class)
public final class Tier {
    private final String id;
    private final String name;
    private final String description;

    private Tier(Tier.Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Tier)) {
            return false;
        }
        Tier otherTier = (Tier)other;
        return this.getId().equals(otherTier.getId())
                && this.getName().equals(otherTier.getName())
                && this.getDescription().equals(otherTier.getDescription());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description);
    }

    public static Tier.Builder builder(Tier tier) {
        return builder()
                .id(tier.getId())
                .description(tier.getDescription())
                .name(tier.getName());
    }

    public static Tier.Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private String id;
        private String name;
        private String description;

        private Builder() {

        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Tier build() {
            if (Utils.isEmpty(name)) {
                throw new IllegalArgumentException("Tier must include a non-null, non-empty name.");
            }
            if (Utils.isEmpty(id)) {
                // if no ID was supplied, generate a new one.
                id = UUID.randomUUID().toString();
            }
            return new Tier(this);
        }
    }
}
