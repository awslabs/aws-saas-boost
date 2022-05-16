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

package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;
import java.util.regex.Pattern;

@JsonDeserialize(builder = Setting.Builder.class)
public class Setting {

    private static final Pattern PARAMETER_STORE_REGEX = Pattern.compile("[a-zA-Z0-9_/\\.-]+");
    private final String name;
    private final String value;
    private final boolean readOnly;
    private final boolean secure;
    private final Long version;
    private final String description;

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

    public static boolean isValidSettingName(String name) {
        boolean valid = false;
        if (name != null) {
            valid = PARAMETER_STORE_REGEX.matcher(name).matches();
        }
        return valid;
    }

    @Override
    public String toString() {
        return Utils.toJson(this);
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
        final Setting other = (Setting) obj;
        return (
                ((name == null && other.name == null) || (name != null && name.equals(other.name))) // Parameter Store is case sensitive
                && ((value == null && other.value == null) || (value != null && value.equals(other.value)))
                && ((description == null && other.description == null) || (description != null && description.equalsIgnoreCase(other.description)))
                && ((version == null && other.version == null) || (version != null && version.equals(other.version)))
                && (readOnly == other.readOnly)
                && (secure == other.secure)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, (description != null ? description.toUpperCase() : null), version, readOnly, secure);
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
            if (!isValidSettingName(name)) {
                throw new IllegalArgumentException("Only a mix of letters, numbers and the following 4 symbols .-_/ are allowed.");
            }
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
