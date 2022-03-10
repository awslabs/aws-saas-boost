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
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.util.*;

public class CoreStackListener implements RequestHandler<SNSEvent, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreStackListener.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String SYSTEM_API_CALL = "System API Call";
    private static final String EVENT_SOURCE = "saas-boost";
    private static final Collection<String> EVENTS_OF_INTEREST = Collections.unmodifiableCollection(
            Arrays.asList("CREATE_COMPLETE", "UPDATE_COMPLETE"));
    private final CloudFormationClient cfn;
    private final EventBridgeClient eventBridge;

    public CoreStackListener() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        this.cfn = Utils.sdkClient(CloudFormationClient.builder(), CloudFormationClient.SERVICE_NAME);
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(SNSEvent event, Context context) {
        LOGGER.info(Utils.toJson(event));

        List<SNSEvent.SNSRecord> records = event.getRecords();
        SNSEvent.SNS sns = records.get(0).getSNS();
        String message = sns.getMessage();

        CloudFormationEvent cloudFormationEvent = CloudFormationEventDeserializer.deserialize(message);

        // CloudFormation sends SNS notifications for every resource in a stack going through each status change.
        // We want to process the resources of the saas-boost-core.yaml CloudFormation stack only after the stack
        // has finished being created or updated so we don't trigger anything downstream prematurely.
        if (filter(cloudFormationEvent)) {
            String stackName = cloudFormationEvent.getStackName();
            String stackStatus = cloudFormationEvent.getResourceStatus();
            LOGGER.info("Stack " + stackName + " is in status " + stackStatus);

            // We're looking for ECR repository resources in a CREATE_COMPLETE state. There could be multiple
            // ECR repos provisioned depending on how the application services are configured.
            try {
                ListStackResourcesResponse resources = cfn.listStackResources(req -> req
                        .stackName(cloudFormationEvent.getStackId())
                );
                for (StackResourceSummary resource : resources.stackResourceSummaries()) {
//                    LOGGER.debug("Processing resource {} {} {} {}", resource.resourceType(),
//                            resource.resourceStatusAsString(), resource.logicalResourceId(),
//                            resource.physicalResourceId());
                    if ("CREATE_COMPLETE".equals(resource.resourceStatusAsString())) {
                        if ("AWS::ECR::Repository".equals(resource.resourceType())) {
                            String ecrRepo = resource.physicalResourceId();
                            String serviceName = resource.logicalResourceId();
                            LOGGER.info("Publishing appConfig update event for ECR repository {} {}", serviceName,
                                    ecrRepo);
                            Map<String, Object> systemApiRequest = new HashMap<>();
                            systemApiRequest.put("resource", "settings/config/" + serviceName + "/ECR_REPO");
                            systemApiRequest.put("method", "PUT");
                            systemApiRequest.put("body", Utils.toJson(Map.of("value", ecrRepo)));
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, SYSTEM_API_CALL,
                                    systemApiRequest);
                        } else if ("AWS::Route53::HostedZone".equals(resource.resourceType())) {
                            // Make this an event vs directly calling the Settings Service API because when this
                            // CloudFormation stack first completes, the Settings Service may not even exist yet
                            // Could also look at matching against UPDATE_COMPLETE
//                            String hostedZoneId = resource.physicalResourceId();
//                            LOGGER.info("Publishing appConfig update event for Route53 hosted zone {}", hostedZoneId);
//                            Map<String, Object> systemApiRequest = new HashMap<>();
//                            systemApiRequest.put("resource", "settings/HOSTED_ZONE");
//                            systemApiRequest.put("method", "PUT");
//                            //systemApiRequest.put("body", Utils.toJson(Map.of("value", ecrRepo)));
//                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, SYSTEM_API_CALL,
//                                    systemApiRequest);
                        }
                    }
                }
            } catch (SdkServiceException cfnError) {
                LOGGER.error("cfn:ListStackResources error", cfnError);
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }
        }
        return null;
    }

    protected static boolean filter(CloudFormationEvent cloudFormationEvent) {
        return ("AWS::CloudFormation::Stack".equals(cloudFormationEvent.getResourceType())
                && cloudFormationEvent.getStackName().startsWith("sb-" + SAAS_BOOST_ENV + "-core-")
                && EVENTS_OF_INTEREST.contains(cloudFormationEvent.getResourceStatus()));
    }

}
