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

import com.amazon.aws.partners.saasfactory.saasboost.appconfig.AppConfig;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.BillingProvider;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.ServiceConfig;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AppConfigTest {

    @Test
    public void testEquals() {
        AppConfig config1 = AppConfig.builder().build();
        AppConfig config2 = null;

        assertFalse("NULL is not equal", config1.equals(config2));

        config2 = config1;
        assertTrue("Same instance", config1.equals(config2));

        assertFalse("Different types are not equal", config1.equals(new HashMap<>()));

        config2 = AppConfig.builder().build();
        assertTrue("Empty config objects are equal", config1.equals(config2));

        Map<String, ServiceConfig> services1 = new HashMap<>();
        Map<String, ServiceConfig> services2 = new HashMap<>();
        services1.put("foo", null);
        services2.put("foo", null);
        config1 = AppConfig.builder().services(services1).build();
        config2 = AppConfig.builder().services(services2).build();
        assertTrue("Both null services", config1.equals(config2));

        services1.put("foo", ServiceConfig.builder().build());
        services2.put("foo", ServiceConfig.builder().build());
        config1 = AppConfig.builder().services(services1).build();
        config2 = AppConfig.builder().services(services2).build();
        assertTrue("Same services", config1.equals(config2));

        services2.put("foo", null);
        config1 = AppConfig.builder().services(services1).build();
        config2 = AppConfig.builder().services(services2).build();
        assertFalse("One service null", config1.equals(config2));

        services1.put("foo", null);
        services2.put("foo", ServiceConfig.builder().build());
        config1 = AppConfig.builder().services(services1).build();
        config2 = AppConfig.builder().services(services2).build();
        assertFalse("One service null", config1.equals(config2));

        services1.put("foo", ServiceConfig.builder().build());
        services2.remove("foo");
        services2.put("bar", ServiceConfig.builder().build());
        config1 = AppConfig.builder().services(services1).build();
        config2 = AppConfig.builder().services(services2).build();
        assertFalse("Different service names", config1.equals(config2));

        services2.put("foo", ServiceConfig.builder().build());
        config1 = AppConfig.builder().services(services1).build();
        config2 = AppConfig.builder().services(services2).build();
        assertFalse("Different number of services", config1.equals(config2));

        services1.clear();
        services2.clear();
        services1.put("foo", ServiceConfig.builder().name("foo").build());
        services2.put("foo", ServiceConfig.builder().name("bar").build());
        config1 = AppConfig.builder().services(services1).build();
        config2 = AppConfig.builder().services(services2).build();
        assertFalse("Different service configs", config1.equals(config2));

        config1 = AppConfig.builder()
                .name("foo")
                .domainName("bar")
                .sslCertificate("baz")
                .services(Map.of("foo", ServiceConfig.builder().build()))
                .billing(BillingProvider.builder().build())
                .build();
        config2 = AppConfig.builder()
                .name("foo")
                .domainName("bar")
                .sslCertificate("baz")
                .services(Map.of("foo", ServiceConfig.builder().build()))
                .billing(BillingProvider.builder().build())
                .build();
        assertTrue("Same name", config1.equals(config2));
    }

    @Test
    public void testToString() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("appConfig.json")) {
            AppConfig appConfig = Utils.MAPPER.readValue(is, AppConfig.class);
            //AppConfig appConfig = Utils.fromJson(is, AppConfig.class);
            //System.out.println(appConfig.toString());
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Test
    public void testIsEmpty() {
        AppConfig config = AppConfig.builder().build();
        assertTrue(config.isEmpty());

        AppConfig config2 = AppConfig.builder().name("test").build();
        assertFalse(config2.isEmpty());

        AppConfig config3 = AppConfig.builder().domainName("example.com").build();
        assertFalse(config3.isEmpty());

        AppConfig config4 = AppConfig.builder().hostedZone("123456").build();
        assertFalse(config4.isEmpty());

        AppConfig config5 = AppConfig.builder().sslCertificate("arn:aws:acm:xxxxx").build();
        assertFalse(config5.isEmpty());

        AppConfig config6 = AppConfig.builder().services(Map.of("foo", ServiceConfig.builder().build())).build();
        assertFalse(config6.isEmpty());

        AppConfig config7 = AppConfig.builder().billing(BillingProvider.builder().build()).build();
        assertTrue(config7.isEmpty());

        AppConfig config8 = AppConfig.builder().billing(BillingProvider.builder().apiKey("test").build()).build();
        assertFalse(config8.isEmpty());
    }

}
