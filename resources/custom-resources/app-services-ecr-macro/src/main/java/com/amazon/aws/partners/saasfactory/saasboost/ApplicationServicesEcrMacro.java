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

import java.util.*;

public class ApplicationServicesEcrMacro implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServicesEcrMacro.class);
    private static final String FRAGMENT = "fragment";
    private static final String REQUEST_ID = "requestId";
    private static final String TEMPLATE_PARAMETERS = "templateParameterValues";
    private static final String STATUS = "status";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAILURE";
    private static final String ERROR_MSG = "errorMessage";

    /**
     * CloudFormation macro to create a lists of ECR repository resources based on the length of the comma separated
     * list of application services passed as a template parameter. Returns failure if either "ApplicationServicse"
     * template parameter is missing.
     * @param event Lambda event containing the CloudFormation request id, fragment, and template parameters
     * @param context Lambda execution context
     * @return CloudFormation macro response of success or failure and the modified template fragment
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        Map<String, Object> response = new HashMap<>();
        response.put(REQUEST_ID, event.get(REQUEST_ID));
        response.put(STATUS, FAILURE);

        Map<String, Object> templateParameters = (Map<String, Object>) event.get(TEMPLATE_PARAMETERS);
        if (templateParameters.containsKey("ApplicationServices")) {
            Map<String, Object> template = (Map<String, Object>) event.get(FRAGMENT);
            if (template.containsKey("Resources")) {
                String servicesList = (String) templateParameters.get("ApplicationServices");
                if (Utils.isNotEmpty(servicesList)) {
                    String[] services = servicesList.split(",");
                    for (String service : services) {
                        // Each application service needs its own ECR repository
                        String ecrResourceName = ecrResourceName(service);
                        ((Map<String, Object>) template.get("Resources")).put(ecrResourceName, ecrResource(service));

                        // Define an EventBridge rule to capture image events on the repo
                        String eventRuleResourceName = eventRuleResourceName(service);
                        ((Map<String, Object>) template.get("Resources")).put(eventRuleResourceName,
                                eventRuleResource(service, ecrResourceName));

                        // And we need a Lambda permission for this rule to invoke the workload deploy function
                        ((Map<String, Object>) template.get("Resources")).put(eventRulePermissionName(service),
                                eventRulePermissionResource(eventRuleResourceName));
                    }
                    LOGGER.info(Utils.toJson(template));
                } else {
                    LOGGER.info("Empty ApplicationServices list. Skipping template modification.");
                }
            } else {
                LOGGER.warn("CloudFormation template fragment does not have Resources");
            }
            response.put(FRAGMENT, template);
            response.put(STATUS, SUCCESS);
        } else {
            LOGGER.error("Invalid template, missing parameter ApplicationServices");
            response.put(ERROR_MSG, "Invalid template, missing parameter ApplicationServices");
        }
        return response;
    }

    protected static String cloudFormationResourceName(String name) {
        return name != null ? name.replaceAll("[^A-Za-z0-9]", "") : null;
    }

    protected static String ecrResourceName(String serviceName) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("service name cannot be blank");
        }
        return cloudFormationResourceName(serviceName);
    }

    protected static Map<String, Object> ecrResource(String serviceName) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("service name cannot be blank");
        }

        Map<String, Object> resourceProperties = new LinkedHashMap<>();
        resourceProperties.put("EncryptionConfiguration", Collections
                .singletonMap("EncryptionType", "AES256")
        );
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("Key", "Name");
        tag.put("Value", serviceName.trim());
        resourceProperties.put("Tags", Collections.singletonList(tag));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("Type", "AWS::ECR::Repository");
        resource.put("Properties", resourceProperties);

        return resource;
    }

    protected static String eventRuleResourceName(String serviceName) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("service name cannot be blank");
        }
        return "ImageEventRule" + cloudFormationResourceName(serviceName);
    }

    /**
     * Generates an AWS::Events::Rule resource to trigger the workload deployment Lambda function when an ECR image
     * event occurs for a given repository.
     *
     * <p>Example: "Service A" returns
     * <blockquote><pre>
     * Type: AWS::Events::Rule
     * Properties:
     *   Name: !Sub sb-${Environment}-ecr-servicea
     *   EventPattern:
     *     source:
     *       - aws.ecr
     *     detail-type:
     *       - ECR Image Action
     *     detail:
     *       repository-name:
     *         - !Ref servicea
     *   State: ENABLED
     *   Targets:
     *     - Arn: !GetAtt WorkloadDeployLambda.Arn
     *       Id: !Sub sb-${Environment}-deploy-servicea
     * </pre></blockquote>
     * @param serviceName The name of the application service
     * @return an AWS::Events:Rule resource object
     */
    protected static Map<String, Object> eventRuleResource(String serviceName, String ecrResource) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("service name cannot be blank");
        }
        if (Utils.isBlank(ecrResource)) {
            throw new IllegalArgumentException("ECR repository resource to reference cannot be blank");
        }
        Map<String, Object> resourceProperties = new LinkedHashMap<>();
        resourceProperties.put("Name", Collections.singletonMap("Fn::Sub", "sb-${Environment}-ecr-"
                + cloudFormationResourceName(serviceName).toLowerCase())
        );
        Map<String, Object> eventPattern = new LinkedHashMap<>();
        eventPattern.put("source", Collections.singletonList("aws.ecr"));
        eventPattern.put("detail-type", Collections.singletonList("ECR Image Action"));
        eventPattern.put("detail", Collections.singletonMap("repository-name", Collections.singletonList(
                Collections.singletonMap("Ref", ecrResource)
        )));
        resourceProperties.put("EventPattern", eventPattern);
        resourceProperties.put("State", "ENABLED");

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("Arn", Collections.singletonMap("Fn::GetAtt", Arrays.asList("WorkloadDeployLambda", "Arn")));
        target.put("Id", Collections.singletonMap("Fn::Sub", "sb-${Environment}-deploy-"
                + cloudFormationResourceName(serviceName).toLowerCase())
        );
        resourceProperties.put("Targets", Collections.singletonList(target));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("Type", "AWS::Events::Rule");
        resource.put("Properties", resourceProperties);

        return resource;
    }

    protected static String eventRulePermissionName(String serviceName) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("service name cannot be blank");
        }
        return "ImageEventPermission" + cloudFormationResourceName(serviceName);
    }

    protected static Map<String, Object> eventRulePermissionResource(String eventRuleResource) {
        if (Utils.isBlank(eventRuleResource)) {
            throw new IllegalArgumentException("EventBridge rule to reference cannot be blank");
        }
        Map<String, Object> resourceProperties = new LinkedHashMap<>();
        resourceProperties.put("FunctionName", Collections.singletonMap("Ref", "WorkloadDeployLambda"));
        resourceProperties.put("Principal", "events.amazonaws.com");
        resourceProperties.put("Action", "lambda:InvokeFunction");
        resourceProperties.put("SourceArn", Collections.singletonMap("Fn::GetAtt",
                Arrays.asList(eventRuleResource, "Arn"))
        );

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("Type", "AWS::Lambda::Permission");
        resource.put("Properties", resourceProperties);

        return resource;
    }
}
