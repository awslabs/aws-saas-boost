package com.amazon.aws.partners.saasfactory.saasboost;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

public class OnboardingEventTest {

    @Test
    public void testValidate() {
        String event = "{\n" +
                "    \"version\": \"0\",\n" +
                "    \"id\": \"c99f48e7-d0cb-5bfe-0adc-e0413809028b\",\n" +
                "    \"detail-type\": \"Onboarding Validated\",\n" +
                "    \"source\": \"saas-boost\",\n" +
                "    \"account\": \"012345678901\",\n" +
                "    \"time\": \"2023-01-01T00:00:00Z\",\n" +
                "    \"region\": \"us-east-1\",\n" +
                "    \"resources\": [],\n" +
                "    \"detail\": {\n" +
                "        \"onboardingId\": \"2f2ffb71-972d-494d-bd95-e832b07db690\"\n" +
                "    }\n" +
                "}";
        Assertions.assertTrue(OnboardingEvent.validate(Utils.fromJson(event, LinkedHashMap.class)));
    }
}
