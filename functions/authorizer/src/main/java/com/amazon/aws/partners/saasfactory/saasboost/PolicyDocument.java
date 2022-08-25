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

@JsonDeserialize(builder = PolicyDocument.Builder.class)
public class PolicyDocument {

    @JsonIgnore
    private static final String VERSION = "2012-10-17";
    @JsonIgnore
    private final List<Statement> statements;

    private PolicyDocument(Builder builder) {
        this.statements = builder.statements;
    }

    @JsonProperty("Version")
    public String getVersion() {
        return VERSION;
    }

    @JsonProperty("Statement")
    public List<Statement> getStatement() {
        return List.copyOf(statements);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private List<Statement> statements = new ArrayList<>(List.of(Statement.builder().build()));

        private Builder() {
        }

        public Builder statement(Statement... statement) {
            if (statement != null && statement.length > 0) {
                statements.clear();
                Collections.addAll(this.statements, statement);
            } else {
                this.statements = new ArrayList<>();
            }
            return this;
        }

        public PolicyDocument build() {
            return new PolicyDocument(this);
        }
    }
}
