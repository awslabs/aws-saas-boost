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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class RdsBootstrap implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RdsBootstrap.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    static final String SQL_STATEMENT_DELIMITER = ";\r?\n";
    static final int MAX_SQL_BATCH_SIZE = 25;
    private final S3Client s3;
    private final SsmClient ssm;

    public RdsBootstrap() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        this.ssm = Utils.sdkClient(SsmClient.builder(), SsmClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        final Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String host = (String) resourceProperties.get("Host");
        final String port = (String) resourceProperties.get("Port");
        final String database = (String) resourceProperties.get("Database");
        final String username = (String) resourceProperties.get("User");
        final String passwordParam = (String) resourceProperties.get("Password");
        final String bootstrapFileBucket = (String) resourceProperties.get("BootstrapFileBucket");
        final String bootstrapFileKey = (String) resourceProperties.get("BootstrapFileKey");
        final String driverClassName = driverClassNameFromPort(port);
        final String type = typeFromPort(port);
        final boolean createAndBootstrap = Utils.isNotBlank(bootstrapFileBucket) && Utils.isNotBlank(bootstrapFileKey);

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");

                    LOGGER.info("Getting database password secret from Parameter Store");
                    String password;
                    try {
                        password = ssm.getParameter(request -> request
                                .withDecryption(true)
                                .name(passwordParam)
                        ).parameter().value();
                    } catch (SdkServiceException ssmError) {
                        LOGGER.error("ssm:GetParameter error", ssmError);
                        throw ssmError;
                    }
                    if (password == null) {
                        throw new RuntimeException("Password is null");
                    }

                    // We need a connection that doesn't specify the database name since we may be creating it right now
                    String dbCheck = null;

                    // Unlike MySQL/MariaDB, you have to specify a database name to get a connection to postgres...
                    // And you should connect to dbo.master in SQL Server to check for a database
                    if (type.equals("postgresql")) {
                        dbCheck = "template1";
                    } else if (type.equals("sqlserver")) {
                        dbCheck = "master";
                    }

                    // Create the database if it doesn't exist - this is helpful for SQL Server because
                    // RDS/CloudFormation won't create a database when you bring up an instance.
                    LOGGER.info("Checking if database {} exists", database);
                    try (Connection dbCheckConn = DriverManager.getConnection(
                            jdbcUrl(type, driverClassName, host, port, dbCheck), username, password)) {
                        String engine = dbCheckConn.getMetaData().getDatabaseProductName().toLowerCase();
                        if (!databaseExists(dbCheckConn, engine, database)) {
                            createdb(dbCheckConn, engine, database);
                        } else {
                            LOGGER.info("Database {} exists", database);
                        }
                    } catch (SQLException e) {
                        LOGGER.error("Can't connect to database host", e);
                        throw new RuntimeException(e);
                    }

                    if (createAndBootstrap) {
                        LOGGER.info("Getting SQL file from S3 s3://{}/{}", bootstrapFileBucket, bootstrapFileKey);
                        try {
                            ResponseInputStream<GetObjectResponse> bootstrapSql = s3.getObject(request -> request
                                    .bucket(bootstrapFileBucket)
                                    .key(bootstrapFileKey)
                            );
                            // We have a database. Execute the SQL commands in the bootstrap file stored in S3.
                            LOGGER.info("Executing bootstrap SQL");
                            try (Connection conn = DriverManager.getConnection(
                                    jdbcUrl(type, driverClassName, host, port, database), username, password);
                                    Statement sql = conn.createStatement()) {
                                conn.setAutoCommit(false);
                                int batch = 0;
                                Scanner sqlScanner = new Scanner(bootstrapSql, StandardCharsets.UTF_8);
                                sqlScanner.useDelimiter(Pattern.compile(SQL_STATEMENT_DELIMITER));
                                while (sqlScanner.hasNext()) {
                                    String ddl = sqlScanner.next().trim();
                                    if (!ddl.isEmpty()) {
                                        //LOGGER.info(String.format("%02d %s", ++batch, ddl));
                                        sql.addBatch(ddl);
                                        batch++;
                                        if (batch % MAX_SQL_BATCH_SIZE == 0) {
                                            sql.executeBatch();
                                            conn.commit();
                                            sql.clearBatch();
                                        }
                                    }
                                }
                                sql.executeBatch();
                                conn.commit();

                                LOGGER.info("Finished initializing database");
                                CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                            } catch (SQLException e) {
                                LOGGER.error("Error executing bootstrap SQL", e);
                                throw new RuntimeException(e);
                            }
                        } catch (SdkServiceException s3Error) {
                            LOGGER.error("s3:GetObject error", s3Error);
                            throw s3Error;
                        }
                    } else {
                        // We were just creating a database and there isn't a SQL file to execute
                        CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                    }
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    static boolean databaseExists(Connection conn, String engine, String database) throws SQLException {
        boolean databaseExists = false;
        Statement sql = null;
        ResultSet rs = null;
        try {
            if ("postgresql".equals(engine)) {
                // Postgres doesn't support multiple databases (catalogs) per connection, so we can't use the JDBC
                // metadata to get a list of all the databases on the host like you can with MySQL/MariaDB
                sql = conn.createStatement();
                rs = sql.executeQuery("SELECT datname AS TABLE_CAT FROM pg_database WHERE datistemplate = false");
            } else {
                DatabaseMetaData dbMetaData = conn.getMetaData();
                rs = dbMetaData.getCatalogs();
            }
            if (rs != null) {
                while (rs.next()) {
                    LOGGER.info("Database exists check: TABLE_CAT = {}", rs.getString("TABLE_CAT"));
                    if (rs.getString("TABLE_CAT").equals(database)) {
                        databaseExists = true;
                        break;
                    }
                }
            } else {
                LOGGER.error("No database catalog result set!");
            }
        } catch (SQLException e) {
            LOGGER.error("Error checking if database exists", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        } finally {
            // Do our own resource cleanup instead of using try...with resources because in the PostgreSQL
            // branch the Statement object will close out our ResultSet before we can loop over it.
            closeQuietly(rs);
            closeQuietly(sql);
        }
        return databaseExists;
    }

    static void createdb(Connection conn, String engine, String database) throws SQLException {
        LOGGER.info("Creating {} database {}", engine, database);
        try (Statement create = conn.createStatement()) {
            if (engine.contains("postgresql")) {
                // Postgres has no real way of doing CREATE DATABASE IF NOT EXISTS...
                create.executeUpdate("CREATE DATABASE " + database);
            } else if (engine.contains("microsoft")) {
                create.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '" + database + "')\n"
                        + "BEGIN\n"
                        + "CREATE DATABASE " + database + "\n"
                        + "END"
                );
            } else if (engine.contains("mysql") || engine.contains("mariadb")) {
                create.executeUpdate("CREATE DATABASE IF NOT EXISTS " + database);
            }
        } catch (SQLException e) {
            LOGGER.error("Error creating database", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
    }

    static String jdbcUrl(String type, String driverClassName, String host, String port, String database) {
        StringBuilder url = new StringBuilder("jdbc:");
        url.append(type);
        if (!"oracle.jdbc.driver.OracleDriver".equals(driverClassName)) {
            url.append("://");
        } else {
            url.append(":@");
        }
        url.append(host);
        url.append(":");
        url.append(port);
        if (Utils.isNotBlank(database)) {
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

    static String typeFromPort(String port) {
        String type;
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
            default:
                type = null;
        }
        return type;
    }

    static String driverClassNameFromPort(String port) {
        String driverClassName;
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
            default:
                driverClassName = null;
        }
        return driverClassName;
    }

    static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                LOGGER.warn("Resource close failed", e);
            }
        }
    }
}
