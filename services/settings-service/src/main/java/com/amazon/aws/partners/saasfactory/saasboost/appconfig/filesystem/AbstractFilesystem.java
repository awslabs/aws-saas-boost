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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

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

    private Boolean encrypt;

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private String encryptionKey;

    protected AbstractFilesystem(Builder b) {
        if (b.mountPoint == null) {
            throw new IllegalArgumentException("Cannot specify a filesystem without a mount point.");
        }
        this.mountPoint = b.mountPoint;
        this.encrypt = b.encrypt == null ? Boolean.FALSE : b.encrypt;
        this.encryptionKey = b.encryptionKey;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public Boolean getEncrypt() {
        return this.encrypt;
    }

    public String getEncryptionKey() {
        return this.encryptionKey;
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
        final AbstractFilesystem other = (AbstractFilesystem) obj;
        return Utils.nullableEquals(this.getMountPoint(), other.getMountPoint())
                && Utils.nullableEquals(this.getEncrypt(), other.getEncrypt())
                && Utils.nullableEquals(this.getEncryptionKey(), other.getEncryptionKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mountPoint, encrypt, encryptionKey);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    protected abstract static class Builder {
        private String mountPoint;
        private Boolean encrypt;
        private String encryptionKey;

        public Builder mountPoint(String mountPoint) {
            this.mountPoint = mountPoint;
            return this;
        }

        public Builder encrypt(Boolean encrypt) {
            this.encrypt = encrypt;
            return this;
        }

        public Builder encryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
            return this;
        }
    }
}