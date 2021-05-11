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



import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;


public class Test {
    public String getPropValues() throws IOException {
        String result = "";
        InputStream inputStream = null;
        try {
            Properties prop = new Properties();
            String propFileName = "git.properties";

            inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            Date time = new Date(System.currentTimeMillis());

            // get the property value and print it out
            String tag = prop.getProperty("git.commit.id.describe");
            String commitTime = prop.getProperty("git.commit.time");
            result = "Version: " + tag + ", Commit time: " + commitTime;

//            System.out.println(result + "\nProgram Ran on " + time );
        } catch (Exception e) {
            System.out.println("Exception: " + e);
        } finally {
            if (null != inputStream) {
                inputStream.close();
            }
        }
        return result;
    }

    public static void main(String[] args) throws Exception {

        final Instant curDateTime = Instant.ofEpochMilli(new Date().getTime());
        LocalDateTime localStartDateTime = LocalDateTime.ofInstant(curDateTime.now(), ZoneId.systemDefault());
        System.out.println("local time: " + localStartDateTime);
        System.out.println("minus 60 minutes: " + localStartDateTime.minusMinutes(60));
        System.out.println("minus -60 minutes: " + localStartDateTime.minusMinutes(-60));

//TEST THE METRIC SERVICE LAMBDA
/*        String body = "{\"tenants\":[\"5fbd498c\",\"73ecc895\"],\"startDate\":\"2020-06-05T23:00:00Z\",\"endDate\"" +
                ":\"2020-06-06T01:00:00Z\",\"metricName\":\"CPUUtilization\"," +
                "\"nameSpace\":\"AWS/ECS\",\"stat\":\"Average\",\"period\":300,\"topTenantList\":false," +
                "\"statsMap\":true,\"" +
                "\"tenantDimensionMap\":{\"73ecc895\":[{\"name\":\"ClusterName\",\"value\":\"tenant-73ecc895\"}," +
                "{\"name\":\"ServiceName\",\"value\":\"tenant-73ecc895\"}],\"5fbd498c\":[{\"name\":\"ClusterName\"," +
                "\"value\":\"tenant-5fbd498c\"},{\"name\":\"ServiceName\",\"value\":\"tenant-5fbd498c\"}]}}";*/


       // EnvironmentVariableCredentialsProvider evp = EnvironmentVariableCredentialsProvider.create();
       // S3Client s3Client = S3Client.builder().credentialsProvider(evp).build();
/*        S3Client s3 = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();*/
        MetricService ms = new MetricService();
        System.out.println("write files for request count");
        //ms.publishRequestCountMetrics(null, null, null);

       // Test test = new Test();
        //test.getPropValues();
        int x= 1;
        if (x == 2) {
            System.exit(0);
        }



//        String body1 = "{\"tenants\":[\"5fbd498c\",\"73ecc895\"],\"startDate\":\"2020-06-05T23:00:00Z\",\"endDate\":\"2020-06-06T01:00:00Z\",\"metricName\":\"CPUUtilization\",\"nameSpace\":\"AWS/ECS\",\"stat\":\"Average\",\"period\":300,\"top10\":false,\"tenantDimensionMap\":{\"73ecc895\":[{\"name\":\"ClusterName\",\"value\":\"tenant-73ecc895\"},{\"name\":\"ServiceName\",\"value\":\"tenant-73ecc895\"}],\"5fbd498c\":[{\"name\":\"ClusterName\",\"value\":\"tenant-5fbd498c\"},{\"name\":\"ServiceName\",\"value\":\"tenant-5fbd498c\"}]}}";
        String body1 = "{\"id\":\"query1\",\"startDate\":\"2020-09-01T02:00:00Z\",\"endDate\":\"2020-09-1T03:00:00Z\"," +
                "\"tenants\":[\"481bf44b-56a1-4a1e-8b39-838c03c1113f\"]," +
                "\"singleTenant\":true," +
                "\"timeRangeName\":\"HOUR_24\"," +
                "\"dimensions\":[{\"metricName\":\"RequestCount\",\"nameSpace\":\"AWS/ApplicationELB\"}," +
                "{\"metricName\":\"HTTPCode_Target_4XX_Count\",\"nameSpace\":\"AWS/ApplicationELB\"}]" +
                ",\"stat\":\"Sum\",\"period\":300,\"topTenants\":true}}";

        body1 = "{\n" +
                "  \"id\": \"albstats\",\n" +
                "  \"timeRangeName\": \"HOUR_2\",\n" +
                "  \"stat\": \"Average\",\n" +
                "  \"dimensions\": [\n" +
                "    {\n" +
                "      \"metricName\": \"HTTPCode_Target_4XX_Count\",\n" +
                "      \"nameSpace\": \"AWS/ApplicationELB\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"topTenants\": false,\n" +
                "  \"statsMap\": false,\n" +
                "  \"tenants\":[\"481bf44b-56a1-4a1e-8b39-838c03c1113f\"],\n" +
                "  \"singleTenant\": true\n" +
                "}";




        Map<String, Object> testMap = new HashMap<String, Object>();
        testMap.put("body", body1);

/*
        APIGatewayProxyResponseEvent responseEvent = ms.queryMetrics(testMap, null);
        System.out.println("Lambda response for body1 query: " + responseEvent.toString());

        if (null != responseEvent) {
            System.exit(0);
        }
*/

        String path = "{\n" +
                "        \"metric\": \"PATH_REQUEST_COUNT\",\n" +
                "        \"timerange\": \"HOUR_24\"\n" +
                "    }";

        Map<String, String> valMap = new HashMap<>();
        valMap.put("metric", "PATH_REQUEST_COUNT");
        valMap.put("timerange", "HOUR_24");
        valMap.put("tenantId", "c18e5a42-c54d-42ef-b108-73f3e9c8f917");

        ms = new MetricService();
        testMap = new HashMap<String, Object>();
        testMap.put("pathParameters", valMap);

        APIGatewayProxyResponseEvent responseEvent = ms.queryAccessLogs(testMap, null);
        System.out.println("Lambda response for pathParam query: " + responseEvent.toString());
        //System.exit(1);


        String body = "{\"id\":\"query1\",\"startDate\":\"2020-08-10T23:00:00Z\",\"endDate\":\"2020-08-11T01:00:00Z\"," +
                //"\"tenants\":[\"tenant-5fbd498c\"]," +
                "\"timeRangeName\":\"TODAY\"," +
                "\"tzOffset\":\"-420\"," +
                "\"dimensions\":[{\"metricName\":\"CPUUtilization\",\"nameSpace\":\"AWS/ECS\"},{\"metricName\":\"MemoryUtilization\",\"nameSpace\":\"AWS/ECS\"}]," +
                "\"stat\":\"Average\"," + "" +
                "\"period\":300," +
                "\"topTenants\":true," +
                "\"statsMap\":false," +
                "\"tenantTaskMaxCapacity\":true}}";
        MetricQuery query1 = Utils.fromJson(body, MetricQuery.class);

        //test metric service
        MetricServiceDAL dal = new MetricServiceDAL();
        List<QueryResult> result = dal.queryMetrics(query1);
        System.out.println("CPUUtilization: " + Utils.toJson(result));

        //Test out a single tenant
/*        body = "{\"id\":\"query1\",\"startDate\":\"2020-06-05T23:00:00Z\",\"endDate\":\"2020-06-06T01:00:00Z\"," +
                "\"singleTenant\":true," +
                "\"tenants\":[\"ae928191-1555-408c-be76-1d0fceabd679\"]," +
                "\"dimensions\":[{\"metricName\":\"CPUUtilization\",\"nameSpace\":\"AWS/ECS\"},{\"metricName\":\"MemoryUtilization\",\"nameSpace\":\"AWS/ECS\"}]," +
                "\"stat\":\"Average\",\"period\":300,\"statsMap\":true}}";*/

        body = "{\n" +
                "  \"id\": \"albstats\",\n" +
                "  \"timeRangeName\": \"DAY_7\",\n" +
                "  \"stat\": \"Average\",\n" +
                "  \"dimensions\": [\n" +
                "    {\n" +
                "      \"metricName\": \"RequestCount\",\n" +
                "      \"nameSpace\": \"AWS/ApplicationELB\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"topTenants\": false,\n" +
                "  \"statsMap\": false,\n" +
                "  \"tenants\":[\"ae928191-1555-408c-be76-1d0fceabd679\"],\n" +
                "  \"singleTenant\": true\n" +
                "}";
        query1 = Utils.fromJson(body, MetricQuery.class);

        //test metric service
        dal = new MetricServiceDAL();
        result = dal.queryTenantMetrics(query1);
        //List<MetricResult> result = dal.queryMetrics(query1);
        System.out.println("CPUUtilization for Single Tenant: " + Utils.toJson(result));

/*        if (null != start1) {
            System.exit(0);
        }*/



        MetricQuery query = new MetricQuery();

        //date range
        Instant start = Instant.ofEpochMilli(new Date().getTime());
        start = Instant.parse("2020-06-05T23:00:00Z");
        query.setStartDate(start);
        Instant endDate = Instant.now();
        endDate = Instant.parse("2020-06-06T01:00:00Z");
        query.setEndDate(endDate);

        //metric
        MetricQuery.Dimension md = new MetricQuery.Dimension("AWS/ECS", "MemoryUtilization");
        List<MetricQuery.Dimension> mdList = new ArrayList<>();
        mdList.add(md);
/*
        query.setNameSpace("AWS/ECS");
        query.getDimensions().add("CPUUtilization");
        query.getDimensions().add("MemoryUtilization");
*/
        query.setDimensions(mdList);
        //query.setStat("SampleCount");
        query.setStat("Average");
/*        query.addTenant("5fbd498c");
        query.addTenant("73ecc895");*/
        query.setPeriod(43200);
        query.setTimeRangeName("HOUR_2");
        System.out.println("Query for AWS/ECS JSON: " + Utils.toJson(query));

      //  result  = dal.queryMetrics(query);
      //  System.out.println("CPUUtilization: " + Utils.toJson(result));


        //metric
        md = new MetricQuery.Dimension("AWS/Usage", "ResourceCount");
        mdList = new ArrayList<>();
        mdList.add(md);
/*
        query.setNameSpace("AWS/ECS");
        query.getDimensions().add("CPUUtilization");
        query.getDimensions().add("MemoryUtilization");
*/
        query.setDimensions(mdList);
        //query.setStat("SampleCount");
        query.setStat("Maximum");
/*        query.addTenant("5fbd498c");
        query.addTenant("73ecc895");*/
        query.setPeriod(43200);
        System.out.println("Query for AWS/Usage JSON: " + Utils.toJson(query));

        result  = dal.queryMetrics(query);
        System.out.println("EC2 ResourceCount: " + Utils.toJson(result));

        //For P90 or aggregrate, need to go through results and calculate values by time slot
        // Build a list for each time entry
        // find the Pxx for each list


        //For Top 10, build a list for each time slot
        // Sort list for top 10.  Return Tenant Id and Value pairs
//        for (Map.Entry<Instant, List<SBValue>> entry : toSortSet.entrySet()) {
//            List<SBValue> sbValues = entry.getValue();
//
//            PriorityQueue heap = new PriorityQueue(sbValues.size());
//            heap.addAll(sbValues);
//            List topElements = (1..k).collect{heap.poll()};
//        }

       // System.out.println("AWS/ECS JSON: " + Utils.toJson(results));

        query = new MetricQuery();
        //metric
        md = new MetricQuery.Dimension("AWS/ApplicationELB", "HTTPCode_Target_4XX_Count");
        mdList = new ArrayList<>();
        mdList.add(md);
        query.setDimensions(mdList);

        /*
        query.setNameSpace("AWS/ApplicationELB");
        query.getDimensions().add("RequestCount");
        query.getDimensions().add("HTTPCode_Target_4XX_Count");

        dset = new LinkedHashSet<MetricQuery.Dimension>();
        dim = new MetricQuery.Dimension("LoadBalancer", "app/tenant-5fbd498c/63f1eedfca597fcc");
        dset.add(dim);
        query.getTenantDimensionMap().put("5fbd498c", dset);

        dset = new LinkedHashSet<MetricQuery.Dimension>();
        dim = new MetricQuery.Dimension("LoadBalancer", "app/tenant-73ecc895/94c0cac0a3c0da46");
        dset.add(dim);
        query.getTenantDimensionMap().put("73ecc895", dset);*/


/*
        Instant start = Instant.ofEpochMilli(new Date().getTime());
        start = Instant.parse("2020-06-04T12:00:00Z");
        Instant endDate = Instant.now();
        endDate = Instant.parse("2020-06-04T23:55:35Z");
*/

        query.setStartDate(start);
        query.setEndDate(endDate);
        //query.setStat("SampleCount");
        query.setStat("Sum");
        query.addTenant("5fbd498c");
        query.addTenant("73ecc895");
        query.setPeriod(60);
        query.setStatsMap(true);
        query.setTopTenants(true);

        System.out.println("Query for ALB JSON: " + Utils.toJson(query));
        result = dal.queryMetrics(query);
/*        for (Metric metric : results) {
            System.out.println(metric);
            //System.out.println(("Tenant: # of values:" + metric.getTimeValMap().size()));
            for (Map.Entry<Instant, MinMaxPriorityQueue<Metric.MetricValue>> entry : metric.getTimeValMap().entrySet()) {
                System.out.println("time: " + entry.getKey() + ", num of values: " + entry.getValue().size());
                Iterator<Metric.MetricValue> itr2=entry.getValue().iterator();
                while (itr2.hasNext()) {
                    System.out.println(itr2.next());
                }
            }
        } */
/*
        System.out.println("ALB Requests: JSON: " + Utils.toJson(result));

        query = new MetricQuery();
        query.setNameSpace("AWS/ApplicationELB");
        query.getDimensions().add("HTTPCode_Target_4XX_Count");

       dset = new LinkedHashSet<MetricQuery.Dimension>();
        dim = new MetricQuery.Dimension("LoadBalancer", "app/tenant-5fbd498c/63f1eedfca597fcc");
        dset.add(dim);
        query.getTenantDimensionMap().put("5fbd498c", dset);

        dset = new LinkedHashSet<MetricQuery.Dimension>();
        dim = new MetricQuery.Dimension("LoadBalancer", "app/tenant-73ecc895/94c0cac0a3c0da46");
        dset.add(dim);
        query.getTenantDimensionMap().put("73ecc895", dset);*/


/*
//        Instant start = Instant.ofEpochMilli(new Date().getTime());
//        start = Instant.parse("2020-06-04T12:00:00Z");
//        Instant endDate = Instant.now();
//        endDate = Instant.parse("2020-06-04T23:55:35Z");


        query.setStartDate(start);
        query.setEndDate(endDate);
        //query.setStat("SampleCount");
        query.setStat("Sum");
        query.addTenant("5fbd498c");
        query.addTenant("73ecc895");
        query.setPeriod(60);
        query.setTopTenants(false);
        query.setStatsMap(true);

        System.out.println("Query for ALB JSON: " + Utils.toJson(query));
        result = dal.queryMetrics(query);
/*        for (Metric metric : results) {
            System.out.println(metric);
            //System.out.println(("Tenant: # of values:" + metric.getTimeValMap().size()));
            for (Map.Entry<Instant, MinMaxPriorityQueue<Metric.MetricValue>> entry : metric.getTimeValMap().entrySet()) {
                System.out.println("time: " + entry.getKey() + ", num of values: " + entry.getValue().size());
                Iterator<Metric.MetricValue> itr2=entry.getValue().iterator();
                while (itr2.hasNext()) {
                    System.out.println(itr2.next());
                }
            }
        }*/

        System.out.println("4XX JSON: " + Utils.toJson(result));
      //  double myPercentile90 = Quantiles.percentiles().index(90).compute(values);
      //  double quartiles = Quantiles.quartiles().index(90).compute(values);

     //   System.out.println("Google p90 " +  myPercentile90);
     //   System.out.println("Google quartiles " +  quartiles);
     //   System.out.println("p90 " +  StatUtils.percentile(values, 90));
       // assertEquals(StatUtils.percentile(values, 95), percentile95.summarize(c), 0.0001);
       // assertEquals(StatUtils.percentile(values, 99), percentile99.summarize(c), 0.0001);
//
       // assertEquals(10, countUnique.summarize(c), 0.0001);

        //Test priority queue
  /*      PriorityQueue p1 = new PriorityQueue(10);
        for (int i = 1; i < 10 ; i++) {
            SBValue val = new SBValue();
            val.value = i * 10;
            val.tenantId = String.format("Tenant%s",i);
            p1.add(val);
        }

        Iterator<SBValue> itr2 = p1.iterator();
        while (itr2.hasNext()) {
            SBValue val1 = itr2.next();
            System.out.println(val1);
        }

        System.out.println("queue: p1 " + p1.toString());
        SBValue val = new SBValue();
        val.value = 15;
        val.tenantId = String.format("Tenant15");
        SBValue sb = (SBValue) p1.peek();
        if (val.value > sb.value) {
            //this needs to go into the Priority queue
            //remove the least item and add this one
            p1.remove();
            p1.add(val);
        }

        itr2 = p1.iterator();
        while (itr2.hasNext()) {
            SBValue val1 = itr2.next();
            System.out.println(val1);
        }

*/

    }


}