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
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public class OnboardingNotification implements RequestHandler<SNSEvent, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(OnboardingNotification.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String TENANT_DEPLOY_LAMBDA = System.getenv("TENANT_DEPLOY_LAMBDA");
    private static final String SYSTEM_API_CALL = "System API Call";
    private static final String UPDATE_TENANT_RESOURCES = "Tenant Update Resources";
    private static final String BILLING_SETUP = "Billing Tenant Setup";
    private static final String BILLING_DISABLE = "Billing Tenant Disable";
    private static final String EVENT_SOURCE = "saas-boost";
    private final CloudFormationClient cfn;
    private final LambdaClient lambda;
    private final EventBridgeClient eventBridge;

    public OnboardingNotification() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        if (Utils.isBlank(TENANT_DEPLOY_LAMBDA)) {
            throw new IllegalStateException("Missing required environment variable TENANT_DEPLOY_LAMBDA");
        }
        this.cfn = Utils.sdkClient(CloudFormationClient.builder(), CloudFormationClient.SERVICE_NAME);
        this.lambda = Utils.sdkClient(LambdaClient.builder(), LambdaClient.SERVICE_NAME);
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
	public Object handleRequest(SNSEvent event, Context context) {
        //LOGGER.info(Utils.toJson(event));
        String type = null;
        String stackId = null;
        String stackName = null;
        String stackStatus = null;

        List<SNSEvent.SNSRecord> records = event.getRecords();
        SNSEvent.SNS sns = records.get(0).getSNS();
        String message = sns.getMessage();

        // Raw SNS message values are escaped JSON strings with \n instead of newlines and
        // single quotes instead of doubles around values
        for (String keyValue : message.split("\\n")) {
            // Each line will look like Key='Value'
            // For example ResourceStatus='CREATE_COMPLETE'
            // We'll be reckless and use substring instead of a regex to break it apart.
            String key = keyValue.substring(0, keyValue.indexOf("="));
            String value = keyValue.substring(keyValue.indexOf("=") + 2, keyValue.length() - 1);
            //LOGGER.info(key + " => " + value);
            switch (key) {
                case "ResourceType":
                    type = value;
                    break;
                case "ResourceStatus":
                    stackStatus = value;
                    break;
                case "LogicalResourceId":
                    stackName = value;
                    break;
                case "StackId":
                    stackId = value;
                    break;
            }
        }

        // CloudFormation sends SNS notifications for every resource in a stack going through each status change.
        // We're only interested in the stack complete event. Now that we have nested stacks for the optional
        // "extensions" like RDS and EFS, we have to check the stack name too
        if ("AWS::CloudFormation::Stack".equals(type) &&
                ("CREATE_COMPLETE".equals(stackStatus) || "CREATE_FAILED".equals(stackStatus)
                        || "UPDATE_COMPLETE".equals(stackStatus)
                        || "DELETE_COMPLETE".equals(stackStatus)
                        || "DELETE_FAILED".equals(stackStatus))
                && stackName.startsWith("Tenant-")) {
            LOGGER.info(Utils.toJson(event));
            LOGGER.info("Beginning post-onboarding flow. Stack " + stackName + " is in status " + stackStatus);
            DescribeStacksResponse stacks = cfn.describeStacks(DescribeStacksRequest
                    .builder()
                    .stackName(stackId)
                    .build()
            );
            Stack stack = stacks.stacks().get(0);
            String tenantId = null;
            for (Parameter parameter : stack.parameters()) {
                if ("TenantId".equals(parameter.parameterKey())) {
                    tenantId = parameter.parameterValue();
                    break;
                }
            }

            if ("CREATE_COMPLETE".equals(stackStatus) || "UPDATE_COMPLETE".equals(stackStatus)) {
                // We need the parameters and outputs from the stack
                String ecrRepo = null;
                String dbHost = null;
                String albName = null;
                String billingPlan = null;

                // Gather up the AWS Console URLs for the resources we're interested in
                Map<String, String> consoleResources = new HashMap<>();
                consoleResources.put(AwsConsoleUrl.CLOUDFORMATION.name(), AwsConsoleUrl.CLOUDFORMATION.formatUrl(AWS_REGION, stack.stackId()));

                for (Parameter parameter : stack.parameters()) {
                    if ("ContainerRepository".equals(parameter.parameterKey())) {
                        ecrRepo = parameter.parameterValue();
                        break;
                    }
                }
                LOGGER.info("Stack Outputs:");
                for (Output output : stack.outputs()) {
                    LOGGER.info("{} => {}", output.outputKey(), output.outputValue());
                    if ("RdsEndpoint".equals(output.outputKey())) {
                        if (Utils.isNotBlank(output.outputValue())) {
                            dbHost = output.outputValue();
                        }
                    }
                    if ("LoadBalancer".equals(output.outputKey())) {
                        if (Utils.isNotBlank(output.outputValue())) {
                            albName = output.outputValue();
                        }
                    }
                    if ("DNSName".equals(output.outputKey())) {
                        if (Utils.isNotBlank(output.outputValue())) {
                            consoleResources.put("LOAD_BALANCER_DNSNAME", output.outputValue());
                        }
                    }
                    if ("BillingPlan".equals(output.outputKey())) {
                        if (Utils.isNotBlank(output.outputValue())) {
                            billingPlan = output.outputValue();
                        }
                    }
                }

                // And we need the resources from the stack
                ListStackResourcesResponse resources = cfn.listStackResources(ListStackResourcesRequest
                        .builder()
                        .stackName(stackId)
                        .build()
                );
                String pipeline = null;
                for (StackResourceSummary resource : resources.stackResourceSummaries()) {
                    String resourceType = resource.resourceType();
                    String physicalResourceId = resource.physicalResourceId();
                    String resourceStatus = resource.resourceStatusAsString();
                    String logicalId = resource.logicalResourceId();
                    LOGGER.debug(("processing resource type: " + resourceType));
                    //LOGGER.info(String.format("%s  %s   %s", resourceType, physicalResourceId, resourceStatus));
                    if ("AWS::CodePipeline::Pipeline".equals(resourceType) && "CREATE_COMPLETE".equals(resourceStatus)) {
                        pipeline = physicalResourceId;
                        consoleResources.put(AwsConsoleUrl.CODE_PIPELINE.name(), AwsConsoleUrl.CODE_PIPELINE.formatUrl(AWS_REGION, pipeline));
                    } else if ("AWS::CloudFormation::Stack".equals(resourceType) && "rds".equals(logicalId)) {
                        //this is the rds sub-stack so get the cluster and instance ids
                        getRdsResources(physicalResourceId, consoleResources);
                    } else {
                        //match on the resource type and build url
                        for (AwsConsoleUrl consoleUrl : AwsConsoleUrl.values()) {
                            if (consoleUrl.getResourceType().equalsIgnoreCase(resourceType)) {
                                if ("AWS::ElasticLoadBalancingV2::Listener".equals(resourceType)) {
                                    //the ALB physical id is the short tenant id
                                    physicalResourceId = "tenant-" + tenantId.split("-")[0];
                                } else if ("AWS::Logs::LogGroup".equals(resourceType)) {
                                    //need to replace / with $252F for the url path
                                    physicalResourceId = physicalResourceId.replaceAll("/", Matcher.quoteReplacement("$252F"));
                                }
                                consoleResources.put(consoleUrl.name(), consoleUrl.formatUrl(AWS_REGION, physicalResourceId));
                            }
                        }
                    }
                }

                // Persist the tenant specific things as tenant settings
                if (dbHost != null) {
                    LOGGER.info("Saving tenant database host setting");
                    Map<String, Object> systemApiRequest = new HashMap<>();
                    systemApiRequest.put("resource", "settings/tenant/" + tenantId + "/DB_HOST");
                    systemApiRequest.put("method", "PUT");
                    systemApiRequest.put("body", "{\"name\":\"DB_HOST\", \"value\":\"" + dbHost + "\"}");
                    publishEvent(systemApiRequest, SYSTEM_API_CALL);
                }
                if (albName != null) {
                    LOGGER.info("Saving tenant ALB setting");
                    Map<String, Object> systemApiRequest = new HashMap<>();
                    systemApiRequest.put("resource", "settings/tenant/" + tenantId + "/ALB");
                    systemApiRequest.put("method", "PUT");
                    systemApiRequest.put("body", "{\"name\":\"ALB\", \"value\":\"" + albName + "\"}");
                    publishEvent(systemApiRequest, SYSTEM_API_CALL);
                }

                // Update the onboarding status from provisioning to provisioned
                LOGGER.info("Updating onboarding status to {}", "CREATE_COMPLETE".equals(stackStatus) ? "provisioned" : "updated");
                Map<String, Object> systemApiRequest = new HashMap<>();
                systemApiRequest.put("resource", "onboarding/status");
                systemApiRequest.put("method", "PUT");
                systemApiRequest.put("body", "{\"tenantId\":\"" + tenantId + "\", \"stackStatus\":\"" + stackStatus + "\"}");
                publishEvent(systemApiRequest, SYSTEM_API_CALL);

                // Update the tenant resources map
                LOGGER.info("Updating tenant resources AWS console links");
                //build string of the resources
                StringBuilder resourcesSb = new StringBuilder("{");
                String prefix = "";
                for (Map.Entry<String, String> entry : consoleResources.entrySet()) {
                    resourcesSb
                            .append(prefix)
                            .append("\"")
                            .append(entry.getKey())
                            .append("\":\"")
                            .append(entry.getValue())
                            .append("\"");
                    prefix = ",";
                }
                resourcesSb.append("}");
                //LOGGER.info("Console Resources Map String:" + resourcesSb.toString());
                Map<String, Object> updateConsoleResourcesEventDetail = new HashMap<>();
                updateConsoleResourcesEventDetail.put("tenantId", tenantId);
                updateConsoleResourcesEventDetail.put("resources", resourcesSb.toString());
                publishEvent(updateConsoleResourcesEventDetail, UPDATE_TENANT_RESOURCES);

                // If there's a billing plan for this tenant, publish the event so they get
                // wired up to the 3rd party system
                if (Utils.isNotBlank(billingPlan)) {
                    LOGGER.info("Triggering tenant billing setup");
                    Map<String, Object> updateBillingPlanEventDetail = new HashMap<>();
                    updateBillingPlanEventDetail.put("tenantId", tenantId);
                    updateBillingPlanEventDetail.put("planId", billingPlan);
                    publishEvent(updateBillingPlanEventDetail, BILLING_SETUP);
                }

                if ("CREATE_COMPLETE". equals(stackStatus)) {
                    // Invoke this tenant's deployment pipeline... probably should be done through a service API call
                    LOGGER.info("Triggering tenant deployment pipeline");
                    // First, build an event object that looks similar to the object generated by
                    // an ECR image push (which is what the deploy method uses).
                    Map<String, Object> lambdaEvent = new HashMap<>();
                    lambdaEvent.put("source", "tenant-onboarding");
                    lambdaEvent.put("region", AWS_REGION);
                    lambdaEvent.put("account", context.getInvokedFunctionArn().split(":")[4]);
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("repository-name", ecrRepo);
                    detail.put("image-tag", "latest");
                    detail.put("tenantId", tenantId);
                    detail.put("pipeline", pipeline);
                    lambdaEvent.put("detail", detail);
                    SdkBytes payload = SdkBytes.fromString(Utils.toJson(lambdaEvent), Charset.forName("UTF-8"));
                    // Now invoke the deployment lambda async... probably move this to the tenant service
                    try {
                        InvokeResponse deployResponse = lambda.invoke(r -> r
                                .functionName(TENANT_DEPLOY_LAMBDA)
                                .invocationType(InvocationType.EVENT)
                                .payload(payload)
                        );
                    } catch (SdkServiceException lambdaError) {
                        LOGGER.error("lambda:Invoke");
                        LOGGER.error(Utils.getFullStackTrace(lambdaError));
                        throw lambdaError;
                    }
                }
            } else if ("CREATE_FAILED".equals(stackStatus)) {
                // Update the onboarding status from provisioning to failed
                LOGGER.info("Updating onboarding status to failed");
                Map<String, Object> systemApiRequest = new HashMap<>();
                systemApiRequest.put("resource", "onboarding/status");
                systemApiRequest.put("method", "PUT");
                systemApiRequest.put("body", "{\"tenantId\":\"" + tenantId + "\", \"stackStatus\":\"" + stackStatus + "\"}");
                publishEvent(systemApiRequest, SYSTEM_API_CALL);
            } else if ("DELETE_COMPLETE".equals(stackStatus)) {
                // Delete the tenant settings via the Settings service
                LOGGER.info("Deleting tenant settings for tenant " + tenantId);
                Map<String, Object> deleteTenantSettingsRequest = new HashMap<>();
                deleteTenantSettingsRequest.put("resource", "settings/tenant/" + tenantId);
                deleteTenantSettingsRequest.put("method", "DELETE");
                publishEvent(deleteTenantSettingsRequest, SYSTEM_API_CALL);

                //Publish event for tenant billing disable
                LOGGER.info("Triggering tenant billing disable event to cancel subscriptions");
                Map<String, Object> updateBillingPlanEventDetail = new HashMap<>();
                updateBillingPlanEventDetail.put("tenantId", tenantId);
                publishEvent(updateBillingPlanEventDetail, BILLING_DISABLE);

                // Update the onboarding status to deleted
                LOGGER.info("Updating onboarding status to delete completed");
                Map<String, Object> updateTenantOnboardingStatusRequest = new HashMap<>();
                updateTenantOnboardingStatusRequest.put("resource", "onboarding/status");
                updateTenantOnboardingStatusRequest.put("method", "PUT");
                updateTenantOnboardingStatusRequest.put("body", "{\"tenantId\":\"" + tenantId + "\", \"stackStatus\":\"" + stackStatus + "\"}");
                publishEvent(updateTenantOnboardingStatusRequest, SYSTEM_API_CALL);
            } else if ("DELETE_FAILED".equals(stackStatus)) {
                // Update the onboarding status to deleted
                LOGGER.info("Updating onboarding status to failed");
                Map<String, Object> systemApiRequest = new HashMap<>();
                systemApiRequest.put("resource", "onboarding/status");
                systemApiRequest.put("method", "PUT");
                systemApiRequest.put("body", "{\"tenantId\":\"" + tenantId + "\", \"stackStatus\":\"" + stackStatus + "\"}");
                publishEvent(systemApiRequest, SYSTEM_API_CALL);
            }
        } else {
            //LOGGER.info("Skipping CloudFormation notification {} {} {} {}", stackId, type, stackName, stackStatus);
        }
        return null;
    }

    private void getRdsResources(String stackId, Map<String, String> consoleResources) {
        try {
            ListStackResourcesResponse resources = cfn.listStackResources(request -> request.stackName(stackId));
            for (StackResourceSummary resource : resources.stackResourceSummaries()) {
                String resourceType = resource.resourceType();
                String physicalResourceId = resource.physicalResourceId();
                AwsConsoleUrl url = null;
                if (resourceType.equalsIgnoreCase(AwsConsoleUrl.RDS_INSTANCE.getResourceType())) {
                    url = AwsConsoleUrl.RDS_INSTANCE;
                } else if (resourceType.equalsIgnoreCase(AwsConsoleUrl.RDS_CLUSTER.getResourceType())) {
                    url = AwsConsoleUrl.RDS_CLUSTER;
                }
                if (url != null) {
                    consoleResources.put(url.name(), url.formatUrl(AWS_REGION, physicalResourceId));
                }
            }
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cfn:ListStackResources error", cfnError);
            LOGGER.error(Utils.getFullStackTrace(cfnError));
            throw cfnError;
        }
    }

    private void publishEvent(Map<String, Object> eventBridgeDetail, String detailType) {
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

    enum AwsConsoleUrl {

        RDS_CLUSTER("https://%s.console.aws.amazon.com/rds/home#database:id=%s;is-cluster=true",
                "AWS::RDS::DBCluster", false),
        RDS_INSTANCE("https://%s.console.aws.amazon.com/rds/home?region=%s#dbinstance:id=%s",
                "AWS::RDS::DBInstance", true),
        ECS_CLUSTER("https://%s.console.aws.amazon.com/ecs/home#/clusters/%s/",
                "AWS::ECS::Cluster", false),
        ECS_CLUSTER_LOG_GROUP("https://%s.console.aws.amazon.com/cloudwatch/home?region=%s#logsV2:log-groups/log-group/%s",
                "AWS::Logs::LogGroup", true),
        VPC("https://%s.console.aws.amazon.com/vpc/home?region=%s#vpcs:search=%s",
                "AWS::EC2::VPC", true),
        CODE_PIPELINE("https://%s.console.aws.amazon.com/codesuite/codepipeline/pipelines/%s/view",
                "AWS::CodePipeline::Pipeline", false),
        ECR_REPO("https://%s.console.aws.amazon.com/ecr/repositories/%s/",
                "AWS::ECR::Repository", false),
        ALB("https://%s.console.aws.amazon.com/ec2/v2/home?region=%s#LoadBalancers:search=%s;sort=loadBalancerName",
                "AWS::ElasticLoadBalancingV2::Listener", true),
        CLOUDFORMATION("https://%s.console.aws.amazon.com/cloudformation/home?region=%s#/stacks/stackinfo?filteringText=&filteringStatus=active&viewNested=true&hideStacks=false&stackId=%s",
                "AWS::CloudFormation::Stack", true);

        private final String urlFormat;
        private final String resourceType;
        private final boolean repeatRegion;

        AwsConsoleUrl(String urlFormat, String resourceType, boolean repeatRegion) {
            this.urlFormat = urlFormat;
            this.resourceType = resourceType;
            this.repeatRegion = repeatRegion;
        }

        public String getUrlFormat() {
            return this.urlFormat;
        }

        public String getResourceType() {
            return this.resourceType;
        }

        public String formatUrl(String region, String resourceId) {
            String url;
            try {
                if (this.repeatRegion) {
                    url = String.format(this.urlFormat, region, region, resourceId);
                } else {
                    url = String.format(this.urlFormat, region, resourceId);
                }
            } catch (IllegalFormatException e) {
                LOGGER.error("Error formatting URL for {}", this.name(), e);
                LOGGER.error(Utils.getFullStackTrace(e));
                throw new RuntimeException(e);
            }
            return url;
        }
    }
}