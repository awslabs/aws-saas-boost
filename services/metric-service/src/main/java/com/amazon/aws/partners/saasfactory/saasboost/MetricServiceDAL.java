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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.*;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.utils.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MetricServiceDAL {

    private final static Logger LOGGER = LoggerFactory.getLogger(MetricServiceDAL.class);
    private final SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm");
    private final static String AWS_REGION = System.getenv("AWS_REGION");
    private final static String ATHENA_DATABASE = System.getenv("ATHENA_DATABASE");
    private final static String S3_ATHENA_OUTPUT_PATH = System.getenv("S3_ATHENA_OUTPUT_PATH");
    private final static String S3_ATHENA_BUCKET = System.getenv("S3_ATHENA_BUCKET");
    private final static String ACCESS_LOGS_TABLE = System.getenv("ACCESS_LOGS_TABLE");
    private final static String ACCESS_LOGS_PATH = System.getenv("ACCESS_LOGS_PATH");
    private final static String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private final static String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private final static String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private final ApplicationAutoScalingClient autoScaling;
    private final CloudWatchClient cloudWatch;
    private final S3Client s3;
    private final S3Presigner s3Presigner;
    private final AthenaClient athenaClient;

    public MetricServiceDAL() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        if (Utils.isBlank(ATHENA_DATABASE)) {
            throw new IllegalStateException("Missing required environment variable ATHENA_DATABASE");
        }
        if (Utils.isBlank(S3_ATHENA_BUCKET)) {
            throw new IllegalStateException("Missing required environment variable S3_ATHENA_BUCKET");
        }
        if (Utils.isBlank(S3_ATHENA_OUTPUT_PATH)) {
            throw new IllegalStateException("Missing required environment variable S3_ATHENA_OUTPUT_PATH");
        }
        if (Utils.isBlank(ACCESS_LOGS_TABLE)) {
            throw new IllegalStateException("Missing required environment variable ACCESS_LOGS_TABLE");
        }
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        this.athenaClient = Utils.sdkClient(AthenaClient.builder(), AthenaClient.SERVICE_NAME);
        this.cloudWatch = Utils.sdkClient(CloudWatchClient.builder(), CloudWatchClient.SERVICE_NAME);
        this.autoScaling = Utils.sdkClient(ApplicationAutoScalingClient.builder(), ApplicationAutoScalingClient.SERVICE_NAME);
        try {
            this.s3Presigner = S3Presigner.builder()
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .region(Region.of(AWS_REGION))
                    .endpointOverride(new URI("https://" + s3.serviceName() + "." + Region.of(AWS_REGION) + ".amazonaws.com")) // will break in China regions
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    /*
    Used to query CW metrics across tenants and aggregate the data
     */
    public List<QueryResult> queryMetrics(final MetricQuery query) throws Exception {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("queryMetrics: start");

        List<MetricResultItem> listResult = new ArrayList<>();
        List<String> periodList = new ArrayList<>();
        List<QueryResult> queryResultList = new ArrayList<>();
        QueryResult mrs = new QueryResult();
        mrs.setId(query.getId());
        try {
            List<String> tenants;
            if (!query.getTenants().isEmpty()) {
                tenants = new ArrayList<>();
                tenants.addAll((query.getTenants()));
                LOGGER.debug(("queryMetrics: use tenants from query"));
            } else {
                tenants = getTenants();
            }
            if (tenants.size() > 500) {;
                throw new RuntimeException("queryMetrics: Cannot process more than 500 tenants");
            } else if (tenants.isEmpty()) {
                throw new RuntimeException("queryMetrics: No tenants to process");
            }

            //build query
            final List<MetricDataQuery> dq = buildCWDataQuery(query, tenants);

            //now that query is built let's execute and get resultant data
            //the data will be stored in Metric object and placed in map by MetricDimension.
            Map<MetricDimension, Metric> metricMap = loadCWMetricData(query, dq);
            LOGGER.debug("queryMetrics: metricMap item count: " + metricMap.size());

            for (final Map.Entry<MetricDimension, Metric> metricEntry : metricMap.entrySet()) {
                final Metric metric = metricEntry.getValue();
                final MetricDimension metricDimension = metricEntry.getKey();
                LOGGER.debug("queryMetrics: Dimension: " + metricDimension.toString() + " Metric count: " + metric.getMetricValues().size());
                //construct a MetricDimension without a Tenant Id as the metrics are not by tenant
                MetricDimension md = new MetricDimension(metricDimension.getNameSpace(), metricDimension.getMetricName(), null);
                MetricResultItem mr = new MetricResultItem();
                mr.setDimension(md);

                List<Double> p90List = new ArrayList<>();
                List<Double> p70List = new ArrayList<>();
                List<Double> p50List = new ArrayList<>();
                List<Double> avgList = new ArrayList<>();
                List<Double> sumList = new ArrayList<>();
                Map<String, Double> tenantSumMap = new LinkedHashMap<>();

                //now process the metric entries
                for (Map.Entry<Instant, PriorityQueue<MetricValue>> entry : metric.getTimeValMap().entrySet()) {
                    //add entry for the period key
//                    LOGGER.debug("queryMetrics: Period: " + entry.getKey());
                    final String period = formatter.format(Date.from(entry.getKey()));
                    if (!periodList.contains(period)) {
                        periodList.add(period);
                    }

                    //build array list in order of least to high
                    final List<MetricValue> metricValueList = new ArrayList<>(entry.getValue().size());
                    while (!entry.getValue().isEmpty()) {
                        //poll retrieve least item first
                        MetricValue tenantVal = entry.getValue().poll();
                        metricValueList.add(tenantVal);
                        if (query.isTopTenants()) {
                            //Note: The -1 is to get the reverse order from greatest to least when we later sort
                            if (tenantSumMap.containsKey(tenantVal.getId())) {
                                tenantSumMap.put(tenantVal.getId(), tenantSumMap.get(tenantVal.getId()) - tenantVal.getValue());
                            } else {
                                tenantSumMap.put(tenantVal.getId(), -1 * tenantVal.getValue());
                            }
                        }
                    }

                    //*TODO: Maybe get rid of this as we only care for entire period
                    //get top 10 from list starting at last item in Array index.
/*                    if (query.isTopTenants()) {
                        int leastPos = 0;
                        if (metricValueList.size() > 10) {
                            leastPos = metricValueList.size() - 10;
                        }

                        //LOGGER.debug(("size: " + metricValueList.size() + ", offset: " + leastPos));
                        List<MetricValue> topTenantListForPeriod = new ArrayList<>();
                        for (int i = metricValueList.size() - 1; i >= leastPos; i--) {
                            topTenantListForPeriod.add(metricValueList.get(i));
                        }
                        mr.addTopTenant(topTenantListForPeriod);
                    }*/

                    if (query.isStatsMap()) {
                        final Map<String, Double> percentilesMap = MetricHelper.getPercentiles(metricValueList);
                        p90List.add(percentilesMap.get("p90"));
                        p70List.add(percentilesMap.get("p70"));
                        p50List.add(percentilesMap.get("p50"));
                        avgList.add(percentilesMap.get("Average"));
                        sumList.add(percentilesMap.get("Sum"));
                    }

                }
                //let's store the lists
                if (query.isStatsMap()) {
                    mr.putStat("P90", p90List);
                    mr.putStat("P70", p70List);
                    mr.putStat("P50", p50List);
                    mr.putStat("Average", avgList);
                    mr.putStat("Sum", sumList);
                }

                //now compute the top 10 tenants
                if (query.isTopTenants()) {
                    Map<String, Double> sortedTenantValMap = tenantSumMap.entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByValue())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                    //now iterate through and get top 10.
                    List<MetricValue> topTenantList = new ArrayList<>();
                    int i=0;
                    for (Map.Entry<String, Double> entry : sortedTenantValMap.entrySet()) {
                        //if the stat is average then divide by number of periods.
                        MetricValue mv = new MetricValue(-1 * entry.getValue(), entry.getKey());
                        if ("Average".equalsIgnoreCase(query.getStat())) {
                            BigDecimal bd = new BigDecimal(-1 * entry.getValue() / periodList.size()).setScale(3, RoundingMode.HALF_UP);
                            mv.setValue(bd.doubleValue());
                        }
                        topTenantList.add(mv);
                        i++;
                        //only output first 10
                        if (10 == i) {
                            break;
                        }
                    }

                    //**TODO: now we have to reverse the order.
                    mr.setTopTenant(topTenantList);
                }

                listResult.add(mr);
            }

            if (query.isTenantTaskMaxCapacity()) {
                Map<String, Integer> tenantTaskMaxCapacityMap = this.getTaskMaxCapacity(tenants);
                //build array of metric value to return
                List<MetricValue> tenantTaskMaxCapacityList = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : tenantTaskMaxCapacityMap.entrySet()) {
                    MetricValue mv = new MetricValue(entry.getValue(), entry.getKey());
                    tenantTaskMaxCapacityList.add(mv);
                }
                mrs.setTenantTaskMaxCapacity(tenantTaskMaxCapacityList);
            }

            mrs.setMetrics(listResult);
            mrs.setPeriods(periodList);
            queryResultList.add(mrs);

        } catch (CloudWatchException e) {
            LOGGER.error("queryMetrics: " + e.awsErrorDetails().errorMessage());
            LOGGER.error("queryMetrics: ", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryMetrics: exec " + totalTimeMillis);
        return queryResultList;
        //return mrs;
    }

    /*
    Used to query metrics for one or more specified tenants
    */
    public List<QueryResult> queryTenantMetrics(final MetricQuery query) throws Exception {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("queryTenantMetrics: start");
        List<MetricResultItem> listResult = new ArrayList<>();
        QueryResult mrs = new QueryResult();
        mrs.setId(query.getId());
        List<QueryResult> queryResultList = new ArrayList<>();
        try {
            Map<String, MetricDimension> dataQueryDimMap = new LinkedHashMap<>();
            String tenantId = null;
            if (query.getTenants().size() != 1) {
                throw new RuntimeException(("queryTenantMetrics: query JSON must have single item in tenants!"));
            }

            //build query
            final List<MetricDataQuery> dq = buildCWDataQuery(query, query.getTenants());

            //now that query is built let's execute and get resultant data
            //the data will be stored in Metric object and placed in map by MetricDimension.
            Map<MetricDimension, Metric> metricMap = loadCWMetricData(query, dq);
            LOGGER.debug("queryTenantMetrics: metricMap Size: {}", metricMap.size());

            boolean firstTime = true;
            for (final Map.Entry<MetricDimension, Metric> metricEntry : metricMap.entrySet()) {
                final Metric metric = metricEntry.getValue();
                final MetricDimension metricDimension = metricEntry.getKey();
                LOGGER.debug("queryTenantMetrics: metricDimension: {}, metricValues: {}", metricDimension.toString(), metric.getMetricValues().size());
                //construct a MetricDimension without a Tenant Id as the metrics are not by tenant
                MetricDimension md = new MetricDimension(metricDimension.getNameSpace(), metricDimension.getMetricName(), null);
                MetricResultItem mr = new MetricResultItem();
                mr.setDimension(md);
                //get the values list and return
                //reverse the values
                Collections.reverse(metric.getMetricValues());
                mr.putStat("Values", metric.getMetricValues());
                if (firstTime) {
                    List<String> periodsList = new ArrayList<>();
                    for (final Instant timeVal : metric.getMetricTimes()) {
                        //add entry  for the period key
                        periodsList.add(formatter.format(Date.from(timeVal)));
                    }
                    Collections.reverse(periodsList);
                    mrs.setPeriods(periodsList);
                    firstTime=false;
                }
                listResult.add(mr);
            }

            mrs.setMetrics(listResult);
            queryResultList.add(mrs);

        } catch (CloudWatchException e) {
            LOGGER.error("queryTenantMetrics: " + e.awsErrorDetails().errorMessage());
            LOGGER.error("queryTenantMetrics: ", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryTenantMetrics: exec " + totalTimeMillis);
        return queryResultList;
    }

    /*
    Used to query ALB access log metrics from Athena and S3 logs
  */
    public List<MetricValue> queryAccessLogs(final String timeRange, final String metricType, final String tenantId) throws Exception {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("queryMetrics: start");
        List<MetricValue> metricValueList;
        try {
            //Query based on Access Logs requested.  REQUEST_COUNT  or RESPONSE_TIME
            //get time range
            Instant[] times = MetricHelper.getTimeRangeForQuery(timeRange, 0,null, null);
            String where = " where target_status_code = '200'\n" +
                    "and time >= '" + times[0] + "' and time <= '" + times[1] + "' \n";

            if (null != tenantId) {
                String tenantAlb = getALBforTenant(tenantId);
                if (null == tenantAlb || tenantAlb.isEmpty()) {
                    throw new RuntimeException("queryAccessLogs: No ALB found for tenantId: " + tenantId);
                }
                where += " and elb = '" + tenantAlb + "' \n";
            }

            String metricCol = "";
            //get query type and build query
            StringBuilder athenaQuery = new StringBuilder();
            if ("PATH_REQUEST_COUNT".equalsIgnoreCase(metricType)) {
                metricCol = "count(1) as request_count\n";
            } else if ("PATH_RESPONSE_TIME".equalsIgnoreCase(metricType)) {
                metricCol = "avg(target_processing_time) as avg_target_time\n";
            }

            athenaQuery.append ("SELECT\n" +
                    "concat(url_extract_path(request_url), '+',request_verb) as url,\n")
                    .append(metricCol)
                    .append("FROM " + ACCESS_LOGS_TABLE + "\n")
                    .append(where)
                    .append ("GROUP BY  concat(url_extract_path(request_url),'+',request_verb)\n" +
                            "order by 2 desc\n" +
                            "limit 10;");

            LOGGER.debug("queryAccessLogs: athena query \n" + athenaQuery.toString());

            //now that query is built let's execute and get resultant data
            //the data will be stored in Metric object and placed in map by MetricDimension.
            metricValueList = getAthenaData(athenaQuery.toString());

        } catch (Exception e) {
            LOGGER.error("queryAccessLogs error: ", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryAccessLogs: exec time " + totalTimeMillis);
        return metricValueList;
    }

    private Map<String, MetricDimension> dataQueryDimMap = new LinkedHashMap<>();
    /*
    Build the Cloudwatch query based on the dimenions from the query
     */
    private List<MetricDataQuery> buildCWDataQuery(MetricQuery query, final List<String> tenants) throws Exception {
        List<MetricDataQuery> dq = new ArrayList<>();
        int dimIndex = 0;

        //lets get the period based on the time range
        int period = getPeriod(query);
        //store the period into query
        query.setPeriod(period);
        LOGGER.debug("buildDataQuery: period value: " + period + " for timeRangeName: " + query.getTimeRangeName());
        for (String tenantId : tenants) {
            tenantId = tenantId.replaceAll("tenant-", "");
            List<Dimension> dimList = new ArrayList<Dimension>();
            //build the dataquery with the dimensions
            for (final MetricQuery.Dimension queryDimension : query.getDimensions()) {
                if ("AWS/ECS".equalsIgnoreCase(queryDimension.getNameSpace())) {
                    //Cluster id is same as tenantId leading part
                    String clusterId = tenantId.split("-")[0];
                    Dimension dimension = Dimension.builder()
                            .name("ClusterName")
                            .value("tenant-" + clusterId)
                            .build();
                    dimList.add(dimension);
                    dimension = Dimension.builder()
                            .name("ServiceName")
                            .value("tenant-" + clusterId)
                            .build();
                    dimList.add(dimension);
                    //build dimension list for the resources of tenant depending on NameSpace
                } else if ("AWS/ApplicationELB".equalsIgnoreCase(queryDimension.getNameSpace())) {
                    //get ALB id from Parameter store
                    //String albId = "app/tenant-5fbd498c/63f1eedfca597fcc";
                    final String albId = getALBforTenant(tenantId);
                    if (StringUtils.isEmpty(albId)) {
                        throw new Exception("queryMetrics: No ALB Id found for Tenant: " + tenantId);
                    }
                    Dimension dimension = Dimension.builder()
                            .name("LoadBalancer")
                            .value(albId)
                            .build();
                    dimList.add(dimension);
                } else {
                    throw new Exception("queryMetrics: Namespace: " + queryDimension.getNameSpace() + " not currently implemented");
                }

                software.amazon.awssdk.services.cloudwatch.model.Metric met = software.amazon.awssdk.services.cloudwatch.model.Metric.builder()
                        //.namespace("ECS/ContainerInsights")
                        //.metricName("TaskCount")
                        .namespace(queryDimension.getNameSpace())
                        .metricName(queryDimension.getMetricName())
                        .dimensions(dimList)
                        .build();

                MetricStat stat = MetricStat.builder()
                        //.stat("Average") //use this for CPU Utilization
                        .stat(query.getStat()) //use SampleCount for TaskCount
                        .period(period)
                        .metric(met)
                        //.unit("Count")
                        .build();

                //store dim in map so we can match with result data later
                MetricDimension metricDimension = new MetricDimension(queryDimension.getNameSpace(), queryDimension.getMetricName(), tenantId);
                this.dataQueryDimMap.put("query_" + dimIndex, metricDimension);
                MetricDataQuery dataQuery = MetricDataQuery.builder()
                        .metricStat(stat)
                        .id("query0_" + dimIndex)
                        .returnData(false)
                        .build();

                dq.add(dataQuery);

                //Tell CW to fill with zeros for gaps
                dataQuery = MetricDataQuery.builder()
                      //  .metricStat(stat)
                        .id("query_" + dimIndex)
                        .expression("FILL(query0_"+ dimIndex + ", 0)")
                        .returnData(true)
                        .build();


                //Max of 500 MetricDataQuery can be in a single call
                dq.add(dataQuery);
                if (dq.size() > 500) {
                    throw new Exception("Can only process up to 500 data query items in GetMetricsData API");
                }
                dimIndex++;
            } //end for of metric dimensions
        }
        return dq;
    }

    private int getPeriod(MetricQuery query) {

        /* If query has the period then it overrides the the time range
         */
        if (null != query.getPeriod()) {
            return query.getPeriod();
        }


        if (StringUtils.isNotBlank(query.getTimeRangeName())) {
            LOGGER.debug("getStartDateTime: Using provided query TimeRangeName: " + query.getTimeRangeName());
            try {
                TimeRange timeRange = TimeRange.valueOf(query.getTimeRangeName());
                switch (timeRange) {
                    case TODAY:
                    case THIS_WEEK:
                        return 60 * 60; //every hour
                    case DAY_7:
                    case DAY_30:
                    case THIS_MONTH:
                        return 60 * 60 * 3;  //every 3 hours
                    case HOUR_8:
                    case HOUR_10:
                    case HOUR_12:
                    case HOUR_24:
                        return 60 * 15;  //every 15 minutes
                    default:
                        return 60 * 5; //every 5 minutes

                }
            } catch (Exception e) {
                throw new RuntimeException(("getPeriod: Invalid TimeRangeName provided: " + query.getTimeRangeName()));
            }
        }
        return query.getPeriod();
    }

    /*
    Loads data from AWS Cloudwatch and builds a Priority queue of values in Metric object for each timestamp
     */
    private Map<MetricDimension, Metric> loadCWMetricData(MetricQuery query,
                                                          List<MetricDataQuery> dq) throws URISyntaxException {
        long startTimeMillis = System.currentTimeMillis();
        String nextToken = null;
        Map<MetricDimension, Metric> metricMap = new LinkedHashMap<>();
        //get start date from Range if provided
        final Instant[] times = MetricHelper.getTimeRangeForQuery(query.getTimeRangeName(), query.getTzOffset(),query.getStartDate(), query.getEndDate());
        LOGGER.debug("loadCWMetricData: Start and Finish times for CW data query are " + times[0] + " and " + times[1]);
        do {
            GetMetricDataRequest getMetReq = GetMetricDataRequest.builder()
                    .maxDatapoints(10000)
                    .startTime(times[0])
                    .endTime(times[1])
                    .metricDataQueries(dq)
                    .nextToken(nextToken)
                    //.scanBy()  TimestampDescending  or TimestampAscending
                    .build();

            final GetMetricDataResponse response = cloudWatch.getMetricData(getMetReq);
            nextToken = response.nextToken();

            final List<MetricDataResult> data = response.metricDataResults();
            LOGGER.info("loadCWMetricData: fetch time in ms: " + (System.currentTimeMillis() - startTimeMillis));

            //process metrics data from CloudWatch into our own POJOs for aggregation
            for (MetricDataResult item : data) {
                LOGGER.debug("loadCWMetricData: " + String.format("Id: %s, label: %s", item.id(), item.label()));
                LOGGER.debug("loadCWMetricData: The status code is " + item.statusCode().toString());
                LOGGER.debug("loadCWMetricData: Returned items count " + item.values().size());

                final MetricDimension metricDimension = dataQueryDimMap.get(item.id());
                Metric metric = metricMap.get(metricDimension);
                if (null == metric) {
                    metric = new Metric();
                    metric.setNameSpace(metricDimension.getNameSpace());
                    metric.setMetricName(metricDimension.getMetricName());
                    metric.setStat(query.getStat());
                    metric.setPeriod(query.getPeriod());
                    metricMap.put(metricDimension, metric);
                }

                for (int x = 0; x < item.values().size(); x++) {
                    BigDecimal bd = new BigDecimal(item.values().get(x)).setScale(3, RoundingMode.HALF_UP);
                    double value = bd.doubleValue();
                    //LOGGER.debug("loadCWMetricData: CW Item Value " + item.values().get(x));
                    //LOGGER.debug("loadCWMetricData: CW Item Timestamp " + item.timestamps().get(x));
                    //construct mv with the Tenant Id
                    if (query.isSingleTenant()) {
                        //store so it is not sorted by value
//                        LOGGER.debug("loadCWMetricData:Add value {}", value);
                        metric.addMetricValue(value);
                        //store time into sorted map
                        metric.addSortTime(item.timestamps().get(x));
                    } else {
                        final MetricValue mv = new MetricValue(value, dataQueryDimMap.get(item.id()).getTenantId());
                        metric.addQueueValue(item.timestamps().get(x), mv);
                    }
                }
            }
        } while (nextToken != null && !nextToken.isEmpty());
        return metricMap;
    }

    private List<String> getTenants() throws Exception {
        long startMillis = System.currentTimeMillis();
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        List<String> tenantList = new ArrayList<>();

        ApiRequest tenantsRequest = ApiRequest.builder()
                .resource("tenants")
                .method("GET")
                .build();
        SdkHttpFullRequest apiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, tenantsRequest);
        String responseBody = null;
        try {
            responseBody = ApiGatewayHelper.signAndExecuteApiRequest(apiRequest, API_TRUST_ROLE, "MetricsService-GetTenants");
            List<Map<String, String>> tenants = Utils.fromJson(responseBody, ArrayList.class);
            if (null == tenants) {
                throw new RuntimeException("responseBody is not valid list of tenants");
            }
            for (Map<String, String> tenant : tenants) {
                tenantList.add(tenant.get("id"));
            }
        } catch (Exception e) {
            LOGGER.error("Error invoking API tenants");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        LOGGER.debug("getTenants: Total time to get list of tenants: " + (System.currentTimeMillis() - startMillis));
        return tenantList;
    }

    private String getALBforTenant(String tenantId) throws URISyntaxException {
        LOGGER.info("Getting ALB for Tenant {}", tenantId);
        String tenantAlbId = null;
        if (parameterStore.isEmpty() || parameterStore.get(tenantId + "/ALB") == null) {
            LOGGER.info("Internal parameter store cache is empty");
            loadParams(tenantId);
        } else {
            LOGGER.info("Internal parameter store cache has {} items", parameterStore.size());
        }
        tenantAlbId = parameterStore.get(tenantId + "/ALB");
        LOGGER.info("Returning tenant ALB {}", tenantAlbId);

        return tenantAlbId;
    }

    private Map<String, String> parameterStore = new TreeMap<>();
    private void loadParams(String tenantId) throws URISyntaxException {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        LOGGER.info("Loading parameters from settings service for tenant {}", tenantId);
        ApiRequest tenantSettings = ApiRequest.builder()
                .resource("settings/tenant/" + tenantId)
                .method("GET")
                .build();
        SdkHttpFullRequest apiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, tenantSettings);
        String responseBody = null;
        try {
            responseBody = ApiGatewayHelper.signAndExecuteApiRequest(apiRequest, API_TRUST_ROLE, "MetricsService-LoadParams");
            List<Map<String, String>> settings = Utils.fromJson(responseBody, ArrayList.class);
            if (null == settings) {
                throw new RuntimeException(("responseBody not valid list of Strings"));
            }
            for (Map<String, String> setting : settings) {
                LOGGER.info("Caching {} => {}", tenantId + "/" + setting.get("name"), setting.get("value"));
                parameterStore.put(tenantId + "/" + setting.get("name"), setting.get("value"));
            }
        } catch (Exception e) {
            LOGGER.error("Error invoking API settings/tenant/" + tenantId);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }

        if (parameterStore.isEmpty()) {
            throw new RuntimeException("loadParams: Error loading Parameter Store SaaS Boost parameters for tenant: " + tenantId);
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("getParams: exec " + totalTimeMillis);
    }

    private Map<String, Integer> getTaskMaxCapacity(List<String> tenants) {

        String nextToken = null;
        final ServiceNamespace ns = ServiceNamespace.ECS;
        final ScalableDimension ecsTaskCount = ScalableDimension.ECS_SERVICE_DESIRED_COUNT;
        List<String> resourceIds = new ArrayList<>();
        Map<String, Integer> capacityMap = new LinkedHashMap<>();
        Map<String, String> tenantMap = new LinkedHashMap<>();
        for (String tenantId : tenants) {
            //get leading part of the id:
            String segment1 = tenantId.split("-")[0];
            if (!tenantId.startsWith("tenant-")) {
                segment1 = "tenant-" + segment1;
            }
            capacityMap.put(tenantId, 1);  //initialize to value of 1 in case the Service does not have autoscaling setup
            tenantMap.put(segment1, tenantId); //store full id so we can return back.
            //tenantId = tenantId.replaceAll("tenant-", "");
            resourceIds.add("service/" + segment1 + "/" + segment1);
            //resourceIds.add("service/tenant-73ecc895/tenant-73ecc895");
        }

        // query for target
        do {
            DescribeScalableTargetsRequest dscRequest = DescribeScalableTargetsRequest.builder()
                    .serviceNamespace(ns)
                    .scalableDimension(ecsTaskCount)
                    .resourceIds(resourceIds)
                    .nextToken(nextToken)
                    .build();
            try {
                long start = System.currentTimeMillis();
                DescribeScalableTargetsResponse resp = autoScaling.describeScalableTargets(dscRequest);
                nextToken = resp.nextToken();
                LOGGER.info("getTaskMaxCapacity: DescribeScalableTargets result in " + (System.currentTimeMillis() - start) + " ms, : ");
               // LOGGER.info(String.valueOf(resp));
                List<ScalableTarget> targets = resp.scalableTargets();
                for (ScalableTarget target : targets) {
                    ScalableDimension dim = target.scalableDimension();
                    String[] id = target.resourceId().split("/");
                    String tenantFullId = tenantMap.get(id[2]);
                    capacityMap.put(tenantFullId, target.maxCapacity());
                    LOGGER.info("getTaskMaxCapacity: Dim: " + dim + ", Resource: " + id[2] + ", MaxCapacity: " + target.maxCapacity());
                }
            } catch (Exception e) {
                LOGGER.error("getTaskMaxCapacity: Unable to describe scalable target: ");
                LOGGER.error(e.getMessage());
                throw new RuntimeException(("getTaskMaxCapacity: Error with task capacity metrics"));
            }
        } while (nextToken != null && !nextToken.isEmpty());
        return capacityMap;
    }

    private List<MetricValue> getAthenaData(String query) throws Exception {
        long startTimeMillis = System.currentTimeMillis();
        String queryExecutionId = MetricHelper.submitAthenaQuery(athenaClient, query, S3_ATHENA_OUTPUT_PATH, ATHENA_DATABASE);
        MetricHelper.waitForQueryToComplete(athenaClient, queryExecutionId);List<MetricValue> metricValueList = MetricHelper.processResultRows(athenaClient, queryExecutionId);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.debug("MetricsService::getAthenaData exec {}", totalTimeMillis);
        return metricValueList;
    }

    //create partition for Athena table
    public void addAthenaPartition() throws Exception {
        long start = System.currentTimeMillis();
        LOGGER.info("addAthenaPartition: Start");
        if (Utils.isBlank(ACCESS_LOGS_PATH)) {
            throw new IllegalStateException("Missing required environment variable ACCESS_LOGS_PATH");
        }
        //**format date
        DateTimeFormatter DATE_TIME_FORMATTER_DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault());
        DateTimeFormatter DATE_TIME_FORMATTER_SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd")
                .withZone(ZoneId.systemDefault());
        //System.out.println(DATE_TIME_FORMATTER.format(new Date().toInstant()));
        Instant today = Instant.now();
        String formatPartitionDate = DATE_TIME_FORMATTER_SLASH.format(today); //"2019/08/01";
        String dateTimeFormat =  DATE_TIME_FORMATTER_DASH.format(today); //"2019-08-01";
        String queryString =
                "ALTER TABLE " +
                ACCESS_LOGS_TABLE + " " +
                "ADD IF NOT EXISTS PARTITION (time='" + dateTimeFormat + "') " +
                "LOCATION '" + ACCESS_LOGS_PATH + "/" + formatPartitionDate +"/';";
        LOGGER.debug("addAthenaPartition: Query for partition: {}", queryString);
        String queryExecutionId = MetricHelper.submitAthenaQuery(athenaClient, queryString, S3_ATHENA_OUTPUT_PATH, ATHENA_DATABASE);
        MetricHelper.waitForQueryToComplete(athenaClient, queryExecutionId);

        //get return data
        //List<MetricValue> metricValueList= MetricHelper.processResultRows(athenaClient, queryExecutionId);
        LOGGER.info("addAthenaPartition: Executed in: " + (System.currentTimeMillis() - start) + " ms");
        //return metricValueList;
    }

    public void publishAccessLogMetrics(final String s3FileName, final Enum timeRangeName, final String metric) {
        long startTimeMillis = System.currentTimeMillis();
        try {
            final List<MetricValue> result = queryAccessLogs(timeRangeName.toString(), metric, null);
            this.s3.putObject(PutObjectRequest.builder()
                    .bucket(S3_ATHENA_BUCKET)
                    .key(s3FileName)
                    .cacheControl("no-store")
                    .build(), RequestBody.fromString(Utils.toJson(result))
            );
        } catch (Exception e) {
            LOGGER.error("writeAccessLogMetrics: Error " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("writeAccessLogMetrics: exec " + totalTimeMillis);
    }


    public URL getPreSignedUrl(String key) {
        // Create a GetObjectRequest to be pre-signed
        long startTimeMillis = System.currentTimeMillis();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(S3_ATHENA_BUCKET)
                .key(key)
                .build();

        // Create a GetObjectPresignRequest to specify the signature duration
        GetObjectPresignRequest getObjectPresignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(60))
                        .getObjectRequest(getObjectRequest)
                        .build();

        // Generate the presigned request
        PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);

        if (presignedGetObjectRequest.isBrowserExecutable()) {
            LOGGER.info("The pre-signed request can be executed using a web browser by " +
                    "visiting the following URL: " + presignedGetObjectRequest.url());
            return presignedGetObjectRequest.url();
        } else {
            LOGGER.info("The pre-signed request has an HTTP method, headers or a payload " +
                    "that prohibits it from being executed by a web browser. See the S3Presigner " +
                    "class-level documentation for an example of how to execute this pre-signed " +
                    "request from Java code.");
            return null;
        }
    }

}
