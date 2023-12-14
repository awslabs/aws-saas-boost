package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import software.amazon.cloudwatchlogs.emf.exception.InvalidTimestampException;
import software.amazon.cloudwatchlogs.emf.util.Validator;

import static org.junit.jupiter.api.Assertions.*;

public class CloudWatchApiTest {

    @Test
    public void testEmfTimestamp() {

        Utils.MAPPER.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Utils.MAPPER.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Utils.MAPPER.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);


        Metric[] metricsArray = Utils.fromJson(getClass().getResourceAsStream("/metrics2.json"), Metric[].class);
        for (int i = 0; i < metricsArray.length; i++) {
            Metric metric = metricsArray[i];
            try {
                Validator.validateTimestamp(metric.getTimestamp());
            } catch (InvalidTimestampException ite) {
                System.out.println("Epoch " + metric.getTimestamp());
                System.out.println(Utils.toJson(metricsArray[i]));
                break;
            }
        }
    }
}
