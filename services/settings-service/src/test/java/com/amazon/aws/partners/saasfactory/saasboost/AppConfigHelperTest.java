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
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.AppConfigHelper;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.BillingProvider;
import com.amazon.aws.partners.saasfactory.saasboost.appconfig.ServiceConfig;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AppConfigHelperTest {

    @Test
    public void testIsDomainChanged() {
        AppConfig existing = AppConfig.builder().build();
        AppConfig altered = AppConfig.builder().build();
        assertFalse("Both null", AppConfigHelper.isDomainChanged(existing, altered));

        existing = AppConfig.builder().domainName("").build();
        altered = AppConfig.builder().domainName("").build();
        assertFalse("Both empty", AppConfigHelper.isDomainChanged(existing, altered));

        existing = AppConfig.builder().domainName("ABC").build();
        altered = AppConfig.builder().domainName("abc").build();
        assertFalse("Ignore case", AppConfigHelper.isDomainChanged(existing, altered));

        existing = AppConfig.builder().build();
        altered = AppConfig.builder().domainName("abc").build();
        assertTrue("null != non-empty", AppConfigHelper.isDomainChanged(existing, altered));

        existing = AppConfig.builder().domainName("abc").build();
        altered = AppConfig.builder().build();
        assertTrue("null != non-empty", AppConfigHelper.isDomainChanged(existing, altered));

        existing = AppConfig.builder().domainName("abc").build();
        altered = AppConfig.builder().domainName("xzy").build();
        assertTrue("Different values", AppConfigHelper.isDomainChanged(existing, altered));
    }

    @Test
    public void testIsBillingProviderChanged() {
        AppConfig existing = AppConfig.builder().build();
        AppConfig altered = AppConfig.builder().build();
        assertFalse("Both null", AppConfigHelper.isBillingChanged(existing, altered));

        existing = AppConfig.builder().billing(BillingProvider.builder().build()).build();
        altered =  AppConfig.builder().billing(BillingProvider.builder().build()).build();
        assertFalse("Both null keys", AppConfigHelper.isBillingChanged(existing, altered));

        String apiKey1 = "AQICAHhcs1hgJKpJfeso9W7CCTmyCVulso9PlceBD2lnnVksMwFVwWN3pbig0jooa4LJ2IbtAAAAzjCBywYJKoZIhvcNAQcGoIG9MIG6AgEAMIG0BgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDGHgQErKnkEmp2kVkQIBEICBhlZ2lux43UJUx2R0Q3DdK80od7FHeWpA5mCLr7uWipkaQ79lxsx2ffRbwAPRbcves2NEWznQJsCm2+bgJRpE1mPEJtSfXwGVCsbf1RUGIAiB0+k+NKCih8qAlBcBsA9iFvRm0kVqoo9acz3ay56pImzWrg8wrjkhGkspnXZhvK7BZg5/zvxZ != AQICAHhcs1hgJKpJfeso9W7CCTmyCVulso9PlceBD2lnnVksMwFVwWN3pbig0jooa4LJ2IbtAAAAzjCBywYJKoZIhvcNAQcGoIG9MIG6AgEAMIG0BgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDGHgQErKnkEmp2kVkQIBEICBhlZ2lux43UJUx2R0Q3DdK80od7FHeWpA5mCLr7uWipkaQ79lxsx2ffRbwAPRbcves2NEWznQJsCm2+bgJRpE1mPEJtSfXwGVCsbf1RUGIAiB0+k+NKCih8qAlBcBsA9iFvRm0kVqoo9acz3ay56pImzWrg8wrjkhGkspnXZhvK7BZg5/zvxZ";
        existing = AppConfig.builder().billing(BillingProvider.builder().build()).build();
        altered = AppConfig.builder().billing(BillingProvider.builder().apiKey(apiKey1).build()).build();
        assertTrue("One null, one not null", AppConfigHelper.isBillingChanged(existing, altered));
        assertTrue("First time set", AppConfigHelper.isBillingFirstTime(existing, altered));
    }

    @Test
    public void testIsSslCertArnChanged() {
        AppConfig existing = AppConfig.builder().build();
        AppConfig altered = AppConfig.builder().build();
        assertFalse("Both null", AppConfigHelper.isSslArnChanged(existing, altered));

        existing = AppConfig.builder().sslCertificate("").build();
        altered = AppConfig.builder().sslCertificate("").build();
        assertFalse("Both empty", AppConfigHelper.isSslArnChanged(existing, altered));

        existing = AppConfig.builder().sslCertificate("ABC").build();
        altered = AppConfig.builder().sslCertificate("abc").build();
        assertFalse("Ignore case", AppConfigHelper.isSslArnChanged(existing, altered));

        existing = AppConfig.builder().build();
        altered = AppConfig.builder().sslCertificate("abc").build();
        assertTrue("null != non-empty", AppConfigHelper.isSslArnChanged(existing, altered));

        existing = AppConfig.builder().sslCertificate("abc").build();
        altered = AppConfig.builder().build();
        assertTrue("null != non-empty", AppConfigHelper.isSslArnChanged(existing, altered));

        existing = AppConfig.builder().sslCertificate("abc").build();
        altered = AppConfig.builder().sslCertificate("xzy").build();
        assertTrue("Different values", AppConfigHelper.isSslArnChanged(existing, altered));
    }

    @Test
    public void testIsComputeChanged() {
        // TODO POEPPT
//        AppConfig existing = AppConfig.builder().build();
//        AppConfig altered = AppConfig.builder().build();
//        assertFalse("Both null", AppConfigHelper.isBillingChanged(existing, altered));
//
//        existing = AppConfig.builder().computeSize("S").build();
//        altered = AppConfig.builder().computeSize("S").build();
//        assertFalse("Both ComputeSize Small", AppConfigHelper.isComputeChanged(existing, altered));
//
//        existing = AppConfig.builder().defaultMemory(2048).defaultCpu(1024).build();
//        altered = AppConfig.builder().defaultMemory(2048).defaultCpu(1024).build();
//        assertFalse("Both ComputeSize Small", AppConfigHelper.isComputeChanged(existing, altered));
//
//        existing = AppConfig.builder().computeSize("S").build();
//        altered = AppConfig.builder().computeSize("M").build();
//        assertTrue("Different ComputeSize", AppConfigHelper.isComputeChanged(existing, altered));
//
//        existing = AppConfig.builder().defaultMemory(2048).defaultCpu(1024).build();
//        altered = AppConfig.builder().defaultMemory(4096).defaultCpu(2048).build();
//        assertTrue("Different CPU/Memory", AppConfigHelper.isComputeChanged(existing, altered));
    }

    @Test
    public void testIsServicesChanged() {
        AppConfig existing = AppConfig.builder().build();
        AppConfig altered = AppConfig.builder().build();
        assertFalse(AppConfigHelper.isServicesChanged(existing, altered));

        Map<String, ServiceConfig> services1 = new HashMap<>();
        services1.put("foo", ServiceConfig.builder().build());
        Map<String, ServiceConfig> services2 = new HashMap<>();
        services2.put("foo", ServiceConfig.builder().build());
        existing = AppConfig.builder().services(services1).build();
        altered = AppConfig.builder().services(services2).build();
        assertFalse(AppConfigHelper.isServicesChanged(existing, altered));

        services2.put("bar", ServiceConfig.builder().build());
        existing = AppConfig.builder().services(services1).build();
        altered = AppConfig.builder().services(services2).build();
        assertTrue(AppConfigHelper.isServicesChanged(existing, altered));

        services2.remove("bar");
        existing = AppConfig.builder().services(services1).build();
        altered = AppConfig.builder().services(services2).build();
        assertFalse(AppConfigHelper.isServicesChanged(existing, altered));

        services1.clear();
        existing = AppConfig.builder().services(services1).build();
        altered = AppConfig.builder().services(services2).build();
        assertTrue(AppConfigHelper.isServicesChanged(existing, altered));
    }
}
