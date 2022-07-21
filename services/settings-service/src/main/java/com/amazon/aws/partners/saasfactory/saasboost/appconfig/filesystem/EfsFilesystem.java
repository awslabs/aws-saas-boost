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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = EfsFilesystem.Builder.class)
public class EfsFilesystem extends AbstractFilesystem {

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private EfsLifecycle lifecycle;

    private EfsFilesystem(Builder b) {
        super(b);
        this.lifecycle = b.lifecycle;
    }

    public static EfsFilesystem.Builder builder() {
        return new EfsFilesystem.Builder();
    }

    public String getLifecycle() {
        return lifecycle.name();
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
        return (Utils.nullableEquals(this.lifecycle, other.lifecycle));
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lifecycle);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder extends AbstractFilesystem.Builder{
        private EfsLifecycle lifecycle = EfsLifecycle.NEVER;

        private Builder() {
        }

        public Builder lifecycle(String lifecycle) {
            try {
                this.lifecycle = EfsLifecycle.valueOf(lifecycle);
            } catch (IllegalArgumentException e) {
                try {
                    Integer days = Integer.parseInt(lifecycle);
                    this.lifecycle = EfsLifecycle.ofDays(days);
                    if (this.lifecycle == null) {
                        this.lifecycle = EfsLifecycle.NEVER;
                    }
                } catch (NumberFormatException nfe) {
                    this.lifecycle = EfsLifecycle.NEVER;
                }
            }
            return this;
        }

        public EfsFilesystem build() {
            return new EfsFilesystem(this);
        }
    }

    enum EfsLifecycle {
        NEVER(0),
        AFTER_7_DAYS(7),
        AFTER_14_DAYS(14),
        AFTER_30_DAYS(30),
        AFTER_60_DAYS(60),
        AFTER_90_DAYS(90);

        private final int lifecycleDays;

        EfsLifecycle(Integer lifecycleDays) {
            this.lifecycleDays = lifecycleDays;
        }

        public int getLifecycleDays() {
            return lifecycleDays;
        }

        public static EfsLifecycle ofDays(Integer days) {
            EfsLifecycle lifecycle = null;
            for (EfsLifecycle l : EfsLifecycle.values()) {
                if (l.getLifecycleDays() == days) {
                    lifecycle = l;
                    break;
                }
            }
            return lifecycle;
        }
    }
}
