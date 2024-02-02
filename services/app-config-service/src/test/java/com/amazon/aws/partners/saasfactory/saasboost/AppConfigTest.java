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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AppConfigTest {

    @Test
    public void testEquals() {
        AppConfig config1 = new AppConfig();
        AppConfig config2 = null;

        assertFalse(config1.equals(config2), "NULL is not equal");

        config2 = config1;
        assertTrue(config1.equals(config2), "Same instance");

        assertFalse(config1.equals(new HashMap<>()), "Different types are not equal");

        config2 = new AppConfig();
        assertTrue(config1.equals(config2), "Empty config objects are equal");

        Map<String, ServiceConfig> services1 = new HashMap<>();
        Map<String, ServiceConfig> services2 = new HashMap<>();
        services1.put("foo", null);
        services2.put("foo", null);
        config1.setServices(services1);
        config2.setServices(services2);
        assertTrue(config1.equals(config2), "Both null services");

        services1.put("foo", ServiceConfig.builder().build());
        services2.put("foo", ServiceConfig.builder().build());
        config1.setServices(services1);
        config2.setServices(services2);
        assertTrue(config1.equals(config2), "Same services");

        services2.put("foo", null);
        config1.setServices(services1);
        config2.setServices(services2);
        assertFalse(config1.equals(config2), "One service null");

        services1.put("foo", null);
        services2.put("foo", ServiceConfig.builder().build());
        config1.setServices(services1);
        config2.setServices(services2);
        assertFalse(config1.equals(config2), "One service null");

        services1.put("foo", ServiceConfig.builder().build());
        services2.remove("foo");
        services2.put("bar", ServiceConfig.builder().build());
        config1.setServices(services1);
        config2.setServices(services2);
        assertFalse(config1.equals(config2), "Different service names");

        services2.put("foo", ServiceConfig.builder().build());
        config1.setServices(services1);
        config2.setServices(services2);
        assertFalse(config1.equals(config2), "Different number of services");

        services1.clear();
        services2.clear();
        services1.put("foo", ServiceConfig.builder().name("foo").build());
        services2.put("foo", ServiceConfig.builder().name("bar").build());
        config1.setServices(services1);
        config2.setServices(services2);
        assertFalse(config1.equals(config2), "Different service configs");

        config1 = new AppConfig();
        config1.setName("foo");
        config1.setDomainName("bar");
        config1.setSslCertificate("baz");
        config1.setServices(Map.of("foo", ServiceConfig.builder().build()));

        config2 = new AppConfig();
        config2.setName("foo");
        config2.setDomainName("bar");
        config2.setSslCertificate("baz");
        config2.setServices(Map.of("foo", ServiceConfig.builder().build()));
        assertTrue(config1.equals(config2), "Same name");
    }

    @Test
    public void testDeserialize() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("appConfig.json")) {
            AppConfig appConfig = Utils.MAPPER.readValue(is, AppConfig.class);
            assertNotNull(appConfig);
            //System.out.println(appConfig.toString());
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Test
    public void testIsEmpty() {
        AppConfig config = new AppConfig();
        assertTrue(config.isEmpty());

        AppConfig config2 = new AppConfig();
        config2.setName("test");
        assertFalse(config2.isEmpty());

        AppConfig config3 = new AppConfig();
        config3.setDomainName("example.com");
        assertFalse(config3.isEmpty());

        AppConfig config4 = new AppConfig();
        config4.setHostedZone("123456");
        assertFalse(config4.isEmpty());

        AppConfig config5 = new AppConfig();
        config5.setSslCertificate("arn:aws:acm:xxxxx");
        assertFalse(config5.isEmpty());

        AppConfig config6 = new AppConfig();
        config6.setServices(Map.of("foo", ServiceConfig.builder().build()));
        assertFalse(config6.isEmpty());
    }

}
