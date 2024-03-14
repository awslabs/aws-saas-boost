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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@JsonDeserialize(builder = User.Builder.class)
public class User {

    private final String id;
    private final String username;
    private final String email;
    private final String phoneNumber;
    private final String password;
    private final String givenName;
    private final String familyName;
    private final List<Group> groups;
    private final List<Role> roles;
    private final String tenantId;
    private final String tier;

    private User(Builder builder) {
        this.id = builder.id;
        this.username = builder.username;
        this.email = builder.email;
        this.phoneNumber = builder.phoneNumber;
        this.password = builder.password;
        this.givenName = builder.givenName;
        this.familyName = builder.familyName;
        this.groups = builder.groups;
        this.roles = builder.roles;
        this.tenantId = builder.tenantId;
        this.tier = builder.tier;
    }

    public String getId() {
        return id;
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

    public String getPassword() {
        return password;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public List<Group> getGroups() {
        return List.copyOf(groups);
    }

    public List<Role> getRoles() {
        return List.copyOf(roles);
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTier() {
        return tier;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String id;
        private String username;
        private String email;
        private String phoneNumber;
        private String password;
        private String givenName;
        private String familyName;
        private List<Group> groups = new ArrayList<>();
        private List<Role> roles = new ArrayList<>();
        private String tenantId;
        private String tier;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
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

        public Builder password(String password) {
            this.password = password;
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

        public Builder groups(Collection<Group> groups) {
            if (groups != null) {
                this.groups.addAll(groups);
            }
            return this;
        }

        public Builder roles(Collection<Role> roles) {
            if (roles != null) {
                this.roles.addAll(roles);
            }
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder tier(String tier) {
            this.tier = tier;
            return this;
        }

        public User build() {
            if (username == null || username.isBlank()) {
                throw new IllegalStateException("Can't build User without username");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalStateException("Can't build User without tenant id");
            }
            return new User(this);
        }
    }
}
