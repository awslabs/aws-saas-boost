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
import software.amazon.awssdk.services.cloudformation.model.ListStackResourcesResponse;
import software.amazon.awssdk.services.cloudformation.model.ResourceStatus;
import software.amazon.awssdk.services.cloudformation.model.StackResourceSummary;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.ecr.model.Tag;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.util.*;

public class CoreStackListener implements RequestHandler<SNSEvent, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoreStackListener.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String EVENT_SOURCE = "saas-boost";
    private static final Collection<String> EVENTS_OF_INTEREST = Collections.unmodifiableCollection(
            Arrays.asList("CREATE_COMPLETE", "UPDATE_COMPLETE"));
    private final CloudFormationClient cfn;
    private final EventBridgeClient eventBridge;
    private final EcrClient ecr;

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
        this.ecr = Utils.sdkClient(EcrClient.builder(), EcrClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(SNSEvent event, Context context) {
        LOGGER.info(Utils.toJson(event));

        // ARN: arn:<partition>:<service>:<region>:<accountId>:<itemType>/<itemName>
        final String[] thisLambdaArn = context.getInvokedFunctionArn().split(":");
        final String partition = thisLambdaArn[1];
        final String region = thisLambdaArn[3];
        final String accountId = thisLambdaArn[4];

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
                Map<String, Object> appConfig = new HashMap<>();
                Map<String, Object> services = new HashMap<>();
                for (StackResourceSummary resource : resources.stackResourceSummaries()) {
//                    LOGGER.debug("Processing resource {} {} {} {}", resource.resourceType(),
//                            resource.resourceStatusAsString(), resource.logicalResourceId(),
//                            resource.physicalResourceId());
                    // TODO or UPDATE_COMPLETE?
                    if (ResourceStatus.CREATE_COMPLETE == resource.resourceStatus()
                            && AwsResource.ECR_REPO.getResourceType().equals(resource.resourceType())) {
                        String ecrRepo = resource.physicalResourceId();
                        String ecrResourceArn = AwsResource.ECR_REPO.formatArn(partition, region, accountId, ecrRepo);
                        LOGGER.info("Listing tags for ECR repo {}", ecrRepo);
                        ListTagsForResourceResponse response = ecr.listTagsForResource(request -> request
                                .resourceArn(ecrResourceArn));
                        String serviceName = resource.logicalResourceId();
                        String serviceNameContext = "Read from Template";
                        if (response.hasTags()) {
                            for (Tag tag : response.tags()) {
                                if ("Name".equalsIgnoreCase(tag.key())) {
                                    serviceName = tag.value();
                                    serviceNameContext = "Read from Tag";
                                }
                            }
                        }
                        LOGGER.info("Publishing appConfig update event for ECR repository {}({}) {}",
                                serviceName,
                                serviceNameContext,
                                ecrRepo);
                        services.put(serviceName, Map.of("containerRepo", ecrRepo));
                    } else if (ResourceStatus.CREATE_COMPLETE.equals(resource.resourceStatus())
                            || ResourceStatus.UPDATE_COMPLETE.equals(resource.resourceStatus())) {
                        if ("AWS::Route53::HostedZone".equals(resource.resourceType())) {
                            // When CloudFormation stack first completes, the Settings Service won't even exist yet.
                            String hostedZoneId = resource.physicalResourceId();
                            LOGGER.info("Publishing appConfig update event for Route53 hosted zone {}", hostedZoneId);
                            appConfig.put("hostedZone", hostedZoneId);
                        }
                    }
                }
                // Only fire one event for all the app config resources changes by this stack
                if (!services.isEmpty()) {
                    appConfig.put("services", services);
                }
                if (!appConfig.isEmpty()) {
                    Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                            "Application Configuration Resource Changed",
                            appConfig);
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
