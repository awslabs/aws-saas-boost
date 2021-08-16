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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class CloudFormationTemplateParserTest {

    @Test
    public void testReadTemplate() throws Exception {
        System.out.println("testReadTemplate");
        InputStream cloudFormationTemplate = this.getClass().getClassLoader().getResourceAsStream("template.yaml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        LinkedHashMap<String, Object> template = mapper.readValue(cloudFormationTemplate, LinkedHashMap.class);
        LinkedHashMap<String, Map<String, Object>> parameters = (LinkedHashMap<String, Map<String, Object>>) template.get("Parameters");

        assertEquals("Template has 3 parameters", parameters.size(), 3);
        for (String parameterName : Arrays.asList("RequiredStringParameter", "DefaultStringParameter", "NumericParameter")) {
            assertTrue(parameterName + " parameter exists", parameters.containsKey(parameterName));
        }

        LinkedHashMap<String, Object> properties = (LinkedHashMap<String, Object>) parameters.get("RequiredStringParameter");
        assertFalse("Required parameter does not have a default value", properties.containsKey("Default"));

        properties = (LinkedHashMap<String, Object>) parameters.get("DefaultStringParameter");
        assertTrue("Default parameter has default value", properties.containsKey("Default"));
        assertEquals("Default value", "foobar", properties.get("Default"));
    }
}
