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

package com.amazon.aws.partners.saasfactory.saasboost.model;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.LocalDateTime;
import java.util.Objects;

@JsonDeserialize(builder = Tier.Builder.class)
public final class Tier {
    private final String id;
    private final LocalDateTime created;
    private final LocalDateTime modified;
    private final String name;
    private final String description;
    private final Boolean defaultTier;

    private Tier(Tier.Builder builder) {
        this.id = builder.id;
        this.created = builder.created;
        this.modified = builder.modified;
        this.name = builder.name;
        this.description = builder.description;
        this.defaultTier = builder.defaultTier;
    }

    public String getId() {
        return this.id;
    }

    public LocalDateTime getCreated() {
        return this.created;
    }

    public LocalDateTime getModified() {
        return this.modified;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public Boolean defaultTier() {
        return this.defaultTier;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Tier)) {
            return false;
        }
        Tier otherTier = (Tier)other;
        return this.getId().equals(otherTier.getId())
                && this.getCreated().equals(otherTier.getCreated())
                && this.getModified().equals(otherTier.getModified())
                && this.getName().equals(otherTier.getName())
                && this.getDescription().equals(otherTier.getDescription())
                && this.defaultTier().equals(otherTier.defaultTier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, created, modified, name, description, defaultTier);
    }

    public static Tier.Builder builder(Tier tier) {
        return builder()
                .id(tier.getId())
                .created(tier.getCreated())
                .modified(tier.getModified())
                .description(tier.getDescription())
                .name(tier.getName())
                .defaultTier(tier.defaultTier());
    }

    public static Tier.Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private String id;
        private LocalDateTime created;
        private LocalDateTime modified;
        private String name;
        private String description;
        private Boolean defaultTier = false;

        private Builder() {

        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder created(LocalDateTime created) {
            this.created = created;
            return this;
        }

        public Builder modified(LocalDateTime modified) {
            this.modified = modified;
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

        public Builder defaultTier(Boolean defaultTier) {
            this.defaultTier = defaultTier;
            return this;
        }

        public Tier build() {
            if (Utils.isEmpty(name)) {
                throw new IllegalArgumentException("Tier must include a non-null, non-empty name.");
            }
            return new Tier(this);
        }
    }
}
