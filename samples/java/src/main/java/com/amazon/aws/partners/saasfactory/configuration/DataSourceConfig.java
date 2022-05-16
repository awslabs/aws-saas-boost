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
package com.amazon.aws.partners.saasfactory.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.*;
import java.util.Scanner;

@Configuration
public class DataSourceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceConfig.class);
    private static final String DB_NAME = System.getenv("DB_NAME");
    private static final String DB_HOST = System.getenv("DB_HOST");
    private static final String DB_PORT = System.getenv("DB_PORT");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSORD = System.getenv("DB_PASSWORD");
    private static Boolean INITIALIZED = Boolean.FALSE;

    @Bean
    public static DataSource getDataSource() {
        if (!StringUtils.hasText(DB_NAME)) {
            throw new IllegalStateException("Missing environment variable DB_NAME");
        }
        if (!StringUtils.hasText(DB_HOST)) {
            throw new IllegalStateException("Missing environment variable DB_HOST");
        }
        if (!StringUtils.hasText(DB_PORT)) {
            throw new IllegalStateException("Missing environment variable DB_PORT");
        }
        if (!StringUtils.hasText(DB_USER)) {
            throw new IllegalStateException("Missing environment variable DB_USER");
        }
        if (!StringUtils.hasText(DB_PASSORD)) {
            throw new IllegalStateException("Missing environment variable DB_PASSWORD");
        }
        String driverClassName = driverClassNameFromPort(DB_PORT);
        String type = typeFromPort(DB_PORT);

        // We may need to bootstrap the database initially
        if (!INITIALIZED) {
            LOGGER.info("Initializing DataSource bean");
            // We need a connection that doesn't specify the database name since we may be creating it right now
            String database = null;

            // Unlike MySQL/MariaDB, you have to specify a database name to get a connection to postgres...
            // And you should connect to dbo.master in SQL Server to check for a database
            if (type.equals("postgresql")) {
                database = "template1";
            } else if (type.equals("sqlserver")) {
                database = "master";
            } else if (type.equals("oracle")) {
                database = DB_NAME;
            }
            LOGGER.info("database={}", database);

            // Create the database if it doesn't exist
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl(type, driverClassName, DB_PORT, database), DB_USER, DB_PASSORD)) {
                String engine = conn.getMetaData().getDatabaseProductName().toLowerCase();
                createdb(conn, engine);
            } catch (SQLException e) {
                LOGGER.error("Can't connect to database host to check for existing database", e);
                throw new RuntimeException(e);
            }

            // Now that we know the database exists, we have to create a new connection to that named
            // database before we issue the bootstrap SQL or we'll create all those relations in the template1
            // template database in Postgres or the tempdb in SQL Server...
            database = DB_NAME;

            // Bootstrap the database relations using "WHERE NOT EXISTS" statements
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl(type, driverClassName, DB_PORT, database), DB_USER, DB_PASSORD)) {
                String engine = conn.getMetaData().getDatabaseProductName().toLowerCase();
                bootstrap(conn, engine);
            } catch (SQLException e) {
                LOGGER.error("Can't connect to database host for initialization", e);
                throw new RuntimeException(e);
            }

            // Only run this once!
            INITIALIZED = Boolean.TRUE;
        } else {
            LOGGER.info("Skipping DataSource bean initialization");
        }

        // Now we know the database exists and the relations have been bootstrapped,
        // so we can create our DataSource bean and return connections
        HikariConfig dbConfig = new HikariConfig();
        dbConfig.setJdbcUrl(jdbcUrl(type, driverClassName, DB_PORT, DB_NAME));
        dbConfig.setPassword(DB_PASSORD);
        dbConfig.setUsername(DB_USER);
        dbConfig.setDriverClassName(driverClassName);

        DataSource dataSource = new HikariDataSource(dbConfig);
        return dataSource;
    }

    public static void createdb(Connection conn, String engine) throws SQLException {
        boolean exists = databaseExists(conn, engine, DB_NAME);
        if (!exists) {
            LOGGER.info("Creating {} database", engine);
            try (Statement create = conn.createStatement()) {
                if (engine.contains("postgresql")) {
                    // Postgres has no real way of doing CREATE DATABASE IF NOT EXISTS...
                    create.executeUpdate("CREATE DATABASE " + DB_NAME);
                } else if (engine.contains("microsoft")) {
                    create.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '" + DB_NAME + "')\n" +
                            "BEGIN\n" +
                            "CREATE DATABASE " + DB_NAME + "\n" +
                            "END"
                    );
                } else if (engine.contains("mysql") || engine.contains("mariadb")) {
                    create.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
                }
            } catch (SQLException e) {
                LOGGER.error("Error creating database", e);
                throw e;
            }
        } else {
            LOGGER.info("Database {} exists", DB_NAME);
        }
    }

    public static void bootstrap(Connection conn, String engine) throws SQLException {

        String bootstrapScriptFilename;
        if (engine.contains("mysql") || engine.contains("mariadb")) {
            LOGGER.info("Loading MySQL/MariaDB bootstrap script");
            bootstrapScriptFilename = "bootstrap-mysql.sql";
        } else if (engine.contains("postgresql")) {
            LOGGER.info("Loading PostgreSQL bootstrap script");
            bootstrapScriptFilename = "bootstrap-pg.sql";
        } else if (engine.contains("microsoft")) {
            LOGGER.info("Loading Microsoft SQL Server bootstrap script");
            bootstrapScriptFilename = "bootstrap-mssql.sql";
        } else {
            return;
        }

        conn.setAutoCommit(false);
        Statement sql = conn.createStatement();

        InputStream bootstrapScript = Thread.currentThread().getContextClassLoader().getResourceAsStream(bootstrapScriptFilename);
        Scanner scanner = new Scanner(bootstrapScript, "UTF-8");
        scanner.useDelimiter(";");
        LOGGER.info("Parsing bootstrap SQL statements");
        while (scanner.hasNext()) {
            // Simple variable replacement in the SQL
            String ddl = scanner.next().replace("{{DB_NAME}}", DB_NAME).trim();
            if (!ddl.isEmpty()) {
                LOGGER.info(ddl);
                sql.addBatch(ddl);
            }
        }
        LOGGER.info("Executing bootstrap SQL {}", bootstrapScriptFilename);
        int[] statements = sql.executeBatch();
        LOGGER.info("Executed {} statements", statements.length);
        conn.commit();
    }

    private static boolean databaseExists(Connection conn, String engine, String database) throws SQLException {
        LOGGER.info("Checking for existence of database {}", database);
        boolean databaseExists = false;
        ResultSet rs;
        if (engine.equals("postgresql")) {
            // Postgres doesn't support multiple databases (catalogs) per connection, so we can't use the JDBC
            // metadata to get a list of all the databases on the host like you can with MySQL/MariaDB
            Statement sql = conn.createStatement();
            rs = sql.executeQuery("SELECT datname AS TABLE_CAT FROM pg_database WHERE datistemplate = false");
        } else {
            DatabaseMetaData dbMetaData = conn.getMetaData();
            rs = dbMetaData.getCatalogs();
        }
        while (rs.next()) {
            String catalog = rs.getString("TABLE_CAT");
            LOGGER.info("TABLE_CAT = {}", catalog);
            if (catalog.equals(database)) {
                LOGGER.info("Found catalog {} == {}", catalog, database);
                databaseExists = true;
                break;
            }
        }
        return databaseExists;
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        boolean tableExists = false;
        DatabaseMetaData dbMetaData = conn.getMetaData();
        ResultSet rs = dbMetaData.getTables(conn.getCatalog(), null, null, new String[]{"TABLE"});
        while (rs.next()) {
            if (rs.getString("TABLE_NAME").equals(table)) {
                tableExists = true;
                break;
            }
        }
        return tableExists;
    }

    private static String jdbcUrl(String type, String driverClassName, String port, String database) {
        StringBuilder url = new StringBuilder("jdbc:");
        url.append(type);
        if (!"oracle.jdbc.driver.OracleDriver".equals(driverClassName)) {
            url.append("://");
        } else {
            url.append(":@");
        }
        url.append(System.getenv("DB_HOST"));
        url.append(":");
        url.append(port);
        LOGGER.info("Type= {}", type);
        if ("oracle.jdbc.driver.OracleDriver".equals(driverClassName)) {
            url.append(":ORCL");
        } else if (database != null && !database.isEmpty()) {
            if (!"com.microsoft.sqlserver.jdbc.SQLServerDriver".equals(driverClassName)) {
                url.append("/");
            } else {
                url.append(";databaseName=");
            }
            url.append(database);
        }
        LOGGER.info("JDBC URL {}", url.toString());
        return url.toString();
    }

    private static String typeFromPort(String port) {
        String type = null;
        switch (port) {
            case "5432":
                type = "postgresql";
                break;
            case "3306":
                type = "mariadb";
                break;
            case "1433":
                type = "sqlserver";
                break;
            case "1521":
                type = "oracle:thin"; // Probably realistic to not support the OCI driver...
                break;
        }
        return type;
    }

    private static String driverClassNameFromPort(String port) {
        String driverClassName = null;
        switch (port) {
            case "5432":
                driverClassName = "org.postgresql.Driver";
                break;
            case "3306":
                driverClassName = "org.mariadb.jdbc.Driver"; // Can use this for both MariaDB and MySQL
                break;
            case "1433":
                driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                break;
            case "1521":
                driverClassName = "oracle.jdbc.driver.OracleDriver";
                break;
        }
        return driverClassName;
    }

}
