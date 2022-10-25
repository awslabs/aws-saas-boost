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
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeHostsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInternetGatewaysResponse;
import software.amazon.awssdk.services.ec2.model.DescribeNatGatewaysResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DescribeDbClustersResponse;
import software.amazon.awssdk.services.rds.model.DescribeDbInstancesResponse;
import software.amazon.awssdk.services.servicequotas.ServiceQuotasClient;
import software.amazon.awssdk.services.servicequotas.model.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuotaTest {

    private final static Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));


    public static void main(String args[]) throws URISyntaxException {

        //SupportClient client = createClient();
       // getServices();

        List<Service> retList = new ArrayList<>();
        Map<String, Double> deployedCountMap = new LinkedHashMap<>();
        String serviceCode = "rds";
        deployedCountMap.put("DB clusters", Double.valueOf(getRdsClusters()));
        deployedCountMap.put("DB instances", Double.valueOf(getRdsInstances()));
        Map<String, Double> quotasMap = getQuotas(serviceCode);

        StringBuilder builder = new StringBuilder();
        compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);

//load balancers
        serviceCode = "elasticloadbalancing";
        deployedCountMap.clear();
        deployedCountMap.put("Application Load Balancers per Region", Double.valueOf(getAlbs()));
        quotasMap = getQuotas(serviceCode);
        compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);

//fargate
        serviceCode = "fargate";
        deployedCountMap.clear();
        deployedCountMap.put("Fargate On-Demand resource count", Double.valueOf(getAlbs()));
        quotasMap = getQuotas(serviceCode);
        compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);


//        System.out.println("Application Load Balancers per Region: " + );

//ecs  **TODO:  does not return anything
/*
        serviceCode = "ecs";
        deployedCountMap.clear();
        deployedCountMap.put("Clusters per account", Double.valueOf(getEcsClusters()));
        //Limit is 10000 so we don't need
        quotasMap = getQuotas(serviceCode);
        compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);
*/


//vpc
        serviceCode = "vpc";
        deployedCountMap.clear();
        deployedCountMap.put("VPCs per Region", Double.valueOf(getVpcs()));
        deployedCountMap.put("Internet gateways per Region", Double.valueOf(getInternetGateways()));
        deployedCountMap.put("NAT gateways per Availability Zone", Double.valueOf(getNatGateways()));
        quotasMap = getQuotas(serviceCode);
        compareValues(retList, deployedCountMap, serviceCode, quotasMap, builder);

        System.out.println(builder.toString());
        System.out.println("Returned values: " + retList.size());
/*//Limit is 10000 so we don't need
        System.out.println("ECS Clusters: " + getEcsClusters());
        System.out.println("ECS Task Definitions: " + getEcsTaskDefinitions());

        System.out.println("VPCs per Region: " + getVpcs());
        System.out.println("Internet gateways per Region: " + getInternetGateways());
        System.out.println("NAT Gateways: " + getNatGateways());
        System.out.println("EC2 Hosts: " + getEc2Instances());*/
    }

    private static void compareValues(List<Service> retList, Map<String, Double> deployedCountMap, String serviceCode, Map<String, Double> quotasMap, StringBuilder builder) {
        //now compare and build list of messages
        for (Map.Entry<String, Double> entry : deployedCountMap.entrySet()) {
            Service service = new Service(serviceCode, entry.getKey(), 0d, entry.getValue());
            if (quotasMap.containsKey(entry.getKey())) {
                Double quotaValue = quotasMap.get(entry.getKey());
                service.setQuotaValue(quotaValue);
                if (quotaValue.compareTo(entry.getValue()) < 1) {
                    builder.append("\nError, number of deployed " + entry.getKey() + " is " + entry.getValue()
                            + " and Service Quota is " + quotaValue);
                } else {
                    builder.append("\nInfo, number of deployed " + entry.getKey() + " is "  + entry.getValue()
                            + " and Service Quota is " + quotaValue);
                }
            } else {
                System.out.println("No Quota found for key: " + entry.getKey());
            }
            retList.add(service);
        }
    }

    private static ServiceQuotasClient createQuotasClient() {

         Region region = Region.US_EAST_1;
         // static ApplicationAutoScalingClient aaClient = (ApplicationAutoScalingClient) ApplicationAutoScalingClient.builder() ;
         ServiceQuotasClient client1 = ServiceQuotasClient.builder()
                 .httpClientBuilder(UrlConnectionHttpClient.builder())
//                .region(region)
                 .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                 .build();
         return client1;
     }

    private static RdsClient createRdsClient() throws URISyntaxException {

        RdsClient rdsClient = RdsClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(AWS_REGION)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(new URI("https://rds." + AWS_REGION.id() + "." + Utils.endpointSuffix(AWS_REGION.id())))
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();
        return rdsClient;
    }

    public static int getRdsClusters() throws URISyntaxException {
        DescribeDbClustersResponse response = createRdsClient().describeDBClusters();
        return response.dbClusters().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }

    public static int getRdsInstances() throws URISyntaxException {
        DescribeDbInstancesResponse response = createRdsClient().describeDBInstances();
        return response.dbInstances().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }

    private static ElasticLoadBalancingV2Client createAlbClient() throws URISyntaxException {

        ElasticLoadBalancingV2Client client = ElasticLoadBalancingV2Client.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(AWS_REGION)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(new URI("https://elasticloadbalancing." + AWS_REGION.id() + "." + Utils.endpointSuffix(AWS_REGION.id())))
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();
        return client;
    }

    public static int getAlbs() throws URISyntaxException {
         DescribeLoadBalancersResponse response = createAlbClient().describeLoadBalancers();
        return response.loadBalancers().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }
    /*
    private static EcsClient createEcsClient() throws URISyntaxException {

        EcsClient client = EcsClient.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(AWS_REGION)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(new URI("https://ecs." + AWS_REGION.id() + ".amazonaws.com")) // will break in China regions
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();
        return client;
    }

    public static int getEcsClusters() throws URISyntaxException {
        ListClustersResponse response = createEcsClient().listClusters();
        return response.clusterArns().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }

    public static int getEcsTaskDefinitions() throws URISyntaxException {
        ListTaskDefinitionsResponse response = createEcsClient().listTaskDefinitions();
        return response.taskDefinitionArns().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }
    */
    private static Ec2Client createEc2Client() throws URISyntaxException {

        Ec2Client client = Ec2Client.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(AWS_REGION)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(new URI("https://ec2." + AWS_REGION.id() + "." + Utils.endpointSuffix(AWS_REGION.id())))
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();
        return client;
    }

    public static int getVpcs() throws URISyntaxException {
        DescribeVpcsResponse response = createEc2Client().describeVpcs();
        return response.vpcs().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }

    public static int getInternetGateways() throws URISyntaxException {
        DescribeInternetGatewaysResponse response = createEc2Client().describeInternetGateways();
        return response.internetGateways().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }

    public static int getNatGateways() throws URISyntaxException {
        DescribeNatGatewaysResponse response = createEc2Client().describeNatGateways();
        return response.natGateways().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }

    public static int getEc2Instances() throws URISyntaxException {
        DescribeHostsResponse response = createEc2Client().describeHosts();
        return response.hosts().size();
        //for ( DBCluster cluster : response.dbClusters()) {
    }


    // Get the Quota
    public static Map<String, Double> getQuotas(String serviceCode) {
        // Possible language parameters: "en" (English), "ja" (Japanese), "fr" (French), "zh" (Chinese)
        Map<String, Double> retVals = new LinkedHashMap<>();
        String nextToken = null;
        do {
            ListServiceQuotasRequest request = ListServiceQuotasRequest.builder()
                    .serviceCode(serviceCode)
                    .nextToken(nextToken)
                    .build();
            ListServiceQuotasResponse response = createQuotasClient().listServiceQuotas(request);
            nextToken = response.nextToken();

            for (ServiceQuota quota : response.quotas()) {
                // Do something with check description.
//            System.out.println("Service: " + quota.serviceName() + " Quota: " + quota.quotaName() + " Value: " + quota.value());
                retVals.put(quota.quotaName(), quota.value());
//            System.out.println(quota.toString());
            }
        } while (nextToken != null && !nextToken.isEmpty());
        return retVals;
    }

    // Get the List of Available Trusted Advisor Checks
    public static void getServices() {
        // Possible language parameters: "en" (English), "ja" (Japanese), "fr" (French), "zh" (Chinese)

        String nextToken = null;

        //build a list of services that we are interested in with a list of the quota names and iterate
        Map<String, List<String>> serviceMap = new LinkedHashMap<>();
        List<String> quotas = new ArrayList<>();

        do {
            ListServicesRequest request = ListServicesRequest.builder().nextToken(nextToken).build();
            ListServicesResponse response = createQuotasClient().listServices(request);
            nextToken = response.nextToken();
            for (ServiceInfo info : response.services()) {
                System.out.println(info.toString());
                getQuotas(info.serviceCode());
            }
        } while (nextToken != null && !nextToken.isEmpty());
    }


    private static class Service {
        private String serviceCode;
        private String serviceName;
        private Double quotaValue;
        private Double serviceCount;

        public Service(String serviceCode, String serviceName, Double quotaValue, Double serviceCount) {
            this.serviceCode = serviceCode;
            this.serviceName = serviceName;
            this.quotaValue = quotaValue;
            this.serviceCount = serviceCount;
        }

        public String getServiceCode() {
            return serviceCode;
        }

        public void setServiceCode(String serviceCode) {
            this.serviceCode = serviceCode;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public Double getQuotaValue() {
            return quotaValue;
        }

        public void setQuotaValue(Double quotaValue) {
            this.quotaValue = quotaValue;
        }

        public Double getServiceCount() {
            return serviceCount;
        }

        public void setServiceCount(Double serviceCount) {
            this.serviceCount = serviceCount;
        }
    }

}
