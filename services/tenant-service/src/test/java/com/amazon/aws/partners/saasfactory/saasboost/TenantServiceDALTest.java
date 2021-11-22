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

import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TenantServiceDALTest {

    private static UUID tenantId;
    private static HashMap<String, Tenant.Resource> resources;

    @BeforeClass
    public static void setup() throws Exception {
        tenantId = UUID.fromString("d1c1e3cc-962f-4f03-b4a8-d8a7c1f986c3");

        resources = new HashMap<>();
        resources.put("VPC", new Tenant.Resource("vpc-0f28a79bbbcce70bb", "arn:aws:ec2:us-west-2:914245659875:vpc/vpc-0f28a79bbbcce70bb", "https://us-west-2.console.aws.amazon.com/vpc/home?region=us-west-2#vpcs:search=vpc-0f28a79bbbcce70bb"));
        resources.put("ECS_CLUSTER", new Tenant.Resource("sb-dev1-tenant-8541aceb", "arn:aws:ecs:us-west-2:914245659875:cluster/sb-dev1-tenant-8541aceb", "https://us-west-2.console.aws.amazon.com/ecs/home#/clusters/sb-dev1-tenant-8541aceb"));
        resources.put("PRIVATE_SUBNET_A", new Tenant.Resource("subnet-03a78eb00d87a0bbf", "arn:aws:ec2:us-west-2:914245659875:subnet/subnet-03a78eb00d87a0bbf", "https://us-west-2.console.aws.amazon.com/vpc/home?region=us-west-2#SubnetDetails:subnetId=subnet-03a78eb00d87a0bbf"));

//        {
//            "id": "8541aceb-42dd-4eb3-a6d7-6aa5e4d76bea",
//                "active": true,
//                "resources": {
//            "VPC": {
//                "name": "vpc-0f28a79bbbcce70bb",
//                        "arn": "arn:aws:ec2:us-west-2:914245659875:vpc/vpc-0f28a79bbbcce70bb",
//                        "consoleUrl": "https://us-west-2.console.aws.amazon.com/vpc/home?region=us-west-2#vpcs:search=vpc-0f28a79bbbcce70bb"
//            },
//            "PRIVATE_SUBNET_B": {
//                "name": "subnet-0879274ebeb003611",
//                        "arn": "arn:aws:ec2:us-west-2:914245659875:subnet/subnet-0879274ebeb003611",
//                "consoleUrl": "https://us-west-2.console.aws.amazon.com/vpc/home?region=us-west-2#SubnetDetails:subnetId=subnet-0879274ebeb003611"
//            },
//            "ECS_CLUSTER": {
//                "name": "sb-dev1-tenant-8541aceb",
//                        "arn": "arn:aws:ecs:us-west-2:914245659875:cluster/sb-dev1-tenant-8541aceb",
//                        "consoleUrl": "https://us-west-2.console.aws.amazon.com/ecs/home#/clusters/sb-dev1-tenant-8541aceb"
//            },
//            "CLOUDFORMATION": {
//                "name": "sb-dev1-tenant-8541aceb",
//                        "arn": "arn:aws:cloudformation:us-west-2:914245659875:stack/sb-dev1-tenant-8541aceb/1aa8fe70-49af-11ec-b0f9-06febf88c4a1",
//                        "consoleUrl": "https://us-west-2.console.aws.amazon.com/cloudformation/home?region=us-west-2#/stacks/stackinfo?filteringStatus=active&viewNested=true&hideStacks=false&stackId=arn:aws:cloudformation:us-west-2:914245659875:stack/sb-dev1-tenant-8541aceb/1aa8fe70-49af-11ec-b0f9-06febf88c4a1"
//            },
//            "ECS_SECURITY_GROUP": {
//                "name": "sg-01741d50bfe787d2c",
//                        "arn": "arn:aws:ec2:us-west-2:914245659875:security-group/sg-01741d50bfe787d2c",
//                        "consoleUrl": "https://us-west-2.console.aws.amazon.com/ec2/v2/home?region=us-west-2#SecurityGroup:groupId=sg-01741d50bfe787d2c"
//            },
//            "PRIVATE_SUBNET_A": {
//                "name": "subnet-03a78eb00d87a0bbf",
//                        "arn": "arn:aws:ec2:us-west-2:914245659875:subnet/subnet-03a78eb00d87a0bbf",
//                        "consoleUrl": "https://us-west-2.console.aws.amazon.com/vpc/home?region=us-west-2#SubnetDetails:subnetId=subnet-03a78eb00d87a0bbf"
//            }
//        },
//            "tier": "default",
//                "created": "2021-11-20T03:07:52.106186",
//                "onboarding": "created",
//                "name": "tenant one",
//                "modified": "2021-11-22T17:36:11.818848"
//        }
    }

    @Test
    public void testToAttributeValueMap() {
        Tenant tenant = new Tenant();
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime modified = LocalDateTime.now();

        tenant.setId(tenantId);
        tenant.setActive(Boolean.TRUE);
        tenant.setCreated(created);
        tenant.setModified(modified);
        tenant.setTier("default");
        tenant.setName("Test Tenant");
        tenant.setOnboardingStatus("succeeded");
        tenant.setPlanId("Billing Plan");
        tenant.setSubdomain("test-tenant");
        tenant.setResources(resources);

        Map<String, AttributeValue> expected = new HashMap<>();
        expected.put("id", AttributeValue.builder().s(tenantId.toString()).build());
        expected.put("active", AttributeValue.builder().bool(Boolean.TRUE).build());
        expected.put("created", AttributeValue.builder().s(created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("modified", AttributeValue.builder().s(modified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("tier", AttributeValue.builder().s("default").build());
        expected.put("name", AttributeValue.builder().s("Test Tenant").build());
        expected.put("onboarding", AttributeValue.builder().s("succeeded").build());
        expected.put("subdomain", AttributeValue.builder().s("test-tenant").build());
        expected.put("planId", AttributeValue.builder().s("Billing Plan").build());
        expected.put("resources", AttributeValue.builder().m(resources.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> AttributeValue.builder().m(
                                Map.of(
                                        "name", AttributeValue.builder().s(entry.getValue().getName()).build(),
                                        "arn", AttributeValue.builder().s(entry.getValue().getArn()).build(),
                                        "consoleUrl", AttributeValue.builder().s(entry.getValue().getConsoleUrl()).build()
                                )).build()
                ))).build());

        Map<String, AttributeValue> actual = TenantServiceDAL.toAttributeValueMap(tenant);
        System.out.println(Utils.toJson(actual));
        assertEquals("Size unequal", expected.size(), actual.size());
        expected.keySet().stream().forEach((key) -> {
            assertEquals("Value mismatch for '" + key + "'", expected.get(key), actual.get(key));
        });
    }
}
