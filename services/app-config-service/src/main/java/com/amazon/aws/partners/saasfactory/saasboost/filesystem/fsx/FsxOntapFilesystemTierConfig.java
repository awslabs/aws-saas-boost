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

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = FsxOntapFilesystemTierConfig.Builder.class)
public final class FsxOntapFilesystemTierConfig extends AbstractFsxFilesystemTierConfig {
    private Integer volumeSize;

    private FsxOntapFilesystemTierConfig(Builder b) {
        super(b);
        this.volumeSize = b.volumeSize;
    }

    public static FsxOntapFilesystemTierConfig.Builder builder() {
        return new FsxOntapFilesystemTierConfig.Builder();
    }

    public Integer getVolumeSize() {
        return volumeSize;
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
        final FsxOntapFilesystemTierConfig other = (FsxOntapFilesystemTierConfig) obj;
        return (Utils.nullableEquals(this.volumeSize, other.volumeSize));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), volumeSize);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder extends AbstractFsxFilesystemTierConfig.Builder {
        private Integer volumeSize;

        private Builder() {
        }

        @JsonProperty(required = true)
        public Builder volumeSize(Integer volumeSize) {
            this.volumeSize = volumeSize;
            return this;
        }

        public FsxOntapFilesystemTierConfig build() {
            return new FsxOntapFilesystemTierConfig(this);
        }
    }
}
