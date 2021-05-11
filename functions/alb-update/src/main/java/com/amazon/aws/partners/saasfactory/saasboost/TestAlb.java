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

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;

import java.util.ArrayList;
import java.util.List;

public class TestAlb {
    public static void main(String[] args) throws Exception {

//        Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));
/*        ElasticLoadBalancingV2Client elbClient = ElasticLoadBalancingV2Client.builder()
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(AWS_REGION)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .endpointOverride(new URI("https://elasticloadbalancing." + AWS_REGION.id() + ".amazonaws.com")) // will break in China regions
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();*/
        try {
            ElasticLoadBalancingV2Client elbClient = ElasticLoadBalancingV2Client.builder()
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .build();
            // Is this event an ECR image action or a first-time deploy custom event?
            String source = "saas-boost";
            String tenantId = "ae928191-1555-408c-be76-1d0fceabd679";
            String status = "false";
            //arn:aws:elasticloadbalancing:us-west-2:573838506705:loadbalancer/
            String albId = "app/tenant-ae928191/2531796b463f0de7";
            String[] albParts = albId.split("/");  //index 1 will have the ALB Name
            List<String> albNames = new ArrayList<>();
            System.out.println("Alb portion = " + albParts[1]);
            albNames.add(albParts[1]);
            elbClient.describeTargetGroups();

            if ("saas-boost".equals(source)) {
                DescribeLoadBalancersResponse response = elbClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(albNames).build());
                LoadBalancer loadBalancer = response.loadBalancers().get(0);
                System.out.println(("Load Balancer " + loadBalancer.toString()));

                DescribeListenersResponse listenersResponse = elbClient.describeListeners(DescribeListenersRequest.builder().loadBalancerArn(loadBalancer.loadBalancerArn()).build());
                System.out.println("Listener " + listenersResponse.listeners().get(0).toString());
                Listener listener = listenersResponse.listeners().get(0);

                DescribeRulesResponse rulesResponse = elbClient.describeRules(DescribeRulesRequest.builder().
                        listenerArn(listener.listenerArn()).
                        build());
                Action action;
                if ("false".equalsIgnoreCase(status)) {
                    //this means we are disabling the tenant.  Set to fixed response with HTML
                    action = Action.builder()
                            .fixedResponseConfig(FixedResponseActionConfig.builder()
                                    .messageBody("<html><body>Access to your application is disabled. Contact our support if you have questions.</body></html")
                                    .contentType("text/html")
                                    .statusCode("200")
                                    .build())
                            .type(ActionTypeEnum.FIXED_RESPONSE)
                            .build();
                } else {
                    //enable the tenant so set to target group for the tenant
                    //get the Target Group ARN and build the forward action

                    DescribeTargetGroupsResponse targetGroupsResponse = elbClient.describeTargetGroups(DescribeTargetGroupsRequest.builder()
                            .names(albNames) //target group names same as ALB names in format of tenant-shortid
                            .build());
                    TargetGroup targetGroup = targetGroupsResponse.targetGroups().get(0);
                    TargetGroupTuple tgTuple = TargetGroupTuple.builder()
                            .targetGroupArn(targetGroup.targetGroupArn())
                            .weight(1)
                            .build();
                    action = Action.builder()
                            .forwardConfig(ForwardActionConfig.builder()
                                    .targetGroups(tgTuple)
                                    .build())
                            .type(ActionTypeEnum.FORWARD)
                            .build();
                }

                for (Rule rule : rulesResponse.rules()) {
                    System.out.println(rule.toString());
                   //remove existing action and add new action
                   // elbClient.deleteRule(DeleteRuleRequest.builder().ruleArn(rule.ruleArn()).build());

                    RuleCondition condition;
                    if (!rule.isDefault()) {
                        condition = RuleCondition.builder()
/*                                .pathPatternConfig(PathPatternConditionConfig.builder()
                                        .values("*")
                                        .build())*/
                                .field("path-pattern")
                                .values("*")
                                .build();
                        elbClient.modifyRule(ModifyRuleRequest.builder().ruleArn(rule.ruleArn()).conditions(condition).actions(action).build());
                    } else {
                       //elbClient.modifyRule(ModifyRuleRequest.builder().ruleArn(rule.ruleArn()).actions(action).build());
                    }

/*                    elbClient.createRule(CreateRuleRequest.builder()
                            .listenerArn(listener.listenerArn())
                            .actions(action)
                            .conditions(condition)
                            .priority(Integer.valueOf(rule.priority()))
                            .build());*/
                }
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.out.println(Utils.getFullStackTrace(e));
        }
    }
}
