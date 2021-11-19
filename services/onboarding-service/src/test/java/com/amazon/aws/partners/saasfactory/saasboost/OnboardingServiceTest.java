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

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class OnboardingServiceTest {

    @Test
    public void cidrBlockPrefixTest() {
        String cidr = "10.255.32.3";
        String prefix = cidr.substring(0, cidr.indexOf(".", cidr.indexOf(".") + 1));
        assertTrue("10.255".equals(prefix));
    }

    @Test
    public void parameterStoreBatchTest() {
        Map<String, String> SAAS_BOOST_PARAMS = Stream
                .of(
                        new AbstractMap.SimpleEntry<String, String>("1", ""),
                        new AbstractMap.SimpleEntry<String, String>("2", ""),
                        new AbstractMap.SimpleEntry<String, String>("3", ""),
                        new AbstractMap.SimpleEntry<String, String>("4", ""),
                        new AbstractMap.SimpleEntry<String, String>("5", ""),
                        new AbstractMap.SimpleEntry<String, String>("6", ""),
                        new AbstractMap.SimpleEntry<String, String>("7", ""),
                        new AbstractMap.SimpleEntry<String, String>("8", ""),
                        new AbstractMap.SimpleEntry<String, String>("9", ""),
                        new AbstractMap.SimpleEntry<String, String>("10", ""),
                        new AbstractMap.SimpleEntry<String, String>("11", ""),
                        new AbstractMap.SimpleEntry<String, String>("12", ""),
                        new AbstractMap.SimpleEntry<String, String>("13", ""),
                        new AbstractMap.SimpleEntry<String, String>("14", ""),
                        new AbstractMap.SimpleEntry<String, String>("15", ""),
                        new AbstractMap.SimpleEntry<String, String>("16", ""),
                        new AbstractMap.SimpleEntry<String, String>("17", ""),
                        new AbstractMap.SimpleEntry<String, String>("18", ""),
                        new AbstractMap.SimpleEntry<String, String>("19", ""),
                        new AbstractMap.SimpleEntry<String, String>("20", ""),
                        new AbstractMap.SimpleEntry<String, String>("21", "")
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<String> batch = new ArrayList<>();
        Iterator<String> it = SAAS_BOOST_PARAMS.keySet().iterator();
        while (it.hasNext()) {
            if (batch.size() < 10) {
                batch.add(it.next());
                if (!it.hasNext()) {
                    System.out.println("Call getParameters " + Arrays.deepToString(batch.toArray()));
                    break;
                }
            } else {
                System.out.println("Call getParameters " + Arrays.deepToString(batch.toArray()));
                batch.clear();
            }
        }
    }

    @Test
    public void testBatchIteration() {
        final int maxBatchSize = 50;
        List<String> objects = new ArrayList<>();
        for (int i = 0; i < (maxBatchSize * 3.7); i++) {
            objects.add("Item " + i);
        }
        System.out.println("Objects contains " + objects.size() + " items");
        int batchStart = 0;
        int batchEnd = 0;
        int loop = 0;
        while (batchEnd < objects.size()) {
            batchStart = batchEnd;
            batchEnd += maxBatchSize;
            if (batchEnd > objects.size()) {
                batchEnd = objects.size();
            }
            List<String> batch = objects.subList(batchStart, batchEnd);
            System.out.println(String.format("Loop %d. Start %d End %d", ++loop, batchStart, batchEnd));
            batch.forEach(System.out::println);
        }
    }

    @Test
    public void testSubdomainCheck() {
        String domainName = "saas-example.com";
        String subdomain = "tenant2";
        String existingSubdomain = "tenant2.saas-example.com.";
        //System.out.println(existingSubdomain.substring(existingSubdomain.indexOf(domainName)));
        existingSubdomain = existingSubdomain.substring(0, existingSubdomain.indexOf(domainName) - 1);
        //System.out.println(existingSubdomain);
        assertTrue("Subdomain Exists", subdomain.equalsIgnoreCase(existingSubdomain));
    }
}