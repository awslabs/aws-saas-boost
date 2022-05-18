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
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.*;

import java.util.*;
import java.util.concurrent.*;

public class SetInstanceProtection implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetInstanceProtection.class);
    private final AutoScalingClient autoScaling;

    public SetInstanceProtection() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        autoScaling = Utils.sdkClient(AutoScalingClient.builder(), AutoScalingClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String autoScalingGroup = (String) resourceProperties.get("AutoScalingGroup");
        final Boolean enableInstanceProtection = Boolean.valueOf((String) resourceProperties.get("Enable"));
        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        LOGGER.info("Setting instance protection to {} for Autoscaling group {}", enableInstanceProtection,
                autoScalingGroup);
        try {
            Runnable r = () -> {
                if ("Delete".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info(requestType.toUpperCase());
                    try {
                        DescribeAutoScalingGroupsResponse response = autoScaling.describeAutoScalingGroups(request ->
                                request.autoScalingGroupNames(autoScalingGroup)
                        );
                        if (response.hasAutoScalingGroups()) {
                            LOGGER.info("AutoScaling found {} groups for {}", response.autoScalingGroups().size(),
                                    autoScalingGroup);
                            if (!response.autoScalingGroups().isEmpty()) {
                                AutoScalingGroup asgGroup = response.autoScalingGroups().get(0);
                                List<String> instancesToUpdate = new ArrayList<>();
                                asgGroup.instances().forEach(ec2 -> instancesToUpdate.add(ec2.instanceId()));
                                try {
                                    autoScaling.setInstanceProtection(request -> request
                                            .instanceIds(instancesToUpdate)
                                            .protectedFromScaleIn(enableInstanceProtection)
                                            .autoScalingGroupName(autoScalingGroup)
                                    );
                                    LOGGER.info("{} instance protection on {} instances.",
                                            ((enableInstanceProtection) ? "Enabled" : "Disabled"),
                                            instancesToUpdate.size()
                                    );
                                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                                } catch (AutoScalingException e) {
                                    LOGGER.error("autoscaling:SetInstanceProtection error", e);
                                    LOGGER.error(Utils.getFullStackTrace(e));
                                    responseData.put("Reason", e.getMessage());
                                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                                }
                            } else {
                                LOGGER.info("No auto scaling groups matched.");
                            }
                        }
                    } catch (AutoScalingException e) {
                        LOGGER.error("autoscaling:describeAutoScalingGroups error", e);
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    }
                } else if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType {}", requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            String stackTrace = Utils.getFullStackTrace(e);
            LOGGER.error(stackTrace);
            responseData.put("Reason", stackTrace);
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

}