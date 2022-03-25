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

import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.AthenaException;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;
import software.amazon.awssdk.utils.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class MetricHelper {

    public static final long SLEEP_AMOUNT_IN_MS = 500;

    // Method is used to build the P90, P70, and P50 for graphing where
    // P90 means 90% of the values were below this value.
    public static Map<String, Double> getPercentiles(final List<MetricValue> metricValueList) {
        final Map<String, Double> retMap = new HashMap<String, Double>();
        final int count = metricValueList.size();
        if (count > 0) {
            retMap.put("p90", getPxx(metricValueList, .95));
            retMap.put("p70", getPxx(metricValueList, .70));
            retMap.put("p50", getPxx(metricValueList, .50));
            BigDecimal sum = new BigDecimal(BigInteger.ZERO);
            for (MetricValue mv : metricValueList) {
                sum = sum.add(new BigDecimal(mv.getValue()));
            }
            BigDecimal average = sum.divide(new BigDecimal(count), 3, RoundingMode.HALF_UP);
            retMap.put("Average", average.doubleValue());
            retMap.put("Sum", sum.setScale(3, RoundingMode.HALF_UP).doubleValue());
        } else {
            retMap.put("p90", 0d);
            retMap.put("p70", 0d);
            retMap.put("p50", 0d);
            retMap.put("Average", 0d);
            retMap.put("Sum", 0d);
        }
        return retMap;
    }

    public static Double getPxx(final List<MetricValue> metricValueList, double index) {
        int pIndex = (int) Math.round(index * metricValueList.size());
        double pX = metricValueList.get(pIndex - 1).getValue();
        //LOGGER.debug("getPXX: PX calculated for Percentile: " + index + " is " + pX + " at index: " + pIndex);
        return pX;
    }

    public static Instant[] getTimeRangeForQuery(String timeRangeName, int offSet, Instant startTime, Instant endTime) {
        final Instant curDateTime = Instant.ofEpochMilli(new Date().getTime());
        LocalDateTime localStartDateTime = LocalDateTime.ofInstant(curDateTime.now(), ZoneId.systemDefault());
        if (Utils.isNotBlank(timeRangeName)) {
            //LOGGER.debug("getStartDateTime: Using provided query TimeRangeName: " + query.getTimeRangeName());
            try {
                TimeRange timeRange = TimeRange.valueOf(timeRangeName);
                switch (timeRange) {
                    case HOUR_1:
                    case HOUR_2:
                    case HOUR_4:
                    case HOUR_8:
                    case HOUR_10:
                    case HOUR_12:
                    case HOUR_24:
                        startTime = curDateTime.minus(timeRange.getValueToSubtract(), ChronoUnit.HOURS);
                        break;
                    case THIS_WEEK:
                        //get Monday at midnight
                        localStartDateTime = localStartDateTime
                                .with(TemporalAdjusters.previous(DayOfWeek.MONDAY))
                                .withHour(0)
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0);
                        localStartDateTime = localStartDateTime
                                .minusMinutes(offSet);
                        startTime = localStartDateTime.atZone(ZoneId.systemDefault()).toInstant();
                        break;
                    case THIS_MONTH:
                        localStartDateTime = localStartDateTime
                                .withDayOfMonth(1)
                                .withHour(0)
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0);
                        localStartDateTime = localStartDateTime
                                .minusMinutes(offSet);
                        startTime = localStartDateTime.atZone(ZoneId.systemDefault()).toInstant();
                        break;
                    case DAY_7:
                    case DAY_30:
                        localStartDateTime = localStartDateTime
                                .minusDays(timeRange.getValueToSubtract())
                                .withHour(0)
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0);
                        localStartDateTime = localStartDateTime
                                .minusMinutes(offSet);
                        startTime = localStartDateTime.atZone(ZoneId.systemDefault()).toInstant();
                        break;
                    case TODAY:
                        localStartDateTime = localStartDateTime
                                .withHour(0)
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0);
                        localStartDateTime = localStartDateTime
                                .minusMinutes(offSet);
                        startTime = localStartDateTime.atZone(ZoneId.systemDefault()).toInstant();
                        break;
                }
                endTime = curDateTime;

            } catch (Exception e) {
                throw new RuntimeException("getTimes: Invalid value for timeRangeName in query");
            }
        }
        //LOGGER.debug(("getTimes: start: " + startTime + ", finish: " + finishTime));
        Instant[] retTimes = {startTime, endTime};
        return retTimes;
    }

    // Submits a sample query to Athena and returns the execution ID of the query.
    public static String submitAthenaQuery(AthenaClient athenaClient, String query, String outputBucket, String athenaDatabase) {
        try {
            // The QueryExecutionContext allows us to set the Database.
            QueryExecutionContext queryExecutionContext = QueryExecutionContext.builder()
                    .database(athenaDatabase).build();

            // The result configuration specifies where the results of the query should go in S3 and encryption options
            ResultConfiguration resultConfiguration = ResultConfiguration.builder()
                    // You can provide encryption options for the output that is written.
                    // .withEncryptionConfiguration(encryptionConfiguration)
                    .outputLocation(outputBucket).build();

            // Create the StartQueryExecutionRequest to send to Athena which will start the query.
            StartQueryExecutionRequest startQueryExecutionRequest = StartQueryExecutionRequest.builder()
                    .queryString(query)
                    .queryExecutionContext(queryExecutionContext)
                    .resultConfiguration(resultConfiguration).build();

            StartQueryExecutionResponse startQueryExecutionResponse = athenaClient.startQueryExecution(startQueryExecutionRequest);
            return startQueryExecutionResponse.queryExecutionId();
        } catch (AthenaException e) {
            //LOGGER.error(Utils.getFullStackTrace(e));
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
                Thread.sleep(SLEEP_AMOUNT_IN_MS);
            }
        }
    }

    /**
     * This code calls Athena and retrieves the results of a query.
     * The query must be in a completed state before the results can be retrieved and
     * paginated. The first row of results are the column headers.
     */
    public static List<MetricValue> processResultRows(AthenaClient athenaClient, String queryExecutionId) {
        List<MetricValue> metricValueList = null;
        try {
            // 1. Counts by PATH with status 200.
            // 2. Latency by Paths with status 200
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
                metricValueList =  processRow(results, columnInfoList);
            }

        } catch (AthenaException e) {
            Utils.getFullStackTrace(e);
            throw e;
        }
        return metricValueList;
    }

    private static List<MetricValue> processRow(List<Row> row, List<ColumnInfo> columnInfoList) {
        //Write out the data
        List<MetricValue> metricValueList = new ArrayList<>();
        boolean first = true;
        for (Row myRow : row) {
            if (first) {
                first = false;
                continue;
            }
            List<Datum> allData = myRow.data();
            //data is read by position.  Position 0 is the path and Position 1 is the value
            BigDecimal bd = new BigDecimal(allData.get(1).varCharValue());
            //bd = bd.multiply(new BigDecimal(1000));  //convert to milli seconds
            MetricValue val = new MetricValue(bd.setScale(5, RoundingMode.HALF_UP).doubleValue(),
                    allData.get(0).varCharValue());
            metricValueList.add(val);
        }
        return metricValueList;
    }
}
