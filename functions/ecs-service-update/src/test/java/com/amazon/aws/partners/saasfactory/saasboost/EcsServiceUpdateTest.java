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

import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class EcsServiceUpdateTest {

    @Test
    public void nestedMapTest() {
        Map<String, Object> data = new HashMap<>();
        data.put("inputArtifacts", new ArrayList());
        data.put("outputArtifacts", new ArrayList());
        Map<String, Object> actionConfiguration = new HashMap<>();
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("FunctionName", "saas-boost-update-ecs-us-west-2");
        configuration.put("UserParameters", "{\"cluster\":\"tenant-5ff91f82\",\"service\":\"arn:aws:ecs:us-west-2:914245659875:service/tenant-5ff91f82\",\"desiredCount\":1}");
        actionConfiguration.put("configuration", configuration);
        data.put("actionConfiguration", actionConfiguration);

        String json = (String) ((Map) ((Map) data.get("actionConfiguration")).get("configuration")).get("UserParameters");
        System.out.println(json);
//        data.values()
//                .stream()
//                .filter(obj -> obj instanceof Map)
//                .map(obj -> (Map) obj)
//                .filter(obj -> obj.containsKey("actionConfiguration"))
//                .filter(obj -> obj.containsKey("configuration"))
//                .forEach(obj -> System.out.println(obj + " instance of " + obj.getClass()));
//                .map(obj -> ((Map) obj).entrySet())
//                .filter(map -> map.getKey().equals("actionConfiguration"))
//                .map(Map.Entry::getValue)
//                .flatMap(m -> ((Map.Entry) m).entrySet().stream())
//                .stream()
//                .filter(map -> map.getKey().equals("actionConfiguration"))
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
//                .entrySet()
//                .stream()
//                .filter(map -> map.getKey().equals("configuration"))
//                .forEach(System.out::println);


//        for (Map.Entry<String, Object> value : data.entrySet()) {
//
//        }
//        data.get("actionConfiguration").get("configuration").get("UserParameters");
    }

}