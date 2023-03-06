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

import org.junit.Test;
import org.mockito.ArgumentMatcher;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackResourceRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackResourceResponse;
import software.amazon.awssdk.services.cloudformation.model.StackResourceDetail;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.HostedZoneConfig;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameResponse;

import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class OnboardingServiceTest {

    @Test
    public void cidrBlockPrefixTest() {
        String cidr = "10.255.32.3";
        String prefix = cidr.substring(0, cidr.indexOf(".", cidr.indexOf(".") + 1));
        assertTrue("10.255".equals(prefix));
    }

//    @Test
//    public void testBatchIteration() {
//        final int maxBatchSize = 50;
//        List<String> objects = new ArrayList<>();
//        for (int i = 0; i < (maxBatchSize * 3.7); i++) {
//            objects.add("Item " + i);
//        }
//        System.out.println("Objects contains " + objects.size() + " items");
//        int batchStart = 0;
//        int batchEnd = 0;
//        int loop = 0;
//        while (batchEnd < objects.size()) {
//            batchStart = batchEnd;
//            batchEnd += maxBatchSize;
//            if (batchEnd > objects.size()) {
//                batchEnd = objects.size();
//            }
//            List<String> batch = objects.subList(batchStart, batchEnd);
//            System.out.println(String.format("Loop %d. Start %d End %d", ++loop, batchStart, batchEnd));
//            batch.forEach(System.out::println);
//        }
//    }

    @Test
    public void testSubdomainCheck() {
        String domainName = "saas-example.com";
        String subdomain = "tenant2";
        String existingSubdomain = "tenant2.saas-example.com.";
        existingSubdomain = existingSubdomain.substring(0, existingSubdomain.indexOf(domainName) - 1);
        assertTrue("Subdomain Exists", subdomain.equalsIgnoreCase(existingSubdomain));
    }

    @Test
    public void testGetPathPriority() {
        InputStream json = getClass().getClassLoader().getResourceAsStream("appConfig.json");
        Map<String, Object> appConfig = Utils.fromJson(json, LinkedHashMap.class);

        Map<String, Object> applicationServices = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            applicationServices.put(String.format("Service%02d", i), Map.of(
                    "public", Boolean.TRUE,
                    "path", Utils.randomString((i * 10))
            ));
        }
        appConfig.put("services", applicationServices);

        Map<String, Integer> expected = Map.of(
                "Service01", 10,
                "Service02", 9,
                "Service03", 8,
                "Service04", 7,
                "Service05", 6,
                "Service06", 5,
                "Service07", 4,
                "Service08", 3,
                "Service09", 2,
                "Service10", 1
        );

        Map<String, Integer> actual = OnboardingService.getPathPriority(appConfig);

        assertEquals("Size unequal", expected.size(), actual.size());
        expected.keySet().stream().forEach(key -> {
            assertEquals("Value mismatch for '" + key + "'", expected.get(key), actual.get(key));
        });
    }
}