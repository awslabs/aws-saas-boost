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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = Metric.Builder.class)
public class Metric {

    private final String name;
    private final Instant timestamp;
    private final Measure measure;
    private final MetricContext context;

    private Metric(Builder builder) {
        this.name = builder.name;
        this.timestamp = builder.timestamp;
        this.measure = builder.measure;
        this.context = builder.context;
    }

    public String getName() {
        return name;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Measure getMeasure() {
        return measure;
    }

    public MetricContext getContext() {
        return (MetricContext) context.clone();
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
        final Metric other = (Metric) obj;
        return (Objects.equals(name, other.name)
                && Objects.equals(timestamp, other.timestamp)
                && Objects.equals(measure, other.measure)
                && Objects.equals(context, other.context));
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, timestamp, measure, context);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String name;
        private Instant timestamp = Instant.now();
        private Measure measure;
        private MetricContext context = new MetricContext();

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder measure(Measure measure) {
            this.measure = measure;
            return this;
        }

        public Builder context(Map<String, String> context) {
            if (context != null) {
                this.context.putAll(context);
            }
            return this;
        }

        public Metric build() {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Can't build Metric without name");
            }
            return new Metric(this);
        }
    }
}
