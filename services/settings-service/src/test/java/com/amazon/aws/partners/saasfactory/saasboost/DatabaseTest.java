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
import org.junit.Ignore;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.util.*;
import java.util.stream.Collectors;

public class DatabaseTest {

    @Ignore
    @Test
    public void testEnumSerialization() throws Exception {
        EnumSet<Database.RDS_ENGINE> engines = EnumSet.allOf(Database.RDS_ENGINE.class);
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(engines);
        System.out.println(json);

        System.out.println();

        EnumMap<Database.RDS_ENGINE, Map<String, String>> enginesMap = new EnumMap<>(Database.RDS_ENGINE.class);
        for (Database.RDS_ENGINE engine : Database.RDS_ENGINE.values()) {
            Map<String, String> details = new HashMap<>();
            details.put("name", engine.getEngine());
            details.put("description", engine.getDescription());
            enginesMap.put(engine, details);
        }
        json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(enginesMap);
        System.out.println(json);

        Map<String, String> os = Arrays.stream(OperatingSystem.values()).collect(Collectors.toMap(OperatingSystem::name, OperatingSystem::getDescription));
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(os));
    }

//    provisionedTenantsWithDefaultSettings = getTenantsResponse
//            .stream()
//            .map(tenants -> tenants.get("id"))
//            .collect(Collectors.toCollection(ArrayList::new));
}