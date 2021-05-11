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
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;

import java.util.*;

public class AlbSetListenerRule implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(AlbSetListenerRule.class);
    private final static String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private final static String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private final static String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private final ElasticLoadBalancingV2Client elb;

    public AlbSetListenerRule() {
        long startTimeMillis = System.currentTimeMillis();
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
        this.elb = Utils.sdkClient(ElasticLoadBalancingV2Client.builder(), ElasticLoadBalancingV2Client.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
	public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        String source = (String) event.get("source");
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String tenantId = (String) detail.get("tenantId");
        String status = (String) detail.get("status");
        LOGGER.info("Processing Tenant Update of Tenant: {} with Status {}", tenantId, status);

        // ALB id will look like app/tenant-ae928191/2531796b463f0de7
        // so index 1 from the split will have the ALB name
        String albId = getAlbId(tenantId);
        List<String> albNames = Arrays.asList(albId.split("/")[1]);

        if ("saas-boost".equals(source)) {
            DescribeLoadBalancersResponse loadBalancersResponse = elb.describeLoadBalancers(request -> request.names(albNames));
            LoadBalancer loadBalancer = loadBalancersResponse.loadBalancers().get(0);
            LOGGER.info("Updating tenant load balancer {}", loadBalancer.toString());

            DescribeListenersResponse listenersResponse = elb.describeListeners(request -> request.loadBalancerArn(loadBalancer.loadBalancerArn()));
            Listener listener = listenersResponse.listeners().get(0);
            LOGGER.info("Updating load balancer listener {}", listener.toString());

            DescribeRulesResponse rulesResponse = elb.describeRules(request -> request.listenerArn(listener.listenerArn()));
            Action action = null;
            if ("false".equalsIgnoreCase(status)) {
                // Disabled tenant. Set to fixed response with HTML.
                action = Action.builder()
                        .fixedResponseConfig(FixedResponseActionConfig.builder()
                                .messageBody("<html><body>Access to your application is disabled. Contact our support if you have questions.</body></html")
                                .contentType("text/html")
                                .statusCode("200")
                                .build())
                        .type(ActionTypeEnum.FIXED_RESPONSE)
                        .build();
            } else {
                // Enabled tenant. Set the target group ARN as the forward action
                DescribeTargetGroupsResponse targetGroupsResponse = elb.describeTargetGroups(request -> request
                        .names(albNames) // target group names same as ALB names in format of tenant-shortid
                );
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
                if (!rule.isDefault()) {
                    LOGGER.info("Updating listener rule {}", rule.toString());
                    RuleCondition condition = RuleCondition.builder()
                            .field("path-pattern")
                            .values("*")
                            .build();
                    elb.modifyRule(ModifyRuleRequest.builder()
                            .ruleArn(rule.ruleArn())
                            .conditions(condition)
                            .actions(action)
                            .build()
                    );
                }
            }
        }

        return null;
    }

    private String getAlbId(String tenantId) {
        // Invoke SaaS Boost private API to get Tenant ALB id
        String albId = null;
        final String resource = "settings/tenant/" + tenantId + "/ALB";
        try {
            ApiRequest request = ApiRequest.builder()
                    .resource(resource)
                    .method("GET")
                    .build();
            SdkHttpFullRequest apiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, request);
            String responseBody = ApiGatewayHelper.signAndExecuteApiRequest(apiRequest, API_TRUST_ROLE, "AlbUpdate");
            Map<String, String> getSettingResponse = Utils.fromJson(responseBody, HashMap.class);
            if (null == getSettingResponse) {
                throw new RuntimeException("responseBody is invalid");
            }
            albId = getSettingResponse.get("value");
        } catch (Exception e) {
            LOGGER.error("getAlbId: Can't invoke API for {}", resource);
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return albId;
    }
}