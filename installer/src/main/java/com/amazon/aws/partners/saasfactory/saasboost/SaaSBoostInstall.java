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

import com.amazon.aws.partners.saasfactory.saasboost.clients.AwsClientBuilderFactory;
import com.amazon.aws.partners.saasfactory.saasboost.model.Environment;
import com.amazon.aws.partners.saasfactory.saasboost.model.EnvironmentLoadException;
import com.amazon.aws.partners.saasfactory.saasboost.model.ExistingEnvironmentFactory;
import com.amazon.aws.partners.saasfactory.saasboost.workflow.UpdateWorkflow;
import com.amazon.aws.partners.saasfactory.saasboost.workflow.Workflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.ResourceStatus;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.quicksight.QuickSightClient;
import software.amazon.awssdk.services.quicksight.model.*;
import software.amazon.awssdk.services.quicksight.model.ListUsersRequest;
import software.amazon.awssdk.services.quicksight.model.ListUsersResponse;
import software.amazon.awssdk.services.quicksight.model.Tag;
import software.amazon.awssdk.services.quicksight.model.User;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.amazon.aws.partners.saasfactory.saasboost.Constants.AWS_REGION;
import static com.amazon.aws.partners.saasfactory.saasboost.Constants.OS;
import static com.amazon.aws.partners.saasfactory.saasboost.Constants.VERSION;
import static com.amazon.aws.partners.saasfactory.saasboost.Utils.isBlank;
import static com.amazon.aws.partners.saasfactory.saasboost.Utils.isEmpty;
import static com.amazon.aws.partners.saasfactory.saasboost.Utils.isNotBlank;
import static com.amazon.aws.partners.saasfactory.saasboost.Utils.isNotEmpty;

public class SaaSBoostInstall {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSBoostInstall.class);

    private final AwsClientBuilderFactory awsClientBuilderFactory;
    private final ApiGatewayClient apigw;
    private final CloudFormationClient cfn;
    private final EcrClient ecr;
    private final IamClient iam;
    // TODO do we need to reassign the quicksight client between getQuickSightUsername and setupQuicksight?
    private QuickSightClient quickSight;
    private final S3Client s3;
    private final SsmClient ssm;
    private final LambdaClient lambda;
    private final SecretsManagerClient secretsManager;

    private final String accountId;
    private Environment environment;
    private String envName;
    private Path workingDir;
    private SaaSBoostArtifactsBucket saasBoostArtifactsBucket;
    private String lambdaSourceFolder = "lambdas";
    private String stackName;
    private Map<String, String> baseStackDetails = new HashMap<>();
    private boolean useAnalyticsModule = false;
    private boolean useQuickSight = false;
    private String quickSightUsername;
    private String quickSightUserArn;

    protected enum ACTION {
        INSTALL(1, "New AWS SaaS Boost install.", false),
        ADD_ANALYTICS(2, "Install Metrics and Analytics into existing AWS SaaS Boost deployment.", true),
        UPDATE_WEB_APP(3, "Update Web Application for existing AWS SaaS Boost deployment.", true),
        UPDATE(4, "Update existing AWS SaaS Boost deployment.", true),
        DELETE(5, "Delete existing AWS SaaS Boost deployment.", true),
        CANCEL(6, "Exit installer.", false);
//        DEBUG(7, "Debug", false);

        private final int choice;
        private final String prompt;
        private final boolean existing;

        ACTION(int choice, String prompt, boolean existing) {
            this.choice = choice;
            this.prompt = prompt;
            this.existing = existing;
        }

        public String getPrompt() {
            return String.format("%2d. %s", choice, prompt);
        }

        public boolean isExisting() {
            return existing;
        }

        public static ACTION ofChoice(int choice) {
            ACTION action = null;
            for (ACTION a : ACTION.values()) {
                if (a.choice == choice) {
                    action = a;
                    break;
                }
            }
            return action;
        }
    }

    public SaaSBoostInstall() {
        awsClientBuilderFactory = AwsClientBuilderFactory.builder()
                .region(AWS_REGION)
                .build();

        apigw = awsClientBuilderFactory.apiGatewayBuilder().build();
        cfn = awsClientBuilderFactory.cloudFormationBuilder().build();
        ecr = awsClientBuilderFactory.ecrBuilder().build();
        iam = awsClientBuilderFactory.iamBuilder().build();
        lambda = awsClientBuilderFactory.lambdaBuilder().build();
        quickSight = awsClientBuilderFactory.quickSightBuilder().build();
        s3 = awsClientBuilderFactory.s3Builder().build();
        ssm = awsClientBuilderFactory.ssmBuilder().build();
        secretsManager = awsClientBuilderFactory.secretsManagerBuilder().build();

        accountId = awsClientBuilderFactory.stsBuilder().build().getCallerIdentity().account();
    }

    public static void main(String[] args) {
        SaaSBoostInstall installer = new SaaSBoostInstall();
        try {
            String existingBucket = null;
            if (args.length > 0) {
                existingBucket = args[0];
            }
            installer.start(existingBucket);
        } catch (Exception e) {
            outputMessage("===========================================================");
            outputMessage("Installation Error: " + e.getLocalizedMessage());
            outputMessage("Please see detailed log file saas-boost-install.log");
            LOGGER.error(getFullStackTrace(e));
        }
    }

    protected void debug() {
//        getExistingSaaSBoostLambdasFolder();
//        processLambdas();
    }

    public void start(String existingBucket) {
        outputMessage("===========================================================");
        outputMessage("Welcome to the AWS SaaS Boost Installer");
        outputMessage("Installer Version: " + VERSION);

        // Do we have Maven, Node and AWS CLI on the PATH?
        checkEnvironment();

        ACTION installOption;
        while (true) {
            for (ACTION action : ACTION.values()) {
                System.out.println(action.getPrompt());
            }
            System.out.print("Please select an option to continue (1-" + ACTION.values().length + "): ");
            Integer option = Keyboard.readInt();
            if (option != null) {
                installOption = ACTION.ofChoice(option);
                if (installOption != null) {
                    break;
                }
            } else {
                System.out.println("Invalid option specified, try again.");
            }
        }

        // If we're taking action on an existing install, load up the environment
        if (installOption.isExisting()) {
            loadExistingSaaSBoostEnvironment();
        }

        if (ACTION.CANCEL != installOption && ACTION.DELETE != installOption) {
            this.workingDir = getWorkingDirectory();
        }

        Workflow workflow = null;

        switch (installOption) {
            case INSTALL:
                installSaaSBoost(existingBucket);
                break;
            case UPDATE:
                workflow = new UpdateWorkflow(this.workingDir, this.environment, this.awsClientBuilderFactory);
                break;
            case UPDATE_WEB_APP:
                updateWebApp();
                break;
            case ADD_ANALYTICS:
                this.useAnalyticsModule = true;
                System.out.print("Would you like to setup Amazon Quicksight for the Analytics module? You must have already registered for Quicksight in your account (y or n)? ");
                this.useQuickSight = Keyboard.readBoolean();
                if (this.useQuickSight) {
                    getQuickSightUsername();
                }
                installAnalyticsModule();
                break;
            case DELETE:
                deleteSaasBoostInstallation();
                break;
            case CANCEL:
                cancel();
                break;
//            case DEBUG:
//                debug();
//                break;
        }

        if (workflow != null) {
            workflow.run();
            System.exit(workflow.getExitCode());
        }
    }

    protected void installSaaSBoost(String existingBucket) {
        LOGGER.info("Performing new installation of AWS SaaS Boost");

        // Check if yarn.lock exists in the client/web folder
        // If it doesn't, exit and ask them to run yarn which will download the NPM dependencies
        // TODO We're doing this because downloading Node modules for the first time takes a while... is this necessary?
        Path webClientDir = workingDir.resolve("client").resolve("web");
        Path yarnLockFile = webClientDir.resolve("yarn.lock");
        if (!Files.exists(yarnLockFile)) {
            outputMessage("Please run 'yarn' command from " + webClientDir.toString() + " before running this installer.");
            System.exit(2);
        }

        while (true) {
            System.out.print("Enter name of the AWS SaaS Boost environment to deploy (Ex. dev, test, uat, prod, etc.): ");
            this.envName = Keyboard.readString();
            if (validateEnvironmentName(this.envName)) {
                break;
            } else {
                outputMessage("Entered value is incorrect, maximum of 10 alphanumeric characters, please try again.");
            }
        }

        String emailAddress;
        while (true) {
            System.out.print("Enter the email address for your AWS SaaS Boost administrator: ");
            emailAddress = Keyboard.readString();
            if (validateEmail(emailAddress)) {
                System.out.print("Enter the email address address again to confirm: ");
                String emailAddress2 = Keyboard.readString();
                if (emailAddress.equals(emailAddress2)) {
                    break;
                } else {
                    outputMessage("Entered value for email address does not match " + emailAddress);
                }
            } else {
                outputMessage("Entered value for email address is incorrect or wrong format, please try again.");
            }
        }

        System.out.print("Would you like to install the metrics and analytics module of AWS SaaS Boost (y or n)? ");
        this.useAnalyticsModule = Keyboard.readBoolean();

        // If installing the analytics module, ask about QuickSight.
        if (useAnalyticsModule) {
            System.out.print("Would you like to setup Amazon Quicksight for the Analytics module? You must have already registered for Quicksight in your account (y or n)? ");
            this.useQuickSight = Keyboard.readBoolean();
        }
        if (this.useQuickSight) {
            getQuickSightUsername();
        }

        System.out.println("If your application runs on Windows and uses a shared file system, Active Directory is required.");
        System.out.print("Would you like to provision AWS Directory Service to use with FSx for Windows File Server (y or n)? ");
        final boolean setupActiveDirectory = Keyboard.readBoolean();

        System.out.println();
        outputMessage("===========================================================");
        outputMessage("");
        outputMessage("Would you like to continue the installation with the following options?");
        outputMessage("AWS Account: " + this.accountId);
        outputMessage("AWS Region: " + AWS_REGION.toString());
        outputMessage("AWS SaaS Boost Environment Name: " + this.envName);
        outputMessage("Admin Email Address: " + emailAddress);
        outputMessage("Install optional Analytics Module: " + this.useAnalyticsModule);
        if (this.useAnalyticsModule && isNotBlank(this.quickSightUsername)) {
            outputMessage("Amazon QuickSight user for Analytics Module: " + this.quickSightUsername);
        } else {
            outputMessage("Amazon QuickSight user for Analytics Module: N/A");
        }
        outputMessage("Setup AWS Directory Service for FSx for Windows File Server: " + setupActiveDirectory);

        System.out.println();
        System.out.print("Continue (y or n)? ");
        boolean continueInstall = Keyboard.readBoolean();
        if (!continueInstall) {
            cancel();
        }

        System.out.println();
        outputMessage("===========================================================");
        outputMessage("Installing AWS SaaS Boost");
        outputMessage("===========================================================");

        // Check for the AWS managed service roles:
        outputMessage("Checking for necessary AWS service linked roles");
        setupAwsServiceRoles();

        if (existingBucket == null) {
            // Create the S3 artifacts bucket
            outputMessage("Creating S3 artifacts bucket");
            saasBoostArtifactsBucket = SaaSBoostArtifactsBucket.createS3ArtifactBucket(s3, envName, AWS_REGION);
            outputMessage("Created S3 artifacts bucket: " + saasBoostArtifactsBucket);

            // Copy the CloudFormation templates
            outputMessage("Uploading CloudFormation templates to S3 artifacts bucket");
            copyTemplateFilesToS3();

            // Compile all the source code
            outputMessage("Compiling Lambda functions and uploading to S3 artifacts bucket. This will take some time...");
            processLambdas();
        } else {
            outputMessage("Reusing existing artifacts bucket " + existingBucket);
            saasBoostArtifactsBucket = new SaaSBoostArtifactsBucket(existingBucket, AWS_REGION);
            try {
                s3.headBucket(request -> request.bucket(saasBoostArtifactsBucket.getBucketName()));
            } catch (SdkServiceException s3error) {
                outputMessage("Bucket " + existingBucket + " does not exist!");
                throw s3error;
            }
        }

        final String activeDirectoryPasswordParameterName = "/saas-boost/" + envName + "/ACTIVE_DIRECTORY_PASSWORD";
        if (setupActiveDirectory) {
            String activeDirectoryPassword = generatePassword(16);
            LOGGER.info("Add SSM param ACTIVE_DIRECTORY_PASSWORD with password");
            try {
                ssm.putParameter(PutParameterRequest.builder()
                        .name(activeDirectoryPasswordParameterName)
                        .type(ParameterType.SECURE_STRING)
                        .value(activeDirectoryPassword)
                        .overwrite(true)
                        .build()
                );
                secretsManager.createSecret(CreateSecretRequest.builder()
                        .name(activeDirectoryPasswordParameterName)
                        .secretString(activeDirectoryPassword)
                        .forceOverwriteReplicaSecret(true)
                        .build()
                );
            } catch (SsmException ssmError) {
                LOGGER.error("ssm:PutParameter error", ssmError);
                LOGGER.error(getFullStackTrace(ssmError));
                throw ssmError;
            } catch (SecretsManagerException smError) {
                LOGGER.error("secretsmanager:createSecret error", smError);
                LOGGER.error(getFullStackTrace(smError));
                throw smError;
            }
            outputMessage("Active Directory admin user password stored in secure SSM Parameter: " + activeDirectoryPasswordParameterName);
        }

        // Run CloudFormation create stack
        outputMessage("Running CloudFormation");
        this.stackName = "sb-" + envName;
        createSaaSBoostStack(stackName, emailAddress, setupActiveDirectory, activeDirectoryPasswordParameterName);

        // TODO if we capture the create complete event for the web bucket from the main SaaS Boost stack
        // we could pop a message in a queue that we could listen to here and trigger the website build
        // while the rest of the CloudFormation is finishing. Or, we could move this to a simple CodeBuild
        // project and have CloudFormation own building/copying the web files to S3.
        // Wait for completion and then build web app
        outputMessage("Build website and upload to S3");
        final String webUrl = buildAndCopyWebApp(this.workingDir, cfn, s3, this.envName, this.accountId);

        if (useAnalyticsModule) {
            LOGGER.info("Install metrics and analytics module");
            // The analytics module stack reads baseStackDetails for its CloudFormation template parameters
            // because we're not yet creating the analytics resources as a nested child stack of the main stack
            this.environment = ExistingEnvironmentFactory.findExistingEnvironment(
                ssm, cfn, this.envName, this.accountId);
            this.baseStackDetails = environment.getBaseCloudFormationStackInfo();
            installAnalyticsModule();
        }

        outputMessage("Check the admin email box for the temporary password.");
        outputMessage("AWS SaaS Boost Artifacts Bucket: " + saasBoostArtifactsBucket);
        outputMessage("AWS SaaS Boost Console URL is: " + webUrl);
    }

    protected void deleteSaasBoostInstallation() {
        // Confirm delete
        outputMessage("****** W A R N I N G");
        outputMessage("Deleting the AWS SaaS Boost environment is IRREVERSIBLE and ALL deployed tenant resources will be deleted!");
        while (true) {
            System.out.print("Enter the SaaS Boost environment name to confirm: ");
            String confirmEnvName = Keyboard.readString();
            if (isNotBlank(confirmEnvName) && this.envName.equalsIgnoreCase(confirmEnvName)) {
                System.out.println("SaaS Boost environment " + this.envName + " for AWS Account " + this.accountId + " in region " + AWS_REGION + " will be deleted. This action cannot be undone!");
                break;
            } else {
                outputMessage("Entered value is incorrect, please try again.");
            }
        }

        System.out.print("Are you sure you want to delete the SaaS Boost environment " + this.envName + "? Enter y to continue or n to cancel: ");
        boolean continueDelete = Keyboard.readBoolean();
        if (!continueDelete) {
            outputMessage("Canceled Delete of AWS SaaS Boost environment");
            System.exit(2);
        } else {
            outputMessage("Continuing Delete of AWS SaaS Boost environment " + this.envName);
        }

        // Delete all the provisioned tenants
        List<LinkedHashMap<String, Object>> tenants = getProvisionedTenants();
        for (LinkedHashMap<String, Object> tenant : tenants) {
            outputMessage("Deleting AWS SaaS Boost tenant " + tenant.get("id"));
            deleteProvisionedTenant(tenant);
        }

        // Clear all the images from ECR or CloudFormation won't be able to delete the repository
        try {
            for (String ecrRepo : getEcrRepositories()) {
                outputMessage("Deleting images from ECR repository " + ecrRepo);
                deleteEcrImages(ecrRepo);
            }
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameter error", ssmError);
            LOGGER.error(getFullStackTrace(ssmError));
            // throw ssmError;
        }

        // Clear all the Parameter Store entries for this environment that CloudFormation doesn't own
        deleteApplicationConfig();

        // Delete the analytics stack if it exists
        String analyticsStackName = analyticsStackName();
        if (checkCloudFormationStack(analyticsStackName)) {
            outputMessage("Deleting AWS SaaS Boost Analytics Module stack: " + analyticsStackName);
            deleteCloudFormationStack(analyticsStackName);
        }

        // Delete the SaaS Boost stack
        outputMessage("Deleting AWS SaaS Boost stack: " + this.stackName);
        deleteCloudFormationStack(this.stackName);
        // Delete the ActiveDirectory password in SecretsManager if it exists
        try {
            secretsManager.deleteSecret(request -> request
                .forceDeleteWithoutRecovery(true)
                .secretId("/saas-boost/" + envName + "/ACTIVE_DIRECTORY_PASSWORD")
                .build());
            outputMessage("ActiveDirectory secretsManager secret deleted.");
        } catch (ResourceNotFoundException rnfe) {
            // there is no ACTIVE_DIRECTORY_PASSWORD secret, so there is nothing to delete
        }

        // Finally, remove the S3 artifacts bucket that this installer created outside of CloudFormation
        LOGGER.info("Clean up s3 bucket: " + saasBoostArtifactsBucket);
        cleanUpS3(s3, saasBoostArtifactsBucket.getBucketName(), null);
        s3.deleteBucket(r -> r.bucket(saasBoostArtifactsBucket.getBucketName()));

        // This installer also creates some Parameter Store entries outside of CloudFormation which are
        // needed to delete stacks via CloudFormation. delete these last.
        // TODO move these parameters to CloudFormation
        try {
            List<String> parameterNamesToDelete = ssm.describeParameters(
                request -> request.parameterFilters(ParameterStringFilter.builder()
                    .key("Path")
                    .values("/saas-boost/" + this.envName + "/")
                    .build()))
                .parameters().stream().map(meta -> meta.name()).collect(Collectors.toList());
            // we need to batch ssm.deleteParameters in sizes of 10 parameters
            // https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_DeleteParameters.html#API_DeleteParameters_RequestSyntax
            final int ssmBatchSize = 10;
            for (int i = 0; i < parameterNamesToDelete.size(); i += ssmBatchSize) {
                int batchStart = i;
                int batchEnd = Math.min(batchStart + ssmBatchSize, parameterNamesToDelete.size());
                // List#subList returns a view of the List inclusive from 'start' to exclusive on 'end'
                ssm.deleteParameters(request -> request.names(parameterNamesToDelete.subList(batchStart, batchEnd)));
            }
        } catch (SdkServiceException ssmError) {
            outputMessage("Failed to delete all Parameter Store entries");
            LOGGER.error("ssm:DeleteParameters error", ssmError);
            LOGGER.error(getFullStackTrace(ssmError));
        }

        outputMessage("Delete of SaaS Boost environment " + this.envName + " complete.");
    }

    private List<String> getEcrRepositories() {
        List<String> repos = new ArrayList<>();
        Map<String, Object> systemApiRequest = new HashMap<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("resource", "settings/config");
        detail.put("method", "GET");
        systemApiRequest.put("detail", detail);
        final byte[] payload = Utils.toJson(systemApiRequest).getBytes();
        try {
            LOGGER.info("Invoking getSettings API");
            InvokeResponse response = lambda.invoke(request -> request
                    .functionName("sb-" + this.envName + "-private-api-client")
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromByteArray(payload))
            );
            if (response.sdkHttpResponse().isSuccessful()) {
                LOGGER.error("got response back: {}", response);
                String configJson = response.payload().asUtf8String();
                HashMap<String, Object> config = Utils.fromJson(configJson, HashMap.class);
                HashMap<String, Object> services = (HashMap<String, Object>) config.get("services");
                for (String serviceName : services.keySet()) {
                    HashMap<String, Object> service = (HashMap<String, Object>) services.get(serviceName);
                    repos.add((String) service.get("containerRepo"));
                }
            } else {
                LOGGER.warn("Private API client Lambda returned HTTP " + response.sdkHttpResponse().statusCode());
                throw new RuntimeException(response.sdkHttpResponse().statusText().get());
            }
        } catch (SdkServiceException lambdaError) {
            LOGGER.error("lambda:Invoke error", lambdaError);
            LOGGER.error(getFullStackTrace(lambdaError));
            throw lambdaError;
        }
        return repos;
    }

    protected void installAnalyticsModule() {
        LOGGER.info("Installing Analytics module into existing AWS SaaS Boost installation.");
        outputMessage("Analytics will be deployed into the existing AWS SaaS Boost environment " + this.envName + ".");

        String metricsStackName = analyticsStackName();
        try {
            DescribeStacksResponse metricsStackResponse = cfn.describeStacks(request -> request.stackName(metricsStackName));
            if (metricsStackResponse.hasStacks()) {
                outputMessage("AWS SaaS Boost Analytics stack with name: " + metricsStackName + " is already deployed");
                System.exit(2);
            }
        } catch (CloudFormationException cfnError) {
            // Calling describe-stacks on a stack name that doesn't exist is an exception
            if (!cfnError.getMessage().contains("Stack with id " + metricsStackName + " does not exist")) {
                LOGGER.error("cloudformation:DescribeStacks error {}", cfnError.getMessage());
                LOGGER.error(getFullStackTrace(cfnError));
                throw cfnError;
            }
        }

        outputMessage("===========================================================");
        outputMessage("");
        outputMessage("Would you like to continue the Analytics module installation with the following options?");
        outputMessage("Existing AWS SaaS Boost environment : " + envName);
        if (useQuickSight) {
            outputMessage("Amazon QuickSight user for Analytics Module: " + quickSightUsername);
        } else {
            outputMessage("Amazon QuickSight user for Analytics Module: N/A");
        }

        System.out.print("Continue (y or n)? ");
        boolean continueInstall = Keyboard.readBoolean();
        if (!continueInstall) {
            outputMessage("Canceled installation of AWS SaaS Boost Analytics");
            cancel();
        }
        outputMessage("Continuing installation of AWS SaaS Boost Analytics");
        outputMessage("===========================================================");
        outputMessage("Installing AWS SaaS Boost Metrics and Analytics Module");
        outputMessage("===========================================================");

        // Generate a password for the RedShift database if we don't already have one
        String dbPassword = null;
        String dbPasswordParam = "/saas-boost/" + this.envName + "/REDSHIFT_MASTER_PASSWORD";
        try {
            GetParameterResponse existingDbPasswordResponse = ssm.getParameter(GetParameterRequest.builder()
                    .name(dbPasswordParam)
                    .withDecryption(true)
                    .build()
            );
            // We actually need the secret value because we need to give it to QuickSight
            dbPassword = existingDbPasswordResponse.parameter().value();
            // And, we'll add the parameter version to the end of the name just in case it's greater than 1
            // so that CloudFormation can properly fetch the secret value
            dbPasswordParam = dbPasswordParam + ":" + existingDbPasswordResponse.parameter().version();
            LOGGER.info("Reusing existing RedShift password for Analytics");
        } catch (SdkServiceException noSuchParameter) {
            LOGGER.info("Generating new random RedShift password for Analytics");
            // Save the database password as a secret
            dbPassword = generatePassword(16);
            try {
                LOGGER.info("Saving RedShift password secret to Parameter Store");
                PutParameterResponse dbPasswordResponse = ssm.putParameter(PutParameterRequest.builder()
                        .name(dbPasswordParam)
                        .type(ParameterType.SECURE_STRING)
                        .overwrite(true)
                        .value(dbPassword)
                        .build()
                );
            } catch (SdkServiceException ssmError) {
                LOGGER.error("ssm:PutParamter error {}", ssmError.getMessage());
                LOGGER.error(getFullStackTrace(ssmError));
                throw ssmError;
            }
            // CloudFormation ssm-secure resolution needs a version number, which is guaranteed to be 1
            // in this case where we just created it
            dbPasswordParam = dbPasswordParam + ":1";
        }
        outputMessage("Redshift Database User Password stored in secure SSM Parameter: " + dbPasswordParam);

        // Run CloudFormation
        outputMessage("Creating CloudFormation stack " + metricsStackName + " for Analytics Module");
        String databaseName = "sb_analytics_" + this.envName.replaceAll("-", "_");
        createMetricsStack(metricsStackName, dbPasswordParam, databaseName);

        // TODO Why doesn't the CloudFormation template own this?
        LOGGER.info("Update SSM param METRICS_ANALYTICS_DEPLOYED to true");
        try {
            PutParameterResponse response = ssm.putParameter(request -> request
                    .name("/saas-boost/" + this.envName + "/METRICS_ANALYTICS_DEPLOYED")
                    .type(ParameterType.STRING)
                    .overwrite(true)
                    .value("true")
            );
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:PutParameter error {}", ssmError.getMessage());
            LOGGER.error(getFullStackTrace(ssmError));
            throw ssmError;
        }

        // Upload the JSON path file for Redshift to the bucket provisioned by CloudFormation
        Map<String, String> outputs = getMetricStackOutputs(metricsStackName);
        String metricsBucket = outputs.get("MetricsBucket");
        Path jsonPathFile = workingDir.resolve(Path.of("metrics-analytics", "deploy", "artifacts", "metrics_redshift_jsonpath.json"));

        LOGGER.info("Copying json files for Metrics and Analytics from {} to {}", jsonPathFile.toString(), metricsBucket);
        try {
            PutObjectResponse s3Response = s3.putObject(PutObjectRequest.builder()
                    .bucket(metricsBucket)
                    .key("metrics_redshift_jsonpath.json")
                    .contentType("text/json")
                    .build(), RequestBody.fromFile(jsonPathFile)
            );
        } catch (SdkServiceException s3Error) {
            LOGGER.error("s3:PutObject error {}", s3Error.getMessage());
            LOGGER.error(getFullStackTrace(s3Error));
            outputMessage("Error copying " + jsonPathFile.toString() + " to " + metricsBucket);
            // TODO Why don't we bail here if that file is required?
            outputMessage("Continuing with installation so you will need to manually upload that file.");
        }

        // Setup the quicksight dataset
        if (useQuickSight) {
            outputMessage("Set up Amazon Quicksight for Analytics Module");
            try {
                // TODO does this fail if it's run more than once?
                setupQuickSight(metricsStackName, outputs, dbPassword);
            } catch (Exception e) {
                outputMessage("Error with setup of Quicksight datasource and dataset. Check log file.");
                outputMessage("Message: " + e.getMessage());
                LOGGER.error(getFullStackTrace(e));
                System.exit(2);
            }
        }
    }

    protected void updateWebApp() {
        LOGGER.info("Perform Update of the Web Application for AWS SaaS Boost");
        outputMessage("Build web app and copy files to S3 web bucket");
        String webUrl = buildAndCopyWebApp(this.workingDir, cfn, s3, this.envName, this.accountId);
        outputMessage("AWS SaaS Boost Console URL is: " + webUrl);
    }

    protected void cancel() {
        outputMessage("Cancelling.");
        System.exit(0);
    }

    protected static Path getWorkingDirectory() {
        Path workingDir = Paths.get("");
        String currentDir = workingDir.toAbsolutePath().toString();
        LOGGER.info("Current dir = {}", currentDir);
        while (true) {
            System.out.print("Directory path of Saas Boost download (Press Enter for '" + currentDir + "'): ");
            String saasBoostDirectory = Keyboard.readString();
            if (isNotBlank(saasBoostDirectory)) {
                workingDir = Path.of(saasBoostDirectory);
            } else {
                workingDir = Paths.get("");
            }
            if (Files.isDirectory(workingDir)) {
                if (Files.isDirectory(workingDir.resolve("resources"))) {
                    break;
                } else {
                    outputMessage("No resources directory found under " + workingDir.toAbsolutePath().toString() + ". Check path.");
                }
            } else {
                outputMessage("Path: " + workingDir.toAbsolutePath().toString() + " is not a directory, try again.");
            }
        }
        LOGGER.info("Using directory {}", workingDir.toAbsolutePath().toString());
        return workingDir;
    }

    protected void getQuickSightUsername() {
        Region quickSightRegion;
        QuickSightClient oldClient = null;
        while (true) {
            System.out.print("Region where you registered for Amazon QuickSight (Press Enter for " + AWS_REGION.id() + "): ");
            String quickSightAccountRegion = Keyboard.readString();
            if (isBlank(quickSightAccountRegion)) {
                quickSightRegion = AWS_REGION;
            } else {
                // Make sure we got a valid AWS region string
                quickSightRegion = Region.regions().stream().filter(request -> request
                        .id()
                        .equals(quickSightAccountRegion))
                        .findAny()
                        .orElse(null);
            }
            if (quickSightRegion != null) {
                // Update the SDK client for the proper AWS region if we need to
                if (!AWS_REGION.equals(quickSightRegion)) {
                    oldClient = quickSight;
                    quickSight = awsClientBuilderFactory.quickSightBuilder().region(quickSightRegion).build();
                }
                // See if there are QuickSight users in this account in this region
                LinkedHashMap<String, User> quickSightUsers = getQuickSightUsers();
                if (!quickSightUsers.isEmpty()) {
                    String defaultQuickSightUsername = quickSightUsers.keySet().stream().findFirst().orElse(null);
                    System.out.print("Amazon Quicksight user name (Press Enter for '" + defaultQuickSightUsername + "'): ");
                    this.quickSightUsername = Keyboard.readString();
                    if (isBlank(this.quickSightUsername)) {
                        this.quickSightUsername = defaultQuickSightUsername;
                    }
                    if (quickSightUsers.containsKey(this.quickSightUsername)) {
                        this.quickSightUserArn = quickSightUsers.get(this.quickSightUsername).arn();
                        break;
                    } else {
                        outputMessage("Entered value is not a valid Quicksight user in your account, please try again.");
                    }
                } else {
                    outputMessage("No users found in QuickSight. Please register in your AWS Account and try install again.");
                    System.exit(2);
                }
            } else {
                outputMessage("Entered value is not a region, please try again.");
            }
        }
        // If we changed the QuickSight SDK client region to look up the username, put it back
        if (oldClient != null) {
            quickSight = oldClient;
        }
    }

    protected List<LinkedHashMap<String, Object>> getProvisionedTenants() {
        List<LinkedHashMap<String, Object>> provisionedTenants = new ArrayList<>();
        Map<String, Object> systemApiRequest = new HashMap<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("resource", "tenants");
        detail.put("method", "GET");
        systemApiRequest.put("detail", detail);
        final byte[] payload = Utils.toJson(systemApiRequest).getBytes(StandardCharsets.UTF_8);
        try {
            LOGGER.info("Invoking get provisioned tenants API");
            InvokeResponse response = lambda.invoke(request -> request
                    .functionName("sb-" + this.envName + "-private-api-client")
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromByteArray(payload))
            );
            if (response.sdkHttpResponse().isSuccessful()) {
                String responseBody = response.payload().asUtf8String();
                LOGGER.info("Response Body");
                LOGGER.info(responseBody);
                provisionedTenants = Utils.fromJson(responseBody, ArrayList.class);
                LOGGER.info("Loaded " + provisionedTenants.size() + " tenants");
            } else {
                LOGGER.warn("Private API client Lambda returned HTTP " + response.sdkHttpResponse().statusCode());
                throw new RuntimeException(response.sdkHttpResponse().statusText().get());
            }
        } catch (SdkServiceException lambdaError) {
            LOGGER.error("lambda:Invoke error", lambdaError);
            LOGGER.error(getFullStackTrace(lambdaError));
            throw lambdaError;
        }
        return provisionedTenants;
    }

    private LinkedHashMap<String, Object> getTenant(String tenantId) {
        LinkedHashMap<String, Object> tenantDetail = new LinkedHashMap<>();
        Map<String, Object> systemApiRequest = new HashMap<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("resource", "tenants/" + tenantId);
        detail.put("method", "GET");
        systemApiRequest.put("detail", detail);
        final byte[] payload = Utils.toJson(systemApiRequest).getBytes();
        try {
            LOGGER.info("Invoking get tenant by id API");
            InvokeResponse response = lambda.invoke(request -> request
                    .functionName("sb-" + this.envName + "-private-api-client")
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromByteArray(payload))
            );
            if (response.sdkHttpResponse().isSuccessful()) {
                String responseBody = response.payload().asUtf8String();
                LOGGER.info("Response Body");
                LOGGER.info(responseBody);
                tenantDetail = Utils.fromJson(responseBody, LinkedHashMap.class);
            } else {
                LOGGER.warn("Private API client Lambda returned HTTP " + response.sdkHttpResponse().statusCode());
                throw new RuntimeException(response.sdkHttpResponse().statusText().get());
            }
        } catch (SdkServiceException lambdaError) {
            LOGGER.error("lambda:Invoke error", lambdaError);
            LOGGER.error(getFullStackTrace(lambdaError));
            throw lambdaError;
        }
        return tenantDetail;
    }

    protected void deleteApplicationConfig() {
        Map<String, Object> systemApiRequest = new HashMap<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("resource", "settings/config");
        detail.put("method", "DELETE");
        systemApiRequest.put("detail", detail);
        final byte[] payload = Utils.toJson(systemApiRequest).getBytes(StandardCharsets.UTF_8);
        try {
            LOGGER.info("Invoking delete application config API");
            InvokeResponse response = lambda.invoke(request -> request
                    .functionName("sb-" + this.envName + "-private-api-client")
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromByteArray(payload))
            );
            if (!response.sdkHttpResponse().isSuccessful()) {
                LOGGER.warn("Private API client Lambda returned HTTP " + response.sdkHttpResponse().statusCode());
                throw new RuntimeException(response.sdkHttpResponse().statusText().get());
            }
        } catch (SdkServiceException lambdaError) {
            LOGGER.error("lambda:Invoke error", lambdaError);
            LOGGER.error(getFullStackTrace(lambdaError));
            throw lambdaError;
        }
    }

    protected void deleteProvisionedTenant(LinkedHashMap<String, Object> tenant) {
        // TODO we can parallelize to improve performance with lots of tenants
        Map<String, Object> systemApiRequest = new HashMap<>();
        Map<String, Object> detail = new HashMap<>();
        detail.put("resource", "tenants/" + (String) tenant.get("id"));
        detail.put("method", "DELETE");
        String tenantId = (String) tenant.get("id");
        Map<String, String> tenantIdOnly = new HashMap<>();
        tenantIdOnly.put("id", tenantId);
        detail.put("body", Utils.toJson(tenantIdOnly));
        systemApiRequest.put("detail", detail);
        final byte[] payload = Utils.toJson(systemApiRequest).getBytes();
        try {
            LOGGER.info("Invoking delete tenant API");
            InvokeResponse response = lambda.invoke(request -> request
                    .functionName("sb-" + this.envName + "-private-api-client")
                    .invocationType(InvocationType.REQUEST_RESPONSE)
                    .payload(SdkBytes.fromByteArray(payload))
            );
            if (response.sdkHttpResponse().isSuccessful()) {
                LOGGER.debug("got response back: {}", response);
                // wait for tenant to reach deleted
                final String DELETED = "deleted";
                LocalDateTime timeout = LocalDateTime.now().plus(60, ChronoUnit.MINUTES);
                String tenantStatus = (String) getTenant(tenantId).get("onboardingStatus");
                boolean deleted = tenantStatus.equalsIgnoreCase(DELETED);
                while (!deleted) {
                    if (LocalDateTime.now().compareTo(timeout) > 0) {
                        // we've timed out retrying
                        outputMessage("Timed out waiting for tenant " + tenantId + " to reach deleted state. " +
                            "Please check CloudFormation in your AWS Console for more details.");
                        // if a tenant delete fails, trying to delete the rest of the stack is guaranteed to fail
                        // due to Tenant resources having cross-dependencies with other resources. stop here to let
                        // the user figure out what went wrong
                        throw new RuntimeException("Delete failed.");
                    }
                    outputMessage("Waiting 1 minute for tenant " + tenantId +
                        " to reach deleted from " + tenantStatus);
                    Thread.sleep(60 * 1000L); // 1 minute
                    tenantStatus = (String) getTenant(tenantId).get("onboardingStatus");
                    deleted = tenantStatus.equalsIgnoreCase(DELETED);
                }
            } else {
                LOGGER.warn("Private API client Lambda returned HTTP " + response.sdkHttpResponse().statusCode());
                throw new RuntimeException(response.sdkHttpResponse().statusText().get());
            }
        } catch (SdkServiceException lambdaError) {
            LOGGER.error("lambda:Invoke error", lambdaError);
            LOGGER.error(getFullStackTrace(lambdaError));
            throw lambdaError;
        } catch (InterruptedException ie) {
            LOGGER.error("Exception in waiting");
            LOGGER.error(getFullStackTrace(ie));
            throw new RuntimeException(ie);
        }
    }

    protected void deleteEcrImages(String ecrRepo) {
        List<ImageIdentifier> imagesToDelete = new ArrayList<>();
        LOGGER.info("Loading images from ECR repository " + ecrRepo);
        String token = null;
        do {
            try {
                ListImagesResponse response = ecr.listImages(ListImagesRequest.builder()
                        .repositoryName(ecrRepo)
                        .nextToken(token)
                        .maxResults(1000)
                        .build()
                );
                if (response.hasImageIds()) {
                    response.imageIds().stream().forEachOrdered(imagesToDelete::add);
                }
                token = response.nextToken();
            } catch (SdkServiceException ecrError) {
                LOGGER.error("ecr:ListImages error", ecrError);
                LOGGER.error(getFullStackTrace(ecrError));
                throw ecrError;
            }
        } while (token != null);
        LOGGER.info("Loaded " + imagesToDelete.size() + " images to delete");
        if (!imagesToDelete.isEmpty()) {
            try {
                BatchDeleteImageResponse response = ecr.batchDeleteImage(request -> request.repositoryName(ecrRepo).imageIds(imagesToDelete));
                if (response.hasImageIds()) {
                    LOGGER.info("Deleted " + response.imageIds().size() + " images");
                }
                if (response.hasFailures() && !response.failures().isEmpty()) {
                    LOGGER.error("Error deleting images from ECR");
                    response.failures().forEach(failure -> LOGGER.error("{} {}", failure.failureCodeAsString(), failure.failureReason()));
                    throw new RuntimeException("ECR delete image failures " + response.failures().size());
                }
            } catch (SdkServiceException ecrError) {
                LOGGER.error("ecr:batchDeleteImage error", ecrError);
                LOGGER.error(getFullStackTrace(ecrError));
                throw ecrError;
            }
        }
    }

    protected Map<String, String> getMetricStackOutputs(String stackName) {
        // Get the Redshift outputs from the metrics CloudFormation stack
        Map<String, String> outputs = null;
        try {
            DescribeStacksResponse stacksResponse = cfn.describeStacks(DescribeStacksRequest.builder().stackName(stackName).build());
            outputs = stacksResponse.stacks().get(0).outputs().stream().collect(Collectors.toMap(Output::outputKey, Output::outputValue));
            for (String requiredOutput : Arrays.asList("RedshiftDatabaseName", "RedshiftEndpointAddress", "RedshiftCluster", "RedshiftEndpointPort", "MetricsBucket")) {
                if (outputs.get(requiredOutput) == null) {
                    outputMessage("Error, CloudFormation stack: " + stackName + " missing required output: " + requiredOutput);
                    outputMessage(("Aborting the installation due to error"));
                    System.exit(2);
                }
            }
        } catch (SdkServiceException cloudFormationError) {
            LOGGER.error("cloudformation:DescribeStack error", cloudFormationError);
            LOGGER.error(getFullStackTrace(cloudFormationError));
            outputMessage("getMetricStackOutputs: Unable to load Metrics and Analytics CloudFormation stack: " + stackName);
            System.exit(2);
        }
        return outputs;
    }

    protected void setupQuickSight(String stackName, Map<String, String> outputs, String dbPassword) {
        /* TODO Note that this entire QuickSight setup is not owned by CloudFormation like most everything
         * else and therefore won't be cleaned up properly when SaaS Boost is deleted/uninstalled.
         */
        LOGGER.info("User for QuickSight: " + this.quickSightUsername);
        LOGGER.info("Create data source in QuickSight for metrics Redshift table in Region: " + AWS_REGION.id());
        final CreateDataSourceResponse createDataSourceResponse = quickSight.createDataSource(CreateDataSourceRequest.builder()
                .dataSourceId("sb-" + this.envName + "-metrics-source")
                .name("sb-" + this.envName + "-metrics-source")
                .awsAccountId(accountId)
                .type(DataSourceType.REDSHIFT)
                .dataSourceParameters(DataSourceParameters.builder()
                        .redshiftParameters(RedshiftParameters.builder()
                                .database(outputs.get("RedshiftDatabaseName"))
                                .host(outputs.get("RedshiftEndpointAddress"))
                                .clusterId(outputs.get("RedshiftCluster"))
                                .port(Integer.valueOf(outputs.get("RedshiftEndpointPort")))
                                .build()
                        )
                        .build()
                )
                .credentials(DataSourceCredentials.builder()
                        .credentialPair(CredentialPair.builder()
                                .username("metricsadmin")
                                .password(dbPassword)
                                .build()
                        )
                        .build()
                )
                .permissions(ResourcePermission.builder()
                        .principal(this.quickSightUserArn)
                        .actions("quicksight:DescribeDataSource","quicksight:DescribeDataSourcePermissions",
                                "quicksight:PassDataSource","quicksight:UpdateDataSource","quicksight:DeleteDataSource",
                                "quicksight:UpdateDataSourcePermissions")
                        .build()
                )
                .sslProperties(SslProperties.builder()
                        .disableSsl(false)
                        .build()
                )
                .tags(Tag.builder()
                        .key("Name")
                        .value(stackName)
                        .build()
                )
                .build()
        );

        // Define the physical table for QuickSight
        List<InputColumn> inputColumns = new ArrayList<>();
        Stream.of("type", "workload", "context", "tenant_id", "tenant_name", "tenant_tier", "metric_name", "metric_unit", "meta_data")
                .map(column -> InputColumn.builder()
                                .name(column)
                                .type(InputColumnDataType.STRING)
                                .build()
                )
                .forEachOrdered(inputColumns::add);
        inputColumns.add(InputColumn.builder()
                .name("metric_value")
                .type(InputColumnDataType.INTEGER)
                .build()
        );
        inputColumns.add(InputColumn.builder()
                .name("timerecorded")
                .type(InputColumnDataType.DATETIME)
                .build()
        );

        PhysicalTable physicalTable = PhysicalTable.builder()
                .relationalTable(RelationalTable.builder()
                        .dataSourceArn(createDataSourceResponse.arn())
                        .schema("public")
                        .name("sb_metrics")
                        .inputColumns(inputColumns)
                        .build()
                )
                .build();

        Map<String, PhysicalTable> physicalTableMap = new HashMap<>();
        physicalTableMap.put("string", physicalTable);

        LOGGER.info("Create dataset for sb_metrics table in Quicksight in Region " + AWS_REGION.id());
        quickSight.createDataSet(CreateDataSetRequest.builder()
                .awsAccountId(accountId)
                .dataSetId("sb-" + this.envName + "-metrics")
                .name("sb-" + this.envName + "-metrics")
                .physicalTableMap(physicalTableMap)
                .importMode(DataSetImportMode.DIRECT_QUERY)
                .permissions(ResourcePermission.builder()
                        .principal(this.quickSightUserArn)
                        .actions("quicksight:DescribeDataSet","quicksight:DescribeDataSetPermissions",
                                "quicksight:PassDataSet","quicksight:DescribeIngestion","quicksight:ListIngestions",
                                "quicksight:UpdateDataSet","quicksight:DeleteDataSet","quicksight:CreateIngestion",
                                "quicksight:CancelIngestion","quicksight:UpdateDataSetPermissions")
                        .build()
                )
                .tags(Tag.builder()
                        .key("Name")
                        .value(stackName)
                        .build()
                )
                .build()
        );
    }

    /*
    Create Service Roles necessary for Tenant Stack Deployment
     */
    protected void setupAwsServiceRoles() {
        /*
        aws iam get-role --role-name "AWSServiceRoleForElasticLoadBalancing" || aws iam create-service-linked-role --aws-service-name "elasticloadbalancing.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForECS" || aws iam create-service-linked-role --aws-service-name "ecs.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForApplicationAutoScaling_ECSService" || aws iam create-service-linked-role --aws-service-name "ecs.application-autoscaling.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForRDS" || aws iam create-service-linked-role --aws-service-name "rds.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForAmazonFsx" || aws iam create-service-linked-role --aws-service-name "fsx.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForAutoScaling" || aws iam create-service-linked-role --aws-service-name "autoscaling.amazonaws.com"        
        */

        Set<String> existingRoles = new HashSet<>();
        ListRolesResponse rolesResponse = iam.listRoles(request -> request.pathPrefix("/aws-service-role"));
        rolesResponse.roles().stream()
                .map(Role::path)
                .forEachOrdered(existingRoles::add);

        List<String> serviceRoles = new ArrayList<>(Arrays.asList("elasticloadbalancing.amazonaws.com",
                "ecs.amazonaws.com", "ecs.application-autoscaling.amazonaws.com", "rds.amazonaws.com",
                "fsx.amazonaws.com", "autoscaling.amazonaws.com")
        );
        for (String serviceRole : serviceRoles) {
            if (existingRoles.contains("/aws-service-role/" + serviceRole + "/")) {
                LOGGER.info("Service role exists for {}", serviceRole);
                continue;
            }
            LOGGER.info("Creating AWS service role in IAM for {}", serviceRole);
            try {
                iam.createServiceLinkedRole(request -> request.awsServiceName(serviceRole));
            } catch (SdkServiceException iamError) {
                LOGGER.error("iam:CreateServiceLinkedRole error", iamError);
                LOGGER.error(getFullStackTrace(iamError));
                throw iamError;
            }
        }
    }

    protected void copyTemplateFilesToS3() {
        final PathMatcher filter = FileSystems.getDefault().getPathMatcher("glob:**.{yaml,yml}");
        final Path resourcesDir = workingDir.resolve(Path.of("resources"));
        try (Stream<Path> stream = Files.list(resourcesDir)) {
            Set<Path> cloudFormationTemplates = stream
                    .filter(filter::matches)
                    .collect(Collectors.toSet());
            outputMessage("Uploading " + cloudFormationTemplates.size() + " CloudFormation templates to S3");
            for (Path cloudFormationTemplate : cloudFormationTemplates) {
                LOGGER.info("Uploading CloudFormation template to S3 " + cloudFormationTemplate.toString() + " -> "
                        + cloudFormationTemplate.getFileName().toString());
                // TODO validate template for syntax errors before continuing with installation
                saasBoostArtifactsBucket.putFile(s3, cloudFormationTemplate, cloudFormationTemplate.getFileName());
            }
        } catch (IOException ioe) {
            LOGGER.error("Error listing resources directory", ioe);
            LOGGER.error(getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
    }

    protected static void printResults(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = "";
            while ((line = reader.readLine()) != null) {
                LOGGER.info(line);
            }
        } catch (IOException ioe) {
            LOGGER.error("Error reading from runtime exec process", ioe);
            LOGGER.error(getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
    }

    public static void outputMessage(String msg) {
        LOGGER.info(msg);
        System.out.println(msg);
    }

    protected static void checkEnvironment() {
        outputMessage("Checking maven, yarn and AWS CLI...");
        try {
            executeCommand("mvn -version", null, null);
        } catch (Exception e) {
            outputMessage("Could not execute 'mvn -version', please check your environment");
            System.exit(2);
        }
        try {
            executeCommand("yarn -version", null, null);
        } catch (Exception e) {
            outputMessage("Could not execute 'yarn -version', please check your environment.");
            System.exit(2);
        }
        try {
            executeCommand("aws --version", null, null);
        } catch (Exception e) {
            outputMessage("Could not execute 'aws --version', please check your environment.");
            System.exit(2);
        }
        try {
            executeCommand("aws sts get-caller-identity", null, null);
        } catch (Exception e) {
            outputMessage("Could not execute 'aws sts get-caller-identity', please check AWS CLI configuration.");
            System.exit(2);
        }
        outputMessage("Environment Checks for maven, yarn, and AWS CLI PASSED.");
        outputMessage("===========================================================");
    }

    protected void loadExistingSaaSBoostEnvironment() {
        this.envName = getExistingSaaSBoostEnvironment();
        try {
            this.environment = ExistingEnvironmentFactory.findExistingEnvironment(ssm, cfn, envName, accountId);
        } catch (EnvironmentLoadException ele) {
            outputMessage("Failed to load existing SaaS Boost Environment: " + ele.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ele));
            System.exit(2);
        }
        this.saasBoostArtifactsBucket = environment.getArtifactsBucket();
        this.lambdaSourceFolder = environment.getLambdasFolderName();
        this.stackName = environment.getBaseCloudFormationStackName();
        this.baseStackDetails = environment.getBaseCloudFormationStackInfo();
        this.useAnalyticsModule = environment.isMetricsAnalyticsDeployed();
    }

    protected String getExistingSaaSBoostEnvironment() {
        LOGGER.info("Asking for existing SaaS Boost environment label");
        String environment = null;
        while (isBlank(environment)) {
            System.out.print("Please enter the existing SaaS Boost environment label: ");
            environment = Keyboard.readString();
            if (!validateEnvironmentName(environment)) {
                outputMessage("Entered value is incorrect, maximum of 10 alphanumeric characters, please try again.");
                environment = null;
            }
        }
        try {
            ssm.getParameter(GetParameterRequest.builder().name("/saas-boost/" + environment + "/SAAS_BOOST_ENVIRONMENT").build());
        } catch (ParameterNotFoundException ssmError) {
            outputMessage("Cannot find existing SaaS Boost environment " + environment
                    + " in this AWS account and region.");
            System.exit(2);
        }
        return environment;
    }

    protected static boolean validateEmail(String emailAddress) {
        boolean valid = false;
        if (emailAddress != null) {
            valid = emailAddress.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
        }
        return valid;
    }

    protected static boolean validateEnvironmentName(String envName) {
        boolean valid = false;
        if (envName != null) {
            // Follows CloudFormation stack name rules but limits to 10 characters
            valid = envName.matches("^[a-zA-Z](?:[a-zA-Z0-9-]){0,9}$");
        }
        return valid;
    }

    protected void processLambdas() {
        try {
            List<Path> sourceDirectories = new ArrayList<>();

            // The parent pom installs an artifact in the local maven cache that child projects
            // expect to exist or you'll get a failed to read artifact descriptor error when you
            // try to build them directly from the child pom. So, before we do anything, install
            // just the parent pom into the local maven cache (and then we'll actually build the
            // things we need to below)
            executeCommand("mvn --non-recursive install", null, workingDir.toAbsolutePath().toFile());

            // Because the parent pom for the layers modules defines its own artifact name
            // we have to build from the parent pom or maven won't be able to find the
            // artifact in the local maven cache.
            executeCommand("mvn --non-recursive install", null, workingDir.resolve(Path.of("layers")).toFile());

            // Now add the separate layers directories to the list so we can upload the lambda
            // package to S3 below. Build utils before anything else.
            sourceDirectories.add(workingDir.resolve(Path.of("layers", "utils")));
            sourceDirectories.add(workingDir.resolve(Path.of("layers", "apigw-helper")));
            sourceDirectories.add(workingDir.resolve(Path.of("layers", "cloudformation-utils")));

            DirectoryStream<Path> functions = Files.newDirectoryStream(workingDir.resolve(Path.of("functions")), Files::isDirectory);
            functions.forEach(sourceDirectories::add);

            DirectoryStream<Path> customResources = Files.newDirectoryStream(workingDir.resolve(Path.of("resources", "custom-resources")), Files::isDirectory);
            customResources.forEach(sourceDirectories::add);

            DirectoryStream<Path> services = Files.newDirectoryStream(workingDir.resolve(Path.of("services")), Files::isDirectory);
            services.forEach(sourceDirectories::add);

            sourceDirectories.add(workingDir.resolve(Path.of("metering-billing", "lambdas")));

            final PathMatcher filter = FileSystems.getDefault().getPathMatcher("glob:**.zip");
            outputMessage("Uploading " + sourceDirectories.size() + " Lambda functions to S3");
            for (Path sourceDirectory : sourceDirectories) {
                if (Files.exists(sourceDirectory.resolve("pom.xml"))) {
                    executeCommand("mvn", null, sourceDirectory.toFile());
                    final Path targetDir = sourceDirectory.resolve("target");
                    try (Stream<Path> stream = Files.list(targetDir)) {
                        Set<Path> lambdaSourcePackage = stream
                                .filter(filter::matches)
                                .collect(Collectors.toSet());
                        for (Path zipFile : lambdaSourcePackage) {
                            LOGGER.info("Uploading Lambda source package to S3 " + zipFile.toString() + " -> " + this.lambdaSourceFolder + "/" + zipFile.getFileName().toString());
                            saasBoostArtifactsBucket.putFile(s3, zipFile,
                                    Path.of(this.lambdaSourceFolder, zipFile.getFileName().toString()));
                        }
                    }
                } else {
                    LOGGER.warn("No POM file found in {}", sourceDirectory.toString());
                }
            }
        } catch (IOException ioe) {
            LOGGER.error("Error processing Lambda source folders", ioe);
            LOGGER.error(getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
    }

    protected void createSaaSBoostStack(final String stackName, String adminEmail, Boolean useActiveDirectory, String activeDirectoryPasswordParam) {
        // Note - most params the default is used from the CloudFormation stack
        List<Parameter> templateParameters = new ArrayList<>();
        templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(envName).build());
        templateParameters.add(Parameter.builder().parameterKey("AdminEmailAddress").parameterValue(adminEmail).build());
        templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").parameterValue(saasBoostArtifactsBucket.getBucketName()).build());
        templateParameters.add(Parameter.builder().parameterKey("Version").parameterValue(VERSION).build());
        templateParameters.add(Parameter.builder().parameterKey("DeployActiveDirectory").parameterValue(useActiveDirectory.toString()).build());
        templateParameters.add(Parameter.builder().parameterKey("ADPasswordParam").parameterValue(activeDirectoryPasswordParam).build());
        templateParameters.add(Parameter.builder().parameterKey("CreateMacroResources").parameterValue(Boolean.toString(!doesCfnMacroResourceExist())).build());

        LOGGER.info("createSaaSBoostStack::create stack " + stackName);
        String stackId = null;
        try {
            CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                    .stackName(stackName)
                    .disableRollback(true)
                    //.onFailure("DO_NOTHING") // TODO bug on roll back?
                    //.timeoutInMinutes(90)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .templateURL(saasBoostArtifactsBucket.getBucketUrl() + "saas-boost.yaml")
                    .parameters(templateParameters)
                    .build()
            );
            stackId = cfnResponse.stackId();
            LOGGER.info("createSaaSBoostStack::stack id " + stackId);

            boolean stackCompleted = false;
            long sleepTime = 5L;
            do {
                DescribeStacksResponse response = cfn.describeStacks(request -> request.stackName(stackName));
                Stack stack = response.stacks().get(0);
                if ("CREATE_COMPLETE".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation Stack: " + stackName + " completed successfully.");
                    stackCompleted = true;
                } else if ("CREATE_FAILED".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation Stack: " + stackName + " failed.");
                    throw new RuntimeException("Error with CloudFormation stack " + stackName + ". Check the events in the AWS CloudFormation Console");
                } else {
                    outputMessage("Awaiting CloudFormation Stack " + stackName + " to complete.  Sleep " + sleepTime + " minute(s)...");
                    try {
                        Thread.sleep(sleepTime * 60 * 1000);
                    } catch (Exception e) {
                        LOGGER.error("Error pausing thread", e);
                    }
                    sleepTime = 1L; // Set to 1 minute after kick off of 5 minute
                }
            } while (!stackCompleted);
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cloudformation error", cfnError);
            LOGGER.error(getFullStackTrace(cfnError));
            throw cfnError;
        }
    }

    protected void createMetricsStack(final String stackName, final String dbPasswordSsmParameter, final String databaseName) {
        LOGGER.info("Creating CloudFormation stack {} with database name {}", stackName, databaseName);
        List<Parameter> templateParameters = new ArrayList<>();
        templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(this.envName).build());
        templateParameters.add(Parameter.builder().parameterKey("LambdaSourceFolder").parameterValue(this.lambdaSourceFolder).build());
        templateParameters.add(Parameter.builder().parameterKey("MetricUserPasswordSSMParameter").parameterValue(dbPasswordSsmParameter).build());
        templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").parameterValue(saasBoostArtifactsBucket.getBucketName()).build());
        templateParameters.add(Parameter.builder().parameterKey("LoggingBucket").parameterValue(baseStackDetails.get("LoggingBucket")).build());
        templateParameters.add(Parameter.builder().parameterKey("DatabaseName").parameterValue(databaseName).build());
        templateParameters.add(Parameter.builder().parameterKey("PublicSubnet1").parameterValue(baseStackDetails.get("PublicSubnet1")).build());
        templateParameters.add(Parameter.builder().parameterKey("PublicSubnet2").parameterValue(baseStackDetails.get("PublicSubnet2")).build());
        templateParameters.add(Parameter.builder().parameterKey("PrivateSubnet1").parameterValue(baseStackDetails.get("PrivateSubnet1")).build());
        templateParameters.add(Parameter.builder().parameterKey("PrivateSubnet2").parameterValue(baseStackDetails.get("PrivateSubnet2")).build());
        templateParameters.add(Parameter.builder().parameterKey("VPC").parameterValue(baseStackDetails.get("EgressVpc")).build());

        // Now run the  stack to provision the infrastructure for Metrics and Analytics
        LOGGER.info("createMetricsStack::stack " + stackName);

        String stackId = null;
        try {
            CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                    .stackName(stackName)
                    //.onFailure("DO_NOTHING") // TODO bug on roll back?
                    //.timeoutInMinutes(90)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .templateURL(saasBoostArtifactsBucket.getBucketUrl() + "saas-boost-metrics-analytics.yaml")
                    .parameters(templateParameters)
                    .build()
            );
            stackId = cfnResponse.stackId();
            LOGGER.info("createMetricsStack::stack id " + stackId);

            boolean stackCompleted = false;
            long sleepTime = 5L;
            do {
                DescribeStacksResponse response = cfn.describeStacks(request -> request.stackName(stackName));
                Stack stack = response.stacks().get(0);
                if ("CREATE_COMPLETE".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation stack: " + stackName + " completed successfully.");
                    stackCompleted = true;
                } else if ("CREATE_FAILED".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation stack: " + stackName + " failed.");
                    throw new RuntimeException("Error with CloudFormation stack " + stackName + ". Check the events in the AWS CloudFormation Console");
                } else {
                    outputMessage("Awaiting CloudFormation Stack " + stackName + " to complete.  Sleep " + sleepTime + " minute(s)...");
                    try {
                        Thread.sleep(sleepTime * 60 * 1000);
                    } catch (Exception e) {
                        LOGGER.error("Error with sleep");
                    }
                    sleepTime = 1L; //set to 1 minute after kick off of 5 minute
                }
            } while (!stackCompleted);
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cloudformation error", cfnError);
            LOGGER.error(getFullStackTrace(cfnError));
            throw cfnError;
        }
    }

    protected void deleteCloudFormationStack(final String stackName) {
        outputMessage("Deleting CloudFormation stack: " + stackName);
        // Describe stacks won't return a response on stack name after it's deleted
        // but it will return a response on the stack id or ARN.
        String stackId = null;
        try {
            DescribeStacksResponse response = cfn.describeStacks(request -> request.stackName(stackName));
            if (response.hasStacks()) {
                stackId = response.stacks().get(0).stackId();
            }
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cloudformation:DescribeStacks error", cfnError);
            LOGGER.error(getFullStackTrace(cfnError));
            throw cfnError;
        }
        try {
            cfn.deleteStack(request -> request.stackName(stackName));
            long sleepTime = 5L;
            while (true) {
                try {
                    DescribeStacksResponse response = cfn.describeStacks(DescribeStacksRequest.builder()
                            .stackName(stackId)
                            .build()
                    );
                    if (response.hasStacks()) {
                        Stack stack = response.stacks().get(0);
                        String stackStatus = stack.stackStatusAsString();
                        if ("DELETE_COMPLETE".equals(stackStatus)) {
                            outputMessage("Delete stack complete " + stackName);
                            break;
                        } else if ("DELETE_FAILED".equals(stackStatus)) {
                            outputMessage("Delete CloudFormation Stack: " + stackName + " failed.");
                            throw new RuntimeException("Error with delete of CloudFormation stack " + stackName + ". Check the events in the AWS CloudFormation Console");
                        } else {
                            outputMessage("Awaiting Delete CloudFormation Stack " + stackName + " to complete.  Sleep " + sleepTime + " minute(s)...");
                            try {
                                Thread.sleep(sleepTime * 60 * 1000);
                            } catch (Exception e) {
                                LOGGER.error("Error pausing thread", e);
                            }
                            sleepTime = 1L; //set to 1 minute after kick off of 5 minute
                        }
                    } else {
                        LOGGER.warn("No stacks described after delete for " + stackName);
                        break;
                    }
                } catch (SdkServiceException cfnError) {
                    if (!cfnError.getMessage().contains("does not exist")) {
                        LOGGER.error("cloudformation:DescribeStacks error", cfnError);
                        LOGGER.error(getFullStackTrace(cfnError));
                        throw cfnError;
                    }
                }
            }
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cloudformation:DeleteStack error", cfnError);
            LOGGER.error(getFullStackTrace(cfnError));
            throw cfnError;
        }
    }

    protected boolean checkCloudFormationStack(final String stackName) {
        LOGGER.info("checkCloudFormationStack stack " + stackName);
        boolean exists = false;
        try {
            DescribeStacksResponse response = cfn.describeStacks(request -> request.stackName(stackName));
            exists = (response.hasStacks() && !response.stacks().isEmpty());
        } catch (SdkServiceException cfnError) {
            if (!cfnError.getMessage().contains("does not exist")) {
                LOGGER.error("cloudformation:DescribeStacks error", cfnError);
                LOGGER.error(getFullStackTrace(cfnError));
                throw cfnError;
            }
        }
        return exists;
    }

    public static String buildAndCopyWebApp(
            Path workingDir,
            CloudFormationClient cfn,
            S3Client s3,
            String envName,
            String accountId) {
        Path webDir = workingDir.resolve(Path.of("client", "web"));
        if (!Files.isDirectory(webDir)) {
            outputMessage("Error, can't find client/web directory at " + webDir.toAbsolutePath().toString());
            System.exit(2);
        }

        /*
        REACT_APP_SIGNOUT_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:webUrl
        REACT_APP_CALLBACK_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:webUrl
        REACT_APP_COGNITO_USERPOOL saas-boost::${ENVIRONMENT}-${AWS_REGION}:userPoolId
        REACT_APP_CLIENT_ID saas-boost::${ENVIRONMENT}-${AWS_REGION}:userPoolClientId
        REACT_APP_COGNITO_USERPOOL_BASE_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:cognitoBaseUri
        REACT_APP_API_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:publicApiUrl
        WEB_BUCKET saas-boost::${ENVIRONMENT}-${AWS_REGION}:webBucket
        */
        String nextToken = null;
        Map<String, String> exportsMap = new HashMap<>();
        try {
            do {
                ListExportsResponse response = cfn.listExports(ListExportsRequest.builder()
                        .nextToken(nextToken)
                        .build());
                nextToken = response.nextToken();
                for (Export export : response.exports()) {
                    if (export.name().startsWith("saas-boost::" + envName)) {
                        exportsMap.put(export.name(), export.value());
                    }
                }
            } while (nextToken != null);
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cloudformation:ListExports error", cfnError);
            LOGGER.error(getFullStackTrace(cfnError));
            throw cfnError;
        }

        final String prefix = "saas-boost::" + envName + "-" + AWS_REGION.id() + ":";

        // We need to know the CloudFront distribution URL to continue
        final String webUrl = exportsMap.get(prefix + "webUrl");
        if (isEmpty(webUrl)) {
            outputMessage("Unexpected errors, CloudFormation export " + prefix + "webUrl not found");
            LOGGER.info("Available exports part of stack output" + String.join(", ", exportsMap.keySet()));
            System.exit(2);
        }

        // We need to know the S3 bucket to host the web files in to continue
        final String webBucket = exportsMap.get(prefix + "webBucket");
        if (isEmpty(webBucket)) {
            outputMessage("Unexpected errors, CloudFormation export " + prefix + "webBucket not found");
            LOGGER.info("Available exports part of stack output" + String.join(", ", exportsMap.keySet()));
            System.exit(2);
        }

        // Execute yarn build to generate the React app
        outputMessage("Start build of AWS SaaS Boost React web application with yarn...");
        ProcessBuilder pb;
        Process process = null;
        try {
            if (isWindows()) {
                pb = new ProcessBuilder("cmd", "/c", "yarn", "build");
            } else {
                pb = new ProcessBuilder("yarn", "build");
            }

            Map<String, String> env = pb.environment();
            pb.directory(webDir.toFile());
            env.put("REACT_APP_SIGNOUT_URI", webUrl);
            env.put("REACT_APP_CALLBACK_URI", webUrl);
            env.put("REACT_APP_COGNITO_USERPOOL", exportsMap.get(prefix + "userPoolId"));
            env.put("REACT_APP_CLIENT_ID", exportsMap.get(prefix + "userPoolClientId"));
            env.put("REACT_APP_COGNITO_USERPOOL_BASE_URI", exportsMap.get(prefix + "cognitoBaseUri"));
            env.put("REACT_APP_API_URI", exportsMap.get(prefix + "publicApiUrl"));
            env.put("REACT_APP_AWS_ACCOUNT", accountId);
            env.put("REACT_APP_ENVIRONMENT", envName);
            env.put("REACT_APP_AWS_REGION", AWS_REGION.id());

            process = pb.start();
            printResults(process);
            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new RuntimeException("Error building web application. Verify version of Node is correct.");
            }
            outputMessage("Completed build of AWS SaaS Boost React web application.");
        } catch (IOException | InterruptedException e) {
            LOGGER.error(getFullStackTrace(e));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        // Sync files to the web bucket
        outputMessage("Synchronizing AWS SaaS Boost web application files to s3 web bucket");
        // First, clear out any files that are currently in the web bucket
        cleanUpS3(s3, webBucket, "");
        String cacheControl = null;
        Path yarnBuildDir = webDir.resolve(Path.of("build"));
        List<Path> filesToUpload;
        try (Stream<Path> stream = Files.walk(yarnBuildDir)) {
            filesToUpload = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            outputMessage("Uploading " + filesToUpload.size() + " files to S3");
            for (Path fileToUpload : filesToUpload) {
                // Remove the parent client/web/build path from the S3 key
                String key = fileToUpload.subpath(yarnBuildDir.getNameCount(), fileToUpload.getNameCount()).toString();
                if (key.endsWith("index.html") || key.endsWith("asset-manifest.json") || key.startsWith("static/")) {
                    cacheControl = "no-store";
                }
                try {
                    // Now copy all of the files from the Node build up to the web bucket
                    LOGGER.info("Uploading to S3 " + fileToUpload.toString() + " -> " + key);
                    s3.putObject(PutObjectRequest.builder()
                            .bucket(webBucket)
                            // java.nio.file.Path will use OS dependent file separators, so when we run the installer on
                            // Windows, the S3 key will have back slashes instead of forward slashes. The CloudFormation
                            // definitions of the Lambda functions will always use forward slashes for the S3Key property.
                            .key(key.replace('\\', '/'))
                            .cacheControl(cacheControl)
                            .build(), RequestBody.fromFile(fileToUpload)
                    );
                } catch (SdkServiceException s3Error) {
                    LOGGER.error("s3:PutObject error", s3Error);
                    LOGGER.error(getFullStackTrace(s3Error));
                    throw s3Error;
                }
            }
        } catch (IOException ioe) {
            LOGGER.error("Error walking web app build directory", ioe);
            LOGGER.error(getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
        return webUrl;
    }

    public static void executeCommand(String command, String[] environment, File dir) {
        LOGGER.info("Executing Commands: " + command);
        if (null != dir) {
            LOGGER.info("Directory: " + dir.getPath());
        }
        if (null != dir && !dir.isDirectory()) {
            throw new RuntimeException("File path: " + dir.getPath() + " is not a directory");
        }
        Process process;
        try {
            if (isWindows()) {
                command = "cmd /c " + command;
            }
            process = Runtime.getRuntime().exec(command, environment, dir);
            printResults(process);
        } catch (Exception e) {
            LOGGER.error("Error running command: " + command);
            LOGGER.error(getFullStackTrace(e));
            throw new RuntimeException("Error running command: " + command);
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (process.exitValue() != 0) {
            String msg = "Installation terminated due to non-zero exit value running command: " + command;
            if (null != dir) {
                msg += " from directory: " + dir.getPath();
            }
            throw new RuntimeException(msg);
        }

        process.destroy();
    }

    protected LinkedHashMap<String, User> getQuickSightUsers() {
        LOGGER.info("Load Quicksight users");
        LinkedHashMap<String, User> users = new LinkedHashMap<>();
        try {
            String nextToken = null;
            do {
                ListUsersResponse response = quickSight.listUsers(ListUsersRequest.builder()
                        .awsAccountId(accountId)
                        .namespace("default")
                        .nextToken(nextToken)
                        .build()
                );
                if (response.hasUserList()) {
                    for (User quickSightUser : response.userList()) {
                        users.put(quickSightUser.userName(), quickSightUser);
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
        } catch (SdkServiceException quickSightError) {
            LOGGER.error("quickSight:ListUsers error {}", quickSightError.getMessage());
            LOGGER.error(getFullStackTrace(quickSightError));
            throw quickSightError;
        }
        LOGGER.info("Completed load of QuickSight users");
        return users;
    }

    protected String analyticsStackName() {
        return this.stackName + "-analytics";
    }

    protected static void cleanUpS3(S3Client s3, String bucket, String prefix) {
        // The list of objects in the bucket to delete
        List<ObjectIdentifier> toDelete = new ArrayList<>();
        if (isNotEmpty(prefix) && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        GetBucketVersioningResponse versioningResponse = s3.getBucketVersioning(request -> request.bucket(bucket));
        if (BucketVersioningStatus.ENABLED == versioningResponse.status() || BucketVersioningStatus.SUSPENDED == versioningResponse.status()) {
            LOGGER.info("Bucket " + bucket + " is versioned (" + versioningResponse.status() + ")");
            ListObjectVersionsResponse listObjectResponse;
            String keyMarker = null;
            String versionIdMarker = null;
            do {
                ListObjectVersionsRequest request;
                if (isNotBlank(keyMarker) && isNotBlank(versionIdMarker)) {
                    request = ListObjectVersionsRequest.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .keyMarker(keyMarker)
                            .versionIdMarker(versionIdMarker)
                            .build();
                } else if (isNotBlank(keyMarker)) {
                    request = ListObjectVersionsRequest.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .keyMarker(keyMarker)
                            .build();
                } else {
                    request = ListObjectVersionsRequest.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build();
                }
                listObjectResponse = s3.listObjectVersions(request);
                keyMarker = listObjectResponse.nextKeyMarker();
                versionIdMarker = listObjectResponse.nextVersionIdMarker();
                listObjectResponse.versions()
                        .stream()
                        .map(version ->
                                ObjectIdentifier.builder()
                                        .key(version.key())
                                        .versionId(version.versionId())
                                        .build()
                        )
                        .forEachOrdered(toDelete::add);
            } while (listObjectResponse.isTruncated());
        } else {
            LOGGER.info("Bucket " + bucket + " is not versioned (" + versioningResponse.status() + ")");
            ListObjectsV2Response listObjectResponse;
            String token = null;
            do {
                ListObjectsV2Request request;
                if (isNotBlank(token)) {
                    request = ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .continuationToken(token)
                            .build();
                } else {
                    request = ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build();
                }
                listObjectResponse = s3.listObjectsV2(request);
                token = listObjectResponse.nextContinuationToken();
                listObjectResponse.contents()
                        .stream()
                        .map(obj ->
                                ObjectIdentifier.builder()
                                        .key(obj.key())
                                        .build()
                        )
                        .forEachOrdered(toDelete::add);
            } while (listObjectResponse.isTruncated());
        }
        if (!toDelete.isEmpty()) {
            LOGGER.info("Deleting " + toDelete.size() + " objects");
            final int maxBatchSize = 1000;
            int batchStart = 0;
            int batchEnd = 0;
            while (batchEnd < toDelete.size()) {
                batchStart = batchEnd;
                batchEnd += maxBatchSize;
                if (batchEnd > toDelete.size()) {
                    batchEnd = toDelete.size();
                }
                Delete delete = Delete.builder()
                        .objects(toDelete.subList(batchStart, batchEnd))
                        .build();
                DeleteObjectsResponse deleteResponse = s3.deleteObjects(builder -> builder
                        .bucket(bucket)
                        .delete(delete)
                        .build()
                );
                LOGGER.info("Cleaned up " + deleteResponse.deleted().size() + " objects in bucket " + bucket);
            }
        } else {
            LOGGER.info("Bucket " + bucket + " is empty. No objects to clean up.");
        }
    }

    private boolean doesCfnMacroResourceExist() {
        // this assumes that the macro resource exists in CloudFormation if and only if all requisite resources also 
        // exist, i.e. the macro Lambda function, execution role, and log group. this should always be true, since the
        // macro resource will never be deleted unless each of the others are deleted thanks to CloudFormation
        // dependency analysis
        List<String> stackNamesToCheck = new ArrayList<>();
        String paginationToken = null;
        do {
            ListStacksResponse listStacksResponse = cfn.listStacks(
                    ListStacksRequest.builder().nextToken(paginationToken).build());
            stackNamesToCheck.addAll(listStacksResponse.stackSummaries().stream()
                    .filter(summary -> summary.stackStatus() != StackStatus.DELETE_COMPLETE 
                                    && summary.stackStatus() != StackStatus.DELETE_IN_PROGRESS)
                    .map(summary -> summary.stackName())
                    .collect(Collectors.toList()));
            paginationToken = listStacksResponse.nextToken();
        } while (paginationToken != null);
        // for each stack, look for Macro Resource (either by listing all or getResource by logical id)
        for (String stackName : stackNamesToCheck) {
            try {
                StackResourceDetail stackResourceDetail = cfn.describeStackResource(request -> request
                        .stackName(stackName)
                        .logicalResourceId("ApplicationServicesMacro")).stackResourceDetail();
                if (stackResourceDetail.resourceStatus() != ResourceStatus.DELETE_COMPLETE) {
                    LOGGER.debug("Found the ApplicationServicesMacro resource in {}", stackName);
                    return true;
                }
            } catch (CloudFormationException cfne) {
                if (cfne.getMessage().contains("Stack '" + stackName + "' does not exist")) {
                    // if stacks are being deleted
                }
            }
        }
        LOGGER.debug("Could not find any ApplicationServicesMacro resource");
        return false;
    }

    public static String getFullStackTrace(Exception e) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        e.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static boolean isWindows() {
        return (OS.contains("win"));
    }

    public static boolean isMac() {
        return (OS.contains("mac"));
    }

    /**
     * Generate a random password that matches the password policy of the Cognito user pool
     * @return a random password that matches the password policy of the Cognito user pool
     */
    public static String generatePassword(int passwordLength) {
        if (passwordLength < 8) {
            throw new IllegalArgumentException("Invalid password length. Minimum of 8 characters is required.");
        }

        // Split the classes of characters into separate buckets so we can be sure to use
        // the correct amount of each type
        final char[][] requiredCharacterBuckets = {
                {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'},
                {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'},
                {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'}
        };

        Random random = new Random();
        StringBuilder password = new StringBuilder(passwordLength);

        // Randomly select one character from each of the required character types
        for (char[] requiredCharacterBucket : requiredCharacterBuckets) {
            password.append(requiredCharacterBucket[random.nextInt(requiredCharacterBucket.length)]);
        }

        // build the remaining password using Utils.randomString
        return password.append(Utils.randomString(passwordLength - requiredCharacterBuckets.length)).toString();
    }
}
