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
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

import java.util.*;
import java.util.regex.Matcher;

public class CoreStackListener implements RequestHandler<SNSEvent, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreStackListener.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String SYSTEM_API_CALL = "System API Call";
    private static final String EVENT_SOURCE = "saas-boost";
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

        String type = cloudFormationEvent.getResourceType();
        String stackId = cloudFormationEvent.getStackId();
        String stackName = cloudFormationEvent.getStackName();
        String stackStatus = cloudFormationEvent.getResourceStatus();

        // CloudFormation sends SNS notifications for every resource in a stack going through each status change.
        // We're only interested in the stack complete event.
        List<String> eventsOfInterest = Arrays.asList("CREATE_COMPLETE", "UPDATE_COMPLETE");
        if ("AWS::CloudFormation::Stack".equals(type) && stackName.startsWith("sb-" + SAAS_BOOST_ENV + "-core-")
                && eventsOfInterest.contains(stackStatus)) {
            LOGGER.info(Utils.toJson(event));
            LOGGER.info("Stack " + stackName + " is in status " + stackStatus);
            final String stackIdName = stackId;
            try {
                ListStackResourcesResponse resources = cfn.listStackResources(req -> req.stackName(stackIdName));
                for (StackResourceSummary resource : resources.stackResourceSummaries()) {
                    String resourceType = resource.resourceType();
                    String physicalResourceId = resource.physicalResourceId();
                    String resourceStatus = resource.resourceStatusAsString();
                    String logicalId = resource.logicalResourceId();
                    LOGGER.info("Processing resource {} {} {} {}", resourceType, resourceStatus, logicalId,
                            physicalResourceId);
                    if ("CREATE_COMPLETE".equals(resourceStatus)) {
                        if ("AWS::ECR::Repository".equals(resourceType)) {
                            LOGGER.info("Publishing appConfig update event for ECR repository {} {}", logicalId,
                                    physicalResourceId);
                            // Logical ID is the service name
                            // Physical ID is the repo name
                            Map<String, Object> systemApiRequest = new HashMap<>();
                            systemApiRequest.put("resource", "settings/config/" + logicalId + "/ECR_REPO");
                            systemApiRequest.put("method", "PUT");
                            systemApiRequest.put("body", Utils.toJson(Map.of("value", physicalResourceId)));
                            publishEvent(systemApiRequest, SYSTEM_API_CALL);
                        }
                    }
                }
            } catch (SdkServiceException cfnError) {
                LOGGER.error("cfn:ListStackResources error", cfnError);
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }
        } else {
            //LOGGER.info("Skipping CloudFormation notification {} {} {} {}", stackId, type, stackName, stackStatus);
        }
        return null;
    }

    protected boolean snsMessageFilter(String message) {
        return true;
    }

    protected void publishEvent(Map<String, Object> eventBridgeDetail, String detailType) {
        try {
            PutEventsRequestEntry systemEvent = PutEventsRequestEntry.builder()
                    .eventBusName(SAAS_BOOST_EVENT_BUS)
                    .detailType(detailType)
                    .source(EVENT_SOURCE)
                    .detail(Utils.toJson(eventBridgeDetail))
                    .build();
            PutEventsResponse eventBridgeResponse = eventBridge.putEvents(r -> r
                    .entries(systemEvent)
            );
            for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
                if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                    LOGGER.info("Put event success {} {}", entry.toString(), systemEvent.toString());
                } else {
                    LOGGER.error("Put event failed {}", entry.toString());
                }
            }
        } catch (SdkServiceException eventBridgeError) {
            LOGGER.error("events::PutEvents", eventBridgeError);
            LOGGER.error(Utils.getFullStackTrace(eventBridgeError));
            throw eventBridgeError;
        }
    }
}
