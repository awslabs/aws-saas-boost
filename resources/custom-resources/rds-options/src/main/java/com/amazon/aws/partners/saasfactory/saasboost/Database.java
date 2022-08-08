/**
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Database.Builder.class)
public class Database {

    enum RdsEngine {
        AURORA_PG("aurora-postgresql", "Amazon Aurora PostgreSQL", 5432),
        AURORA_MYSQL("aurora-mysql", "Amazon Aurora MySQL", 3306),
        MYSQL("mysql", "MySQL", 3306),
        MARIADB("mariadb", "MariaDB", 3306),
        POSTGRES("postgres", "PostgreSQL", 5432),
        MS_SQL_EXPRESS("sqlserver-ex", "SQL Server Express Edition", 1433),
        MS_SQL_WEB("sqlserver-web", "SQL Server Web Edition", 1433),
        MS_SQL_STANDARD("sqlserver-se", "SQL Server Standard Edition", 1433),
        MS_SQL_ENTERPRISE("sqlserver-ee", "SQL Server Enterprise Edition", 1433),
        ORACLE("oracle-ee", "Oracle", 1521);

        private final String engine;
        private final String description;
        private final Integer port;

        RdsEngine(String name, String description, Integer port) {
            this.engine = name;
            this.description = description;
            this.port = port;
        }

        public String getEngine() {
            return engine;
        }

        public String getDescription() {
            return description;
        }

        public Integer getPort() {
            return port;
        }

        public static RdsEngine ofEngine(String engine) {
            RdsEngine rdsEngine = null;
            for (RdsEngine e : RdsEngine.values()) {
                if (e.getEngine().equals(engine)) {
                    rdsEngine = e;
                    break;
                }
            }
            return rdsEngine;
        }
    }

    enum RdsInstance {
        T3_MICRO("db.t3.micro", "2 vCPUs 1 GiB RAM"),
        T3_SMALL("db.t3.small", "2 vCPUs 2 GiB RAM"),
        T3_MEDIUM("db.t3.medium", "2 vCPUs 4 GiB RAM"),
        T3_LARGE("db.t3.large", "2 vCPUs 8 GiB RAM"),
        T3_XL("db.t3.xlarge", "4 vCPUs 16 GiB RAM"),
        T3_2XL("db.t3.2xlarge", "8 vCPUs 32 GiB RAM"),
        M5_LARGE("db.m5.large", "2 vCPUs 8 GiB RAM"),
        M5_XL("db.m5.xlarge", "4 vCPUs 16 GiB RAM"),
        M5_2XL("db.m5.2xlarge", "8 vCPUs 32 GiB RAM"),
        M5_4XL("db.m5.4xlarge", "16 vCPUs 64 GiB RAM"),
        M5_12XL("db.m5.12xlarge", "48 vCPUs 192 GiB RAM"),
        M5_24XL("db.m5.24xlarge", "96 vCPUs 384 GiB RAM"),
        R5_LARGE("db.r5.large", "2 vCPUs 16 GiB RAM"),
        R5_XL("db.r5.xlarge", "4 vCPUs 32 GiB RAM"),
        R5_2XL("db.r5.2xlarge", "8 vCPUs 64 GiB RAM"),
        R5_4XL("db.r5.4xlarge", "16 vCPUs 128 GiB RAM"),
        R5_12XL("db.r5.12xlarge", "48 vCPUs 384 GiB RAM"),
        R5_24XL("db.r5.24xlarge", "96 vCPUs 768 GiB RAM");

        private final String instanceClass;
        private final String description;

        RdsInstance(String name, String description) {
            this.instanceClass = name;
            this.description = description;
        }

        public String getInstanceClass() {
            return instanceClass;
        }

        public String getDescription() {
            return description;
        }

        public static RdsInstance ofInstanceClass(String instanceClass) {
            RdsInstance instance = null;
            for (RdsInstance ec2 : RdsInstance.values()) {
                if (ec2.getInstanceClass().equals(instanceClass)) {
                    instance = ec2;
                    break;
                }
            }
            return instance;
        }
    }

    private RdsEngine engine;
    private RdsInstance instance;
    private String version;
    private String family;
    private String database;
    private String username;
    private String password;

    private Database(Builder builder) {
        this.engine = builder.engine;
        this.instance = builder.instance;
        this.version = builder.version;
        this.family = builder.family;
        this.database = builder.database;
        this.username = builder.username;
        this.password = builder.password;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEngine() {
        return engine != null ? engine.name() : null;
    }

    public String getEngineName() {
        return engine != null ? engine.getEngine() : null;
    }

    public String getInstance() {
        return instance != null ? instance.name() : null;
    }

    public String getInstanceClass() {
        return instance != null ? instance.getInstanceClass() : null;
    }

    public String getVersion() {
        return version;
    }

    public String getFamily() {
        return family;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Integer getPort() {
        return engine != null ? engine.getPort() : null;
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    @JsonIgnoreProperties(value = {"engineName", "instanceClass", "port"})
    public static final class Builder {

        private RdsEngine engine;
        private RdsInstance instance;
        private String version;
        private String family;
        private String database;
        private String username;
        private String password;

        private Builder() {
        }

        public Builder engine(String engine) {
            try {
                this.engine = RdsEngine.valueOf(engine);
            } catch (IllegalArgumentException e) {
                this.engine = RdsEngine.ofEngine(engine);
            }
            return this;
        }

        public Builder instance(String instance) {
            try {
                this.instance = RdsInstance.valueOf(instance);
            } catch (IllegalArgumentException e) {
                this.instance = RdsInstance.ofInstanceClass(instance);
            }
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder family(String family) {
            this.family = family;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Database build() {
            return new Database(this);
        }
    }
}
