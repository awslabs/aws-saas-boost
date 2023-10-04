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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.efs;

import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.AbstractFilesystem;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonDeserialize(builder = EfsFilesystem.Builder.class)
public class EfsFilesystem extends AbstractFilesystem {

    private Map<String, EfsFilesystemTierConfig> tiers;

    private EfsFilesystem(Builder b) {
        super(b);
        tiers = b.tiers;
    }

    public Map<String, EfsFilesystemTierConfig> getTiers() {
        return this.tiers;
    }

    public static EfsFilesystem.Builder builder() {
        return new EfsFilesystem.Builder();
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
        return true;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder extends AbstractFilesystem.Builder {
        private Map<String, EfsFilesystemTierConfig> tiers = new HashMap<>();

        private Builder() {
        }

        public Builder tiers(Map<String, EfsFilesystemTierConfig> tiers) {
            this.tiers = tiers;
            return this;
        }

        public EfsFilesystem build() {
            return new EfsFilesystem(this);
        }
    }
}
