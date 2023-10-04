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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.efs.EfsFilesystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx.FsxOntapFilesystem;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.fsx.FsxWindowsFilesystem;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Map;
import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @Type(value = EfsFilesystem.class, name = AbstractFilesystem.EFS),
        @Type(value = FsxWindowsFilesystem.class, name = AbstractFilesystem.FSXW),
        @Type(value = FsxOntapFilesystem.class, name = AbstractFilesystem.FSXO)
})
public abstract class AbstractFilesystem {
    protected static final String EFS = "EFS";
    protected static final String FSXW = "FSX_WINDOWS";
    protected static final String FSXO = "FSX_ONTAP";

    private String mountPoint;

    protected AbstractFilesystem(Builder b) {
        if (b.mountPoint == null) {
            throw new IllegalArgumentException("Cannot specify a filesystem without a mount point.");
        }
        this.mountPoint = b.mountPoint;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public abstract Map<String, ? extends AbstractFilesystemTierConfig> getTiers();

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
        final AbstractFilesystem other = (AbstractFilesystem) obj;

        return Utils.nullableEquals(this.getMountPoint(), other.getMountPoint());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mountPoint);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public abstract static class Builder {
        private String mountPoint;

        public Builder mountPoint(String mountPoint) {
            this.mountPoint = mountPoint;
            return this;
        }

        public abstract AbstractFilesystem build();
    }
}