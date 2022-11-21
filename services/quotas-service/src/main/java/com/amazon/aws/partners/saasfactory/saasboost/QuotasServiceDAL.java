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
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeNatGatewaysResponse;
import software.amazon.awssdk.services.ec2.model.NatGateway;
import software.amazon.awssdk.services.ec2.model.NatGatewayState;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Limit;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.AccountQuota;
import software.amazon.awssdk.services.servicequotas.ServiceQuotasClient;
import software.amazon.awssdk.services.servicequotas.model.ListServiceQuotasRequest;
import software.amazon.awssdk.services.servicequotas.model.ListServiceQuotasResponse;
import software.amazon.awssdk.services.servicequotas.model.ServiceQuota;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class QuotasServiceDAL {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuotasServiceDAL.class);
    private final ElasticLoadBalancingV2Client elb;
    private final Ec2Client ec2;
    private final ServiceQuotasClient serviceQuotas;
    private final RdsClient rds;
    private final CloudWatchClient cloudWatch;

    public QuotasServiceDAL() {
        final long startTimeMillis = System.currentTimeMillis();
        this.elb = Utils.sdkClient(ElasticLoadBalancingV2Client.builder(), ElasticLoadBalancingV2Client.SERVICE_NAME);
        this.ec2 = Utils.sdkClient(Ec2Client.builder(), Ec2Client.SERVICE_NAME);
        this.serviceQuotas = Utils.sdkClient(ServiceQuotasClient.builder(), ServiceQuotasClient.SERVICE_NAME);
        this.rds = Utils.sdkClient(RdsClient.builder(), RdsClient.SERVICE_NAME);
        this.cloudWatch = Utils.sdkClient(CloudWatchClient.builder(), CloudWatchClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public QuotaCheck checkQuotas() {
        String serviceCode;
        Map<String, Double> deployedCountMap = new LinkedHashMap<>();
        Map<String, Double> quotasMap = new LinkedHashMap<>();
        StringBuilder builder = new StringBuilder();

        boolean reportBackError = false;
        boolean exceedsLimit = false;
        List<Service> retList = new ArrayList<>();
        // RDS
        serviceCode = "rds";
        deployedCountMap.clear();
        deployedCountMap.put("DB clusters", Double.valueOf(getRdsClusters()));
        deployedCountMap.put("DB instances", Double.valueOf(getRdsInstances()));
        quotasMap = getQuotas(serviceCode);
        exceedsLimit = compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
        reportBackError = reportBackError || exceedsLimit;

        // load balancers
        serviceCode = "elasticloadbalancing";
        deployedCountMap.clear();
        deployedCountMap.put("Application Load Balancers per Region", Double.valueOf(getAlbs()));
        quotasMap = getQuotas(serviceCode);
        exceedsLimit = compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
        reportBackError = reportBackError || exceedsLimit;

        // fargate
        serviceCode = "fargate";
        deployedCountMap.clear();
        deployedCountMap.put("Fargate On-Demand vCPU resource count", getFargateResourceCount());
        deployedCountMap.put("Fargate Spot vCPU resource count", getFargateSpotResourceCount());
        quotasMap = getQuotas(serviceCode);
        // Remove old on demand quota that have been replaced with the new vCPU quota
        quotasMap.remove("Fargate On-Demand resource count");
        quotasMap.remove("Fargate Spot resource count");
        exceedsLimit = compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
        reportBackError = reportBackError || exceedsLimit;

        // vpc
        serviceCode = "vpc";
        deployedCountMap.clear();
        deployedCountMap.put("VPCs per Region", Double.valueOf(getVpcs()));
        deployedCountMap.put("Internet gateways per Region", Double.valueOf(getInternetGateways()));
        deployedCountMap.put("NAT gateways per Availability Zone", Double.valueOf(getNatGateways()));
        quotasMap = getQuotas(serviceCode);
        exceedsLimit = compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
        reportBackError = reportBackError || exceedsLimit;

        // ec2 vCPU
        serviceCode = "ec2";
        deployedCountMap.clear();
        deployedCountMap.put("Running On-Demand Standard (A, C, D, H, I, M, R, T, Z) instances", getVCpuCount());
        quotasMap = getQuotas(serviceCode);
        exceedsLimit = compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
        reportBackError = reportBackError || exceedsLimit;

        QuotaCheck quotaCheck = new QuotaCheck();
        quotaCheck.setPassed(!reportBackError);
        quotaCheck.setServiceList(retList);
        quotaCheck.setMessage(builder.toString());
        return quotaCheck;
    }

    private static boolean compareValues(List<Service> retList, Map<String, Double> deployedCountMap, String serviceCode, Map<String, Double> quotasMap, StringBuilder builder) {
        //now compare and build list of messages
        boolean exceedsLimit = false;
        for (Map.Entry<String, Double> entry : deployedCountMap.entrySet()) {
            Service service = new Service(serviceCode, entry.getKey(), entry.getValue());
            if (quotasMap.containsKey(entry.getKey())) {
                Double quotaValue = quotasMap.get(entry.getKey());
                LOGGER.info("Entry key : {}, Entry value: {}, quotaValue: {}", entry.getKey(), entry.getValue(), quotaValue);
                service.setQuotaValue(quotaValue);
                if (null != quotaValue) {
                    if (quotaValue.compareTo(entry.getValue()) < 1) {
                        builder.append("Quota will be exceeded for service ");
                        builder.append(entry.getKey());
                        builder.append(". You are currently consuming ");
                        builder.append(entry.getValue());
                        builder.append(", and Service Quota is ");
                        builder.append(quotaValue);
                        builder.append(".");
                        exceedsLimit = true;
                    } else {
                        service.setPassed(true);
                    }
                } else {
                    throw new RuntimeException("Unexpected null value for Quota: " + entry.getKey());
                }
            } else {
                LOGGER.info("No Quota found for key: {}", entry.getKey());
            }
            retList.add(service);
        }
        return exceedsLimit;
    }

    private int getRdsClusters() {
        int clusters = 0;
        try {
            clusters = rds.describeDBClusters().dbClusters().size();
        } catch (SdkServiceException rdsError) {
            LOGGER.error("rds::DescribeClusters", rdsError);
            LOGGER.error(Utils.getFullStackTrace(rdsError));
            throw rdsError;
        }
        return clusters;
    }

    private int getRdsInstances() {
        int instances = 0;
        try {
            instances = rds.describeDBInstances().dbInstances().size();
        } catch (SdkServiceException rdsError) {
            LOGGER.error("rds::DescribeDBInstances", rdsError);
            LOGGER.error(Utils.getFullStackTrace(rdsError));
            throw rdsError;
        }
        return instances;
    }

    private int getAlbs() {
        int loadBalancers = 0;
        try {
            loadBalancers = elb.describeLoadBalancers().loadBalancers().size();
        } catch (SdkServiceException elbError) {
            LOGGER.error("elasticloadbalancing::DescribeLoadBalancers", elbError);
            LOGGER.error(Utils.getFullStackTrace(elbError));
            throw elbError;
        }
        return loadBalancers;
    }

    private int getVpcs() {
        int vpcs = 0;
        try {
            vpcs = ec2.describeVpcs().vpcs().size();
        } catch (SdkServiceException ec2Error) {
            LOGGER.error("ec2::DescribeVpcs", ec2Error);
            LOGGER.error(Utils.getFullStackTrace(ec2Error));
            throw ec2Error;
        }
        return vpcs;
    }

    private int getInternetGateways() {
        int gateways = 0;
        try {
            gateways = ec2.describeInternetGateways().internetGateways().size();
        } catch (SdkServiceException ec2Error) {
            LOGGER.error("ec2::DescribeInternetGateways", ec2Error);
            LOGGER.error(Utils.getFullStackTrace(ec2Error));
            throw ec2Error;
        }
        return gateways;
    }

    private int getNatGateways() {
        int natGateways = 0;
        try {
            DescribeNatGatewaysResponse response = ec2.describeNatGateways();
            if (response.hasNatGateways()) {
                for (NatGateway natGateway : response.natGateways()) {
                    if (NatGatewayState.AVAILABLE == natGateway.state()
                            || NatGatewayState.PENDING == natGateway.state()) {
                        natGateways++;
                    }
                }
            }
        } catch (SdkServiceException ec2Error) {
            LOGGER.error("ec2::DescribeNatGateways", ec2Error);
            LOGGER.error(Utils.getFullStackTrace(ec2Error));
            throw ec2Error;
        }
        return natGateways;
    }

    private Double getFargateResourceCount() {
        final long startTime = System.currentTimeMillis();
        Double count = 0d;
        try {
            Metric metric = Metric.builder()
                    .metricName("ResourceCount")
                    .namespace("AWS/Usage")
                    .dimensions(Arrays.asList(
                            Dimension.builder().name("Type").value("Resource").build(),
                            Dimension.builder().name("Resource").value("vCPU").build(),
                            Dimension.builder().name("Service").value("Fargate").build(),
                            Dimension.builder().name("Class").value("Standard/OnDemand").build()
                    ))
                    .build();

            MetricStat metricStat = MetricStat.builder()
                    .stat("Maximum")
                    .period(600)
                    .metric(metric)
                    .build();

            MetricDataQuery dataQuery = MetricDataQuery.builder()
                    .metricStat(metricStat)
                    .id("fargate")
                    .returnData(true)
                    .build();

            Instant end = Instant.now();
            Instant start = end.minus(600, ChronoUnit.SECONDS);

            GetMetricDataRequest getMetricDataRequest = GetMetricDataRequest.builder()
                    .maxDatapoints(10000)
                    .startTime(start)
                    .endTime(end)
                    .metricDataQueries(Arrays.asList(dataQuery))
                    .build();

            GetMetricDataResponse response = cloudWatch.getMetricData(getMetricDataRequest);
            for (MetricDataResult item : response.metricDataResults()) {
                //get the last value as it is the most current
                if (!item.values().isEmpty()) {
                    count = item.values().get(item.values().size() - 1);
                    break;
                }
            }
            LOGGER.info("Time to process: " + (System.currentTimeMillis() - startTime));
        } catch (CloudWatchException cloudWatchError) {
            LOGGER.error("cloudwatch::GetMetricData", cloudWatchError);
            LOGGER.error(Utils.getFullStackTrace(cloudWatchError));
            throw cloudWatchError;
        }
        return count;
    }
    private Double getFargateSpotResourceCount() {
        final long startTime = System.currentTimeMillis();
        Double count = 0d;
        try {
            Metric metric = Metric.builder()
                    .metricName("ResourceCount")
                    .namespace("AWS/Usage")
                    .dimensions(Arrays.asList(
                            Dimension.builder().name("Type").value("Resource").build(),
                            Dimension.builder().name("Resource").value("vCPU").build(),
                            Dimension.builder().name("Service").value("Fargate").build(),
                            Dimension.builder().name("Class").value("Standard/Spot").build()
                    ))
                    .build();

            MetricStat metricStat = MetricStat.builder()
                    .stat("Maximum")
                    .period(600)
                    .metric(metric)
                    .build();

            MetricDataQuery dataQuery = MetricDataQuery.builder()
                    .metricStat(metricStat)
                    .id("fargate")
                    .returnData(true)
                    .build();

            Instant end = Instant.now();
            Instant start = end.minus(600, ChronoUnit.SECONDS);

            GetMetricDataRequest getMetricDataRequest = GetMetricDataRequest.builder()
                    .maxDatapoints(10000)
                    .startTime(start)
                    .endTime(end)
                    .metricDataQueries(Arrays.asList(dataQuery))
                    .build();

            GetMetricDataResponse response = cloudWatch.getMetricData(getMetricDataRequest);
            for (MetricDataResult item : response.metricDataResults()) {
                //get the last value as it is the most current
                if (!item.values().isEmpty()) {
                    count = item.values().get(item.values().size() - 1);
                    break;
                }
            }
            LOGGER.info("Time to process: " + (System.currentTimeMillis() - startTime));
        } catch (CloudWatchException cloudWatchError) {
            LOGGER.error("cloudwatch::GetMetricData", cloudWatchError);
            LOGGER.error(Utils.getFullStackTrace(cloudWatchError));
            throw cloudWatchError;
        }
        return count;
    }
    private Double getVCpuCount() {
        final long startTime = System.currentTimeMillis();
        Double count = 0d;
        try {
            Metric metric = Metric.builder()
                    .metricName("ResourceCount")
                    .namespace("AWS/Usage")
                    .dimensions(Arrays.asList(
                            Dimension.builder().name("Type").value("Resource").build(),
                            Dimension.builder().name("Resource").value("vCPU").build(),
                            Dimension.builder().name("Service").value("EC2").build(),
                            Dimension.builder().name("Class").value("Standard/OnDemand").build()
                    ))
                    .build();

            MetricStat metricStat = MetricStat.builder()
                    .stat("Maximum")
                    .period(600)
                    .metric(metric)
                    .build();

            MetricDataQuery dataQuery = MetricDataQuery.builder()
                    .metricStat(metricStat)
                    .id("vcpu")
                    .returnData(true)
                    .build();

            Instant end = Instant.now();
            Instant start = end.minus(600, ChronoUnit.SECONDS);

            GetMetricDataRequest getMetricDataRequest = GetMetricDataRequest.builder()
                    .maxDatapoints(10000)
                    .startTime(start)
                    .endTime(end)
                    .metricDataQueries(Arrays.asList(dataQuery))
                    .build();

            GetMetricDataResponse response = cloudWatch.getMetricData(getMetricDataRequest);
            for (MetricDataResult item : response.metricDataResults()) {
                //get the last value as it is the most current
                if (!item.values().isEmpty()) {
                    count = item.values().get(item.values().size() - 1);
                    break;
                }
            }
            LOGGER.info("Time to process: " + (System.currentTimeMillis() - startTime));
        } catch (CloudWatchException cloudWatchError) {
            LOGGER.error("cloudwatch::GetMetricData", cloudWatchError);
            LOGGER.error(Utils.getFullStackTrace(cloudWatchError));
            throw cloudWatchError;
        }
        return count;
    }

    // Get the Quota
    private Map<String, Double> getQuotas(String serviceCode) {
        // Possible language parameters: "en" (English), "ja" (Japanese), "fr" (French), "zh" (Chinese)
        Map<String, Double> retVals = new LinkedHashMap<>();
        String nextToken = null;
        LOGGER.info("Service: {}", serviceCode);
        try {
            do {
                ListServiceQuotasRequest request = ListServiceQuotasRequest.builder()
                        .serviceCode(serviceCode)
                        .nextToken(nextToken)
                        .build();
                ListServiceQuotasResponse response = serviceQuotas.listServiceQuotas(request);
                nextToken = response.nextToken();

                for (ServiceQuota quota : response.quotas()) {
                    // Do something with check description.
                    //LOGGER.info("Service: " + quota.serviceName() + " Quota: " + quota.quotaName() + " Value: " + quota.value());
                    retVals.put(quota.quotaName(), quota.value());
                    if (null == quota.value()) {
                        LOGGER.debug(quota.toString());  //this is for permissions error troubleshooting
                    }
                }
            } while (nextToken != null && !nextToken.isEmpty());
            return retVals;
        } catch (Exception e) {
            LOGGER.error("Error fetching quota for service {} with message {}", serviceCode, e.getMessage());
            LOGGER.error((Utils.getFullStackTrace(e)));
            throw e;
        }
    }

    // AWS Service Quotas is currently unavailable in the GCR regions, so we use the quota from service itself for check.
    public QuotaCheck checkQuotasForCNRegion() {
        String serviceCode;
        Map<String, Double> deployedCountMap = new LinkedHashMap<>();
        Map<String, Double> quotasMap = new LinkedHashMap<>();
        StringBuilder builder = new StringBuilder();

        boolean reportBackError = false;
        boolean exceedsLimit = false;
        List<Service> retList = new ArrayList<>();
        // RDS
        serviceCode = "rds";
        deployedCountMap.clear();
        deployedCountMap.put("DB clusters", Double.valueOf(getRdsClusters()));
        deployedCountMap.put("DB instances", Double.valueOf(getRdsInstances()));
        quotasMap = getRdsInstancesQuota();
        exceedsLimit = compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
        reportBackError = reportBackError || exceedsLimit;

        // load balancers
        serviceCode = "elasticloadbalancing";
        deployedCountMap.clear();
        deployedCountMap.put("Application Load Balancers per Region", Double.valueOf(getAlbs()));
        quotasMap = getELBQuota();
        exceedsLimit = compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
        reportBackError = reportBackError || exceedsLimit;

        QuotaCheck quotaCheck = new QuotaCheck();
        quotaCheck.setPassed(!reportBackError);
        quotaCheck.setServiceList(retList);
        quotaCheck.setMessage(builder.toString());
        return quotaCheck;
    }

    private Map<String, Double> getRdsInstancesQuota() {
        long instances;
        long clusters;
        try {
            List<AccountQuota> accountQuotas = rds.describeAccountAttributes().accountQuotas();
            clusters = accountQuotas.stream().filter(quota -> quota.accountQuotaName().equals("DBClusters"))
                    .findFirst().map(AccountQuota::max).orElse(40L);
            instances = accountQuotas.stream().filter(quota -> quota.accountQuotaName().equals("DBInstances"))
                    .findFirst().map(AccountQuota::max).orElse(40L);
        } catch (SdkServiceException rdsError) {
            LOGGER.error("rds::describeAccountAttributes", rdsError);
            LOGGER.error(Utils.getFullStackTrace(rdsError));
            throw rdsError;
        }
        Map<String, Double> retVals = new LinkedHashMap<>();
        retVals.put("DB clusters", Double.valueOf(clusters));
        retVals.put("DB instances", Double.valueOf(instances));
        return retVals;
    }

    private Map<String, Double> getELBQuota() {
        long instances;
        try {
            instances = elb.describeAccountLimits().limits().stream()
                    .filter(x -> x.name().equals("application-load-balancers"))
                    .findFirst().map(Limit::max).map(Long::valueOf).orElse(50L);
        } catch (SdkServiceException elbError) {
            LOGGER.error("elb::describeAccountLimits", elbError);
            LOGGER.error(Utils.getFullStackTrace(elbError));
            throw elbError;
        }
        Map<String, Double> retVals = new LinkedHashMap<>();
        retVals.put("Application Load Balancers per Region", Double.valueOf(instances));
        return retVals;
    }

//    // Get the List of Available Trusted Advisor Checks
//    private void getServices() {
//        // Possible language parameters: "en" (English), "ja" (Japanese), "fr" (French), "zh" (Chinese)
//
//        String nextToken = null;
//
//        //build a list of services that we are interested in with a list of the quota names and iterate
//        Map<String, List<String>> serviceMap = new LinkedHashMap<>();
//        List<String> quotas = new ArrayList<>();
//
//        do {
//            ListServicesRequest request = ListServicesRequest.builder().nextToken(nextToken).build();
//            ListServicesResponse response = serviceQuotasClient.listServices(request);
//            nextToken = response.nextToken();
//            for (ServiceInfo info : response.services()) {
//                System.out.println(info.toString());
//                getQuotas(info.serviceCode());
//            }
//        } while (nextToken != null && !nextToken.isEmpty());
//    }

    public static class Service {
        private String serviceCode;
        private String serviceName;
        private Double quotaValue = 0d;
        private Double serviceCount;
        private boolean passed = false;

        public Service(String serviceCode, String serviceName, Double serviceCount) {
            this.serviceCode = serviceCode;
            this.serviceName = serviceName;
            this.serviceCount = serviceCount;
        }

        public void setPassed(boolean ok) {
            passed = ok;
        }

        public void setQuotaValue(Double quotaValue) {
            this.quotaValue = quotaValue;
        }
    }

    public static class QuotaCheck {
        private List<Service> serviceList;
        private boolean passed = false;
        private String message = "";

        public void setServiceList(List<Service> serviceList) {
            this.serviceList = serviceList;
        }

        public void setPassed(boolean checkPassed) {
            this.passed = checkPassed;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
