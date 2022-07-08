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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = FsxWindowsFilesystem.Builder.class)
public class FsxWindowsFilesystem extends AbstractFsxFilesystem {

    private FsxWindowsFilesystem(Builder b) {
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
        final FsxWindowsFilesystem other = (FsxWindowsFilesystem) obj;
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder extends AbstractFsxFilesystem.Builder {

        private Builder() {
        }

        public FsxWindowsFilesystem build() {
            return new FsxWindowsFilesystem(this);
        }
    }
}
