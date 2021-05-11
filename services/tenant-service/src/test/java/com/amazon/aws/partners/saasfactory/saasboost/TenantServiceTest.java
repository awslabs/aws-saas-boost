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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.*;

import static org.junit.Assert.*;

public class TenantServiceTest {

    private static String tenantId;
    private static HashMap<String, String> resources;
    private static Map<String, Object> event;
    private static Map<String, Object> eventDetail;

    @BeforeClass
    public static void setup() throws Exception {
        tenantId = "d1c1e3cc-962f-4f03-b4a8-d8a7c1f986c3";

        resources = new HashMap<>();
        resources.put("alb", "http://my.alb2.com");
        resources.put("ecs", "http://new.ecs2.com");
        resources.put("rds", "http://new.rds2.com");

        eventDetail = new HashMap<>();
        eventDetail.put("tenantId", tenantId);
        eventDetail.put("resources", new ObjectMapper().writeValueAsString(resources));
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
    public void parseTenantUpdateResourcesEventTest() throws Exception {
        Tenant expected = new Tenant();
        expected.setId(UUID.fromString(tenantId));
        expected.setResources(resources);

        Tenant tenant = TenantService.parseTenantUpdateResourcesEvent(event);

        assertEquals(expected.getResources().get("alb"), tenant.getResources().get("alb"));
        assertEquals(expected.getResources().get("ecs"), tenant.getResources().get("ecs"));
        assertEquals(expected.getResources().get("rds"), tenant.getResources().get("rds"));
    }

    @Test
    public void testQuoteJson() throws Exception {
        System.out.println("testQuoteJson");
        Tenant tenant = new Tenant();
        tenant.setId(UUID.fromString(tenantId));
        tenant.setComputeSize("L");
        tenant.setMinCount(1);
        tenant.setMaxCount(2);
        tenant.setCpu(2048);
        tenant.setMemory(4096);

        String json = Utils.toJson(tenant);
        String quoted = Utils.escapeJson(json);
        System.out.println(json);
        System.out.println();
        System.out.println(quoted);
        System.out.println();
        System.out.println(Utils.toQuotedJson(tenant));

        //Map<String, Object> m1 = Utils.fromQuotedJson(quoted, HashMap.class);
        //System.out.println(m1);

        String decoded = Utils.unescapeJson(quoted);
        System.out.println(decoded);
        Map<String, Object> m = Utils.fromJson(decoded, HashMap.class);
        System.out.println(m);
    }
}
