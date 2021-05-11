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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class RdsBootstrap implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(RdsBootstrap.class);
    private final static Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));
    private S3Client s3;
    private SsmClient ssm;

    public RdsBootstrap() throws URISyntaxException {
        try {
            LOGGER.info("Version Info: " + getVersionInfo());;
        } catch (Exception e) {
            LOGGER.error("Error getting version number", e);
            LOGGER.error(getFullStackTrace(e));
        }
        this.s3 = S3Client.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(AWS_REGION)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(new URI("https://s3." + AWS_REGION.id() + ".amazonaws.com"))
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();
        this.ssm = SsmClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(AWS_REGION)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(new URI("https://ssm." + AWS_REGION.id() + ".amazonaws.com"))
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        try {
            LOGGER.info(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(event));
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not log input");
        }

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
        final boolean createAndBootstrap = (bootstrapFileBucket != null && !bootstrapFileBucket.isBlank()
                && bootstrapFileKey != null && !bootstrapFileKey.isBlank());

        ExecutorService service = Executors.newSingleThreadExecutor();
        ObjectNode responseData = JsonNodeFactory.instance.objectNode();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");

                    LOGGER.info("Getting database password secret from Parameter Store");
                    String password = null;
                    try {
                        password = ssm.getParameter(request -> request.withDecryption(true).name(passwordParam)).parameter().value();
                    } catch (SdkServiceException ssmError) {
                        LOGGER.error("ssm:GetParameter error", ssmError.getMessage());
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

                    // Create the database if it doesn't exist - this is helpful
                    // for SQL Server because RDS/CloudFormation won't create a
                    // database when you bring up an instance.
                    LOGGER.info("Checking if database {} exists", database);
                    try (Connection dbCheckConn = DriverManager.getConnection(jdbcUrl(type, driverClassName, host, port, dbCheck), username, password)) {
                        String engine = dbCheckConn.getMetaData().getDatabaseProductName().toLowerCase();
                        if (!databaseExists(dbCheckConn, engine, database)) {
                            createdb(dbCheckConn, engine, database);
                        } else {
                            LOGGER.info("Database {} exists", database);
                        }
                    } catch (SQLException e) {
                        LOGGER.error("Can't connect to database host", e.getMessage());
                        throw new RuntimeException(e);
                    }

                    if (createAndBootstrap) {
                        LOGGER.info("Getting SQL file from S3 s3://{}/{}", bootstrapFileBucket, bootstrapFileKey);
                        InputStream bootstrapSQL = null;
                        try {
                            ResponseBytes<GetObjectResponse> responseBytes = s3.getObjectAsBytes(request -> request
                                    .bucket(bootstrapFileBucket)
                                    .key(bootstrapFileKey)
                            );
                            bootstrapSQL = responseBytes.asInputStream();
                        } catch (SdkServiceException s3Error) {
                            LOGGER.error("s3:GetObject error", s3Error.getMessage());
                            throw s3Error;
                        }
                        // We have a database. Execute the SQL commands in the bootstrap file stored in S3.
                        LOGGER.info("Executing bootstrap SQL");
                        try (Connection conn = DriverManager.getConnection(jdbcUrl(type, driverClassName, host, port, database), username, password);
                             Statement sql = conn.createStatement()) {
                            conn.setAutoCommit(false);
                            Scanner sqlScanner = new Scanner(bootstrapSQL, "UTF-8");
                            sqlScanner.useDelimiter(";");
                            int batch = 0;
                            while (sqlScanner.hasNext()) {
                                String ddl = sqlScanner.next().trim();
                                if (!ddl.isEmpty()) {
                                    //LOGGER.info(String.format("%02d %s", ++batch, ddl));
                                    sql.addBatch(ddl);
                                }
                            }
                            sql.executeBatch();
                            conn.commit();

                            LOGGER.info("Finished initializing database");
                            sendResponse(event, context, "SUCCESS", responseData);
                        } catch (SQLException e) {
                            LOGGER.error("Error executing bootstrap SQL", e.getMessage());
                            throw new RuntimeException(e);
                        }
                    } else {
                        // We were just creating a database and there isn't a SQL file to execute
                        sendResponse(event, context, "SUCCESS", responseData);
                    }
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
                    sendResponse(event, context, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    sendResponse(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    sendResponse(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            LOGGER.error(getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            sendResponse(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    public static String getVersionInfo() throws IOException {
        String result = "";
        InputStream inputStream = null;
        try {
            Properties prop = new Properties();
            String propFileName = "git.properties";

            inputStream = RdsBootstrap.class.getClassLoader().getResourceAsStream(propFileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            // get the property value and print it out
            String tag = prop.getProperty("git.commit.id.describe");
            String commitTime = prop.getProperty("git.commit.time");
            result = tag + ", Commit time: " + commitTime;
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    //LOGGER.error("getVersionInfo: Error closing inputStream");
                }
            }
        }
        return result;
    }

    /**
     * Send a response to CloudFormation regarding progress in creating resource.
     *
     * @param event
     * @param context
     * @param responseStatus
     * @param responseData
     * @return
     */
    public final Object sendResponse(final Map<String, Object> event, final Context context, final String responseStatus, ObjectNode responseData) {
        String responseUrl = (String) event.get("ResponseURL");
        LOGGER.info("ResponseURL: " + responseUrl + "\n");

        URL url;
        try {
            url = new URL(responseUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "");
            connection.setRequestMethod("PUT");

            ObjectNode responseBody = JsonNodeFactory.instance.objectNode();
            responseBody.put("Status", responseStatus);
            responseBody.put("RequestId", (String) event.get("RequestId"));
            responseBody.put("LogicalResourceId", (String) event.get("LogicalResourceId"));
            responseBody.put("StackId", (String) event.get("StackId"));
            responseBody.put("PhysicalResourceId", (String) event.get("LogicalResourceId"));
            if (!"FAILED".equals(responseStatus)) {
                responseBody.set("Data", responseData);
            } else {
                responseBody.put("Reason", responseData.get("Reason").asText());
            }
            LOGGER.info("Response Body: " + responseBody.toString());

            try (OutputStreamWriter response = new OutputStreamWriter(connection.getOutputStream())) {
                response.write(responseBody.toString());
            } catch (IOException ioe) {
                LOGGER.error("Failed to call back to CFN response URL");
                LOGGER.error(getFullStackTrace(ioe));
            }

            LOGGER.info("Response Code: " + connection.getResponseCode());
            connection.disconnect();
        } catch (IOException e) {
            LOGGER.error("Failed to open connection to CFN response URL");
            LOGGER.error(getFullStackTrace(e));
        }

        return null;
    }

    private static boolean databaseExists(Connection conn, String engine, String database) throws SQLException {
        boolean databaseExists = false;
        ResultSet rs = null;
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
            LOGGER.info("Database exists check: TABLE_CAT = {}", rs.getString("TABLE_CAT"));
            if (rs.getString("TABLE_CAT").equals(database)) {
                databaseExists = true;
                break;
            }
        }
        return databaseExists;
    }

    public static void createdb(Connection conn, String engine, String database) throws SQLException {
        LOGGER.info("Creating {} database", engine);
        try (Statement create = conn.createStatement()) {
            if (engine.indexOf("postgresql") != -1) {
                // Postgres has no real way of doing CREATE DATABASE IF NOT EXISTS...
                create.executeUpdate("CREATE DATABASE " + database);
            } else if (engine.indexOf("microsoft") != -1) {
                create.executeUpdate("IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '" + database + "')\n" +
                        "BEGIN\n" +
                        "CREATE DATABASE " + database + "\n" +
                        "END"
                );
            } else if (engine.indexOf("mysql") != -1 || engine.indexOf("mariadb") != -1) {
                create.executeUpdate("CREATE DATABASE IF NOT EXISTS " + database);
            }
        } catch (SQLException e) {
            LOGGER.error("Error creating database", e);
            throw e;
        }
    }

    private static String jdbcUrl(String type, String driverClassName, String host, String port, String database) {
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
        if (database != null && !database.isEmpty()) {
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

    private static String getFullStackTrace(Exception e) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        e.printStackTrace(pw);
        return sw.getBuffer().toString();
    }
}
