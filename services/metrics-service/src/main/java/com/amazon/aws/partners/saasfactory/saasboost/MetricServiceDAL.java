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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MetricServiceDAL {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricServiceDAL.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String ATHENA_DATABASE = System.getenv("ATHENA_DATABASE");
    private static final String S3_ATHENA_OUTPUT_PATH = System.getenv("S3_ATHENA_OUTPUT_PATH");
    private static final String S3_ATHENA_BUCKET = System.getenv("S3_ATHENA_BUCKET");
    private static final String ACCESS_LOGS_TABLE = System.getenv("ACCESS_LOGS_TABLE");
    private static final String ACCESS_LOGS_PATH = System.getenv("ACCESS_LOGS_PATH");
    private final ApplicationAutoScalingClient autoScaling;
    private final CloudWatchClient cloudWatch;
    private final S3Client s3;
    private final S3Presigner s3Presigner;
    private final AthenaClient athenaClient;
    private final Map<String, MetricDimension> dataQueryDimMap = new LinkedHashMap<>();

    public MetricServiceDAL() {
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
    }

    // Used to query CW metrics across tenants and aggregate the data
    public List<QueryResult> queryMetrics(final MetricQuery query) {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("queryMetrics: start");

        List<MetricResultItem> listResult = new ArrayList<>();
        List<String> periodList = new ArrayList<>();
        List<QueryResult> queryResultList = new ArrayList<>();
        QueryResult mrs = new QueryResult();
        mrs.setId(query.getId());
        try {
            List<String> tenants;
            if (!query.getTenants().isEmpty()) {
                LOGGER.info("queryMetrics: use tenants from query");
                tenants = new ArrayList<>(query.getTenants());
            } else {
                tenants = new ArrayList<>(MetricService.tenantCache.keySet());
            }

            if (tenants.size() > 500) {
                throw new RuntimeException("queryMetrics: Cannot process more than 500 tenants");
            } else if (tenants.isEmpty()) {
                throw new RuntimeException("queryMetrics: No tenants to process");
            }

            //build query
            final List<MetricDataQuery> dq = cloudWatchMetricsQueries(query, tenants);

            //now that query is built let's execute and get resultant data
            //the data will be stored in Metric object and placed in map by MetricDimension.
            Map<MetricDimension, Metric> metricMap = loadCloudWatchMetricsData(query, dq);
            LOGGER.info("queryMetrics: metricMap item count: " + metricMap.size());

            for (final Map.Entry<MetricDimension, Metric> metricEntry : metricMap.entrySet()) {
                final Metric metric = metricEntry.getValue();
                final MetricDimension metricDimension = metricEntry.getKey();
                LOGGER.info("queryMetrics: Dimension: {} {} Count: {}", metricDimension.getNameSpace(),
                        metric.getMetricName(), metric.getMetricValues().size());
                //construct a MetricDimension without a Tenant Id as the metrics are not by tenant
                MetricDimension md = new MetricDimension(metricDimension.getNameSpace(), metricDimension.getMetricName());
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
                    final String period = DateTimeFormatter
                            .ofPattern("MM-dd HH:mm")
                            .withZone(ZoneId.systemDefault())
                            .format(entry.getKey());
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
                    int i = 0;
                    for (Map.Entry<String, Double> entry : sortedTenantValMap.entrySet()) {
                        //if the stat is average then divide by number of periods.
                        MetricValue mv = new MetricValue(-1 * entry.getValue(), entry.getKey());
                        if ("Average".equalsIgnoreCase(query.getStat())) {
                            BigDecimal bd = BigDecimal.valueOf(-1 * entry.getValue() / periodList.size())
                                    .setScale(3, RoundingMode.HALF_UP);
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
                Map<String, Integer> tenantTaskMaxCapacityMap = getTaskMaxCapacity(tenants);
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
    }

    // Used to query metrics for a specified tenant
    public List<QueryResult> queryTenantMetrics(final MetricQuery query) {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("queryTenantMetrics: start");
        List<QueryResult> queryResults = new ArrayList<>();
        List<MetricResultItem> metrics = new ArrayList<>();
        
        QueryResult queryResult = new QueryResult();
        queryResult.setId(query.getId());
        try {
            if (query.getTenants().size() != 1) {
                throw new RuntimeException(("queryTenantMetrics: query JSON must have single item in tenants!"));
            }

            //build query
            final List<MetricDataQuery> dataQueries = cloudWatchMetricsQueries(query, query.getTenants());

            //now that query is built let's execute and get resultant data
            //the data will be stored in Metric object and placed in map by MetricDimension.
            Map<MetricDimension, Metric> metricMap = loadCloudWatchMetricsData(query, dataQueries);
            LOGGER.info("queryTenantMetrics: metricMap Size: {}", metricMap.size());

            boolean firstTime = true;
            for (final Map.Entry<MetricDimension, Metric> entry : metricMap.entrySet()) {
                final Metric metric = entry.getValue();
                final MetricDimension dimension = entry.getKey();
                LOGGER.info("queryTenantMetrics: dimension: {}, metricValues: {}", dimension.toString(),
                        metric.getMetricValues().size());
                
                //construct a MetricDimension without a Tenant Id as the metrics are not by tenant
                MetricDimension metricDimension = new MetricDimension(dimension.getNameSpace(), dimension.getMetricName());
                MetricResultItem metricResultItem = new MetricResultItem();
                metricResultItem.setDimension(metricDimension);
                
                //reverse the values
                List<Double> values = new ArrayList<>(metric.getMetricValues());
                Collections.reverse(values);
                metricResultItem.putStat("Values", values);
                
                // Need a single copy of the time periods for this query
                if (firstTime) {
                    List<String> periodsList = new ArrayList<>();
                    for (final Instant timeVal : metric.getMetricTimes()) {
                        //add entry  for the period key
                        periodsList.add(DateTimeFormatter
                                .ofPattern("MM-dd HH:mm")
                                .withZone(ZoneId.systemDefault())
                                .format(timeVal)
                        );
                    }
                    Collections.reverse(periodsList);
                    queryResult.setPeriods(periodsList);
                    firstTime = false;
                }
                
                metrics.add(metricResultItem);
            }

            queryResult.setMetrics(metrics);
            queryResults.add(queryResult);

        } catch (CloudWatchException e) {
            LOGGER.error("queryTenantMetrics: " + e.awsErrorDetails().errorMessage());
            LOGGER.error("queryTenantMetrics: ", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw e;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryTenantMetrics: exec " + totalTimeMillis);
        return queryResults;
    }

    /*
    Used to query ALB access log metrics from Athena and S3 logs
  */
    public List<MetricValue> queryAccessLogs(String timeRange, String metricType, String tenantId) {
        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("queryMetrics: start");
        List<MetricValue> metricValueList;
        try {
            //Query based on Access Logs requested. REQUEST_COUNT or RESPONSE_TIME
            //get time range
            Instant[] times = MetricHelper.getTimeRangeForQuery(timeRange, 0,null, null);
            String where = "WHERE target_status_code = '200' AND time >= '" + times[0] + "' AND time <= '" + times[1] + "'\n";

            if (tenantId != null) {
                String tenantAlb = getTenantLoadBalancerId(tenantId);
                if (Utils.isEmpty(tenantAlb)) {
                    throw new RuntimeException("queryAccessLogs: No ALB found for tenantId: " + tenantId);
                }
                where += " AND elb = '" + tenantAlb + "'\n";
            }

            String metricCol;
            if ("PATH_REQUEST_COUNT".equalsIgnoreCase(metricType)) {
                metricCol = ", count(1) AS request_count\n";
            } else if ("PATH_RESPONSE_TIME".equalsIgnoreCase(metricType)) {
                metricCol = ", avg(target_processing_time) AS avg_target_time\n";
            } else {
                LOGGER.warn("Unknown metricType {}", metricType);
                metricCol = "\n";
            }

            String query = new StringBuilder().append("SELECT\n")
                    .append("concat(url_extract_path(request_url), '+', request_verb) AS url")
                    .append(metricCol)
                    .append("FROM \"")
                    .append(ACCESS_LOGS_TABLE)
                    .append("\"\n")
                    .append(where)
                    .append("GROUP BY concat(url_extract_path(request_url), '+', request_verb)\n")
                    .append("ORDER BY 2 DESC\n")
                    .append("LIMIT 10;")
                    .toString();

            LOGGER.info("queryAccessLogs: athena query \n" + query);

            //now that query is built let's execute and get resultant data
            //the data will be stored in Metric object and placed in map by MetricDimension.
            metricValueList = getAthenaData(query);
        } catch (Exception e) {
            LOGGER.error("queryAccessLogs error: ", e);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("queryAccessLogs: exec time " + totalTimeMillis);
        return metricValueList;
    }

    // Build the CloudWatch query based on the dimensions from the query
    private List<MetricDataQuery> cloudWatchMetricsQueries(MetricQuery query, final List<String> tenants) {
        List<MetricDataQuery> dq = new ArrayList<>();
        int dimIndex = 0;

        //lets get the period based on the time range
        int period = getPeriod(query);
        //store the period into query
        query.setPeriod(period);
        LOGGER.info("buildDataQuery: period value: " + period + " for timeRangeName: " + query.getTimeRangeName());
        
        for (String tenantId : tenants) {
            Set<Dimension> dimList = new HashSet<>();
            //build the dataquery with the dimensions
            for (final MetricQuery.Dimension queryDimension : query.getDimensions()) {
                if ("AWS/ECS".equalsIgnoreCase(queryDimension.getNameSpace())) {
                    String cluster = getTenantEcsCluster(tenantId);
                    if (Utils.isEmpty(cluster)) {
                        throw new RuntimeException("queryMetrics: No ECS cluster found for tenant: " + tenantId);
                    }
                    // We don't know how many ECS services there are, so ask CloudWatch for all of the
                    // dimensions we can use for this metric in the tenant's cluster.
                    ListMetricsResponse availableMetrics = cloudWatch.listMetrics(request -> request
                            .namespace(queryDimension.getNameSpace())
                            .metricName(queryDimension.getMetricName())
                            .dimensions(DimensionFilter.builder().name("ClusterName").value(cluster).build())
                    );
                    if (availableMetrics.hasMetrics()) {
                        for (software.amazon.awssdk.services.cloudwatch.model.Metric availableMetric : availableMetrics.metrics()) {
                            dimList.addAll(availableMetric.dimensions());
                        }
                    }
                //} else if ("ECS/ContainerInsights".equalsIgnoreCase(queryDimension.getNameSpace())) {
                } else if ("AWS/ApplicationELB".equalsIgnoreCase(queryDimension.getNameSpace())) {
                    final String albId = getTenantLoadBalancerId(tenantId);
                    if (Utils.isEmpty(albId)) {
                        throw new RuntimeException("queryMetrics: No ALB Id found for tenant: " + tenantId);
                    }
                    Dimension dimension = Dimension.builder()
                            .name("LoadBalancer")
                            .value(albId)
                            .build();
                    dimList.add(dimension);
                } else {
                    throw new RuntimeException("queryMetrics: Namespace: " + queryDimension.getNameSpace()
                            + " not currently implemented");
                }

                software.amazon.awssdk.services.cloudwatch.model.Metric met = software.amazon.awssdk.services.cloudwatch.model.Metric.builder()
                        .namespace(queryDimension.getNameSpace())
                        .metricName(queryDimension.getMetricName())
                        .dimensions(dimList)
                        .build();

                MetricStat stat = MetricStat.builder()
                        .stat(query.getStat())
                        .period(period)
                        .metric(met)
                        .build();

                //store dim in map so we can match with result data later
                MetricDimension metricDimension = new MetricDimension(
                        queryDimension.getNameSpace(),
                        queryDimension.getMetricName(),
                        tenantId
                );
                this.dataQueryDimMap.put("query_" + dimIndex, metricDimension);

                MetricDataQuery dataQuery = MetricDataQuery.builder()
                        .metricStat(stat)
                        .id("query0_" + dimIndex)
                        .returnData(false)
                        .build();
                dq.add(dataQuery);

                //Tell CW to fill with zeros for gaps
                dataQuery = MetricDataQuery.builder()
                        .id("query_" + dimIndex)
                        .expression("FILL(query0_" + dimIndex + ", 0)")
                        .returnData(true)
                        .build();

                //Max of 500 MetricDataQuery can be in a single call
                dq.add(dataQuery);
                if (dq.size() > 500) {
                    throw new RuntimeException("Can only process up to 500 data query items in GetMetricsData API");
                }
                dimIndex++;
            } //end for of metric dimensions
        }
        return dq;
    }

    private int getPeriod(MetricQuery query) {
        // If query has the period then it overrides the the time range
        if (query.getPeriod() != null) {
            return query.getPeriod();
        }

        if (Utils.isNotBlank(query.getTimeRangeName())) {
            LOGGER.info("getStartDateTime: Using provided query TimeRangeName: " + query.getTimeRangeName());
            try {
                TimeRange timeRange = TimeRange.valueOf(query.getTimeRangeName());
                switch (timeRange) {
                    case TODAY:
                    case THIS_WEEK:
                        return 60 * 60; // every hour
                    case DAY_7:
                    case DAY_30:
                    case THIS_MONTH:
                        return 60 * 60 * 3; // every 3 hours
                    case HOUR_8:
                    case HOUR_10:
                    case HOUR_12:
                    case HOUR_24:
                        return 60 * 15; // every 15 minutes
                    default:
                        return 60 * 5; // every 5 minutes
                }
            } catch (Exception e) {
                throw new RuntimeException("getPeriod: Invalid TimeRangeName provided: " + query.getTimeRangeName());
            }
        }
        return query.getPeriod();
    }

    // Loads data from AWS Cloudwatch and builds a Priority queue of values in Metric object for each timestamp
    private Map<MetricDimension, Metric> loadCloudWatchMetricsData(MetricQuery query, List<MetricDataQuery> dq) {
        final long startTimeMillis = System.currentTimeMillis();
        String nextToken = null;
        Map<MetricDimension, Metric> metricMap = new LinkedHashMap<>();
        //get start date from Range if provided
        final Instant[] times = MetricHelper.getTimeRangeForQuery(
                query.getTimeRangeName(),
                query.getTzOffset(),
                query.getStartDate(),
                query.getEndDate()
        );
        LOGGER.info("loadCWMetricData: Start and Finish times for CW data query are {} and {}", times[0], times[1]);
        //LOGGER.info(Utils.toJson(dq));
        do {
            GetMetricDataRequest getMetReq = GetMetricDataRequest.builder()
                    .maxDatapoints(10000)
                    .startTime(times[0])
                    .endTime(times[1])
                    .metricDataQueries(dq)
                    .nextToken(nextToken)
                    .build();

            final GetMetricDataResponse response = cloudWatch.getMetricData(getMetReq);
            nextToken = response.nextToken();

            final List<MetricDataResult> data = response.metricDataResults();
            LOGGER.info("loadCWMetricData: fetch time in ms: " + (System.currentTimeMillis() - startTimeMillis));
            //LOGGER.info(Utils.toJson(data));

            //process metrics data from CloudWatch into our own POJOs for aggregation
            for (MetricDataResult item : data) {
                LOGGER.info("loadCWMetricData: " + String.format("Id: %s, label: %s", item.id(), item.label()));
                LOGGER.info("loadCWMetricData: The status code is " + item.statusCode().toString());
                LOGGER.info("loadCWMetricData: Returned items count " + item.values().size());

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
                    BigDecimal bd = BigDecimal.valueOf(item.values().get(x)).setScale(3, RoundingMode.HALF_UP);
                    double value = bd.doubleValue();
                    //LOGGER.info("CloudWatch Metric Value " + item.values().get(x));
                    //LOGGER.info("Metric Value as double {}", value);
                    //LOGGER.info("CloudWatch Metric Timestamp " + item.timestamps().get(x));
                    if (query.isSingleTenant()) {
                        //store so it is not sorted by value
                        metric.addMetricValue(value);
                        //store time into sorted map
                        metric.addSortTime(item.timestamps().get(x));
                    } else {
                        // If we're querying for all tenants, save the metrics keyed by tenant id
                        final MetricValue mv = new MetricValue(value, dataQueryDimMap.get(item.id()).getTenantId());
                        metric.addQueueValue(item.timestamps().get(x), mv);
                    }
                }
            }
        } while (Utils.isNotEmpty(nextToken));
        return metricMap;
    }

    protected String getTenantLoadBalancerId(String tenantId) {
        LOGGER.info("Getting ALB for tenant {}", tenantId);
        Map<String, Object> tenant = MetricService.tenantCache.get(tenantId);
        String alb;
        try {
            Map<String, Object> resources = (Map<String, Object>) tenant.get("resources");
            alb = ((Map<String, String>) resources.get("LOAD_BALANCER")).get("name");
        } catch (NullPointerException npe) {
            LOGGER.error("Can't find LOAD_BALANCER resource for tenant {}", tenantId);
            alb = "";
        }
        return alb;
    }

    protected String getTenantEcsCluster(String tenantId) {
        LOGGER.info("Getting ECS cluster for tenant {}", tenantId);
        Map<String, Object> tenant = MetricService.tenantCache.get(tenantId);
        String cluster;
        try {
            Map<String, Object> resources = (Map<String, Object>) tenant.get("resources");
            cluster = ((Map<String, String>) resources.get("ECS_CLUSTER")).get("name");
        } catch (NullPointerException npe) {
            LOGGER.error("Can't find ECS_CLUSTER resource for tenant {}", tenantId);
            cluster = "";
        }
        return cluster;
    }

    protected Map<String, Integer> getTaskMaxCapacity(List<String> tenants) {
        String nextToken = null;
        final ServiceNamespace ns = ServiceNamespace.ECS;
        final ScalableDimension ecsTaskCount = ScalableDimension.ECS_SERVICE_DESIRED_COUNT;
        List<String> resourceIds = new ArrayList<>();
        Map<String, Integer> capacityMap = new LinkedHashMap<>();
        Map<String, String> tenantMap = new LinkedHashMap<>();
        for (String tenantId : tenants) {
            String segment1 = tenantId.split("-")[0];
            if (!tenantId.startsWith("tenant-")) {
                segment1 = "tenant-" + segment1;
            }
            capacityMap.put(tenantId, 1);  //initialize to value of 1 in case the Service does not have autoscaling setup
            tenantMap.put(segment1, tenantId); //store full id so we can return back.
            resourceIds.add("service/" + segment1 + "/" + segment1);
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
        } while (Utils.isNotEmpty(nextToken));
        return capacityMap;
    }

    private List<MetricValue> getAthenaData(String query) throws Exception {
        final long startTimeMillis = System.currentTimeMillis();
        String queryExecutionId = MetricHelper.submitAthenaQuery(athenaClient, query, S3_ATHENA_OUTPUT_PATH, ATHENA_DATABASE);
        MetricHelper.waitForQueryToComplete(athenaClient, queryExecutionId);
        List<MetricValue> metricValueList = MetricHelper.processResultRows(athenaClient, queryExecutionId);
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("MetricsService::getAthenaData exec {}", totalTimeMillis);
        return metricValueList;
    }

    //create partition for Athena table
    public void addAthenaPartition() throws Exception {
        final long start = System.currentTimeMillis();
        LOGGER.info("addAthenaPartition: Start");
        if (Utils.isBlank(ACCESS_LOGS_PATH)) {
            throw new IllegalStateException("Missing required environment variable ACCESS_LOGS_PATH");
        }

        //System.out.println(DATE_TIME_FORMATTER.format(new Date().toInstant()));
        Instant today = Instant.now();
        String formatPartitionDate = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.systemDefault())
                .format(today); //"2019/08/01";
        String dateTimeFormat =  DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
                .format(today); //"2019-08-01";
        String queryString = "ALTER TABLE \"" + ACCESS_LOGS_TABLE + "\" ADD IF NOT EXISTS PARTITION "
                + "(time='" + dateTimeFormat + "') LOCATION '" + ACCESS_LOGS_PATH + "/" + formatPartitionDate + "/';";
        LOGGER.info("addAthenaPartition: Query for partition: {}", queryString);
        String queryExecutionId = MetricHelper.submitAthenaQuery(
                athenaClient,
                queryString,
                S3_ATHENA_OUTPUT_PATH,
                ATHENA_DATABASE
        );
        MetricHelper.waitForQueryToComplete(athenaClient, queryExecutionId);

        //get return data
        //List<MetricValue> metricValueList= MetricHelper.processResultRows(athenaClient, queryExecutionId);
        LOGGER.info("addAthenaPartition: Executed in: " + (System.currentTimeMillis() - start) + " ms");
        //return metricValueList;
    }

    public void publishAccessLogMetrics(String s3FileName, Enum<TimeRange> timeRangeName, String metric) {
        final long startTimeMillis = System.currentTimeMillis();
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
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(S3_ATHENA_BUCKET)
                .key(key)
                .build();

        GetObjectPresignRequest getObjectPresignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(60))
                        .getObjectRequest(getObjectRequest)
                        .build();

        // Generate the presigned request
        LOGGER.info("Generating presigned S3 URL for {}{}", S3_ATHENA_BUCKET, key);
        PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        URL url;
        if (presignedGetObjectRequest.isBrowserExecutable()) {
            url = presignedGetObjectRequest.url();
        } else {
            LOGGER.error("S3 URL can't be executed by web browser");
            url = null;
        }
        return url;
    }

}
