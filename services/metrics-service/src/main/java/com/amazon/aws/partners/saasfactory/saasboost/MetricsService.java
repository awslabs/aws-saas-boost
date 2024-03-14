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
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

import java.net.HttpURLConnection;
import java.util.*;
import java.util.stream.Collectors;

public class MetricsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String METRICS_QUEUE = System.getenv("METRICS_QUEUE");
    private static final String METRICS_DLQ = System.getenv("METRICS_DLQ");
    private final MetricsDataAccessLayer dal;
    private final SqsClient sqs;

    public MetricsService() {
        this(new DefaultDependencyFactory());
    }

    public MetricsService(MetricServiceDependencyFactory init) {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_ENV");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = init.dal();
        this.sqs = init.sqs();
    }

    /**
     * Create new new metric using the configured Metric Provider. Integration for POST /metrics endpoint
     * @param event API Gateway proxy request event
     * @param context Lambda function context
     * @return HTTP Created on success
     */
    public APIGatewayProxyResponseEvent putMetrics(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        Utils.MAPPER.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Utils.MAPPER.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Utils.MAPPER.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        //Utils.logRequestEvent(event);
        Metric[] metricsArray = Utils.fromJson(event.getBody(), Metric[].class);
        if (metricsArray == null) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withBody(Utils.toJson(Map.of("message", "Invalid request body")));
        }
        List<Metric> metrics = new ArrayList<>(Arrays.asList(metricsArray));
        if (metrics.isEmpty()) {
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .withBody(Utils.toJson(Map.of("message", "Invalid request body")));
        } else {
            LOGGER.info("Enqueuing {} metrics for processing", metrics.size());
            try {
                // Queue metrics for batch processing by our metrics provider
                List<SendMessageBatchRequestEntry> batch = new ArrayList<>();
                try {
                    for (Metric metric : metrics) {
                        if (batch.size() < 10) {
                            batch.add(SendMessageBatchRequestEntry.builder()
                                    .id(String.valueOf(metric.hashCode()))
                                    .messageBody(Utils.toJson(metric))
                                    .build()
                            );
                        } else {
                            // Batch has reached max size of 10, make the request
                            SendMessageBatchResponse response = sqs.sendMessageBatch(request -> request
                                    .entries(batch)
                                    .queueUrl(METRICS_QUEUE)
                            );
                            if (response.hasFailed()) {
                                for (BatchResultErrorEntry error : response.failed()) {
                                    LOGGER.error("Failed to enqueue metric {} {}", error.code(), error.message());
                                }
                            }
                            // Clear the batch so we can fill it up for the next request
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        // get the last batch
                        SendMessageBatchResponse response = sqs.sendMessageBatch(request -> request
                                .entries(batch)
                                .queueUrl(METRICS_QUEUE)
                        );
                        if (response.hasFailed()) {
                            for (BatchResultErrorEntry error : response.failed()) {
                                LOGGER.error("Failed to enqueue metric {} {}", error.code(), error.message());
                            }
                        }
                    }
                } catch (SdkServiceException sqsError) {
                    LOGGER.error("sqs:SendMessageBatch error", sqsError);
                    LOGGER.error(Utils.getFullStackTrace(sqsError));
                    throw sqsError;
                }

                return new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(HttpURLConnection.HTTP_CREATED);
            } catch (SdkServiceException sqsError) {
                LOGGER.error("sqs:SendMessage error", sqsError);
                LOGGER.error(Utils.getFullStackTrace(sqsError));
                throw sqsError;
            }
        }
    }

    public SQSBatchResponse processMetricsQueue(SQSEvent event, Context context) {

        final List<SQSBatchResponse.BatchItemFailure> retry = new ArrayList<>();
        final List<SQSEvent.SQSMessage> fatal = new ArrayList<>();

        Utils.MAPPER.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Utils.MAPPER.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Utils.MAPPER.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        List<Metric> metrics = new ArrayList<>();
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            String messageId = message.getMessageId();
            String messageBody = message.getBody();
            Metric metric = Utils.fromJson(messageBody, Metric.class);
            if (metric != null) {
                metrics.add(metric);
            } else {
                LOGGER.error("Can't parse metric from message {}", messageId);
                fatal.add(message);
            }
        }
        MetricsProviderConfig providerConfig = dal.getProviderConfig();
        MetricsProvider provider = MetricsProviderFactory.getInstance().getProvider(providerConfig);
        provider.api().putMetrics(metrics);

        if (!fatal.isEmpty()) {
            LOGGER.info("Moving non-recoverable failures to DLQ");
            SendMessageBatchResponse dlq = sqs.sendMessageBatch(request -> request
                    .queueUrl(METRICS_DLQ)
                    .entries(fatal.stream()
                            .map(msg -> SendMessageBatchRequestEntry.builder()
                                    .id(msg.getMessageId())
                                    .messageBody(msg.getBody())
                                    .build()
                            )
                            .collect(Collectors.toList())
                    )
            );
            LOGGER.info(dlq.toString());
        }

        return SQSBatchResponse.builder().withBatchItemFailures(retry).build();
    }

    interface MetricServiceDependencyFactory {

        SqsClient sqs();

        MetricsDataAccessLayer dal();
    }

    private static final class DefaultDependencyFactory implements MetricServiceDependencyFactory {

        @Override
        public SqsClient sqs() {
            return Utils.sdkClient(SqsClient.builder(), SqsClient.SERVICE_NAME);
        }

        @Override
        public MetricsDataAccessLayer dal() {
            return new MetricsDataAccessLayer();
        }
    }

}
