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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = FsxOntapFilesystem.Builder.class)
public class FsxOntapFilesystem extends AbstractFsxFilesystem {

    private Map<String, FsxOntapFilesystemTierConfig> tiers;

    private FsxOntapFilesystem(Builder b) {
        super(b);
        tiers = b.tiers;
    }

    public Map<String, FsxOntapFilesystemTierConfig> getTiers() {
        return this.tiers;
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
        final FsxOntapFilesystem other = (FsxOntapFilesystem) obj;
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder extends AbstractFsxFilesystem.Builder {

        private Map<String, FsxOntapFilesystemTierConfig> tiers = new HashMap<>();

        private Builder() {
        }

        public Builder tiers(Map<String, FsxOntapFilesystemTierConfig> tiers) {
            this.tiers = tiers;
            return this;
        }

        public FsxOntapFilesystem build() {
            return new FsxOntapFilesystem(this);
        }
    }
}
