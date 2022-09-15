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
}
