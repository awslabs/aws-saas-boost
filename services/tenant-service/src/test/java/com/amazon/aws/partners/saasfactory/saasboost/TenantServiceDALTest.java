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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TenantServiceDALTest {

    private static UUID tenantId;
    private static HashMap<String, String> attributes;
    private static HashMap<String, Tenant.Resource> resources;

    @BeforeClass
    public static void setup() throws Exception {
        tenantId = UUID.fromString("d1c1e3cc-962f-4f03-b4a8-d8a7c1f986c3");

        attributes = new HashMap<>();
        attributes.put("User Defined Key", "User Defined Value");

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
        tenant.setBillingPlan("Billing Plan");
        tenant.setHostname("test-tenant.saas-example.com");
        tenant.setSubdomain("test-tenant");
        tenant.setAttributes(attributes);
        tenant.setResources(resources);

        Map<String, AttributeValue> expected = new HashMap<>();
        expected.put("id", AttributeValue.builder().s(tenantId.toString()).build());
        expected.put("active", AttributeValue.builder().bool(Boolean.TRUE).build());
        expected.put("created", AttributeValue.builder().s(created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("modified", AttributeValue.builder().s(modified.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).build());
        expected.put("tier", AttributeValue.builder().s("default").build());
        expected.put("name", AttributeValue.builder().s("Test Tenant").build());
        expected.put("onboarding_status", AttributeValue.builder().s("succeeded").build());
        expected.put("hostname", AttributeValue.builder().s("test-tenant.saas-example.com").build());
        expected.put("subdomain", AttributeValue.builder().s("test-tenant").build());
        expected.put("billing_plan", AttributeValue.builder().s("Billing Plan").build());
        expected.put("attributes", AttributeValue.builder().m(attributes.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> AttributeValue.builder().s(
                                String.valueOf(entry.getValue())
                        ).build()
                ))
        ).build());
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
                ))
        ).build());

        Map<String, AttributeValue> actual = TenantServiceDAL.toAttributeValueMap(tenant);

        // DynamoDB marshalling
        assertEquals("Size unequal", expected.size(), actual.size());
        expected.keySet().stream().forEach(key -> {
            assertEquals("Value mismatch for '" + key + "'", expected.get(key), actual.get(key));
        });

        // Ignore read only properties from JSON serialization
        Collection<String> ignoreProperties = new HashSet<>();
        try {
            for (PropertyDescriptor reflection : Introspector.getBeanInfo(Tenant.class).getPropertyDescriptors()) {
                Method getter = reflection.getReadMethod();
                if (getter != null) {
                    JsonProperty jsonProperty = getter.getDeclaredAnnotation(JsonProperty.class);
                    if (jsonProperty != null && jsonProperty.access() == JsonProperty.Access.READ_ONLY) {
                        ignoreProperties.add(reflection.getName());
                    }
                }
            }
        } catch (IntrospectionException ie) {
            System.err.println(Utils.getFullStackTrace(ie));
        }
        // Have we reflected all class properties we serialize for API calls in DynamoDB?
        Map<String, Object> json = Utils.fromJson(Utils.toJson(tenant), LinkedHashMap.class);
        json.keySet().stream()
                .filter(key -> !ignoreProperties.contains(key))
                .map(key -> Utils.toSnakeCase(key))
                .forEach(key -> {
            assertTrue("Class property '" + key + "' does not exist in DynamoDB attribute map", actual.containsKey(key));
        });
    }
}
