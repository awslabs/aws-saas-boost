/**
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
package com.amazonaws.saas.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.PutRecordBatchRequest;
import software.amazon.awssdk.services.firehose.model.Record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetricEventLogger {
    private static final Logger logger = LoggerFactory.getLogger(MetricEventLogger.class);
    public static final int DEFAULT_FLUSH_TIME_IN_SECS = 60;
    private final FirehoseClient firehose;
    private final String streamName;
    private final int bufferSize;
    private List<Record> recordBuffer;
    private Long startTime;
    private int flushTimeWindowInSeconds = DEFAULT_FLUSH_TIME_IN_SECS;

    private MetricEventLogger(String kinesisStreamName, Region region, int batchSize, int flushTimeWindow) {
        this.bufferSize = batchSize;
        this.streamName = kinesisStreamName;
        this.flushTimeWindowInSeconds = flushTimeWindow;
        this.initializeBuffer();
        this.firehose = FirehoseClient.builder().region(region).build();
    }

    public static MetricEventLogger getBatchLoggerFor(String kinesisStreamName, Region region, int batchSize, int flushTimeWindowInSeconds) {
        return new MetricEventLogger(kinesisStreamName, region, batchSize, flushTimeWindowInSeconds);
    }

    public static MetricEventLogger getLoggerFor(String kinesisStreamName, Region region) {
        return new MetricEventLogger(kinesisStreamName, region, 1, DEFAULT_FLUSH_TIME_IN_SECS);
    }

    public void log(MetricEvent event) {
        String eventJsonString = "";

        try {
            eventJsonString = (new ObjectMapper()).writeValueAsString(event);
            logger.debug("Metric Event Json: " + eventJsonString);
            Record record = Record.builder().data(SdkBytes.fromByteArray(eventJsonString.getBytes(CharsetUtil.UTF_8))).build();
            recordBuffer.add(record);
            if (shouldSendToKinesis()) {
                writeToKinesisFirehose();
            }
        } catch (Exception var4) {
            logger.debug("Error: Unable to log metric: " + eventJsonString, var4);
        }

    }

    public void shutdown() {
        logger.debug("Clean shutdown, sending buffer data to kinesis");
        writeToKinesisFirehose();
    }

    private boolean shouldSendToKinesis() {
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000L;
        if (recordBuffer.size() >= bufferSize) {
            logger.debug("Buffer full, writing to kinesis");
            return true;
        } else if (elapsedTime >= flushTimeWindowInSeconds) {
            logger.debug("Time elapsed, writing to kinesis");
            return true;
        } else {
            return false;
        }
    }

    protected void writeToKinesisFirehose() {
        firehose.putRecordBatch(PutRecordBatchRequest.builder().deliveryStreamName(streamName).records(recordBuffer).build());
        initializeBuffer();
    }

    private void initializeBuffer() {
        logger.debug("Initializing Buffer");
        startTime = System.currentTimeMillis();
        recordBuffer = Collections.synchronizedList(new ArrayList<>());
    }
}
