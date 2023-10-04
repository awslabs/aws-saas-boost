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
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.*;

public class MetricService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String API_APP_CLIENT = System.getenv("API_APP_CLIENT");
    private static final String PATH_REQUEST_COUNT_1_HOUR_FILE = "datasets/pathRequestCount01Hour.js";
    private static final String PATH_REQUEST_COUNT_24_HOUR_FILE = "datasets/pathRequestCount24Hour.js";
    private static final String PATH_REQUEST_COUNT_7_DAY_FILE = "datasets/pathRequestCount07Day.js";
    private static final String PATH_RESPONSE_TIME_1_HOUR_FILE = "datasets/pathResponseTime01Hour.js";
    private static final String PATH_RESPONSE_TIME_24_HOUR_FILE = "datasets/pathResponseTime24Hour.js";
    private static final String PATH_RESPONSE_TIME_7_DAY_FILE = "datasets/pathResponseTime07Day.js";
    private static final String PATH_REQUEST_COUNT = "PATH_REQUEST_COUNT";
    private static final String PATH_RESPONSE_TIME = "PATH_RESPONSE_TIME";
    private final MetricServiceDAL dal;
    private ApiGatewayHelper api;
    Map<String, Map<String, Object>> tenantCache = new HashMap<>();

    public MetricService() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new MetricServiceDAL();
    }

    protected void refreshTenantCache(Context context) {
        tenantCache = getTenants(context);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent queryMetrics(Map<String, Object> event, Context context) {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        Utils.logRequestEvent(event);

        MetricQuery query = Utils.fromJson((String) event.get("body"), MetricQuery.class);
        if (query == null) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\" : \"Invalid request body\"}");
        }

        boolean refreshTenantCache = tenantCache.isEmpty();
        for (String tenantId : query.getTenants()) {
            if (!tenantCache.containsKey(tenantId)) {
                refreshTenantCache = true;
                break;
            }
        }
        if (refreshTenantCache) {
            refreshTenantCache(context);
        }

        APIGatewayProxyResponseEvent response;
        try {
            List<QueryResult> result;
            if (query.isSingleTenant()) {
                LOGGER.info("queryMetrics: Execute Tenant metrics");
                result = dal.queryTenantMetrics(query, tenantCache);
            } else {
                LOGGER.info("queryMetrics: Execute across all tenants");
                result = dal.queryMetrics(query, tenantCache);
            }
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(200)
                    .withBody(Utils.toJson(result));
        } catch (Exception e) {
            LOGGER.error("queryMetrics: Error " + e.getMessage());
            LOGGER.error("queryMetrics: " + Utils.getFullStackTrace(e));
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(404)
                    .withBody("{\"message\" : \"" + e.getMessage() + "\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryMetrics: exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent queryAccessLogs(Map<String, Object> event, Context context) {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        Utils.logRequestEvent(event);
        if (tenantCache.isEmpty()) {
            refreshTenantCache(context);
        }

        Map<String, String> params = (Map) event.get("pathParameters");
        //get the Time Range
        String timeRangeParam = params.get("timerange");
        //get metric type of PATH_REQUEST_COUNT or PATH_RESPONSE_TIME
        String metricParam = params.get("metric");

        if (Utils.isBlank(timeRangeParam) || Utils.isBlank(metricParam)) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\" : \"Must specify timeRange and metric parameters!\"}");
        }

        try {
            TimeRange.valueOf(timeRangeParam);
        } catch (IllegalArgumentException e) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\" : \"Invalid value for timeRange!\"}");
        }

        if (!(metricParam.equals(PATH_REQUEST_COUNT) || metricParam.equals(PATH_RESPONSE_TIME))) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\" : \"Invalid value for metric. Expecting PATH_REQUEST_COUNT or PATH_RESPONSE_TIME.\"}");
        }

        APIGatewayProxyResponseEvent response;
        try {
            List<MetricValue> result = dal.queryAccessLogs(timeRangeParam, metricParam, params.get("id"), tenantCache);
            if (result != null) {
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(200)
                        .withBody(Utils.toJson(result));
            } else {
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(404);
            }
        } catch (Exception e) {
            LOGGER.error("queryAccessLogs: Error " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\" : \"" + e.getMessage() + "\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryAccessLogs: exec " + totalTimeMillis);
        return response;
    }

    // publish files to S3 web bucket with access log data for graphing to speed up UI
    // This is called from scheduled Cloudwatch event.
    public void publishRequestCountMetrics(InputStream inputStream, OutputStream outputStream, Context context) {
        dal.publishAccessLogMetrics(PATH_REQUEST_COUNT_1_HOUR_FILE, TimeRange.HOUR_1, PATH_REQUEST_COUNT, tenantCache);
        dal.publishAccessLogMetrics(PATH_REQUEST_COUNT_24_HOUR_FILE, TimeRange.HOUR_24, PATH_REQUEST_COUNT, tenantCache);
        dal.publishAccessLogMetrics(PATH_REQUEST_COUNT_7_DAY_FILE, TimeRange.DAY_7, PATH_REQUEST_COUNT, tenantCache);
    }

    public void publishResponseTimeMetrics(InputStream inputStream, OutputStream outputStream, Context context) {
        dal.publishAccessLogMetrics(PATH_RESPONSE_TIME_1_HOUR_FILE, TimeRange.HOUR_1, PATH_RESPONSE_TIME, tenantCache);
        dal.publishAccessLogMetrics(PATH_RESPONSE_TIME_24_HOUR_FILE, TimeRange.HOUR_24, PATH_RESPONSE_TIME, tenantCache);
        dal.publishAccessLogMetrics(PATH_RESPONSE_TIME_7_DAY_FILE, TimeRange.DAY_7, PATH_RESPONSE_TIME, tenantCache);
    }

    // Creates a new partition for the day
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
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        //Utils.logRequestEvent(event);
        LOGGER.info("getAccessLogSignedUrls: starting");

        Map<String, URL> signedUrls = new LinkedHashMap<>();
        signedUrls.put("PATH_REQUEST_COUNT_1_HOUR_FILE", dal.getPreSignedUrl(PATH_REQUEST_COUNT_1_HOUR_FILE));
        signedUrls.put("PATH_REQUEST_COUNT_24_HOUR_FILE", dal.getPreSignedUrl(PATH_REQUEST_COUNT_24_HOUR_FILE));
        signedUrls.put("PATH_REQUEST_COUNT_7_DAY_FILE", dal.getPreSignedUrl(PATH_REQUEST_COUNT_7_DAY_FILE));
        signedUrls.put("PATH_RESPONSE_TIME_1_HOUR_FILE", dal.getPreSignedUrl(PATH_RESPONSE_TIME_1_HOUR_FILE));
        signedUrls.put("PATH_RESPONSE_TIME_24_HOUR_FILE", dal.getPreSignedUrl(PATH_RESPONSE_TIME_24_HOUR_FILE));
        signedUrls.put("PATH_RESPONSE_TIME_7_DAY_FILE", dal.getPreSignedUrl(PATH_RESPONSE_TIME_7_DAY_FILE));

        String responseBody = Utils.toJson(List.copyOf(signedUrls.entrySet()));
        LOGGER.info("Presigned URLS = {}", responseBody);

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(200)
                .withBody(responseBody);

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("getAccessLogSignedUrls: exec " + totalTimeMillis);

        return response;
    }

    protected Map<String, Map<String, Object>> getTenants(Context context) {
        final long startMillis = System.currentTimeMillis();
        if (Utils.isBlank(API_APP_CLIENT)) {
            throw new IllegalStateException("Missing required environment variable API_APP_CLIENT");
        }
        LOGGER.info("Calling tenant service to fetch tenants");
        String getTenantResponseBody = apiGatewayHelper().authorizedRequest("GET", "tenants");
        List<Map<String, Object>> tenants = Utils.fromJson(getTenantResponseBody, ArrayList.class);
        if (tenants == null) {
            tenants = new ArrayList<>();
        }
        LOGGER.info("getTenants: Total time to get list of tenants: {}", (System.currentTimeMillis() - startMillis));
        LOGGER.info("Caching {} tenants", tenants.size());
        Map<String, Map<String, Object>> tenantMap = new HashMap<>();
        for (Map<String, Object> tenant : tenants) {
            tenantMap.put((String) tenant.get("id"), tenant);
        }
        return tenantMap;
    }

    protected ApiGatewayHelper apiGatewayHelper() {
        if (this.api == null) {
            this.api = ApiGatewayHelper.clientCredentialsHelper(API_APP_CLIENT);
        }
        return this.api;
    }
}
