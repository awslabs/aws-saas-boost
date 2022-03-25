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
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.AthenaException;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;

import java.util.List;

public class AthenaTest {

/**
 * StartQueryExample
 * -------------------------------------
 * This code shows how to submit a query to Athena for execution, wait till results
 * are available, and then process the results.
 */
    public static void main(String[] args) throws InterruptedException {

        // Build an Athena client
        AthenaClient athenaClient = AthenaClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();
      /*          .region(Region.US_WEST_2)
                .build();*/
        long start = System.currentTimeMillis();

        String queryExecutionId = submitAthenaQuery(athenaClient);

        waitForQueryToComplete(athenaClient, queryExecutionId);

        processResultRows(athenaClient, queryExecutionId);
        System.out.println(("Executed in: " + (System.currentTimeMillis() - start)));
    }

    /**
     * Submits a query to Athena and returns the execution ID of the query.
     */
    public static String submitAthenaQuery(AthenaClient athenaClient) {

        try {
            // The QueryExecutionContext allows us to set the Database.
            QueryExecutionContext queryExecutionContext = QueryExecutionContext.builder()
                    .database(ExampleConstants.ATHENA_DEFAULT_DATABASE).build();

            // The result configuration specifies where the results of the query should go in S3 and encryption options
            ResultConfiguration resultConfiguration = ResultConfiguration.builder()
                    // You can provide encryption options for the output that is written.
                    // .withEncryptionConfiguration(encryptionConfiguration)
                    .outputLocation(ExampleConstants.ATHENA_OUTPUT_BUCKET).build();

            // Create the StartQueryExecutionRequest to send to Athena which will start the query.
            StartQueryExecutionRequest startQueryExecutionRequest = StartQueryExecutionRequest.builder()
                    .queryString(ExampleConstants.QUERY1)
                    .queryExecutionContext(queryExecutionContext)
                    .resultConfiguration(resultConfiguration).build();

            StartQueryExecutionResponse startQueryExecutionResponse = athenaClient.startQueryExecution(startQueryExecutionRequest);
            return startQueryExecutionResponse.queryExecutionId();
        } catch (AthenaException e) {
            Utils.getFullStackTrace(e);
            throw e;
        }

    }

    /**
     * Wait for an Athena query to complete, fail or to be cancelled. This is done by polling Athena over an
     * interval of time. If a query fails or is cancelled, then it will throw an exception.
     */

    public static void waitForQueryToComplete(AthenaClient athenaClient, String queryExecutionId) throws InterruptedException {
        GetQueryExecutionRequest getQueryExecutionRequest = GetQueryExecutionRequest.builder()
                .queryExecutionId(queryExecutionId).build();

        GetQueryExecutionResponse getQueryExecutionResponse;
        boolean isQueryStillRunning = true;
        while (isQueryStillRunning) {
            getQueryExecutionResponse = athenaClient.getQueryExecution(getQueryExecutionRequest);
            String queryState = getQueryExecutionResponse.queryExecution().status().state().toString();
            if (queryState.equals(QueryExecutionState.FAILED.toString())) {
                throw new RuntimeException("Query Failed to run with Error Message: " + getQueryExecutionResponse
                        .queryExecution().status().stateChangeReason());
            } else if (queryState.equals(QueryExecutionState.CANCELLED.toString())) {
                throw new RuntimeException("Query was cancelled.");
            } else if (queryState.equals(QueryExecutionState.SUCCEEDED.toString())) {
                isQueryStillRunning = false;
            } else {
                // Sleep an amount of time before retrying again.
                Thread.sleep(ExampleConstants.SLEEP_AMOUNT_IN_MS);
            }
            //System.out.println("Current Status is: " + queryState);
        }
    }

    /**
     * This code calls Athena and retrieves the results of a query.
     * The query must be in a completed state before the results can be retrieved and
     * paginated. The first row of results are the column headers.
     */
    public static void processResultRows(AthenaClient athenaClient, String queryExecutionId) {

        try {

            /*
            1. Counts by PATH with status 200.  Need to bucket by 
            2. Latency by Paths
             */
            GetQueryResultsRequest getQueryResultsRequest = GetQueryResultsRequest.builder()
                    // Max Results can be set but if its not set,
                    // it will choose the maximum page size
                    // As of the writing of this code, the maximum value is 1000
                    // .withMaxResults(1000)
                    .queryExecutionId(queryExecutionId).build();

            GetQueryResultsIterable getQueryResultsResults = athenaClient.getQueryResultsPaginator(getQueryResultsRequest);

            for (GetQueryResultsResponse result : getQueryResultsResults) {
                List<ColumnInfo> columnInfoList = result.resultSet().resultSetMetadata().columnInfo();
                List<Row> results = result.resultSet().rows();
                processRow(results, columnInfoList);
            }

        } catch (AthenaException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processRow(List<Row> row, List<ColumnInfo> columnInfoList) {

        //Write out the data
        boolean first = true;
        for (Row myRow : row) {
            if (first) {
                first = false;
                continue;
            }
            List<Datum> allData = myRow.data();
            int i = 0;
            for (Datum data : allData) {
                ColumnInfo colInfo = columnInfoList.get(i);
                colInfo.type();
                System.out.println("The value of the column " + colInfo.name() + " of type: " + colInfo.type() + " is " + data.varCharValue());
                i++;
            }
        }
    }
}

