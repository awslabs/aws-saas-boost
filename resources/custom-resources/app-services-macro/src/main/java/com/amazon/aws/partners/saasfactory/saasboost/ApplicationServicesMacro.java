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

public class ApplicationServicesMacro implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServicesMacro.class);
    private static final String FRAGMENT = "fragment";
    private static final String REQUEST_ID = "requestId";
    private static final String TEMPLATE_PARAMETERS = "templateParameterValues";
    private static final String STATUS = "status";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAILURE";
    private static final String ERROR_MSG = "errorMessage";

    /**
     * CloudFormation macro to create resources based on SaaS Boost appConfig objects:
     *  1/ ECR repository resources for each application service passed as a template parameter. 
     *     Returns failure if the "ApplicationServices" template parameter is missing.
     *  2/ S3 bucket resource for all application services if enabled via a template parameter.
     *     Assumes a missing `AppExtension` parameter means S3 support is not enabled.
     * 
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
        Map<String, Object> template = (Map<String, Object>) event.get(FRAGMENT);

        String ecrError = updateTemplateForEcr(templateParameters, template);
        if (ecrError != null) {
            LOGGER.error("Encountered error updating template for ECR repositories: {}");
            response.put(ERROR_MSG, ecrError);
            return response;
        }
        LOGGER.info("Successfully altered template for ECR repositories");
        
        String extensionsError = updateTemplateForPooledExtensions(templateParameters, template);
        if (extensionsError != null) {
            LOGGER.error("Encountered error updating template for pooled extensions: {}");
            response.put(ERROR_MSG, extensionsError);
            return response;
        }
        LOGGER.info("Successfully altered template for extensions");

        response.put(FRAGMENT, template);
        response.put(STATUS, SUCCESS);
        return response;
    }

    protected static String updateTemplateForPooledExtensions(
            final Map<String, Object> templateParameters,
            Map<String, Object> template) {
        Set<String> processedExtensions = new HashSet<String>();
        if (templateParameters.containsKey("AppExtensions") && template.containsKey("Resources")) {
            String applicationExtensions = (String) templateParameters.get("AppExtensions");
            if (Utils.isNotEmpty(applicationExtensions)) {
                String[] extensions = applicationExtensions.split(",");
                for (String extension : extensions) {
                    if (processedExtensions.contains(extension)) {
                        LOGGER.warn("Skipping duplicate extension {}", extension);
                        continue;
                    }
                    switch (extension) {
                        case "s3": {
                            String s3Error = updateTemplateForS3(templateParameters, template);
                            if (s3Error != null) {
                                LOGGER.error("Processing S3 extension failed: {}", s3Error);
                                return s3Error;
                            }
                            LOGGER.info("Successfully processed s3 extension");
                            break;
                        }
                        default: {
                            LOGGER.warn("Skipping unknown extension {}", extension);
                        }
                    }
                    processedExtensions.add(extension);
                }
            } else {
                LOGGER.debug("Empty AppExtensions parameter, skipping updating template for extensions");
            }
        } else {
            LOGGER.error("Invalid template, missing AppExtensions parameter or missing Resources");
            return "Invalid template, missing AppExtensions parameter or missing Resources";
        }
        return null;
    }

    protected static String updateTemplateForS3(final Map<String, Object> templateParameters,
            Map<String, Object> template) {
        if (templateParameters.containsKey("Environment")
                && templateParameters.containsKey("LoggingBucket")) {
            String environmentName = (String) templateParameters.get("Environment");
            // Bucket Resource
            Map<String, Object> s3Resource = s3Resource(environmentName,
                    (String) templateParameters.get("LoggingBucket"));
            ((Map<String, Object>) template.get("Resources")).put("TenantStorage", s3Resource);

            // Custom Resource to clear the bucket before we delete it
            Map<String, Object> clearBucketResource = Map.of(
                    "Type", "Custom::CustomResource",
                    "Properties", Map.of(
                            "ServiceToken", "{{resolve:ssm:/saas-boost/" + environmentName + "/CLEAR_BUCKET_ARN}}",
                            "Bucket", Map.of("Ref", "TenantStorage")
                    )
            );
            ((Map<String, Object>) template.get("Resources")).put("ClearTenantStorageBucket", clearBucketResource);
        } else {
            return "Invalid template, missing parameter Environment or LoggingBucket";
        }
        return null;
    }

    protected static Map<String, Object> s3Resource(String environment, String loggingBucket) {
        Map<String, Object> resourceProperties = new LinkedHashMap<>();

        // tags
        resourceProperties.put("Tags", List.of(Map.of(
                "Key", "SaaS Boost",
                "Value", environment
        )));

        // encryptionConfiguration
        resourceProperties.put("BucketEncryption", Map.of(
                "ServerSideEncryptionConfiguration", List.of(Map.of(
                        "BucketKeyEnabled", true,
                        "ServerSideEncryptionByDefault", Map.of("SSEAlgorithm", "AES256")
                ))
        ));

        // loggingConfiguration
        resourceProperties.put("LoggingConfiguration", Map.of(
                "DestinationBucketName", loggingBucket,
                "LogFilePrefix", "s3extension-logs"
        ));

        // ownershipControls
        resourceProperties.put("OwnershipControls", Map.of(
                "Rules", List.of(Map.of("ObjectOwnership", "BucketOwnerEnforced"))
        ));

        // publicAccessBlockConfiguration
        resourceProperties.put("PublicAccessBlockConfiguration", Map.of(
                "BlockPublicAcls", true,
                "BlockPublicPolicy", true,
                "IgnorePublicAcls", true,
                "RestrictPublicBuckets", true
        ));

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("Type", "AWS::S3::Bucket");
        resource.put("Properties", resourceProperties);

        return resource;
    }

    protected static String updateTemplateForEcr(
            final Map<String, Object> templateParameters,
            Map<String, Object> template) {
        if (templateParameters.containsKey("ApplicationServices")) {
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
        } else {
            LOGGER.error("Invalid template, missing parameter ApplicationServices");
            return "Invalid template, missing parameter ApplicationServices";
        }
        // no error message implies success?
        return null;
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
