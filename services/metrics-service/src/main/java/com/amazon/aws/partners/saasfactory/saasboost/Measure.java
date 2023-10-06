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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Measure.Builder.class)
public class Measure {

    enum Type {
        count,
        total,
        min,
        max
    }

    private final Type type;
    private final Number value;
    private final String unit;

    private Measure(Builder builder) {
        this.type = builder.type;
        this.value = builder.value;
        this.unit = builder.unit;
    }

    public Type getType() {
        return type;
    }

    public Number getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return type == null || value == null;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private Type type;
        private Number value;
        private String unit;

        private Builder() {
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder value(Number value) {
            this.value = value;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Measure build() {
            return new Measure(this);
        }
    }
}
