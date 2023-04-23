package com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.ecs;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.EcsLaunchType;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.compute.AbstractCompute;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = EcsCompute.Builder.class)
public class EcsCompute extends AbstractCompute {

    private final Map<String, EcsComputeTier> tiers;
    private final EcsLaunchType ecsLaunchType;
    private final Boolean ecsExecEnabled;

    protected EcsCompute(Builder b) {
        super(b);
        this.tiers = b.tiers;
        this.ecsLaunchType = b.ecsLaunchType;
        this.ecsExecEnabled = b.ecsExecEnabled;
    }

    public Map<String, EcsComputeTier> getTiers() {
        return tiers;
    }

    public EcsLaunchType getEcsLaunchType() {
        return ecsLaunchType;
    }

    public Boolean getEcsExecEnabled() {
        return ecsExecEnabled;
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

        EcsCompute other = (EcsCompute) obj;

        boolean tiersEqual = true;
        Map<String, EcsComputeTier> otherTiers = other.getTiers();
        for (Map.Entry<String, EcsComputeTier> tierEntry : this.getTiers().entrySet()) {
            tiersEqual = tiersEqual && tierEntry.getValue().equals(otherTiers.get(tierEntry.getKey()));
        }

        return (super.equals(other) && tiersEqual
                && Utils.nullableEquals(ecsLaunchType, other.ecsLaunchType))
                && Utils.nullableEquals(ecsExecEnabled, other.getEcsExecEnabled());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), ecsLaunchType, ecsExecEnabled)
                + Arrays.hashCode(tiers != null ? tiers.keySet().toArray(new String[0]) : null)
                + Arrays.hashCode(tiers != null ? tiers.values().toArray(new Object[0]) : null);
    }

    public Builder builder() {
        return ((Builder) super.fillBuilder(new Builder()))
                .ecsLaunchType(ecsLaunchType)
                .ecsExecEnabled(ecsExecEnabled)
                .tiers(tiers);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder extends AbstractCompute.Builder {

        private Map<String, EcsComputeTier> tiers = new HashMap<>();
        private EcsLaunchType ecsLaunchType;
        private Boolean ecsExecEnabled = false;

        private Builder() {
            
        }

        public Builder tiers(Map<String, EcsComputeTier> tiers) {
            this.tiers = tiers;
            return this;
        }

        public Builder ecsLaunchType(String ecsLaunchType) {
            if (ecsLaunchType != null) {
                try {
                    this.ecsLaunchType = EcsLaunchType.valueOf(ecsLaunchType);
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException(
                        new IllegalArgumentException("Can't find EcsLaunchType for value " + ecsLaunchType)
                    );
                }
            }
            return this;
        }

        public Builder ecsLaunchType(EcsLaunchType ecsLaunchType) {
            this.ecsLaunchType = ecsLaunchType;
            return this;
        }

        public Builder ecsExecEnabled(Boolean ecsExecEnabled) {
            this.ecsExecEnabled = ecsExecEnabled;
            return this;
        }

        @Override
        public EcsCompute build() {
            return new EcsCompute(this);
        }
    }    
}
