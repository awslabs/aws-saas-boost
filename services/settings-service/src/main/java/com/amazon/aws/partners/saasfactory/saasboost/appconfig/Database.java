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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonDeserialize(builder = Database.Builder.class)
public class Database {

    private final RdsEngine engine;
    private final String version;
    private final String family;
    private final String database;
    private final String username;
    private final String password;
    private String bootstrapFilename;
    private String passwordParam;
    private final Map<String, DatabaseTierConfig> tiers;

    private Database(Builder builder) {
        this.engine = builder.engine;
        this.version = builder.version;
        this.family = builder.family;
        this.database = builder.database;
        this.username = builder.username;
        this.password = builder.password;
        this.bootstrapFilename = builder.bootstrapFilename;
        this.passwordParam = builder.passwordParam;
        this.tiers = builder.tiers;
    }

    public String getEngine() {
        return engine != null ? engine.name() : null;
    }

    public String getEngineName() {
        return engine != null ? engine.getEngine() : null;
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

    public String getBootstrapFilename() {
        return bootstrapFilename;
    }

    public String getPasswordParam() {
        return passwordParam;
    }

    public void setPasswordParam(String passwordParam) {
        this.passwordParam = passwordParam;
    }

    public void setBootstrapFilename(String bootstrapFilename) {
        this.bootstrapFilename = bootstrapFilename;
    }

    public Map<String, DatabaseTierConfig> getTiers() {
        return tiers;
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
        final Database other = (Database) obj;

        boolean tiersEqual = tiers != null && other.tiers != null;
        if (tiersEqual) {
            tiersEqual = tiers.size() == other.tiers.size();
            if (tiersEqual) {
                for (Map.Entry<String, DatabaseTierConfig> tier : tiers.entrySet()) {
                    tiersEqual = tier.getValue().equals(other.tiers.get(tier.getKey()));
                    if (!tiersEqual) {
                        break;
                    }
                }
            }
        }

        return (
                ((version == null && other.version == null) || (version != null && version.equals(other.version)))
                && ((family == null && other.family == null) || (family != null && family.equals(other.family)))
                && ((database == null && other.database == null)
                    || (database != null && database.equalsIgnoreCase(other.database)))
                && ((username == null && other.username == null)
                    || (username != null && username.equals(other.username)))
                && ((password == null && other.password == null)
                    || (password != null && password.equals(other.password)))
                && ((bootstrapFilename == null && other.bootstrapFilename == null)
                    || (bootstrapFilename != null && bootstrapFilename.equals(other.bootstrapFilename)))
                && (engine == other.engine)
                && ((tiers == null && other.tiers == null) || tiersEqual));
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, family, database, username, password, bootstrapFilename, engine, tiers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Database other) {
        return new Builder()
            .engine(other.getEngine())
            .version(other.getVersion())
            .family(other.getFamily())
            .database(other.getDatabase())
            .username(other.getUsername())
            .password(other.getPassword())
            .passwordParam(other.getPasswordParam())
            .bootstrapFilename(other.getBootstrapFilename())
            .tiers(other.getTiers());
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    @JsonIgnoreProperties(value = {"engineName", "port"})
    public static final class Builder {

        private RdsEngine engine;
        private String version;
        private String family;
        private String database;
        private String username;
        private String password;
        private String passwordParam;
        private String bootstrapFilename;
        private Map<String, DatabaseTierConfig> tiers = new HashMap<>();

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

        public Builder passwordParam(String passwordParam) {
            this.passwordParam = passwordParam;
            return this;
        }

        public Builder bootstrapFilename(String bootstrapFilename) {
            this.bootstrapFilename = bootstrapFilename;
            return this;
        }

        public Builder tiers(Map<String, DatabaseTierConfig> tiers) {
            this.tiers = tiers != null ? tiers : new HashMap<>();
            return this;
        }

        public Database build() {
            return new Database(this);
        }
    }

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
}
