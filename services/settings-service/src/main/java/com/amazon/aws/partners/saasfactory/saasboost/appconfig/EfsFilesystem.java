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

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = EfsFilesystem.Builder.class)
public class EfsFilesystem {

    enum FILE_SYSTEM_LIFECYCLE {
        NEVER(0),
        AFTER_7_DAYS(7),
        AFTER_14_DAYS(14),
        AFTER_30_DAYS(30),
        AFTER_60_DAYS(60),
        AFTER_90_DAYS(90);

        private final int lifecycleDays;

        FILE_SYSTEM_LIFECYCLE(Integer lifecycleDays) {
            this.lifecycleDays = lifecycleDays;
        }

        public int getLifecycleDays() {
            return lifecycleDays;
        }

        public static FILE_SYSTEM_LIFECYCLE ofDays(Integer days) {
            FILE_SYSTEM_LIFECYCLE lifecycle = null;
            for (FILE_SYSTEM_LIFECYCLE l : FILE_SYSTEM_LIFECYCLE.values()) {
                if (l.getLifecycleDays() == days) {
                    lifecycle = l;
                    break;
                }
            }
            return lifecycle;
        }
    }

    private final Boolean encryptAtRest;
    private final FILE_SYSTEM_LIFECYCLE lifecycle;

    private EfsFilesystem(Builder builder) {
        this.encryptAtRest = builder.encryptAtRest;
        this.lifecycle = builder.lifecycle;
    }

    public static EfsFilesystem.Builder builder() {
        return new EfsFilesystem.Builder();
    }

    public Boolean getEncryptAtRest() {
        return encryptAtRest;
    }

    public String getFilesystemLifecycle() {
        return lifecycle.name();
    }

    public int getLifecycle() {
        return lifecycle != null ? lifecycle.getLifecycleDays() : 0;
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
        final EfsFilesystem other = (EfsFilesystem) obj;
        return (
                ((encryptAtRest == null && other.encryptAtRest == null) || (encryptAtRest != null && encryptAtRest.equals(other.encryptAtRest)))
                && ((lifecycle == null && other.lifecycle == null) || (lifecycle != null && lifecycle == other.lifecycle))
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(encryptAtRest, lifecycle);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    @JsonIgnoreProperties(value = {"filesystemLifecycle"})
    public static final class Builder {

        private Boolean encryptAtRest = Boolean.FALSE;
        private FILE_SYSTEM_LIFECYCLE lifecycle = FILE_SYSTEM_LIFECYCLE.NEVER;

        private Builder() {
        }

        public Builder encryptAtRest(Boolean encryptAtRest) {
            this.encryptAtRest = encryptAtRest;
            return this;
        }

        public Builder encryptAtRest(String encryptAtRest) {
            this.encryptAtRest = Boolean.valueOf(encryptAtRest);
            return this;
        }

        public Builder lifecycle(String lifecycle) {
            try {
                this.lifecycle = FILE_SYSTEM_LIFECYCLE.valueOf(lifecycle);
            } catch (IllegalArgumentException e) {
                this.lifecycle = FILE_SYSTEM_LIFECYCLE.NEVER;
            }
            return this;
        }

        public Builder lifecycle(int lifecycleDays) {
            this.lifecycle = FILE_SYSTEM_LIFECYCLE.ofDays(lifecycleDays);
            if (this.lifecycle == null) {
                this.lifecycle = FILE_SYSTEM_LIFECYCLE.NEVER;
            }
            return this;
        }

        public EfsFilesystem build() {
            return new EfsFilesystem(this);
        }
    }


}
