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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

// Note that these tests will only work if you run them from Maven or if you add
// the AWS_REGION and SAAS_BOOST_ENV environment variables to your IDE's configuration settings
public class SettingsDataAccessLayerTest {

    private static String env;

    public SettingsDataAccessLayerTest() {
        if (System.getenv("AWS_REGION") == null || System.getenv("SAAS_BOOST_ENV") == null) {
            throw new IllegalStateException("Missing required environment variables for tests!");
        }
    }

    @BeforeAll
    public static void setup() {
        env = System.getenv("SAAS_BOOST_ENV");
    }

    @Test
    public void testFromParameterStore() {
        String settingName = "SAAS_BOOST_BUCKET";
        String parameterName = SettingsDataAccessLayer.PARAMETER_STORE_PREFIX + settingName;
        String parameterValue = "sb-" + env + "-artifacts-test";

        assertNull(SettingsDataAccessLayer.fromParameterStore(null), "null parameter returns null setting");
        assertThrows(RuntimeException.class,
                () -> SettingsDataAccessLayer.fromParameterStore(Parameter.builder().build()),
                "null parameter name throws RuntimeException");
        assertThrows(RuntimeException.class,
                () -> SettingsDataAccessLayer.fromParameterStore(Parameter.builder().name("").build()),
                "Empty parameter name throws RuntimeException");
        assertThrows(RuntimeException.class,
                () -> SettingsDataAccessLayer.fromParameterStore(Parameter.builder().name(" ").build()),
                "Blank parameter name is invalid pattern throws RuntimeException");
        assertThrows(RuntimeException.class,
                () -> SettingsDataAccessLayer.fromParameterStore(Parameter.builder().name("foobar").build()),
                "Invalid pattern parameter name throws RuntimeException");

        Parameter validParam = Parameter.builder()
                .name(parameterName)
                .value(parameterValue)
                .type(ParameterType.STRING)
                .version(null)
                .build();
        Setting expectedValidSetting = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .secure(false)
                .version(null)
                .description(null)
                .build();
        assertEquals(expectedValidSetting, SettingsDataAccessLayer.fromParameterStore(validParam), "Valid " + parameterName + " param equals " + settingName + " setting");

        String readWriteParameterName = SettingsDataAccessLayer.PARAMETER_STORE_PREFIX + "APP_NAME";
        Parameter readWriteParameter = Parameter.builder()
                .name(readWriteParameterName)
                .value("foobar")
                .type(ParameterType.STRING)
                .version(null)
                .build();
        Setting expectedReadWriteSetting = Setting.builder()
                .name("APP_NAME")
                .value("foobar")
                .secure(false)
                .version(null)
                .description(null)
                .build();
        assertEquals(expectedReadWriteSetting, SettingsDataAccessLayer.fromParameterStore(readWriteParameter), "Read/Write param " + readWriteParameterName + " equals APP_NAME setting");

        Parameter emptyParameter = Parameter.builder()
                .name(parameterName)
                .value("N/A")
                .type(ParameterType.STRING)
                .version(null)
                .build();
        Setting expectedEmptySetting = Setting.builder()
                .name(settingName)
                .value("")
                .secure(false)
                .version(null)
                .description(null)
                .build();
        assertEquals(expectedEmptySetting, SettingsDataAccessLayer.fromParameterStore(emptyParameter), "Empty " + parameterName + " param equals blank setting");

        Parameter secretParameter = Parameter.builder()
                .name(parameterName)
                .value(parameterValue)
                .type(ParameterType.SECURE_STRING)
                .version(null)
                .build();
        Setting expectedSecretSetting = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .secure(true)
                .version(null)
                .description(null)
                .build();
        assertEquals(expectedSecretSetting, SettingsDataAccessLayer.fromParameterStore(secretParameter), "Valid secret param equals secure setting");
    }

    @Test
    public void testToParameterStore() {
        String settingName = "SAAS_BOOST_BUCKET";
        String parameterName = SettingsDataAccessLayer.PARAMETER_STORE_PREFIX + settingName;
        String parameterValue = "sb-" + env + "-artifacts-test";

        assertThrows(RuntimeException.class, () -> SettingsDataAccessLayer.toParameterStore(null),
                "null setting throws RuntimeException");
        assertThrows(RuntimeException.class, () -> SettingsDataAccessLayer.toParameterStore(Setting.builder().build()),
                "null setting name throws RuntimeException");
        assertThrows(RuntimeException.class,
                () -> SettingsDataAccessLayer.toParameterStore(Setting.builder().name("").build()),
                "Empty setting name throws RuntimeException");
        assertThrows(RuntimeException.class,
                () -> SettingsDataAccessLayer.toParameterStore(Setting.builder().name(" ").build()),
                "Blank setting name throws RuntimeException");

        Parameter expectedEmptyParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.STRING)
                .value("N/A")
                .build();
        Setting settingNullValue = Setting.builder()
                .name(settingName)
                .value(null)
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals(expectedEmptyParameter, SettingsDataAccessLayer.toParameterStore(settingNullValue), "null setting value equals N/A parameter value");

        Setting settingEmptyValue = Setting.builder()
                .name(settingName)
                .value("")
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals(expectedEmptyParameter, SettingsDataAccessLayer.toParameterStore(settingEmptyValue), "Empty setting value equals N/A parameter value");

        Parameter expectedBlankParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.STRING)
                .value(" ")
                .build();
        Setting settingBlankValue = Setting.builder()
                .name(settingName)
                .value(" ")
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals(expectedBlankParameter, SettingsDataAccessLayer.toParameterStore(settingBlankValue), "Blank setting value equals N/A parameter value");

        Parameter expectedValueParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.STRING)
                .value(parameterValue)
                .build();
        Setting settingWithValue = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .description(null)
                .version(null)
                .secure(false)
                .readOnly(false)
                .build();
        assertEquals(expectedValueParameter, SettingsDataAccessLayer.toParameterStore(settingWithValue), "Setting value equals parameter value");

        Parameter expectedSecretParameter = Parameter.builder()
                .name(parameterName)
                .type(ParameterType.SECURE_STRING)
                .value(parameterValue)
                .build();
        Setting settingSecretValue = Setting.builder()
                .name(settingName)
                .value(parameterValue)
                .description(null)
                .version(null)
                .secure(true)
                .readOnly(false)
                .build();
        assertEquals(expectedSecretParameter, SettingsDataAccessLayer.toParameterStore(settingSecretValue), "Setting secret value equals secure parameter");
    }

}
