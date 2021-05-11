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
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.FailureType;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

import java.util.HashMap;
import java.util.Map;

public class EcsServiceUpdate implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(EcsServiceUpdate.class);
    private final EcsClient ecs;
    private final CodePipelineClient codepipeline;

    public EcsServiceUpdate() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.ecs = Utils.sdkClient(EcsClient.builder(), EcsClient.SERVICE_NAME);
        this.codepipeline = Utils.sdkClient(CodePipelineClient.builder(), CodePipelineClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        //logRequestEvent(event);

        Map<String, Object> job = (Map<String, Object>) event.get("CodePipeline.job");
        String jobId = (String) job.get("id");

        String json = (String) ((Map) ((Map) ((Map) job.get("data")).get("actionConfiguration")).get("configuration")).get("UserParameters");
        Map<String, Object> params = Utils.fromJson(json, HashMap.class);
        if (null == params) {
            throw new RuntimeException("json for params is invalid");
        }        
        String cluster = (String) params.get("cluster");
        String service = (String) params.get("service");
        Integer count = (Integer) params.get("desiredCount");
        try {
            DescribeServicesResponse existingServiceSettings = ecs.describeServices(r -> r
                    .cluster(cluster)
                    .services(service)
            );
            for (Service ecsService : existingServiceSettings.services()) {
                if (ecsService.desiredCount() < count) {
                    LOGGER.info("Updating desired count for service " + service + " to " + count);
                    try {
                        UpdateServiceResponse updateServiceResponse = ecs.updateService(r -> r
                                .cluster(cluster)
                                .service(service)
                                .desiredCount(count)
                        );
                    } catch (SdkServiceException ecsError) {
                        LOGGER.error("ecs::UpdateService", ecsError);
                        LOGGER.error(Utils.getFullStackTrace(ecsError));
                        failJob(jobId, "Error calling ecs::UpdateService for " + service + " in cluster " + cluster, context);
                        throw ecsError;
                    }
                }
            }
        } catch (SdkServiceException ecsError) {
            LOGGER.error("ecs::DescribeServices", ecsError);
            LOGGER.error(Utils.getFullStackTrace(ecsError));
            failJob(jobId, "Error calling ecs::DescribeServices for " + service + " in cluster " + cluster, context);
            throw ecsError;
        }

        // Tell CodePipeline that it can continue
        codepipeline.putJobSuccessResult(r -> r.jobId(jobId));
        return null;
    }

    private void failJob(String jobId, String message, Context context) {
        try {
            codepipeline.putJobFailureResult(r -> r
                    .jobId(jobId)
                    .failureDetails(details -> details
                            .type(FailureType.JOB_FAILED)
                            .message(message)
                            .externalExecutionId(context.getAwsRequestId())
                    )
            );
        } catch (SdkServiceException codepipelineError) {
            LOGGER.error("codepipeline::PutJobFailureResult", codepipelineError);
            LOGGER.error(Utils.getFullStackTrace(codepipelineError));
            throw codepipelineError;
        }
    }

}

