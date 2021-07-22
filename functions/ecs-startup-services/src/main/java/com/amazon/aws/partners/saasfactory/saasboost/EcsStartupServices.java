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
package com.amazon.aws.partners.saasfactory.saasboost;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

import java.util.ArrayList;
import java.util.Map;

public class EcsStartupServices implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(EcsStartupServices.class);
    private final static String AWS_REGION = System.getenv("AWS_REGION");
    private final static String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private final static String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private final static String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private final static String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private final EcsClient ecs;
    
    public EcsStartupServices() {
        long startTimeMillis = System.currentTimeMillis();
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

        Integer defaultMinCount = getDefaultMinCount(context);
        ArrayList<Map<String, Object>> provisionedTenants = getProvisionedTenants(context);
        if (provisionedTenants != null) {
            LOGGER.info("{} provisioned tenants to process", provisionedTenants.size());
            for (Map<String, Object> tenant : provisionedTenants) {
                // The ECS Cluster and Service for each tenant is named with their short id
                // We could save this info in parameter store with the other tenant infra
                // pieces so we're not relying on naming convention
                String cluster = "tenant-" + ((String) tenant.get("id")).substring(0, 8);
                String service = cluster;
                Integer count = null;
                if (Boolean.TRUE.equals(tenant.get("overrideDefaults")) && tenant.containsKey("minCount") && tenant.get("minCount") != null) {
                    try {
                        count = (Integer) tenant.get("minCount");
                    } catch (NumberFormatException nfe) {
                        LOGGER.error("Error parsing minCount from tenant {}", tenant.get("id"));
                        count = defaultMinCount;
                    }
                } else {
                    count = defaultMinCount;
                }

                try {
                    DescribeServicesResponse existingServiceSettings = ecs.describeServices(r -> r
                            .cluster(cluster)
                            .services(service)
                    );
                    for (Service ecsService : existingServiceSettings.services()) {
                        if (ecsService.desiredCount() < count) {
                            LOGGER.info("Updating desired count for service {} from {} to {}", service, ecsService.desiredCount(), count);
                            try {
                                UpdateServiceResponse updateServiceResponse = ecs.updateService(UpdateServiceRequest.builder()
                                        .cluster(cluster)
                                        .service(service)
                                        .desiredCount(count)
                                        .build()
                                );
                            } catch (SdkServiceException ecsError) {
                                LOGGER.error("ecs::UpdateService", ecsError);
                                LOGGER.error(Utils.getFullStackTrace(ecsError));
                                throw ecsError;
                            }
                        }
                    }
                } catch (SdkServiceException ecsError) {
                    LOGGER.error("ecs::DescribeServices", ecsError);
                    LOGGER.error(Utils.getFullStackTrace(ecsError));
                    throw ecsError;
                }
            }
        }

        return null;
    }

    protected ArrayList<Map<String, Object>> getProvisionedTenants(Context context) {
        ArrayList<Map<String, Object>> provisionedTenants = null;
        try {
            String getTenantsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                    ApiGatewayHelper.getApiRequest(
                            API_GATEWAY_HOST,
                            API_GATEWAY_STAGE,
                            ApiRequest.builder()
                                    .resource("tenants/provisioned")
                                    .method("GET")
                                    .build()
                    ),
                    API_TRUST_ROLE,
                    context.getAwsRequestId()
            );
            provisionedTenants = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
        } catch (Exception e) {
            LOGGER.error("Error invoking API " + API_GATEWAY_STAGE + "/tenants/provisioned");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return provisionedTenants;
    }

    protected Integer getDefaultMinCount(Context context) {
        Integer defaultMin = null;
        try {
            String getSettingResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                    ApiGatewayHelper.getApiRequest(
                            API_GATEWAY_HOST,
                            API_GATEWAY_STAGE,
                            ApiRequest.builder()
                                    .resource("settings?setting=MIN_COUNT")
                                    .method("GET")
                                    .build()
                    ),
                    API_TRUST_ROLE,
                    context.getAwsRequestId()
            );
            ArrayList<Map<String, Object>> settings = Utils.fromJson(getSettingResponseBody, ArrayList.class);
            Map<String, Object> setting = settings.get(0);
            try {
                defaultMin = Integer.parseInt((String) setting.get("value"));
            } catch (NumberFormatException nfe) {
                LOGGER.error("Error parsing numeric value from MIN_COUNT setting {}", setting.get("value"));
            }
        } catch (Exception e) {
            LOGGER.error("Error invoking API " + API_GATEWAY_STAGE + "/settings?setting=MIN_COUNT");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return defaultMin;
    }
}