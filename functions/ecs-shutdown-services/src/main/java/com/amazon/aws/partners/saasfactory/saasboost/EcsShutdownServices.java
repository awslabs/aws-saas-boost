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
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EcsShutdownServices implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EcsShutdownServices.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private final EcsClient ecs;

    public EcsShutdownServices() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_ENV");
        }
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));

        this.ecs = Utils.sdkClient(EcsClient.builder(), EcsClient.SERVICE_NAME);

        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        List<Map<String, Object>> provisionedTenants = getProvisionedTenants(context);
        if (provisionedTenants != null) {
            LOGGER.info("{} provisioned tenants to process", provisionedTenants.size());

            // Fetch the app config and make a list of all the configured services
            Map<String, Object> appConfig = getAppConfig(context);
            Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
            List<String> serviceNames = new ArrayList<>(services.keySet());

            // Batch the list of services into slices of 10 to deal with the limitations of the
            // describeServices SDK call
            final int maxDescribeServices = 10;
            final AtomicInteger batch = new AtomicInteger(0);
            Collection<List<String>> describeServiceBatches = serviceNames
                    .stream()
                    .collect(
                            Collectors.groupingBy(slice -> (batch.getAndIncrement() / maxDescribeServices))
                    )
                    .values();

            for (Map<String, Object> tenant : provisionedTenants) {
                Map<String, Map<String, String>> tenantResources = (Map<String, Map<String, String>>) tenant.get("resources");
                String cluster = tenantResources.get("ECS_CLUSTER").get("name");
                LOGGER.info("Shutting down services in cluster {}", cluster);

                // For each batch of services (will only be 1 batch unless there are more than 10 services
                // in the app config), update each service's desired count to zero. Setting the service's
                // desired count to 0 will gracefully remove all running tasks
                final Integer count = 0;
                for (List<String> describeServiceBatch : describeServiceBatches) {
                    try {
                        DescribeServicesResponse existingServiceSettings = ecs.describeServices(request -> request
                                .cluster(cluster)
                                .services(describeServiceBatch)
                        );
                        for (Service ecsService : existingServiceSettings.services()) {
                            if (ecsService.desiredCount() > count) {
                                LOGGER.info("Updating desired count for service {} to {}", ecsService.serviceName(),
                                        count);
                                try {
                                    ecs.updateService(request -> request
                                            .cluster(cluster)
                                            .service(ecsService.serviceName())
                                            .desiredCount(count)
                                    );
                                } catch (SdkServiceException ecsError) {
                                    LOGGER.error("ecs::UpdateService", ecsError);
                                    LOGGER.error(Utils.getFullStackTrace(ecsError));
                                    throw ecsError;
                                }
                            } else {
                                LOGGER.info("Skipping desired count for service {} already at {}",
                                        ecsService.serviceName(), ecsService.desiredCount());
                            }
                        }
                    } catch (SdkServiceException ecsError) {
                        LOGGER.error("ecs::DescribeServices", ecsError);
                        LOGGER.error(Utils.getFullStackTrace(ecsError));
                        throw ecsError;
                    }
                }
            }
        }

        return null;
    }

    protected Map<String, Object> getAppConfig(Context context) {
        // Fetch all of the services configured for this application
        LOGGER.info("Calling settings service get app config API");
        String getAppConfigResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                ApiGatewayHelper.getApiRequest(
                        API_GATEWAY_HOST,
                        API_GATEWAY_STAGE,
                        ApiRequest.builder()
                                .resource("settings/config")
                                .method("GET")
                                .build()
                ),
                API_TRUST_ROLE,
                context.getAwsRequestId()
        );
        Map<String, Object> appConfig = Utils.fromJson(getAppConfigResponseBody, LinkedHashMap.class);
        return appConfig;
    }

    protected List<Map<String, Object>> getProvisionedTenants(Context context) {
        LOGGER.info("Calling tenants service get tenants API");
        String resource = "tenants?status=provisioned";
        String getTenantsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                ApiGatewayHelper.getApiRequest(
                        API_GATEWAY_HOST,
                        API_GATEWAY_STAGE,
                        ApiRequest.builder()
                                .resource(resource)
                                .method("GET")
                                .build()
                ),
                API_TRUST_ROLE,
                context.getAwsRequestId()
        );
        List<Map<String, Object>> tenants = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
        return tenants;
    }
}