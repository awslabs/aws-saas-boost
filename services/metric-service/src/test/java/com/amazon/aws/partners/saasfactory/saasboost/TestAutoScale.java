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
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClientBuilder;
import software.amazon.awssdk.services.applicationautoscaling.model.*;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

import java.util.ArrayList;
import java.util.List;

public class TestAutoScale {

   // static ApplicationAutoScalingClient aaClient = (ApplicationAutoScalingClient) ApplicationAutoScalingClient.builder() ;
    static ApplicationAutoScalingClient aaClient1 = ApplicationAutoScalingClient.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .build();



    public static void main(String args[]) {

        String nextToken = null;
        final ServiceNamespace ns = ServiceNamespace.ECS;
        final ScalableDimension ecsTaskCount = ScalableDimension.ECS_SERVICE_DESIRED_COUNT;
        List<String> resourceIds = new ArrayList<>();
        resourceIds.add("service/tenant-5fbd498c/tenant-5fbd498c");
        resourceIds.add("service/tenant-73ecc895/tenant-73ecc895");

        // Verify that the target was created
        do {
            DescribeScalableTargetsRequest dscRequest = DescribeScalableTargetsRequest.builder()
                    .serviceNamespace(ns)
                    .scalableDimension(ecsTaskCount)
                    .resourceIds(resourceIds)
                    .nextToken(nextToken)
                    .build();
            try {
                long start = System.currentTimeMillis();
                DescribeScalableTargetsResponse resp = aaClient1.describeScalableTargets(dscRequest);
                nextToken = resp.nextToken();
                System.out.println("DescribeScalableTargets result in " + (System.currentTimeMillis() - start) + " ms, : ");
                System.out.println(resp);
                List<ScalableTarget> targets = resp.scalableTargets();
                for (ScalableTarget target : targets) {
                    ScalableDimension dim = target.scalableDimension();
                    String[] id = target.resourceId().split("/");
                    System.out.println("Dim: " + dim + ", MaxCapacity: " + target.maxCapacity() + ", resource: " +  id[2]);
                }
                //System.out.println(resp.scalableTargets());
            } catch (Exception e) {
                System.err.println("Unable to describe scalable target: ");
                System.err.println(e.getMessage());
            }
        } while (nextToken != null && !nextToken.isEmpty());

        System.out.println();
    }

}


