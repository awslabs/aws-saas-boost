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

 package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = S3Storage.Builder.class)
public final class S3Storage {

    private final String bucketName;

    public S3Storage(Builder b) {
        this.bucketName = b.bucketName;
    }

    public String getBucketName() {
        return this.bucketName;
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

        S3Storage other = (S3Storage) obj;

        return Utils.nullableEquals(this.getBucketName(), other.getBucketName());
    }

    @Override
    public int hashCode() {
        return 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(S3Storage other) {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String bucketName;

        public Builder bucketName(String bucketName) {
            this.bucketName = bucketName;
            return this;
        }

        public S3Storage build() {
            return new S3Storage(this);
        }
    }
}
