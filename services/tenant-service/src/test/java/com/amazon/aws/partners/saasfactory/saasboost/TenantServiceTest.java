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

import org.junit.BeforeClass;
import org.junit.Test;
import java.util.*;

import static org.junit.Assert.*;

public class TenantServiceTest {

    private static String tenantId;
    private static HashMap<String, Tenant.Resource> resources;
    private static Map<String, Object> event;
    private static Map<String, Object> eventDetail;

    @BeforeClass
    public static void setup() throws Exception {
        tenantId = "d1c1e3cc-962f-4f03-b4a8-d8a7c1f986c3";

        resources = new HashMap<>();
        resources.put("VPC", new Tenant.Resource("vpc-0f28a79bbbcce70bb",
                "arn:aws:ec2:us-east-1:111111111:vpc/vpc-0f28a79bbbcce70bb",
                "https://us-east-1.console.aws.amazon.com/vpc/home?region=us-east-1#vpcs:search=vpc-0f28a79bbbcce70bb"));
        resources.put("ECS_CLUSTER", new Tenant.Resource("sb-dev1-tenant-8541aceb",
                "arn:aws:ecs:us-east-1:111111111:cluster/sb-dev1-tenant-8541aceb",
                "https://us-east-1.console.aws.amazon.com/ecs/home#/clusters/sb-dev1-tenant-8541aceb"));
        resources.put("PRIVATE_SUBNET_A", new Tenant.Resource("subnet-03a78eb00d87a0bbf",
                "arn:aws:ec2:us-east-1:111111111:subnet/subnet-03a78eb00d87a0bbf",
                "https://us-east-1.console.aws.amazon.com/vpc/home?region=us-east-1#SubnetDetails:subnetId=subnet-03a78eb00d87a0bbf"));

        eventDetail = new HashMap<>();
        eventDetail.put("tenantId", tenantId);
        eventDetail.put("resources", Utils.toJson(resources));
        eventDetail.put("timestamp", "1597192178924");

        event = new HashMap<>();
        event.put("version", "0");
        event.put("id", "b3f78abe-24d7-955d-f063-e0255d74e348");
        event.put("detail-type", "Tenant Update Resources");
        event.put("source", "saas-boost");
        event.put("account", "111111111111");
        event.put("time", "2020-08-12T00:29:39Z");
        event.put("region", "us-west-2");
        event.put("resources", new ArrayList<String>());
        event.put("detail", eventDetail);
    }

    @Test
    public void testFromTenantResourcesChangedEvent() {
        Map<String, Tenant.Resource> expected = resources;
        Map<String, Tenant.Resource> actual = TenantService.fromTenantResourcesChangedEvent(event);

        assertEquals("Size unequal", expected.size(), actual.size());
        expected.keySet().stream().forEach((key) -> {
            assertEquals("Value mismatch for '" + key + "'", expected.get(key), actual.get(key));
        });
    }

}
