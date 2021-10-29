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

// import com.amazon.redshift.jdbc42.Driver;  //keep this just make sure jar is present
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;

public class RedshiftTable implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(RedshiftTable.class);
    private SsmClient ssm;
    public RedshiftTable()  {
            LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
            this.ssm = Utils.sdkClient(SsmClient.builder(), SsmClient.SERVICE_NAME);
            //LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String username = (String) resourceProperties.get("Username");
        final String passwordParam = (String) resourceProperties.get("Password");
        final String tableName = (String) resourceProperties.get("TableName");
        final String jdbcUrl = (String) resourceProperties.get("DatabaseUrl");

        ExecutorService service = Executors.newSingleThreadExecutor();
        ObjectNode responseData = JsonNodeFactory.instance.objectNode();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");
                    Connection connection = null;
                    Statement statement = null;

                    LOGGER.info("Getting database password secret from Parameter Store");
                    String password = null;
                    try {
                        password = ssm.getParameter(request -> request.withDecryption(true).name(passwordParam)).parameter().value();
                    } catch (SdkServiceException ssmError) {
                        LOGGER.error("ssm:GetParameter error", ssmError.getMessage());
                        throw ssmError;
                    }
                    //LOGGER.info("Password is '" + password + "'");
                    if (password == null) {
                        throw new RuntimeException("Password is null");
                    }

                    try {
                        LOGGER.info("Using JDBC Url: {}", jdbcUrl);
                        Class.forName("com.amazon.redshift.jdbc42.Driver");
                        Properties properties = new Properties();
                        properties.setProperty("user", username);
                        properties.setProperty("password", password);
                        int attempts = 1;
                        do {
                            try {
                                connection = DriverManager.getConnection(jdbcUrl, properties);
                                LOGGER.info("Connected to Redshift database");
                            } catch (Exception e) {
                                LOGGER.error("Error connecting {}", e.getMessage());
                                LOGGER.error(Utils.getFullStackTrace(e));
                                attempts++;
                                try {
                                    LOGGER.info("Sleep one minute for dns to resolve");
                                    Thread.sleep(60 * 1000);
                                } catch (InterruptedException te) {
                                    //
                                }
                            }
                        } while (null == connection && attempts < 5);

                        if (null == connection) {
                            throw new RuntimeException("Unable to connect to Redshift database after 5 attempts.");
                        }

                        //Execute a query
                        LOGGER.info("Creating table in given database {}", jdbcUrl);
                        statement = connection.createStatement();

                        String sql = "CREATE TABLE IF NOT EXISTS public." +
                                tableName +
                                "(\n" +
                                "\"type\" VARCHAR(256) ENCODE lzo\n" +
                                ",workload VARCHAR(256) ENCODE lzo\n" +
                                ",context VARCHAR(256) ENCODE lzo\n" +
                                ",tenant_id VARCHAR(256) ENCODE lzo\n" +
                                ",tenant_name VARCHAR(256) ENCODE lzo\n" +
                                ",tenant_tier VARCHAR(256) ENCODE lzo\n" +
                                ",timerecorded TIMESTAMP WITH TIME ZONE ENCODE az64\n" +
                                ",metric_name VARCHAR(256) ENCODE lzo\n" +
                                ",metric_unit VARCHAR(256) ENCODE lzo\n" +
                                ",metric_value NUMERIC(18,0) ENCODE az64\n" +
                                ",meta_data VARCHAR(256) ENCODE lzo\n" +
                                ")\n" +
                                "DISTSTYLE AUTO";
                        statement.executeUpdate(sql);
                        // Further code to follow
                    } catch(ClassNotFoundException cnfe) {
                        String stackTrace = Utils.getFullStackTrace(cnfe);
                        LOGGER.error(stackTrace);
                        responseData.put("Reason", stackTrace);
                        sendResponse(event, context, "FAILED", responseData);
                    } catch (SQLException sqle) {
                        String stackTrace = Utils.getFullStackTrace(sqle);
                        LOGGER.error(stackTrace);
                        responseData.put("Reason", stackTrace);
                        sendResponse(event, context, "FAILED", responseData);
                    }  finally{
                    //finally block used to close resources
                        try{
                            if(statement != null) {
                                connection.close();
                            }
                        } catch(SQLException se){
                        }// do nothing
                        try {
                            if (connection != null) {
                                connection.close();
                            }
                        } catch(SQLException se){
                            se.printStackTrace();
                        } //end finally try
                    } //end try

                    // Tell CloudFormation we're done
                    sendResponse(event, context, "SUCCESS", responseData);
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
            String stackTrace = Utils.getFullStackTrace(e);
            LOGGER.error(stackTrace);
            responseData.put("Reason", stackTrace);
            sendResponse(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
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
                LOGGER.error(Utils.getFullStackTrace(ioe));
            }

            LOGGER.info("Response Code: " + connection.getResponseCode());
            connection.disconnect();
        } catch (IOException e) {
            LOGGER.error("Failed to open connection to CFN response URL");
            LOGGER.error(Utils.getFullStackTrace(e));
        }

        return null;
    }

}