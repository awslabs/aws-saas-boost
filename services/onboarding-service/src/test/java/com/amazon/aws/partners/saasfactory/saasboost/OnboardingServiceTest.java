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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.tests.EventLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OnboardingServiceTest {

    private Context context;
    private static UUID onboardingId;
    private static Onboarding onboarding;

    @BeforeAll
    public static void setup() throws Exception {
        onboardingId = UUID.fromString("f11cadd8-9c3c-40be-9106-4d64e2478daf");
        onboarding = new Onboarding();
        onboarding.setId(onboardingId);
    }

    @Test
    public void testGetOnboardingById() {
        OnboardingDataAccessLayer mockDal = mock(OnboardingDataAccessLayer.class);
        when(mockDal.getOnboarding(onboardingId.toString())).thenReturn(onboarding);

        MockDependencyFactory init = new MockDependencyFactory();
        init.setDal(mockDal);

        OnboardingService onboardingService = new OnboardingService(init);

        APIGatewayProxyRequestEvent request = EventLoader
                .loadApiGatewayRestEvent("getOnboardingByIdEvent.json");
        assertEquals(onboardingId.toString(), request.getPathParameters().get("id"));

        APIGatewayProxyResponseEvent response = onboardingService.getOnboarding(request, context);
        Onboarding onboarding = Utils.fromJson(response.getBody(), Onboarding.class);

        assertNotNull(onboarding);
        assertEquals(onboardingId, onboarding.getId());
    }

    @Test
    public void testGetOnboardingByIdInvalid() {
        String invalidId = "1234";
        OnboardingDataAccessLayer mockDal = mock(OnboardingDataAccessLayer.class);
        when(mockDal.getOnboarding(invalidId)).thenReturn(null);

        MockDependencyFactory init = new MockDependencyFactory();
        init.setDal(mockDal);

        OnboardingService onboardingService = new OnboardingService(init);

        APIGatewayProxyRequestEvent request = EventLoader
                .loadApiGatewayRestEvent("getOnboardingByIdInvalidEvent.json");
        assertEquals(invalidId, request.getPathParameters().get("id"));

        APIGatewayProxyResponseEvent response = onboardingService.getOnboarding(request, context);
        Onboarding onboarding = Utils.fromJson(response.getBody(), Onboarding.class);

        assertNull(onboarding);
        assertEquals(404, response.getStatusCode());
    }
}
