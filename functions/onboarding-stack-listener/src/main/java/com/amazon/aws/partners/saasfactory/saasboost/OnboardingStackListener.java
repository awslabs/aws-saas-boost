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
import java.util.regex.Pattern;

public class OnboardingStackListener implements RequestHandler<SNSEvent, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingStackListener.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String SYSTEM_API_CALL = "System API Call";
    private static final String UPDATE_TENANT_RESOURCES = "Tenant Update Resources";
    private static final String BILLING_SETUP = "Billing Tenant Setup";
    private static final String BILLING_DISABLE = "Billing Tenant Disable";
    private static final String EVENT_SOURCE = "saas-boost";
    private static final Pattern STACK_NAME_PATTERN = Pattern
            .compile("^sb-" + SAAS_BOOST_ENV + "-tenant-[a-z0-9]{8}$");
    private static final Collection<String> EVENTS_OF_INTEREST = Collections.unmodifiableCollection(
            Arrays.asList("CREATE_COMPLETE", "CREATE_FAILED", "UPDATE_COMPLETE", "DELETE_COMPLETE", "DELETE_FAILED"));
    private final CloudFormationClient cfn;
    private final EventBridgeClient eventBridge;

    public OnboardingStackListener() {
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
        //LOGGER.info(Utils.toJson(event));

        List<SNSEvent.SNSRecord> records = event.getRecords();
        SNSEvent.SNS sns = records.get(0).getSNS();
        String message = sns.getMessage();

        CloudFormationEvent cloudFormationEvent = CloudFormationEventDeserializer.deserialize(message);

        // CloudFormation sends SNS notifications for every resource in a stack going through each status change.
        // We want to process the resources of the tenant-onboarding.yaml CloudFormation stack only after the
        // stack has finished being created or updated so we don't trigger anything downstream prematurely.
        if (filter(cloudFormationEvent)) {
            LOGGER.info(Utils.toJson(event));
            String stackName = cloudFormationEvent.getStackName();
            String stackStatus = cloudFormationEvent.getResourceStatus();
            String stackId = cloudFormationEvent.getStackId();
            LOGGER.info("Stack " + stackName + " is in status " + stackStatus);

            String tenantId = null;
            String domainName = null;
            String hostedZone = null;
            String subdomain = null;
            try {
                DescribeStacksResponse stacks = cfn.describeStacks(req -> req
                        .stackName(stackId)
                );
                Stack stack = stacks.stacks().get(0);
                for (Parameter parameter : stack.parameters()) {
                    if ("TenantId".equals(parameter.parameterKey())) {
                        tenantId = parameter.parameterValue();
                    }
                    if ("DomainName".equals(parameter.parameterKey())) {
                        domainName = parameter.parameterValue();
                    }
                    if ("HostedZoneId".equals(parameter.parameterKey())) {
                        hostedZone = parameter.parameterValue();
                    }
                    if ("TenantSubDomain".equals(parameter.parameterKey())) {
                        subdomain = parameter.parameterValue();
                    }
                }

                // The public URL to access this tenant's environment is either a custom DNS entry we made a
                // Route53 record set for and pointed at the load balancer, or we can fall back to the ALB's DNS.
                String hostname = null;
                if (Utils.isNotBlank(domainName) && Utils.isNotBlank(hostedZone) && Utils.isNotBlank(subdomain)) {
                    hostname = subdomain + "." + domainName;
                } else {
                    if (stack.hasOutputs()) {
                        for (Output output : stack.outputs()) {
                            if ("DNSName".equals(output.outputKey())) {
                                hostname = output.outputValue();
                                break;
                            }
                        }
                    }
                }
                // Fire a tenant hostname changed event
                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE, "Tenant Hostname Changed",
                        Map.of("tenantId", tenantId, "hostname", hostname));
            } catch (SdkServiceException cfnError) {
                LOGGER.error("cfn:DescribeStacks error", cfnError);
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }

            if ("CREATE_COMPLETE".equals(stackStatus) || "UPDATE_COMPLETE".equals(stackStatus)) {
                // We'll use these to build the ARN string for resources that CloudFormation doesn't return the ARN
                // as either the physical or logical resource id
                final String[] lambdaArn = context.getInvokedFunctionArn().split(":");
                final String partition = lambdaArn[1];
                final String accountId = lambdaArn[4];
                final String stackIdName = stackId;

                // Now, collect up all of the provisioned resources for this tenant that we want to save with the
                // tenant record. Start with the "parent" CloudFormation stack.
                Map<String, Map<String, String>> tenantResources = new HashMap<>();
                tenantResources.put(AwsResource.CLOUDFORMATION.name(), Map.of(
                        "name", stackName,
                        "arn", stackId,
                        "consoleUrl", AwsResource.CLOUDFORMATION.formatUrl(AWS_REGION, stackId))
                );

                // Loop through all of the resources and grab the ones we need to save to the tenant record.
                ListStackResourcesResponse resources = cfn.listStackResources(req -> req.stackName(stackIdName));
                for (StackResourceSummary resource : resources.stackResourceSummaries()) {
                    String resourceType = resource.resourceType();
                    String physicalResourceId = resource.physicalResourceId();
                    String resourceStatus = resource.resourceStatusAsString();
                    String logicalId = resource.logicalResourceId();
                    LOGGER.info("Processing resource {} {} {} {}", resourceType, resourceStatus, logicalId, physicalResourceId);
                    if ("CREATE_COMPLETE".equals(resourceStatus)) {
                        if ("AWS::EC2::SecurityGroup".equals(resourceType) && "ECSSecurityGroup".equals(logicalId)) {
                            LOGGER.info("Saving ECS Security Group {} {}", logicalId, physicalResourceId);
                            tenantResources.put(AwsResource.ECS_SECURITY_GROUP.name(), Map.of(
                                    "name", physicalResourceId,
                                    "arn", AwsResource.ECS_SECURITY_GROUP.formatArn(partition, AWS_REGION, accountId, physicalResourceId),
                                    "consoleUrl", AwsResource.ECS_SECURITY_GROUP.formatUrl(AWS_REGION, physicalResourceId))
                            );
                        } else if ("AWS::EC2::Subnet".equals(resourceType)) {
                            // Process all the subnet resources together because we only want the 2 private subnets and
                            // there are other subnets in the stack which would end up overwriting the values in the
                            // resources map with whatever subnet happens to be last in the stack summary.
                            if ("SubnetPrivateA".equals(logicalId)) {
                                LOGGER.info("Saving Private Subnet {} {}", logicalId, physicalResourceId);
                                tenantResources.put(AwsResource.PRIVATE_SUBNET_A.name(), Map.of(
                                        "name", physicalResourceId,
                                        "arn", AwsResource.PRIVATE_SUBNET_A.formatArn(partition, AWS_REGION, accountId, physicalResourceId),
                                        "consoleUrl", AwsResource.PRIVATE_SUBNET_A.formatUrl(AWS_REGION, physicalResourceId))
                                );
                            } else if ("SubnetPrivateB".equals(logicalId)) {
                                LOGGER.info("Saving Private Subnet {} {}", logicalId, physicalResourceId);
                                tenantResources.put(AwsResource.PRIVATE_SUBNET_B.name(), Map.of(
                                        "name", physicalResourceId,
                                        "arn", AwsResource.PRIVATE_SUBNET_B.formatArn(partition, AWS_REGION, accountId, physicalResourceId),
                                        "consoleUrl", AwsResource.PRIVATE_SUBNET_B.formatUrl(AWS_REGION, physicalResourceId))
                                );
                            }
                        } else if ("AWS::EC2::RouteTable".equals(resourceType)) {
                            // Process all of the route table resources together because we only want the route table
                            // for the private subnets and there are other route tables in the stack which may end up
                            // overwriting the values in the resources map depending on which order they are listed in
                            // from the stack summary
                            if ("RouteTablePrivate".equals(logicalId)) {
                                LOGGER.info("Saving Private Route Table {} {}", logicalId, physicalResourceId);
                                tenantResources.put(AwsResource.PRIVATE_ROUTE_TABLE.name(), Map.of(
                                        "name", physicalResourceId,
                                        "arn", AwsResource.PRIVATE_ROUTE_TABLE.formatArn(partition, AWS_REGION, accountId, physicalResourceId),
                                        "consoleUrl", AwsResource.PRIVATE_ROUTE_TABLE.formatUrl(AWS_REGION, physicalResourceId))
                                );
                            }
                        } else if ("AWS::ServiceDiscovery::PrivateDnsNamespace".equals(resourceType)) {
                            if ("ServiceDiscoveryNamespace".equals(logicalId)) {
                                LOGGER.info("Saving Private DNS Namespace {} {}", logicalId, physicalResourceId);
                                AwsResource namespace = AwsResource.PRIVATE_SERVICE_DISCOVERY_NAMESPACE;
                                tenantResources.put(namespace.name(), Map.of(
                                        "name", physicalResourceId,
                                        "arn", namespace.formatArn(partition, AWS_REGION, accountId, physicalResourceId),
                                        "consoleUrl", namespace.formatUrl(AWS_REGION, physicalResourceId)
                                ));
                            }
                        } else {
                            // Match on the resource type and build the console url
                            for (AwsResource awsResource : AwsResource.values()) {
                                if (awsResource.getResourceType().equalsIgnoreCase(resourceType)) {
                                    if ("AWS::ElasticLoadBalancingV2::LoadBalancer".equals(resourceType)) {
                                        // CloudFormation returns the ARN for the physical id of the load balancer
                                        // The console url can use the name of the load balancer as a search string
                                        // and the name is the short tenant id
                                        tenantResources.put(awsResource.name(), Map.of(
                                                "name", physicalResourceId.substring(physicalResourceId.indexOf(":loadbalancer/") + 14),
                                                "arn", physicalResourceId,
                                                "consoleUrl", AwsResource.LOAD_BALANCER.formatUrl(AWS_REGION, "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantId.split("-")[0]))
                                        );
                                    } else if ("AWS::ElasticLoadBalancingV2::Listener".equals(resourceType)) {
                                        if ("HttpListener".equals(logicalId)) {
                                            LOGGER.info("Saving HTTP listener {} {}", logicalId, physicalResourceId);
                                            tenantResources.put(AwsResource.HTTP_LISTENER.name(), Map.of(
                                                    "name", physicalResourceId,
                                                    "arn", physicalResourceId,
                                                    // Same URL as the load balancer
                                                    "consoleUrl", AwsResource.LOAD_BALANCER.formatUrl(AWS_REGION, "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantId.split("-")[0]))
                                            );
                                        } else if ("HttpsListener".equals(logicalId)) {
                                            LOGGER.info("Saving HTTPS listener {} {}", logicalId, physicalResourceId);
                                            tenantResources.put(AwsResource.HTTPS_LISTENER.name(), Map.of(
                                                    "name", physicalResourceId,
                                                    "arn", physicalResourceId,
                                                    // Same URL as the load balancer
                                                    "consoleUrl", AwsResource.LOAD_BALANCER.formatUrl(AWS_REGION, "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantId.split("-")[0]))
                                            );
                                        }
                                    } else if ("AWS::Logs::LogGroup".equals(resourceType)) {
                                        //need to replace / with $252F for the url path
                                        //physicalResourceId = physicalResourceId.replaceAll("/", Matcher.quoteReplacement("$252F"));
                                    } else {
                                        // Don't overwrite something we've already set
                                        if (!tenantResources.containsKey(awsResource.name())) {
                                            LOGGER.info("Saving {} {} {}", awsResource.name(), logicalId, physicalResourceId);
                                            tenantResources.put(awsResource.name(), Map.of(
                                                    "name", physicalResourceId,
                                                    "arn", awsResource.formatArn(partition, AWS_REGION, accountId, physicalResourceId),
                                                    "consoleUrl", awsResource.formatUrl(AWS_REGION, physicalResourceId))
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Fire a tenant resources updated event
                LOGGER.info("Updating tenant resources AWS console links");
                Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                        "Tenant Resources Changed",
                        Map.of("tenantId", tenantId, "resources", Utils.toJson(tenantResources))
                );

//                // If there's a billing plan for this tenant, publish the event so they get
//                // wired up to the 3rd party system
//                if (Utils.isNotBlank(billingPlan)) {
//                    LOGGER.info("Triggering tenant billing setup");
//                    Map<String, Object> updateBillingPlanEventDetail = new HashMap<>();
//                    updateBillingPlanEventDetail.put("tenantId", tenantId);
//                    updateBillingPlanEventDetail.put("planId", billingPlan);
//                    publishEvent(updateBillingPlanEventDetail, BILLING_SETUP);
//                }

            }

            // Fire a stack status change event
            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                    "Onboarding Stack Status Changed",
                    Map.of("tenantId", tenantId, "stackId", stackId, "stackStatus", stackStatus));

            //TODO deal with a deleted stack canceling billing subscription
            //TODO deal with a created stack creating a billing subscription
        } else {
            //LOGGER.info("Skipping CloudFormation notification {} {} {} {}", stackId, type, stackName, stackStatus);
        }
        return null;
    }

    protected static boolean filter(CloudFormationEvent cloudFormationEvent) {
        return ("AWS::CloudFormation::Stack".equals(cloudFormationEvent.getResourceType())
                && STACK_NAME_PATTERN.matcher(cloudFormationEvent.getStackName()).matches()
                && EVENTS_OF_INTEREST.contains(cloudFormationEvent.getResourceStatus()));
    }

}
