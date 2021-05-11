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
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MetricService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private final static Logger LOGGER = LoggerFactory.getLogger(MetricService.class);
    private final static Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<String, String>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    //for access log metrics
    private final static String PATH_REQUEST_COUNT_1_HOUR_FILE = "datasets/pathRequestCount01Hour.js";
    private final static String PATH_REQUEST_COUNT_24_HOUR_FILE = "datasets/pathRequestCount24Hour.js";
    private final static String PATH_REQUEST_COUNT_7_DAY_FILE = "datasets/pathRequestCount07Day.js";
    private final static String PATH_RESPONSE_TIME_1_HOUR_FILE = "datasets/pathResponseTime01Hour.js";
    private final static String PATH_RESPONSE_TIME_24_HOUR_FILE = "datasets/pathResponseTime24Hour.js";
    private final static String PATH_RESPONSE_TIME_7_DAY_FILE = "datasets/pathResponseTime07Day.js";
    private final static String PATH_REQUEST_COUNT = "PATH_REQUEST_COUNT";
    private final static String PATH_RESPONSE_TIME = "PATH_RESPONSE_TIME";
    private final MetricServiceDAL dal;

    public MetricService() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new MetricServiceDAL();
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent queryMetrics(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        MetricQuery query = Utils.fromJson((String) event.get("body"), MetricQuery.class);
        if (query == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\" : \"Invalid Metric Query object\"}");
            return response;
        }

        try {
            List<QueryResult> result = null;
            if (query.isSingleTenant()) {
                LOGGER.debug("queryMetrics: Execute Tenant metrics");
                result = dal.queryTenantMetrics(query);
            } else {
                LOGGER.debug("queryMetrics: Execute across all tenants");
                result = dal.queryMetrics(query);
            }
            if (result != null) {
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(result));
            } else {
                response = new APIGatewayProxyResponseEvent().withStatusCode(404);
            }
        } catch (Exception e) {
            LOGGER.error("queryMetrics: Error " + e.getMessage());
            LOGGER.error("queryMetrics: " + Utils.getFullStackTrace(e));
            response = new APIGatewayProxyResponseEvent().withStatusCode(404).withBody("{\"message\" : \"" + e.getMessage() + "\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryMetrics: exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent queryAccessLogs(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        long startTimeMillis = System.currentTimeMillis();
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        MetricQuery query = null;
        String timeRangeParam = null;
        String metricParam = null;

        //get the Time Range
        timeRangeParam = params.get("timerange");

        //get metric type of PATH_REQUEST_COUNT or PATH_RESPONSE_TIME
        metricParam = params.get("metric");

        if (Utils.isBlank(timeRangeParam) || Utils.isBlank(metricParam)) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\" : \"Must specify timeRange and metric parameters!\"}");
            return response;
        }

        try {
            final TimeRange val = TimeRange.valueOf(timeRangeParam);
        } catch (IllegalArgumentException e) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\" : \"Invalid value for timeRange!\"}");
            return response;
        }

        if (!(metricParam.equals(PATH_REQUEST_COUNT) || metricParam.equals(PATH_RESPONSE_TIME))) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\" : \"Invalid value for metric. Expecting PATH_REQUEST_COUNT or PATH_RESPONSE_TIME!\"}");
            return response;
        }

        try {
            List<MetricValue> result = dal.queryAccessLogs(timeRangeParam, metricParam, params.get("id"));
            if (result != null) {
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(result));
            } else {
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withHeaders(CORS);
            }
        } catch (Exception e) {
            LOGGER.error("queryAccessLogs: Error " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\" : \"" + e.getMessage() + "\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryAccessLogs: exec " + totalTimeMillis);
        return response;
    }

/*
    publish files to S3 web bucket with access log data for graphing to speed up UI
    This is called from scheduled Cloudwatch event.
 */
    public void publishRequestCountMetrics(InputStream inputStream, OutputStream outputStream, Context context) {
        dal.publishAccessLogMetrics(PATH_REQUEST_COUNT_1_HOUR_FILE, TimeRange.HOUR_1, PATH_REQUEST_COUNT );
        dal.publishAccessLogMetrics(PATH_REQUEST_COUNT_24_HOUR_FILE, TimeRange.HOUR_24, PATH_REQUEST_COUNT );
        dal.publishAccessLogMetrics(PATH_REQUEST_COUNT_7_DAY_FILE, TimeRange.DAY_7, PATH_REQUEST_COUNT );
    }

    public void publishResponseTimeMetrics(InputStream inputStream, OutputStream outputStream, Context context) {
        dal.publishAccessLogMetrics(PATH_RESPONSE_TIME_1_HOUR_FILE, TimeRange.HOUR_1, PATH_RESPONSE_TIME );
        dal.publishAccessLogMetrics(PATH_RESPONSE_TIME_24_HOUR_FILE, TimeRange.HOUR_24, PATH_RESPONSE_TIME );
        dal.publishAccessLogMetrics(PATH_RESPONSE_TIME_7_DAY_FILE, TimeRange.DAY_7, PATH_RESPONSE_TIME );
    }

    /*
    Creates a new partition for the day
 */
    public void addAthenaPartition(InputStream inputStream, OutputStream outputStream, Context context) {
        try {
            dal.addAthenaPartition();
        } catch (Exception e) {
            LOGGER.error("addAthenaPartition: Error with function. {}", e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            //*TODO: should we send a SNS message
        }
    }

    public APIGatewayProxyResponseEvent getAccessMetricsSignedUrls(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }
        long startTimeMillis = System.currentTimeMillis();
        //Utils.logRequestEvent(event);
        LOGGER.info("getAccessLogSignedUrls: starting");
        APIGatewayProxyResponseEvent response = null;

        Map<String, URL> signedUrls = new LinkedHashMap<>();
        signedUrls.put("PATH_REQUEST_COUNT_1_HOUR_FILE", dal.getPreSignedUrl(PATH_REQUEST_COUNT_1_HOUR_FILE));
        signedUrls.put("PATH_REQUEST_COUNT_24_HOUR_FILE", dal.getPreSignedUrl(PATH_REQUEST_COUNT_24_HOUR_FILE));
        signedUrls.put("PATH_REQUEST_COUNT_7_DAY_FILE", dal.getPreSignedUrl(PATH_REQUEST_COUNT_7_DAY_FILE));
        signedUrls.put("PATH_RESPONSE_TIME_1_HOUR_FILE", dal.getPreSignedUrl(PATH_RESPONSE_TIME_1_HOUR_FILE));
        signedUrls.put("PATH_RESPONSE_TIME_24_HOUR_FILE", dal.getPreSignedUrl(PATH_RESPONSE_TIME_24_HOUR_FILE));
        signedUrls.put("PATH_RESPONSE_TIME_7_DAY_FILE", dal.getPreSignedUrl(PATH_RESPONSE_TIME_7_DAY_FILE));

        String responseBody = Utils.toJson(List.copyOf(signedUrls.entrySet()));
        LOGGER.info("Presigned URLS = {}", responseBody);

        response = new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(responseBody);

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("getAccessLogSignedUrls: exec " + totalTimeMillis);

        return response;
    }

}
