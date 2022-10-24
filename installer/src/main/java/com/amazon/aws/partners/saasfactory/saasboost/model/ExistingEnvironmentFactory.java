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

package com.amazon.aws.partners.saasfactory.saasboost.model;

import com.amazon.aws.partners.saasfactory.saasboost.Constants;
import com.amazon.aws.partners.saasfactory.saasboost.SaaSBoostArtifactsBucket;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ExistingEnvironmentFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExistingEnvironmentFactory.class);

    public static Environment findExistingEnvironment(
            SsmClient ssm, 
            CloudFormationClient cfn, 
            String environmentName,
            String accountId) {
        if (Utils.isBlank(environmentName)) {
            throw new EnvironmentLoadException("EnvironmentName cannot be blank.");
        }

        String baseCloudFormationStackName = getExistingSaaSBoostStackName(ssm, environmentName);

        return Environment.builder()
            .artifactsBucket(getExistingSaaSBoostArtifactBucket(ssm, environmentName, Constants.AWS_REGION))
            .baseCloudFormationStackName(baseCloudFormationStackName)
            .baseCloudFormationStackInfo(getExistingSaaSBoostStackDetails(cfn, baseCloudFormationStackName))
            .lambdasFolderName(getExistingSaaSBoostLambdasFolder(ssm, environmentName))
            .metricsAnalyticsDeployed(getExistingSaaSBoostAnalyticsDeployed(ssm, environmentName))
            .name(environmentName)
            .accountId(accountId)
            .build();
    }

    // VisibleForTesting
    static SaaSBoostArtifactsBucket getExistingSaaSBoostArtifactBucket(
            SsmClient ssm, 
            String environmentName, 
            Region region) {
        LOGGER.debug("Getting existing SaaS Boost artifact bucket name from Parameter Store");
        String artifactsBucket = null;
        try {
            // note: this currently assumes Settings service implementation details and should eventually be
            //       replaced with a call to getSettings
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name("/saas-boost/" + environmentName + "/SAAS_BOOST_BUCKET")
            );
            artifactsBucket = response.parameter().value();
        } catch (ParameterNotFoundException paramStoreError) {
            LOGGER.error("Parameter /saas-boost/" + environmentName + "/SAAS_BOOST_BUCKET not found");
            LOGGER.error(Utils.getFullStackTrace(paramStoreError));
            throw paramStoreError;
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameter error {}", ssmError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
        LOGGER.info("Loaded artifacts bucket {}", artifactsBucket);
        return new SaaSBoostArtifactsBucket(artifactsBucket, region);
    }

    // VisibleForTesting
    static String getExistingSaaSBoostStackName(SsmClient ssm, String environmentName) {
        LOGGER.debug("Getting existing SaaS Boost CloudFormation stack name from Parameter Store");
        String stackName = null;
        try {
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name("/saas-boost/" + environmentName + "/SAAS_BOOST_STACK")
            );
            stackName = response.parameter().value();
        } catch (ParameterNotFoundException paramStoreError) {
            LOGGER.warn("Parameter /saas-boost/" + environmentName
                    + "/SAAS_BOOST_STACK not found setting to default 'sb-" + environmentName + "'");
            stackName = "sb-" + environmentName;
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameter error {}", ssmError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
        LOGGER.info("Loaded stack name {}", stackName);
        return stackName;
    }

    // VisibleForTesting
    static Map<String, String> getExistingSaaSBoostStackDetails(
            CloudFormationClient cfn, 
            String baseCloudFormationStackName) {
        LOGGER.debug("Getting CloudFormation stack details for SaaS Boost stack {}", baseCloudFormationStackName);
        Map<String, String> details = new HashMap<>();
        List<String> requiredOutputs = List.of("PublicSubnet1", "PublicSubnet2", "PrivateSubnet1", 
                "PrivateSubnet2", "EgressVpc", "LoggingBucket");
        try {
            DescribeStacksResponse response = cfn.describeStacks(
                    request -> request.stackName(baseCloudFormationStackName));
            if (response.hasStacks() && !response.stacks().isEmpty()) {
                Stack stack = response.stacks().get(0);
                Map<String, String> outputs = stack.outputs().stream()
                        .collect(Collectors.toMap(Output::outputKey, Output::outputValue));
                for (String requiredOutput : requiredOutputs) {
                    if (!outputs.containsKey(requiredOutput)) {
                        throw new EnvironmentLoadException("Missing required CloudFormation stack output "
                                + requiredOutput + " from stack " + baseCloudFormationStackName);
                    }
                }
                LOGGER.info("Loaded stack outputs from stack " + baseCloudFormationStackName);
                Map<String, String> parameters = stack.parameters().stream()
                        .collect(Collectors.toMap(Parameter::parameterKey, Parameter::parameterValue));

                details.putAll(parameters);
                details.putAll(outputs);
            }
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cloudformation:DescribeStacks error", cfnError);
            LOGGER.error(Utils.getFullStackTrace(cfnError));
            throw cfnError;
        }
        return details;
    }

    // VisibleForTesting
    static String getExistingSaaSBoostLambdasFolder(SsmClient ssm, String environmentName) {
        LOGGER.debug("Getting existing SaaS Boost Lambdas folder from Parameter Store");
        String lambdasFolder = null;
        try {
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name("/saas-boost/" + environmentName + "/SAAS_BOOST_LAMBDAS_FOLDER")
            );
            lambdasFolder = response.parameter().value();
        } catch (ParameterNotFoundException paramStoreError) {
            LOGGER.warn("Parameter /saas-boost/" + environmentName
                    + "/SAAS_BOOST_LAMBDAS_FOLDER not found setting to default 'lambdas'");
            lambdasFolder = "lambdas";
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameter error {}", ssmError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
        LOGGER.info("Loaded Lambdas folder {}", lambdasFolder);
        return lambdasFolder;
    }

    // VisibleForTesting
    static boolean getExistingSaaSBoostAnalyticsDeployed(SsmClient ssm, String environmentName) {
        LOGGER.debug("Getting existing SaaS Boost Analytics module deployed from Parameter Store");
        boolean analyticsDeployed = false;
        try {
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name("/saas-boost/" + environmentName + "/METRICS_ANALYTICS_DEPLOYED")
            );
            analyticsDeployed = Boolean.parseBoolean(response.parameter().value());
        } catch (ParameterNotFoundException paramStoreError) {
            // this means the parameter doesn't exist, so ignore
        } catch (SdkServiceException ssmError) {
            // TODO CloudFormation should own this parameter, not the installer...
            LOGGER.error("ssm:GetParameter error {}", ssmError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
        LOGGER.info("Loaded analytics deployed {}", analyticsDeployed);
        return analyticsDeployed;
    }
}
