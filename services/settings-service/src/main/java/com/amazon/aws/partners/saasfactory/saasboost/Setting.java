/**
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
package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Setting.Builder.class)
public class Setting {

    private final String name;
    private final String value;
    private boolean readOnly;
    private boolean secure;
    private Long version;
    private String description;

    private Setting(Builder builder) {
        this.name = builder.name;
        this.value = builder.value;
        this.readOnly = builder.readOnly;
        this.secure = builder.secure;
        this.version = builder.version;
        this.description = builder.description;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isSecure() {
        return secure;
    }

    public Long getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private String name;
        private String value;
        private boolean readOnly = true;
        private boolean secure = false;
        private Long version;
        private String description;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Setting build() {
            return new Setting(this);
        }
    }
}
