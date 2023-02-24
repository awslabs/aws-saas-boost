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
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.filesystem.AbstractFilesystem;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

public abstract class AbstractFsxFilesystem extends AbstractFilesystem {

    private String windowsMountDrive;

    protected AbstractFsxFilesystem(Builder b) {
        super(b);
        this.windowsMountDrive = b.windowsMountDrive;
    }

    public String getWindowsMountDrive() {
        return this.windowsMountDrive;
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
        final AbstractFsxFilesystem other = (AbstractFsxFilesystem) obj;
        return Utils.nullableEquals(this.getWindowsMountDrive(), other.getWindowsMountDrive())
                && super.equals(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getWindowsMountDrive());
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    protected abstract static class Builder extends AbstractFilesystem.Builder {
        private String windowsMountDrive;

        public Builder windowsMountDrive(String windowsMountDrive) {
            this.windowsMountDrive = windowsMountDrive;
            return this;
        }
    }
}
