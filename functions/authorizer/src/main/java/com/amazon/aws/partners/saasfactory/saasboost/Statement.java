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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonDeserialize(builder = Statement.Builder.class)
public class Statement {

    @JsonIgnore
    private static final String ACTION = "execute-api:Invoke";
    @JsonIgnore
    private final String effect;
    @JsonIgnore
    private final List<String> resources;

    private Statement(Builder builder) {
        this.effect = builder.effect;
        this.resources = builder.resources;
    }

    @JsonProperty("Action")
    public String getAction() {
        return ACTION;
    }

    @JsonProperty("Effect")
    public String getEffect() {
        return effect;
    }

    @JsonProperty("Resource")
    public List<String> getResource() {
        return List.copyOf(resources);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String effect = "Deny";
        private List<String> resources = new ArrayList<>(List.of("*"));

        private Builder() {
        }

        public Builder effect(String effect) {
            this.effect = Utils.isNotBlank(effect) ? effect : "Deny";
            return this;
        }

        public Builder resource(String... resource) {
            if (resource != null && resource.length > 0) {
                resources.clear();
                Collections.addAll(this.resources, resource);
            } else {
                this.resources = new ArrayList<>(List.of("*"));
            }
            return this;
        }

        public Statement build() {
            return new Statement(this);
        }
    }
}
