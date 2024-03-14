package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MetricContextTest {

    @Test
    public void testDefaultContextKeys() {
        MetricContext context = new MetricContext();
        assertTrue(context.containsKey("TenantId"));
        assertTrue(context.containsKey("UserId"));
        assertTrue(context.containsKey("Action"));
        assertTrue(context.containsKey("Application"));

        String json = "  {\n" +
                "    \"name\": \"Jobs Scheduled\",\n" +
                "    \"timestamp\": 1695980548324,\n" +
                "    \"measure\": {\n" +
                "      \"type\": \"count\",\n" +
                "      \"value\": 1\n" +
                "    },\n" +
                "    \"context\": {\n" +
                "      \"TenantId\": \"deb0451a-5e20-4415-a9f2-9d9b941bb1f1\",\n" +
                "      \"UserId\": \"48517b4c-7169-421f-8187-138b6e109ea5\",\n" +
                "      \"Application\": \"Job Scheduler Service\",\n" +
                "      \"Action\": \"scheduleJob\"\n" +
                "    }\n" +
                "  }";
        Utils.MAPPER.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Utils.MAPPER.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Utils.MAPPER.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Metric m = Utils.fromJson(json, Metric.class);
        System.out.println(m.getTimestamp());
        String json2 = Utils.toJson(m);
        System.out.println(json2);
        Metric m2 = Utils.fromJson(json2, Metric.class);
        System.out.println(Utils.toJson(m2));
    }
}
