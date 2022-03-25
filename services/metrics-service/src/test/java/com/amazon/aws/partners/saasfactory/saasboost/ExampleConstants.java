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

public class ExampleConstants {
    public static final int CLIENT_EXECUTION_TIMEOUT = 100000;
    public static final String ATHENA_OUTPUT_BUCKET = "s3://some-test-bucket/query-results/"; //change the bucket name to match your environment
    // This example demonstrates how to query a table with a CSV  For information, see
    //https://docs.aws.amazon.com/athena/latest/ug/work-with-data.html
    public static final String ATHENA_SAMPLE_QUERY = "SELECT request_url,\n" +
            "         target_status_code,\n" +
            "         date_trunc('hour', (date_parse(time, '%Y-%m-%dT%H:%i:%s.%fZ'))) as time_hour,\n" +
            "         count(1) AS count,\n" +
            "         avg(target_processing_time) AS avg_time,\n" +
            "         max(target_processing_time) AS max_time\n" +
            "FROM alb_logs\n" +
            "where time > '2020-07-07T14'\n" +
            "      and target_status_code = '200'\n" +
            "GROUP BY  request_url,\n " +
            "          target_status_code,\n" +
            "          date_trunc('hour', (date_parse(time, '%Y-%m-%dT%H:%i:%s.%fZ')))\n" +
            ";"; //change the Query statement to match your environment

    public static final String ATHENA_PATH_COUNT_QUERY = "SELECT date_trunc('hour', (date_parse(time, '%Y-%m-%dT%H:%i:%s.%fZ'))) AS time_hour,\n" +
            "concat(url_extract_path(request_url), '+',request_verb) as url,\n" +
            "count(1) as count\n" +
            "FROM alb_logs\n" +
            "WHERE target_status_code = '200'\n" +
            "GROUP BY  concat(url_extract_path(request_url),'+',request_verb),\n" +
            "date_trunc('hour', (date_parse(time, '%Y-%m-%dT%H:%i:%s.%fZ'))) \n" +
            "order by 1;";
    public static final long SLEEP_AMOUNT_IN_MS = 500;
    public static final String ATHENA_DEFAULT_DATABASE = "saas-boost-alb-log"; //Change the database to match your database
    public static final String QUERY1 = "SELECT\n" +
            "concat(url_extract_path(request_url), '+',request_verb) as url,\n" +
            "count(1) as request_count\n" +
            "FROM alb_logs\n" +
            " where target_status_code = '200'\n" +
            "and time >= '2020-07-09T23:11:33.827Z' and time <= '2020-07-10T23:11:33.827Z' \n" +
            "GROUP BY  concat(url_extract_path(request_url),'+',request_verb)\n" +
            "order by 2 desc\n" +
            "limit 10;";

}

