package com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.ecs;

import com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.AbstractComputeTier;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = EcsComputeTier.Builder.class)
public class EcsComputeTier extends AbstractComputeTier {

    private EcsComputeTier(Builder b) {
        super(b);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        // Same reference?
        if (this == obj) {
            return true;
        }
        // Same type?
        if (getClass() != obj.getClass()) {
            return false;
        }
        final EcsComputeTier other = (EcsComputeTier) obj;
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder extends AbstractComputeTier.Builder {

        private Builder() {
            
        }

        @Override
        public EcsComputeTier build() {
            return new EcsComputeTier(this);
        }
    }
}
