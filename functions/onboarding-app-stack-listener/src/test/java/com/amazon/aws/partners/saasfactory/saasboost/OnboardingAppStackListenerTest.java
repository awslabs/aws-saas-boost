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

import java.util.UUID;

import static org.junit.Assert.*;

public class OnboardingAppStackListenerTest {

    @Test
    public void testServiceNameResourceKey() {
        String serviceName = "foo bar";
        String resourceType = "CODE_PIPELINE";

        assertThrows(IllegalArgumentException.class, () -> {
            OnboardingAppStackListener.serviceNameResourceKey(null, resourceType);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            OnboardingAppStackListener.serviceNameResourceKey("", resourceType);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            OnboardingAppStackListener.serviceNameResourceKey(" ", resourceType);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            OnboardingAppStackListener.serviceNameResourceKey(serviceName, null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            OnboardingAppStackListener.serviceNameResourceKey(serviceName, "");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            OnboardingAppStackListener.serviceNameResourceKey(serviceName, "  ");
        });

        String expected = "SERVICE_FOO_BAR_CODE_PIPELINE";
        assertEquals(expected, OnboardingAppStackListener.serviceNameResourceKey(serviceName, resourceType));
    }

    @Test
    public void testFilter() {
        UUID id = UUID.randomUUID();
        String tenantId = id.toString().split("-")[0];
        CloudFormationEvent event = CloudFormationEvent.builder()
                .stackName("sb-" + System.getenv("SAAS_BOOST_ENV") + "-tenant-" + tenantId + "-app-foobar-"
                        + Utils.randomString(12))
                .resourceType("AWS::CloudFormation::Stack")
                .resourceStatus("CREATE_COMPLETE")
                .build();
        assertTrue(OnboardingAppStackListener.filter(event));
    }
}
