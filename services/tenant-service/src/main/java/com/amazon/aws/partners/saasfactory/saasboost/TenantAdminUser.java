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

@JsonDeserialize(builder = TenantAdminUser.Builder.class)
public class TenantAdminUser {

    private final String username;
    private final String email;
    private final String phoneNumber;
    private final String givenName;
    private final String familyName;

    private TenantAdminUser(Builder builder) {
        this.username = builder.username;
        this.email = builder.email;
        this.phoneNumber = builder.phoneNumber;
        this.givenName = builder.givenName;
        this.familyName = builder.familyName;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TenantAdminUser other = (TenantAdminUser) obj;

        return (
                Objects.equals(username, other.username)
                && Objects.equals(email, other.email)
                && Objects.equals(phoneNumber, other.phoneNumber)
                && Objects.equals(givenName, other.givenName)
                && Objects.equals(familyName, other.familyName));
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, email, phoneNumber, givenName, familyName);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String username;
        private String email;
        private String phoneNumber;
        private String givenName;
        private String familyName;

        private Builder() {
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder givenName(String givenName) {
            this.givenName = givenName;
            return this;
        }

        public Builder familyName(String familyName) {
            this.familyName = familyName;
            return this;
        }

        public TenantAdminUser build() {
            if (username == null || username.isBlank()) {
                throw new IllegalStateException("Can't build TenantAdminUser without username");
            }
            return new TenantAdminUser(this);
        }
    }
}
