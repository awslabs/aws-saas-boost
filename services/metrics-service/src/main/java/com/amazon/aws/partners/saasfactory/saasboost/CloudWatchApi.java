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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.Unit;

import java.util.Collection;

public class CloudWatchApi implements MetricsProviderApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudWatchApi.class);
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private final MetricsLogger emf;
    private final String logGroupName;
    private final String logStreamName;

    public CloudWatchApi() {
        this(null, null);
    }

    public CloudWatchApi(String logGroupName, String logStreamName) {
        this(logGroupName, logStreamName, new DefaultDependencyFactory());
    }

    public CloudWatchApi(String logGroupName, String logStreamName, CloudWatchApiDependencyFactory init) {
        this.emf = init.emf();
        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;
    }

    @Override
    public void putMetrics(Collection<Metric> metrics) {
        LOGGER.info("Generating EMF data for {} metrics", metrics.size());
        try {
            emf.setNamespace("sb-" + SAAS_BOOST_ENV);
            //emf.setFlushPreserveDimensions(false);
            for (Metric metric : metrics) {
                Unit unit = metric.getMeasure().getType() == Measure.Type.count ? Unit.COUNT : Unit.NONE;
                emf.setTimestamp(metric.getTimestamp());
                emf.putProperty("UserId", metric.getContext().getUserId());
                emf.putProperty("Application", metric.getContext().getApplication());
                emf.putProperty("Action", metric.getContext().getAction());
                emf.setDimensions(DimensionSet.of(
                        metric.getName() + " By Tenant", metric.getContext().getTenantId()
                ));
                emf.putMetric(metric.getName(), metric.getMeasure().getValue().doubleValue(), unit);
                emf.flush();
                //emf.resetDimensions(false);
            }
        } catch (Exception e) {
            LOGGER.error("CloudWatch EMF error", e);
        }
    }

    interface CloudWatchApiDependencyFactory {

        MetricsLogger emf();
    }

    private static final class DefaultDependencyFactory implements CloudWatchApiDependencyFactory {

        @Override
        public MetricsLogger emf() {
            return new MetricsLogger();
        }
    }
}
