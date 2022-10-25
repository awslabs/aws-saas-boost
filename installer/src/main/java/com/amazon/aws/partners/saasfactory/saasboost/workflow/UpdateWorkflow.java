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

package com.amazon.aws.partners.saasfactory.saasboost.workflow;

import com.amazon.aws.partners.saasfactory.saasboost.Constants;
import com.amazon.aws.partners.saasfactory.saasboost.GitVersionInfo;
import com.amazon.aws.partners.saasfactory.saasboost.Keyboard;
import com.amazon.aws.partners.saasfactory.saasboost.SaaSBoostInstall;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazon.aws.partners.saasfactory.saasboost.clients.AwsClientBuilderFactory;
import com.amazon.aws.partners.saasfactory.saasboost.model.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.amazon.aws.partners.saasfactory.saasboost.SaaSBoostInstall.outputMessage;

public class UpdateWorkflow extends AbstractWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateWorkflow.class);

    private final Environment environment;
    private final Path workingDir;
    private final AwsClientBuilderFactory clientBuilderFactory;
    private final boolean doesCfnMacroResourceExist;

    public UpdateWorkflow(
            Path workingDir, 
            Environment environment, 
            AwsClientBuilderFactory clientBuilderFactory, 
            boolean doesCfnMacroResourceExist) {
        this.environment = environment;
        this.workingDir = workingDir;
        this.clientBuilderFactory = clientBuilderFactory;
        this.doesCfnMacroResourceExist = doesCfnMacroResourceExist;
    }

    private boolean confirm() {
        outputMessage("******* W A R N I N G *******");
        outputMessage("Updating AWS SaaS Boost environment is an IRREVERSIBLE operation. You should test an "
                + "updated install in a non-production environment\n"
                + "before updating a production environment. By continuing you understand and ACCEPT the RISKS!");
        System.out.print("Enter y to continue with UPDATE of " + environment.getBaseCloudFormationStackName()
                + " or n to CANCEL: ");
        boolean continueUpgrade = Keyboard.readBoolean();
        if (!continueUpgrade) {
            outputMessage("Canceled UPDATE of AWS SaaS Boost environment");
        } else {
            outputMessage("Continuing UPDATE of AWS SaaS Boost stack " + environment.getBaseCloudFormationStackName());
        }
        return continueUpgrade;
    }

    public void run() {
        LOGGER.info("Perform Update of AWS SaaS Boost deployment");
        if (!confirm()) {
            setExitCode(2);
            return;
        }

        // Get values for all the CloudFormation parameters including possibly new ones in the template file on disk
        Map<String, String> cloudFormationParamMap = getCloudFormationParameterMap(
                workingDir.resolve(Path.of("resources", "saas-boost.yaml")), 
                environment.getBaseCloudFormationStackInfo());

        // find all changed files from git, and execute specific update actions for each
        List<Path> changedPaths = findChangedPaths(cloudFormationParamMap);
        LOGGER.debug("Found changedPaths: {}", changedPaths);
        for (UpdateAction action : getUpdateActionsFromPaths(changedPaths)) {
            LOGGER.debug("executing UpdateAction: {}", action);
            switch (action) {
                case CLIENT: {
                    outputMessage("Updating admin web application...");
                    SaaSBoostInstall.copyAdminWebAppSourceToS3(workingDir,
                            environment.getArtifactsBucket().getBucketName(),
                            clientBuilderFactory.s3Builder().build());
                    break;
                }
                case CUSTOM_RESOURCES:
                case FUNCTIONS:
                case LAYERS:
                case METERING_BILLING:
                case SERVICES: {
                    // for each target, run the update script in the target's directory
                    for (String target : action.getTargets()) {
                        // TODO update this logic for windows
                        File updatedDirectory = new File(action.getDirectoryName(), target);
                        outputMessage("Updating " + updatedDirectory + " using "
                                + new File(updatedDirectory, "update.sh"));
                        // if this fails because update.sh does not exist, does not have the proper
                        // permissions or any other reason, a runtimeException will be thrown, exiting
                        // the run() execution
                        SaaSBoostInstall.executeCommand(
                                "./update.sh " + environment.getName(), // command to execute
                                null, // environment to use
                                updatedDirectory.getAbsoluteFile()); // directory to execute from
                    }
                    break;
                }
                case RESOURCES: {
                    // upload the template to the Boost Artifacts bucket
                    for (String target : action.getTargets()) {
                        outputMessage("Updating CloudFormation template: " + target);
                        environment.getArtifactsBucket().putFile(
                                clientBuilderFactory.s3Builder().build(), // s3 client
                                Path.of(action.getDirectoryName(), target), // local path
                                Path.of(target)); // remote path
                        if (target.equals("saas-boost-metrics-analytics.yaml")
                                && environment.isMetricsAnalyticsDeployed()) {
                            // the metrics-analytics stack is not a child stack of the base stack,
                            // so just updating the base stack won't update. update it manually.
                            String analyticsStackName = environment.getBaseCloudFormationStackName() + "-analytics";
                            // Load up the existing parameters from CloudFormation
                            Map<String, String> stackParamsMap = new LinkedHashMap<>();
                            try {
                                DescribeStacksResponse response = clientBuilderFactory.cloudFormationBuilder().build()
                                        .describeStacks(request -> request.stackName(analyticsStackName));
                                if (response.hasStacks() && !response.stacks().isEmpty()) {
                                    Stack stack = response.stacks().get(0);
                                    stackParamsMap = stack.parameters().stream()
                                            .collect(Collectors.toMap(
                                                Parameter::parameterKey, Parameter::parameterValue));
                                }
                            } catch (SdkServiceException cfnError) {
                                if (cfnError.getMessage().contains("does not exist")) {
                                    outputMessage("Analytics module CloudFormation stack "
                                            + analyticsStackName + " not found.");
                                    System.exit(2);
                                }
                                LOGGER.error("cloudformation:DescribeStacks error", cfnError);
                                LOGGER.error(Utils.getFullStackTrace(cfnError));
                                throw cfnError;
                            }
                            Map<String, String> paramsMap = getCloudFormationParameterMap(
                                    workingDir.resolve(Path.of("resources", "saas-boost-metrics-analytics.yaml")),
                                    stackParamsMap);
                            updateCloudFormationStack(analyticsStackName, paramsMap, target);
                        }
                    }
                    break;
                }
                default: {
                    // unrecognized case above means either not implemented or something was missed
                    LOGGER.error("Ignoring parsed UpdateAction: " + action
                            + " since no update case is implemented for it.");
                }
            }
        }

        // Update the version number
        outputMessage("Updating Version parameter to " + Constants.VERSION);
        cloudFormationParamMap.put("Version", Constants.VERSION);

        // If CloudFormation macro resources do not exist, that means that another environment that had previously
        // owned those resources was deleted. In this case we should make sure to create them.
        if (!doesCfnMacroResourceExist) {
            cloudFormationParamMap.put("CreateMacroResources", Boolean.TRUE.toString());
        }

        // Always call update stack
        outputMessage("Executing CloudFormation update stack on: " + environment.getBaseCloudFormationStackName());
        updateCloudFormationStack(
                environment.getBaseCloudFormationStackName(), 
                cloudFormationParamMap, 
                "saas-boost.yaml");

        runApiGatewayDeployment(cloudFormationParamMap);

        setExitCode(0);
        outputMessage("Update of SaaS Boost environment " + environment.getName() + " complete.");
    }

    protected static Map<String, String> getCloudFormationParameterMap(
            Path cloudFormationTemplateFile, 
            Map<String, String> stackParamsMap) {
        
        if (!Files.exists(cloudFormationTemplateFile)) {
            outputMessage("Unable to find file " + cloudFormationTemplateFile.toString());
            throw new RuntimeException("Could not find base CloudFormation stack: " + cloudFormationTemplateFile);
        }
        // Open CFN template yaml file and prompt for values of params that are not in the existing stack
        LOGGER.info("Building map of parameters for template " + cloudFormationTemplateFile);
        Map<String, String> cloudFormationParamMap = new LinkedHashMap<>();

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream cloudFormationTemplate = Files.newInputStream(cloudFormationTemplateFile)) {
            LinkedHashMap<String, Object> template = mapper.readValue(cloudFormationTemplate, LinkedHashMap.class);
            LinkedHashMap<String, Map<String, Object>> parameters = 
                    (LinkedHashMap<String, Map<String, Object>>) template.get("Parameters");
            for (Map.Entry<String, Map<String, Object>> parameter : parameters.entrySet()) {
                String parameterKey = parameter.getKey();
                LinkedHashMap<String, Object> parameterProperties = 
                        (LinkedHashMap<String, Object>) parameter.getValue();

                // For each parameter in the template file, set the value to any existing value
                // otherwise prompt the user to set the value.
                Object existingParameter = stackParamsMap.get(parameterKey);
                if (existingParameter != null) {
                    // We're running an update. Start with reusing the current value for this parameter.
                    // The calling code can override this parameter's value before executing update stack.
                    LOGGER.info("Reuse existing value for parameter {} => {}", parameterKey, existingParameter);
                    cloudFormationParamMap.put(parameterKey, stackParamsMap.get(parameterKey));
                } else {
                    // This is a new parameter added to the template file on disk. Prompt the user for a value.
                    Object defaultValue = parameterProperties.get("Default");
                    String parameterType = (String) parameterProperties.get("Type");
                    System.out.print("Enter a " + parameterType + " value for parameter " + parameterKey);
                    if (defaultValue != null) {
                        // No default value for this property
                        System.out.print(". (Press Enter for '" + defaultValue + "'): ");
                    } else {
                        System.out.print(": ");
                    }
                    String enteredValue = Keyboard.readString();
                    if (Utils.isEmpty(enteredValue) && defaultValue != null) {
                        cloudFormationParamMap.put(parameterKey, String.valueOf(defaultValue));
                        LOGGER.info("Using default value for parameter {} => {}", 
                                parameterKey, cloudFormationParamMap.get(parameterKey));
                    } else if (Utils.isEmpty(enteredValue) && defaultValue == null) {
                        cloudFormationParamMap.put(parameterKey, "");
                        LOGGER.info("Using entered value for parameter {} => {}", 
                                parameterKey, cloudFormationParamMap.get(parameterKey));
                    } else {
                        cloudFormationParamMap.put(parameterKey, enteredValue);
                        LOGGER.info("Using entered value for parameter {} => {}", 
                                parameterKey, cloudFormationParamMap.get(parameterKey));
                    }
                }
            }
        } catch (IOException ioe) {
            LOGGER.error("Error parsing YAML file from path", ioe);
            LOGGER.error(Utils.getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
        return cloudFormationParamMap;
    }

    // TODO git functionality should be extracted to a "gitToolbox" object for easier mock/testing
    protected List<Path> findChangedPaths(Map<String, String> cloudFormationParamMap) {
        // list all staged and committed changes against the last updated commit
        String versionParameter = cloudFormationParamMap.get("Version");
        LOGGER.debug("Found existing version: {}", versionParameter);
        String commitHash = null;
        if (versionParameter.startsWith("{") && versionParameter.endsWith("}")) {
            // we know this is a JSON-created versionParameter, so attempt deserialization to GitVersionInfo
            GitVersionInfo parsedInfo = Utils.fromJson(versionParameter, GitVersionInfo.class);
            if (parsedInfo != null) {
                commitHash = parsedInfo.getCommit();
            } else {
                // we cannot continue with an update without being able to parse the version information
                throw new RuntimeException("Unable to continue with update; cannot parse VERSION as JSON: "
                        + versionParameter);
            }
        } else {
            // this versionParameter was created before the JSON migration of git information,
            // so parse using the old logic

            // if Version was created with "Commit time", we need to remove that to get commit hash
            if (versionParameter.contains(",")) {
                versionParameter = versionParameter.split(",")[0];
            }
            // if last update or install was created with uncommitted code, assume we're working from
            // the last information we have: the commit on top of which the uncommitted code was written
            if (versionParameter.contains("-dirty")) {
                versionParameter = versionParameter.split("-")[0];
            }
            commitHash = versionParameter;
        }
        LOGGER.debug("Parsed commit hash to: {}", commitHash);
        List<Path> changedPaths = new ArrayList<>();
        // -b               : ignore whitespace-only changes
        // --name-only      : only output the filename (for easy parsing)
        // $(version)       : output changes since $(version)
        String gitDiffCommand = "git diff -b --name-only " + commitHash;
        changedPaths.addAll(listPathsFromGitCommand(gitDiffCommand));

        // list all untracked changes (i.e. net new un-added files)
        String gitListUntrackedFilesCommand = "git ls-files --others --exclude-standard";
        if (SaaSBoostInstall.isWindows()) {
            gitListUntrackedFilesCommand = "cmd /c " + gitListUntrackedFilesCommand;
        }
        changedPaths.addAll(listPathsFromGitCommand(gitListUntrackedFilesCommand));

        return changedPaths;
    }

    private List<Path> listPathsFromGitCommand(String command) {
        List<Path> paths = new ArrayList<>();
        Process process = null;
        try {
            if (SaaSBoostInstall.isWindows()) {
                command = "cmd /c " + command;
            }

            LOGGER.debug("Executing `" + command + "`");

            process = Runtime.getRuntime().exec(command);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = "";
                while ((line = reader.readLine()) != null) {
                    LOGGER.debug(line);
                    paths.add(Path.of(line));
                }
            } catch (IOException ioe) {
                LOGGER.error("Error reading from runtime exec process", ioe);
                LOGGER.error(Utils.getFullStackTrace(ioe));
                throw new RuntimeException(ioe);
            }

            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new RuntimeException("Error running command `" + command + "`: exit code " + exitValue);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return paths;
    }

    protected Collection<UpdateAction> getUpdateActionsFromPaths(List<Path> changedPaths) {
        Set<UpdateAction> actions = EnumSet.noneOf(UpdateAction.class);

        /*
         * Take for example the following list of changed paths:
         *   client/web/src/App.js
         *   functions/core-stack-listener/src/...
         *   services/onboarding-service/src/...
         *   services/tenant-service/src/...
         *   services/tenant-service/src/...
         *   resources/saas-boost.yaml
         *   resources/saas-boost-svc-tenant.yaml
         *   resources/custom-resources/app-services-ecr-macro/src/...
         *
         * The intention of this algorithm is to pull out the high level SaaS Boost components from the Path, as
         * represented by the UpdateAction Enum. e.g. CLIENT, FUNCTIONS, SERVICES, CUSTOM_RESOURCES, RESOURCES for
         * the above example, following these steps
         *   - for each path
         *     - traverse through each path component, up to a maximum depth of 2 (optimization, since no component
         *       pathname is at a depth deeper than two)
         *       - if we find the resources/ path component and the next component is custom-resources, continue
         *       - otherwise match the path component against an UpdateAction. if we find a valid one, add it to our
         *         list taking into account not only the UpdateAction itself (e.g. FUNCTIONS) but also the "target"
         *         of the UpdateAction (e.g. FUNCTIONS -> core-stack-listener)
         *
         * So the expected set of UpdateActions resulting from the above example is:
         *   CLIENT
         *   FUNCTIONS -> core-stack-listener
         *   SERVICES -> onboarding-service, tenant-service
         *   RESOURCES -> saas-boost.yaml, saas-boost-svc-tenant.yaml
         *   CUSTOM_RESOURCES -> app-services-ecr-macro
         */
        for (Path changedPath : changedPaths) {
            LOGGER.debug("processing {}", changedPath);
            Path absolutePath = Path.of(workingDir.toString(), changedPath.toString());
            if (!absolutePath.toFile().exists()) {
                LOGGER.debug("Skipping {} since it doesn't exist", changedPath);
                continue;
            }
            final int maximumTraversalDepth = 2;
            for (int i = 0; i < Math.min(changedPath.getNameCount(), maximumTraversalDepth); i++) {
                UpdateAction pathAction = UpdateAction.fromDirectoryName(changedPath.getName(i).toString());
                if (pathAction != null) {
                    // edge case: if this is a resources/custom-resources/.. path, we might be pinging on resources/
                    //            when we should on custom-resources. so skip if it is
                    LOGGER.debug("found action {} from path {}", pathAction, changedPath);
                    if ((i + 1) == changedPath.getNameCount()) {
                        // "this" name at `i` resolved to an UpdateAction, but there is no valid target
                        // represented by the next value in the name. this is an invalid changed path:
                        // a directory itself isn't changed, the files underneath is changed
                        LOGGER.error("Skipping {}, since it's an invalid changed path: expecting a file", changedPath);
                        break;
                    }
                    String target = changedPath.getName(i + 1).toString();
                    if (pathAction == UpdateAction.RESOURCES
                            && UpdateAction.fromDirectoryName(target) == UpdateAction.CUSTOM_RESOURCES) {
                        LOGGER.debug("Skipping RESOURCES for CUSTOM_RESOURCES in {}", changedPath);
                        continue;
                    }
                    // now add targets if necessary
                    switch (pathAction) {
                        case RESOURCES: {
                            if (target.endsWith(".yaml")) {
                                LOGGER.debug("Adding new target {} to UpdateAction {}", target, pathAction);
                                pathAction.addTarget(target);
                            } else if (target.endsWith("keycloak/Dockerfile")) {
                                LOGGER.debug("Adding new target {} to UpdateAction {}", target, pathAction);
                                pathAction.addTarget(target);
                            } else {
                                LOGGER.debug("Skipping adding {} to UpdateAction {}", target, pathAction);
                            }
                            break;
                        }
                        case CLIENT:
                        case CUSTOM_RESOURCES:
                        case FUNCTIONS:
                        case LAYERS:
                        case METERING_BILLING:
                        case SERVICES: {
                            // each of the above actions use update.sh to update. the target here needs to be
                            // a directory, because the update workflow looks underneath the target for the update
                            // script. therefore editing something like layers/ parent pom or metering-billing
                            // parent pom is not something worth updating
                            LOGGER.debug("Adding new target {} to UpdateAction {}", target, pathAction);
                            // absolute against workingDir, rather than against running dir
                            Path targetPath = Path.of(workingDir.toString(), changedPath.subpath(0, i + 2).toString());
                            if (targetPath.toFile().isDirectory()) {
                                // a non-yaml file (e.g. pom.xml) is not an acceptable target,
                                // since there will be no update path underneath it
                                pathAction.addTarget(target);
                            }
                            break;
                        }
                        default: {
                            // do nothing
                        }
                    }
                    if (pathAction.getTargets().size() > 0 && !actions.contains(pathAction)) {
                        LOGGER.debug("Adding new action {} from path {}", pathAction, changedPath);
                        actions.add(pathAction);
                    }
                    break;
                }
            }
        }
        
        return actions;
    }

    // TODO all CloudFormation activities (reading params, updating stacks)
    //      should be extracted to a class for easier mocking/testing
    private void updateCloudFormationStack(String stackName, Map<String, String> paramsMap, String yamlFile) {
        List<Parameter> templateParameters = paramsMap.entrySet().stream()
                .map(entry -> Parameter.builder().parameterKey(entry.getKey()).parameterValue(entry.getValue()).build())
                .collect(Collectors.toList());

        CloudFormationClient cfn = clientBuilderFactory.cloudFormationBuilder().build();
        LOGGER.info("Executing CloudFormation update stack for " + stackName);
        try {
            UpdateStackResponse updateStackResponse = cfn.updateStack(UpdateStackRequest.builder()
                    .stackName(stackName)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .templateURL(environment.getArtifactsBucket().getBucketUrl() + yamlFile)
                    .parameters(templateParameters)
                    .build()
            );
            String stackId = updateStackResponse.stackId();
            LOGGER.info("Waiting for update stack to complete for " + stackId);
            long sleepTime = 1L;
            final long timeoutMinutes = 60L;
            final long timeout = (timeoutMinutes * 60 * 1000) + System.currentTimeMillis();
            while (true) {
                if (System.currentTimeMillis() > timeout) {
                    outputMessage("CloudFormation update of stack: " + stackName + " timed out. "
                            + "Check the events in the AWS CloudFormation console.");
                }
                DescribeStacksResponse response = cfn.describeStacks(request -> request.stackName(stackId));
                Stack stack = response.stacks().get(0);
                StackStatus stackStatus = stack.stackStatus();
                EnumSet<StackStatus> failureStatuses = EnumSet.of(
                        StackStatus.UPDATE_ROLLBACK_COMPLETE, 
                        StackStatus.UPDATE_FAILED, 
                        StackStatus.UPDATE_ROLLBACK_FAILED);
                if (stackStatus == StackStatus.UPDATE_COMPLETE) {
                    outputMessage("CloudFormation stack: " + stackName + " updated successfully.");
                    break;
                } else if (failureStatuses.contains(stackStatus)) {
                    outputMessage("CloudFormation stack: " + stackName + " update failed.");
                    throw new RuntimeException("Error with CloudFormation stack " + stackName
                            + ". Check the events in the AWS CloudFormation Console");
                } else {
                    // TODO should we set an upper bound on this loop?
                    outputMessage("Awaiting Update of CloudFormation Stack " + stackName
                            + " to complete.  Sleep " + sleepTime + " minute(s)...");
                    try {
                        Thread.sleep(sleepTime * 60 * 1000);
                    } catch (Exception e) {
                        LOGGER.error("Error pausing thread", e);
                    }
                    sleepTime = 1L; //set to 1 minute after kick off of 5 minute
                }
            }
        } catch (SdkServiceException cfnError) {
            if (cfnError.getMessage().contains("No updates are to be performed")) {
                outputMessage("No Updates to be performed for Stack: " + stackName);
            } else {
                LOGGER.error("updateCloudFormationStack::update stack failed {}", cfnError.getMessage());
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }
        }
    }

    // VisibleForTesting
    protected void runApiGatewayDeployment(Map<String, String> cloudFormationParamMap) {
        // CloudFormation will not redeploy an API Gateway stage on update
        outputMessage("Updating API Gateway deployment for stages");
        try {
            String publicApiName = "sb-" + environment.getName() + "-public-api";
            String privateApiName = "sb-" + environment.getName() + "-private-api";
            ApiGatewayClient apigw = clientBuilderFactory.apiGatewayBuilder().build();
            GetRestApisResponse response = apigw.getRestApis();
            if (response.hasItems()) {
                for (RestApi api : response.items()) {
                    String apiName = api.name();
                    boolean isPublicApi = publicApiName.equals(apiName);
                    boolean isPrivateApi = privateApiName.equals(apiName);
                    if (isPublicApi || isPrivateApi) {
                        String stage = isPublicApi ? cloudFormationParamMap.get("PublicApiStage")
                                : cloudFormationParamMap.get("PrivateApiStage");
                        outputMessage("Updating API Gateway deployment for " + apiName + " to stage: " + stage);
                        apigw.createDeployment(request -> request
                                .restApiId(api.id())
                                .stageName(stage)
                        );
                    }
                }
            }
        } catch (SdkServiceException apigwError) {
            LOGGER.error("apigateway error", apigwError);
            LOGGER.error(Utils.getFullStackTrace(apigwError));
            throw apigwError;
        }
    }
}
