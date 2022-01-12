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

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

// Note that these tests will only work if you run them from Maven or if you add
// the AWS_REGION environment variable to your IDE's configuration settings
public class SaaSBoostInstallTest {

    /*
    private static LinkedHashMap<String, Object> template;

    @BeforeClass
    public static void initTemplate() {
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        LinkedHashMap<String, Object> requiredStringParameter = new LinkedHashMap<>();
        LinkedHashMap<String, Object> defaultStringParameter = new LinkedHashMap<>();
        LinkedHashMap<String, Object> numericParameter = new LinkedHashMap<>();
        requiredStringParameter.put("Description", "String parameter with no default");
        requiredStringParameter.put("Type", "String");
        parameters.put("RequiredStringParameter", requiredStringParameter);
        defaultStringParameter.put("Description", "String parameter with default value");
        defaultStringParameter.put("Type", "String");
        defaultStringParameter.put("Default", "foobar");
        parameters.put("DefaultStringParameter", defaultStringParameter);
        numericParameter.put("Description", "Number parameter with default value");
        numericParameter.put("Type", "Number");
        numericParameter.put("Default", 0);
        parameters.put("NumericParameter", numericParameter);

        LinkedHashMap<String, Object> resources = new LinkedHashMap<>();
        LinkedHashMap<String, Object> resource = new LinkedHashMap<>();
        LinkedHashMap<String, Object> resourceProperties = new LinkedHashMap<>();
        resourceProperties.put("Name", "/saas-boost/FOOBAR");
        resourceProperties.put("Type", "String");
        resourceProperties.put("Value", "!Ref DefaultStringParameter");
        resource.put("Type", "AWS::SSM::Parameter");
        resource.put("Properties", resourceProperties);
        resources.put("Resource", resource);

        LinkedHashMap<String, Object> outputs = new LinkedHashMap<>();
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("Description", "Output of the requried string parameter value");
        output.put("Value", "!Ref RequiredStringParameter");
        outputs.put("RequiredStringParameterOutput", output);

        template = new LinkedHashMap<>();
        template.put("AWSTemplateFormatVersion","2010-09-09");
        template.put("Description", "Fake CloudFormation template for testing YAML parser");
        template.put("Parameters", parameters);
        template.put("Resources", resources);
        template.put("Outputs", outputs);
    }
    */

    @After
    public void resetStdIn() {
        System.setIn(System.in);
    }

    @Test
    public void testGetCloudFormationParameterMap() throws Exception {
        // The input map represents the existing CloudFormation parameter values.
        // These will either be the template defaults, or they will be the parameter
        // values read from a created stack with the describeStacks call.
        // We'll pretend that the RequiredStringParameter parameter is newly added
        // to the template on disk so the user should be prompted for a value
        Map<String, String> input = new LinkedHashMap<>();
        input.put("DefaultStringParameter", "foobar");
        input.put("NumericParameter", "1"); // Let's pretend that we overwrote the default the first time around

        // Fill up standard input with a response for the Keyboard class
        System.setIn(new ByteArrayInputStream(("keyboard input" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8)));

        Path cloudFormationTemplate = Path.of(this.getClass().getClassLoader().getResource("template.yaml").toURI());
        Map<String, String> actual = SaaSBoostInstall.getCloudFormationParameterMap(cloudFormationTemplate, input);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("RequiredStringParameter", "keyboard input");
        expected.put("DefaultStringParameter", "foobar");
        expected.put("NumericParameter", "1");

        assertEquals("Template has 3 parameters", expected.size(), actual.size());
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getKey() + " equals " + entry.getValue(), entry.getValue(), actual.get(entry.getKey()));
        }
    }
}
