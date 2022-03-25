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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

public class ApplicationServicesEcrMacroTest {

    @Test(expected = IllegalArgumentException.class)
    public void testResourceNameNullServiceName() {
        ApplicationServicesEcrMacro.ecrResourceName(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResourceNameEmptyServiceName() {
        ApplicationServicesEcrMacro.ecrResourceName("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResourceNameBlankServiceName() {
        ApplicationServicesEcrMacro.ecrResourceName(" ");
    }

    @Test
    public void testResourceName() {
        String serviceName = "foo";
        String expected = "foo";
        String actual = ApplicationServicesEcrMacro.ecrResourceName(serviceName);
        assertEquals(expected, actual);

        serviceName = "Foo";
        expected = "Foo";
        actual = ApplicationServicesEcrMacro.ecrResourceName(serviceName);
        assertEquals(expected, actual);

        serviceName = "Foo Bar";
        expected = "FooBar";
        actual = ApplicationServicesEcrMacro.ecrResourceName(serviceName);
        assertEquals(expected, actual);

        serviceName = "Foo_Bar";
        expected = "FooBar";
        actual = ApplicationServicesEcrMacro.ecrResourceName(serviceName);
        assertEquals(expected, actual);

        serviceName = "Foo-Bar";
        expected = "FooBar";
        actual = ApplicationServicesEcrMacro.ecrResourceName(serviceName);
        assertEquals(expected, actual);
    }

    @Test
    public void testHandleRequest() throws Exception {
        try (InputStream json = Files.newInputStream(Path.of(this.getClass().getClassLoader().getResource("template.json").toURI()))) {
            LinkedHashMap<String, Object> template = Utils.fromJson(json, LinkedHashMap.class);

            ApplicationServicesEcrMacro macro = new ApplicationServicesEcrMacro();

            // Blank ApplicationServices parameter should return the same template
            Map<String, Object> response = macro.handleRequest(buildEvent(template), null);
            assertTrue(response.containsKey("fragment"));
            LinkedHashMap<String, Object> modifiedTemplate = (LinkedHashMap<String, Object>) response.get("fragment");

            assertEquals("Size unequal", template.size(), modifiedTemplate.size());
            for (Map.Entry<String, Object> entry : template.entrySet()) {
                assertEquals("Value mismatch for '" + entry.getKey() + "'", template.get(entry.getKey()), modifiedTemplate.get(entry.getKey()));
            }

            // No ApplicationServices parameter should return failure
            Map<String, Object> applicationServices = (Map<String, Object>) ((LinkedHashMap<String, Object>) template.get("Parameters")).get("ApplicationServices");
            ((LinkedHashMap<String, Object>) template.get("Parameters")).remove("ApplicationServices");
            response = macro.handleRequest(buildEvent(template), null);
            assertEquals("No ApplicationServices parameter is an error", "FAILURE", response.get("status"));

            // List of ApplicationServices should return new resources in the fragment
            applicationServices.put("Default", "foo, Bar,baz Oole");
            ((LinkedHashMap<String, Object>) template.get("Parameters")).put("ApplicationServices", applicationServices);
            response = macro.handleRequest(buildEvent(template), null);
            assertTrue(response.containsKey("fragment"));
            modifiedTemplate = (LinkedHashMap<String, Object>) response.get("fragment");
            LinkedHashMap<String, Object> resources = (LinkedHashMap<String, Object>) modifiedTemplate.get("Resources");
            assertEquals(9, resources.size());
            assertTrue(resources.containsKey("foo"));
            assertTrue(resources.containsKey("ImageEventRulefoo"));
            assertTrue(resources.containsKey("ImageEventPermissionfoo"));
            assertTrue(resources.containsKey("Bar"));
            assertTrue(resources.containsKey("ImageEventRuleBar"));
            assertTrue(resources.containsKey("ImageEventPermissionBar"));
            assertTrue(resources.containsKey("bazOole"));
            assertTrue(resources.containsKey("ImageEventRulebazOole"));
            assertTrue(resources.containsKey("ImageEventPermissionbazOole"));

            // There should be a single tag for Name and it should have the non-modified application service name
            // for its Value
            List<Map<String, Object>> tags = (List<Map<String, Object>>) ((LinkedHashMap<String, Object>) ((LinkedHashMap<String, Object>) resources.get("bazOole")).get("Properties")).get("Tags");
            assertEquals(1, tags.size());
            assertEquals("baz Oole", tags.get(0).get("Value"));
        }
    }

    static Map<String, Object> buildEvent(LinkedHashMap<String, Object> template) {
        Map<String, Object> event = new HashMap<>();
        event.put("requestId", UUID.randomUUID().toString());
        event.put("templateParameterValues", templateParameters(template));
        event.put("fragment", template);
        return event;
    }

    static LinkedHashMap<String, Object> templateParameters(LinkedHashMap<String, Object> template) {
        LinkedHashMap<String, Object> templateParameters = new LinkedHashMap<>();
        LinkedHashMap<String, Object> parameters = (LinkedHashMap<String, Object>) template.get("Parameters");
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            templateParameters.put(parameter.getKey(), ((Map<String, Object>) parameter.getValue()).get("Default"));
        }
        return templateParameters;
    }
}