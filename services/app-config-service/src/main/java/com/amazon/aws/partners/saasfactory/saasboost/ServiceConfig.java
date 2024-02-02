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

import com.amazon.aws.partners.saasfactory.saasboost.compute.AbstractCompute;
import com.amazon.aws.partners.saasfactory.saasboost.filesystem.AbstractFilesystem;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = ServiceConfig.Builder.class)
public class ServiceConfig {

    @JsonProperty("public")
    private final Boolean publiclyAddressable;
    private final String name;
    private final String description;
    private final String path;
    private final Database database;
    private final ObjectStorage objectStorage;
    private final AbstractFilesystem filesystem;
    private final AbstractCompute compute;

    private ServiceConfig(Builder builder) {
        this.publiclyAddressable = builder.publiclyAddressable;
        this.name = builder.name;
        this.description = builder.description;
        this.path = builder.path;
        this.database = builder.database;
        this.objectStorage = builder.objectStorage;
        this.filesystem = builder.filesystem;
        this.compute = builder.compute;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ServiceConfig other) {
        return new Builder()
                .publiclyAddressable(other.isPublic())
                .name(other.getName())
                .description(other.getDescription())
                .path(other.getPath())
                .database(other.getDatabase())
                .objectStorage(other.objectStorage)
                .filesystem(other.getFilesystem())
                .compute(other.getCompute());
    }

    public Boolean isPublic() {
        return publiclyAddressable;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPath() {
        return path;
    }

    public Database getDatabase() {
        return database;
    }

    public boolean hasDatabase() {
        return database != null;
    }

    public ObjectStorage getObjectStorage() {
        return objectStorage;
    }

    public AbstractFilesystem getFilesystem() {
        return filesystem;
    }
    
    public AbstractCompute getCompute() {
        return compute;
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

        final ServiceConfig other = (ServiceConfig) obj;

        return Utils.nullableEquals(name, other.name)
            && Utils.nullableEquals(description, other.description)
            && Utils.nullableEquals(path, other.path)
            && Utils.nullableEquals(publiclyAddressable, other.publiclyAddressable)
            && Utils.nullableEquals(database, other.database)
            && Utils.nullableEquals(objectStorage, other.objectStorage)
            && Utils.nullableEquals(filesystem, other.filesystem)
            && Utils.nullableEquals(compute, other.compute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, path, publiclyAddressable, database, objectStorage, filesystem, compute);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        @JsonProperty("public")
        private Boolean publiclyAddressable;
        private String name;
        private String description;
        private String path;
        private Database database;
        private ObjectStorage objectStorage;
        private AbstractFilesystem filesystem;
        private AbstractCompute compute;

        private Builder() {
        }

        public Builder publiclyAddressable(Boolean publiclyAddressable) {
            this.publiclyAddressable = publiclyAddressable;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder database(Database database) {
            this.database = database;
            return this;
        }

        public Builder objectStorage(ObjectStorage s3) {
            this.objectStorage = s3;
            return this;
        }

        public Builder filesystem(AbstractFilesystem filesystem) {
            this.filesystem = filesystem;
            return this;
        }

        public Builder compute(AbstractCompute compute) {
            this.compute = compute;
            return this;
        }

        public ServiceConfig build() {
            return new ServiceConfig(this);
        }
    }
}
