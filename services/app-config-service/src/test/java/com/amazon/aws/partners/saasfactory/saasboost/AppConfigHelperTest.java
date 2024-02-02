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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class AppConfigHelperTest {

    @Test
    public void testIsDomainChanged() {
        AppConfig existing = new AppConfig();
        AppConfig altered = new AppConfig();
        assertFalse(AppConfigHelper.isDomainChanged(existing, altered), "Both null");

        existing.setDomainName("");
        altered.setDomainName("");
        assertFalse(AppConfigHelper.isDomainChanged(existing, altered), "Both empty");

        existing.setDomainName("ABC");
        altered.setDomainName("abc");
        assertFalse(AppConfigHelper.isDomainChanged(existing, altered), "Ignore case");

        existing = new AppConfig();
        altered.setDomainName("abc");
        assertTrue(AppConfigHelper.isDomainChanged(existing, altered), "null != non-empty");

        existing = new AppConfig();
        existing.setDomainName("abc");
        altered = new AppConfig();
        assertTrue(AppConfigHelper.isDomainChanged(existing, altered), "null != non-empty");

        existing = new AppConfig();
        existing.setDomainName("abc");
        altered = new AppConfig();
        altered.setDomainName("xzy");
        assertTrue(AppConfigHelper.isDomainChanged(existing, altered), "Different values");
    }

    @Test
    public void testIsSslCertArnChanged() {
        AppConfig existing = new AppConfig();
        AppConfig altered = new AppConfig();
        assertFalse(AppConfigHelper.isSslArnChanged(existing, altered), "Both null");

        existing.setSslCertificate("");
        altered.setSslCertificate("");
        assertFalse(AppConfigHelper.isSslArnChanged(existing, altered), "Both empty");

        existing.setSslCertificate("ABC");
        altered.setSslCertificate("abc");
        assertFalse(AppConfigHelper.isSslArnChanged(existing, altered), "Ignore case");

        existing = new AppConfig();
        altered = new AppConfig();
        altered.setSslCertificate("abc");
        assertTrue(AppConfigHelper.isSslArnChanged(existing, altered), "null != non-empty");

        existing = new AppConfig();
        existing.setSslCertificate("abc");
        altered = new AppConfig();
        assertTrue(AppConfigHelper.isSslArnChanged(existing, altered), "null != non-empty");

        existing = new AppConfig();
        existing.setSslCertificate("abc");
        altered = new AppConfig();
        altered.setSslCertificate("xyz");
        assertTrue(AppConfigHelper.isSslArnChanged(existing, altered), "Different values");
    }

    @Test
    public void testIsServicesChanged() {
        AppConfig existing = new AppConfig();
        AppConfig altered = new AppConfig();
        assertFalse(AppConfigHelper.isServicesChanged(existing, altered));

        Map<String, ServiceConfig> services1 = new HashMap<>();
        services1.put("foo", ServiceConfig.builder().build());
        Map<String, ServiceConfig> services2 = new HashMap<>();
        services2.put("foo", ServiceConfig.builder().build());
        existing.setServices(services1);
        altered.setServices(services2);
        assertFalse(AppConfigHelper.isServicesChanged(existing, altered));

        existing = new AppConfig();
        existing.setServices(services1);
        services2.put("bar", ServiceConfig.builder().build());
        altered.setServices(services2);
        assertTrue(AppConfigHelper.isServicesChanged(existing, altered));

        services2.remove("bar");
        altered.setServices(services2);
        assertFalse(AppConfigHelper.isServicesChanged(existing, altered));

        services1.clear();
        existing.setServices(services1);
        assertTrue(AppConfigHelper.isServicesChanged(existing, altered));
    }

    @Test
    public void testRemovedServices() {
        AppConfig existing = new AppConfig();
        AppConfig altered = new AppConfig();
        assertTrue(AppConfigHelper.removedServices(existing, altered).isEmpty());

        Map<String, ServiceConfig> services1 = new HashMap<>();
        services1.put("foo", ServiceConfig.builder().build());
        Map<String, ServiceConfig> services2 = new HashMap<>();
        services2.put("FOO", ServiceConfig.builder().build());
        existing.setServices(services1);
        altered.setServices(services2);
        // foo | FOO
        assertTrue(AppConfigHelper.removedServices(existing, altered).isEmpty());

        // foo | FOO,bar
        services2.put("bar", ServiceConfig.builder().build());
        altered.setServices(services2);
        assertTrue(AppConfigHelper.removedServices(existing, altered).isEmpty());

        // foo | FOO
        services2.remove("bar");
        altered.setServices(services2);
        assertTrue(AppConfigHelper.removedServices(existing, altered).isEmpty());

        // foo | bar
        services2.remove("FOO");
        services2.put("bar", ServiceConfig.builder().build());
        altered.setServices(services2);
        assertFalse(AppConfigHelper.removedServices(existing, altered).isEmpty());

        // christmas,easter | bar,baz
        services1.clear();
        services1.put("christmas", ServiceConfig.builder().build());
        services1.put("easter", ServiceConfig.builder().build());
        services2.clear();
        services2.put("bar", ServiceConfig.builder().build());
        services2.put("baz", ServiceConfig.builder().build());
        existing.setServices(services1);
        altered.setServices(services2);
        assertFalse(AppConfigHelper.removedServices(existing, altered).isEmpty());
    }
}
