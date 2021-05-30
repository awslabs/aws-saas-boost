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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.regions.Region;

import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.apigateway.model.GetRestApisResponse;
import software.amazon.awssdk.services.apigateway.model.RestApi;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.PublishVersionRequest;
import software.amazon.awssdk.services.lambda.model.PublishVersionResponse;
import software.amazon.awssdk.services.lambda.model.UpdateAliasRequest;
import software.amazon.awssdk.services.quicksight.QuickSightClient;
import software.amazon.awssdk.services.quicksight.model.*;
import software.amazon.awssdk.services.quicksight.model.ListUsersRequest;
import software.amazon.awssdk.services.quicksight.model.ListUsersResponse;
import software.amazon.awssdk.services.quicksight.model.Tag;
import software.amazon.awssdk.services.quicksight.model.User;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;
import software.amazon.awssdk.services.sts.StsClient;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SaaSBoostInstall {
    private final static Region AWS_REGION = Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable()));
    private CloudFormationClient cfn;
    private AmazonS3 s3;
    private final static Logger LOGGER = LoggerFactory.getLogger(SaaSBoostInstall.class);
    private String s3ArtifactBucket = null;
    private String rootDir = null;
    private String envName = null;
    private String lambdaSourceFolder;
    private String mavenCommand = "mvn";
    private IamClient iam;
    private StsClient sts;
    private String emailAddress = "";
    private String domain = "";
    private String metricsAndAnalytics;
    private String meteringAndBilling;
    private String dbPassword= "";
    private String dbPasswordSSMParameter = "";
    private static String OS = System.getProperty("os.name").toLowerCase();
    private String webDir = "";
    private QuickSightClient quickSightClient;
    private String setupQuickSight;
    private User quickSightUser;
    private int installOption = 0;
    private SsmClient ssm;
    private DynamoDbClient ddb;
    private EcrClient ecr;

    // This filter will only include files ending with .py
    private final FilenameFilter zipFileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File f, String name) {
            return name.endsWith(".zip");
        }
    };

    // This filter will only include files ending with .yaml
    private final FilenameFilter resourceFileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File f, String name) {
            return name.endsWith(".yaml");
        }
    };

    private Scanner input = null;
    private String accountId = "";
    private String VERSION = null;
    private Region quickSightRegion;
    private String activeDirectory = "n";
    private String activeDirectoryPasswordParam = "";

    public SaaSBoostInstall() {
        try {
            LOGGER.info("Version Info: " + getVersionInfo());
        } catch (IOException ioe) {
            LOGGER.error("Error getting VersionInfo", ioe);
            LOGGER.error(getFullStackTrace(ioe));
        }
        this.s3 = AmazonS3Client.builder().withRegion(AWS_REGION.id()).build();
        this.cfn = CloudFormationClient.builder().region(AWS_REGION).build();

        this.ddb = DynamoDbClient.builder()
                .region(AWS_REGION).build();
        this.ecr = EcrClient.builder().region(AWS_REGION).build();
        this.ssm = SsmClient.builder().region(AWS_REGION).build();

        //iam requires global region
        this.iam = IamClient.builder()
                .region(Region.AWS_GLOBAL)
                .build();

        this.sts = StsClient.builder()
                .region(Region.AWS_GLOBAL)
                .build();

        input = new Scanner(System.in);
    }

    public String getVersionInfo() throws IOException {
        String result = "";
        InputStream inputStream = null;
        String propFileName = "git.properties";
        try {
            Properties prop = new Properties();
            inputStream = SaaSBoostInstall.class.getClassLoader().getResourceAsStream(propFileName);

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
            }

            // get the property value and print it out
            String tag = prop.getProperty("git.commit.id.describe");
            String commitTime = prop.getProperty("git.commit.time");
            result = tag + ", Commit time: " + commitTime;
            VERSION = prop.getProperty("git.closest.tag.name");
            if (null == VERSION || "".equals(VERSION)) {
                outputMessage("Setting version to v0 as it is missing from the git properties file.");
                VERSION = "v0";
            }
        } catch (FileNotFoundException e) {
            outputMessage("Error setting Version from git.properties: " + e.getMessage());
            outputMessage("Setting version to v0");
            outputMessage(propFileName + " is generated by Maven build, check for a .git folder");
            VERSION = "v0";
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    LOGGER.error("getVersionInfo: Error closing inputStream");
                }
            }
        }
        return result;
    }

    private static boolean isWindows() {
        return (OS.indexOf("win") >= 0);
    }

    private static boolean isMac() {
        return (OS.indexOf("mac") >= 0);
    }

    public static void main(String[] args) throws IOException {
        SaaSBoostInstall sbInstall = new SaaSBoostInstall();
        try {
            sbInstall.start();
        } catch (Exception e) {
            outputMessage("===========================================================");
            outputMessage("Installation Error: " + e.getLocalizedMessage());
            outputMessage("Please see detailed log file saas-boost-install.log");
            sbInstall.LOGGER.error(getFullStackTrace(e));
            outputMessage(getFullStackTrace(e));
        }
    }

    /*
    Installs Metrics
    Could be run after initial install
     */
    private void installMetrics(String sbStackName) throws IOException {
        outputMessage("Metrics and Analytics will be deployed into an existing AWS SaaS Boost environment.\n");
        boolean isValid = false;
        if (null == sbStackName) {
            try {
                do {
                    outputMessage("Enter name of the AWS SaaS Boost CloudFormation stack that has already been deployed (Ex. sb-dev): ");
                    sbStackName = input.next().trim();
                    if (null != sbStackName && !sbStackName.isEmpty()) {
                        //validate the stackName exists
                        isValid = loadSaaSBoostStack(sbStackName);
                        if (!isValid) {
                            outputMessage("Please try again.");
                        }
                    } else {
                        outputMessage("Entered value is incorrect, please try again.");
                    }
                } while (!isValid);

                //check if the  metrics stack already exists
                if (checkCloudFormationStack(sbStackName + "-metrics")) {
                    outputMessage("AWS SaaS Boost Metrics stack with name: " + sbStackName + "-metrics is already deployed");
                    System.exit(2);
                };

                //installing Metrics --> ask about quicksight.
                isValid = false;
                do {
                    outputMessage("Would you like to setup Amazon Quicksight for Metrics and Analytics? You must have already registered for Quicksight in your account. (y or n)? : ");
                    this.setupQuickSight = input.next();
                    if ("y".equalsIgnoreCase(setupQuickSight) || "n".equalsIgnoreCase(setupQuickSight)) {
                        isValid = true;
                    } else {
                        outputMessage("Entered value must be y or n, please try again.");
                    }
                } while (!isValid);

                if ("y".equalsIgnoreCase(setupQuickSight)) {
                    isValid = false;
                    input.nextLine();
                    do {
                        outputMessage("Region where you registered for Amazon Quicksight identity (Press Enter for '" + AWS_REGION.id() + "'): ");
                        String regionInput = input.nextLine().trim();
                        //outputMessage("Input value is: " + quickSightUserInput);
                        // Skip the newline
                        //input.nextLine();
                        if (regionInput == null || regionInput.equals("")) {
                            quickSightRegion = AWS_REGION;
                            isValid = true;
                        } else {
                            isValid = false;
                            Region region = Region.of(regionInput);
                            if (!Region.regions().contains(region)) {
                                outputMessage("Entered value is not a region, please try again.");
                            } else {
                                isValid = true;
                                quickSightRegion = region;
                            }
                        }
                    } while (!isValid);
                    //validate the user for quicksight
                    List<User> users = getQuicksightUsers();
                    Map<String, User> userMap = new HashMap<>();
                    for (User user : users) {
                        userMap.put(user.userName(), user);
                    }
                    if (users.isEmpty()) {
                        outputMessage("No users found in Quicksight. Please register in your AWS Account and try install again.");
                        System.exit(2);
                    }

                    isValid = false;
                    //input.nextLine();
                    do {
                        outputMessage("Amazon Quicksight user name (Press Enter for '" + users.get(0).userName() + "'): ");
                        String quickSightUserInput = input.nextLine().trim();
                        //outputMessage("Input value is: " + quickSightUserInput);
                        // Skip the newline
                        //input.nextLine();
                        if (quickSightUserInput == null || quickSightUserInput.equals("")) {
                            quickSightUser = users.get(0);
                            isValid = true;
                        } else {
                            isValid = false;
                            if (!userMap.containsKey(quickSightUserInput)) {
                                outputMessage("Entered value is not a valid Quicksight user in your account, please try again.");
                            } else {
                                quickSightUser = userMap.get(quickSightUserInput);
                                isValid = true;
                            }
                        }
                    } while (!isValid);
                }

                outputMessage("===========================================================");
                outputMessage("");
                outputMessage("Would you like to continue the Metrics and Analytics installation with the following options?");
                outputMessage("Existing AWS SaaS Boost environment stack: " + sbStackName);
                outputMessage("AWS SaaS Boost Environment Name: " + envName);

                if (null != quickSightUser) {
                    outputMessage("Amazon Quicksight user for setup of Metrics and Analytics: " + quickSightUser.userName());
                } else {
                    outputMessage("Amazon Quicksight user for setup of Metrics and Analytics: n/a");
                }

                isValid = false;
                do {
                    System.out.print("Enter y to continue or n to cancel: ");
                    String continueInstall = input.next();
                    if ("n".equalsIgnoreCase(continueInstall)) {
                        outputMessage("Canceled installation of AWS SaaS Boost Metrics and Analytics");
                        System.exit(2);
                    } else if ("y".equalsIgnoreCase(continueInstall)) {
                        outputMessage("Continuing installation of AWS SaaS Boost Metrics and Analytics");
                        isValid = true;
                    } else {
                        outputMessage("Invalid option for continue installation: " + continueInstall + ", try again.");
                    }
                } while (!isValid);
            } catch (Exception e) {
                LOGGER.error(getFullStackTrace(e));
                throw e;
            }
            //S3 Bucket from params
            LOGGER.info("Using S3 Artifact Bucket: " + this.s3ArtifactBucket);
            LOGGER.info("Using Environment: " + this.envName);

            //copy yaml files
            outputMessage("Copy Metrics CloudFormation template to S3 artifact bucket");
            List<String> fileNames = new ArrayList<>(Arrays.asList("saas-boost-metrics-analytics.yaml"));
            copyTemplateFilesToS3(fileNames);
        } else {
            //need to fetch stack outputs
            loadSaaSBoostStack(sbStackName);
        }

        outputMessage("===========================================================");
        outputMessage("Installing AWS SaaS Boost Metrics and Analytics Module");
        outputMessage("===========================================================");

        //build lambdas
        outputMessage("Build Metrics and Analytics Lambdas and copy zip files to S3 artifacts bucket...");
        //custom/resources
        final List<String> list = new ArrayList<>(Arrays.asList("redshift-table"));

        //refresh s3 client
        this.s3 = AmazonS3Client.builder().withRegion(AWS_REGION.id()).build();
        buildAndCopyLambdas("resources" + File.separator + "custom-resources", list);

        //create db password if metrics installed
        if (null == dbPassword || dbPassword.isEmpty()) {
            UUID uniqueId = UUID.randomUUID();
            String[] parts = uniqueId.toString().split("-");  //UUID 29219402-d9e2-4727-afec-2cd61f54fa8f
            dbPassword = "Pass12" + parts[0];
            //outputMessage("The database password for Redshift Metrics database is: " + this.dbPassword);
        }

        //create secure SSM parameter for password
        ssm = SsmClient.builder()
                .region(AWS_REGION).build();
        // /saas-boost/${Environment}/METRICS_ANALYTICS_DEPLOYED to true
        LOGGER.info("Update SSM param REDSHIFT_MASTER_PASSWORD with dbPassword");
        //*TODO: check if parameter exists and do not update if it exists.
        software.amazon.awssdk.services.ssm.model.Parameter passwordParam = putParameter(toParameterStore("REDSHIFT_MASTER_PASSWORD", dbPassword ,true));
        dbPasswordSSMParameter = passwordParam.name();
        outputMessage("Redshift Database User Password stored in secure SSM Parameter: " + dbPasswordSSMParameter);

        //execute CloudFormation
        String stackName = sbStackName + "-metrics";
        outputMessage("Execute the CloudFormation stack " + stackName + " for Metrics and Analytics");

        createMetricsStack(stackName);

        ssm = SsmClient.builder()
                .region(AWS_REGION).build();
        // /saas-boost/${Environment}/METRICS_ANALYTICS_DEPLOYED to true
        LOGGER.info("Update SSM param METRICS_ANALYTICS_DEPLOYED to true");
        software.amazon.awssdk.services.ssm.model.Parameter updated = putParameter(toParameterStore("METRICS_ANALYTICS_DEPLOYED", "true", false));

        Map<String, String> outputs = getMetricStackOutputs(stackName);
        File file = new File(rootDir + File.separator + "metrics-analytics" +
                File.separator + "deploy" + File.separator + "artifacts" +
                File.separator + "metrics_redshift_jsonpath.json");
        LOGGER.info("Copying json files for Metrics and Analytics from {} to {}", file.toString(), outputs.get("MetricsBucket"));
        //refresh the client first
        try {
            s3 = AmazonS3Client.builder().withRegion(AWS_REGION.id()).build();
            s3.putObject(outputs.get("MetricsBucket"), "metrics_redshift_jsonpath.json", file);
        } catch (Exception e) {
            outputMessage("Error with s3 copy of " + file.toString() + " to " + outputs.get("MetricsBucket"));
            outputMessage(("Continuing with installation so you will need to manually upload that file."));
            LOGGER.error((getFullStackTrace(e)));
        }

        //setup the quicksight dataset
        if ("y".equalsIgnoreCase(setupQuickSight)) {
            outputMessage("Set up Amazon Quicksight for Metrics and Analytics");
            try {
                setupQuickSight(stackName, outputs);
            } catch (Exception e) {
                outputMessage("Error with setup of Quicksight datasource and dataset. Check log file.");
                outputMessage("Message: " + e.getMessage());
                LOGGER.error(getFullStackTrace(e));
                System.exit(2);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloudFormationParameter {
        String description;
        String  type;
        String minLength;
        String defaultValue;
        List<String> allowedValues;
        String allowedPattern;

        @JsonProperty("Default")
        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
        @JsonProperty("Description")
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @JsonProperty("Type")
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @JsonProperty("MinLength")
        public String getMinLength() {
            return minLength;
        }

        public void setMinLength(String minLength) {
            this.minLength = minLength;
        }

        @JsonProperty("AllowedValues")
        public List getAllowedValues() {
            return allowedValues;
        }

        public void setAllowedValues(List allowedValues) {
            this.allowedValues = allowedValues;
        }

        @JsonProperty("AllowedPattern")
        public String getAllowedPattern() {
            return allowedPattern;
        }

        public void setAllowedPattern(String allowedPattern) {
            this.allowedPattern = allowedPattern;
        }

        @Override
        public String toString() {
            return "Parameter{" +
                    "description='" + description + '\'' +
                    ", type='" + type + '\'' +
                    ", minLength='" + minLength + '\'' +
                    ", allowedValues='" + (null == allowedValues ? "" : Arrays.toString(allowedValues.toArray())) + '\'' +
                    ", allowedPattern='" + allowedPattern + '\'' +
                    '}';
        }
    }

    private void updateSaaSBoost() throws IOException {
        boolean isValid = false;
        String stackName;
        Map<String, String> stackParamsMap = new HashMap<>();
        do {
            System.out.print("Enter name of the AWS SaaS Boost CloudFormation stack that has already been deployed (Ex. sb-dev): ");
            stackName = input.next().trim();
            if (null != stackName && !stackName.isEmpty()) {
                //validate the stackName exists
                try {
                    DescribeStacksResponse stacks = cfn.describeStacks(DescribeStacksRequest
                            .builder()
                            .stackName(stackName)
                            .build()
                    );
                    if (stacks.stacks().isEmpty()) {
                        outputMessage("No CloudFormation stack found with name: " + stackName);
                    }
                    Stack stack = stacks.stacks().get(0);
                    for (Parameter parameter : stack.parameters()) {
                        stackParamsMap.put(parameter.parameterKey(), parameter.parameterValue());
                    }
                    isValid = true;

                } catch (Exception e) {
                    outputMessage("Error loading parameters from CloudFormation stack " + stackName);
                    LOGGER.error(getFullStackTrace(e));
                    System.exit(2);
                }

                if (!isValid) {
                    outputMessage("Stack " + stackName + " could not be loaded. Please enter a valid stack name.");
                }
            } else {
                outputMessage("Entered value is incorrect, please try again.");
            }
        } while (!isValid);

        isValid = false;
        outputMessage("******* W A R N I N G *******");
        outputMessage("Updating AWS SaaS Boost environment is an IRREVERSIBLE operation. You should test out first in a test environment" +
                "\n before updating a production environment. By continuing you understand and ACCEPT the RISKS!");
        do {
            System.out.print("Enter y to continue with UPDATE of " + stackName + " or n to CANCEL: ");
            String continueUpgrade = input.next();
            if ("n".equalsIgnoreCase(continueUpgrade)) {
                outputMessage("Canceled UPDATE of AWS SaaS Boost environment");
                System.exit(2);
            } else if ("y".equalsIgnoreCase(continueUpgrade)) {
                outputMessage("Continuing UPDATE of AWS SaaS Boost stack " + stackName);
                isValid = true;
            } else {
                outputMessage("Invalid option for continue update: " + continueUpgrade + ", try again.");
            }
        } while (!isValid);

        Map<String, String> cloudFormationParamMap = getCloudFormationParameterMap("saas-boost.yaml", stackParamsMap);

        this.s3ArtifactBucket = cloudFormationParamMap.get("SaaSBoostBucket");
        this.envName = cloudFormationParamMap.get("Environment");

        if (null == this.envName)    {
            outputMessage("Environment parameter is null from existing stack");
            System.exit(2);
        } else if (null == this.s3ArtifactBucket) {
            outputMessage("SaaSBoostBucket parameter is null from existing stack");
            System.exit(2);
        }



        //copy yaml files
        outputMessage("Copy CloudFormation template files to S3 artifacts bucket " + this.s3ArtifactBucket);
        copyTemplateFilesToS3(new ArrayList<>());

        String existingLambdaSourceFolder = cloudFormationParamMap.get("LambdaSourceFolder");

        //build lambdas and copy to a new lambda_version folder
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
        this.lambdaSourceFolder = "lambdas-" + sdf.format(new Date());

        outputMessage("Build Lambdas and copy zip files to S3 artifacts bucket under " + lambdaSourceFolder +"...");
        processLambdas(true);


        //run CFN with the params and new lambda_version folder
        outputMessage("Run update on CloudFormation stack: " + stackName);
        //override the lambda source folder to deploy updated code
        cloudFormationParamMap.put("LambdaSourceFolder", this.lambdaSourceFolder);

        //update the version number
        cloudFormationParamMap.put("Version", this.VERSION);

        //check if the  metrics stack exists and update it
        if (checkCloudFormationStack(stackName + "-metrics")) {
            outputMessage("Update the Metrics and Analytics stack (" + stackName + "-metrics)...");
            updateMetricsStack(stackName + "-metrics");
        }

        updateCloudFormationStack(stackName, cloudFormationParamMap, "saas-boost.yaml");

/*        //update lambdas that need to have version incremented and alias updated.
        try {
            outputMessage("Update Lambda Aliases...");
            updateLambdaAliases();
        } catch (Exception e) {
            outputMessage("Error updating Lambda Aliases");
            LOGGER.error(getFullStackTrace(e));
        }*/

        //deploy the API Gateway to v1 stage
        outputMessage("Updating API Gateway Deployment for Stages");
        ApiGatewayClient apiGatewayClient = ApiGatewayClient.builder().region(AWS_REGION).build();

        GetRestApisResponse response = apiGatewayClient.getRestApis();
        for (RestApi api : response.items()) {
            //System.out.println(api);
            try {
                Thread.sleep((3000));
            } catch (InterruptedException ie) {
                //eat it
            }
            if (api.name().equalsIgnoreCase("sb-public-api-" + envName)) {
                String stage = cloudFormationParamMap.get("PublicApiStage");
                outputMessage("Update API Gateway deployment for " + api.name() + " to stage: " + stage);
                apiGatewayClient.createDeployment(CreateDeploymentRequest.builder()
                        .restApiId(api.id())
                        .stageName(stage)
                        .build());
            } else if (api.name().equalsIgnoreCase("sb-private-api-" + envName)) {
                String stage = cloudFormationParamMap.get("PrivateApiStage");
                outputMessage("Update API Gateway deployment for " + api.name() + " to stage: " + stage);
                apiGatewayClient.createDeployment(CreateDeploymentRequest.builder()
                        .restApiId(api.id())
                        .stageName(stage)
                        .build());
            }
        }

        //build and copy the web site
        buildAndCopyWebApp();

        //delete the old lambdas zip files
        outputMessage("Delete files from previous Lambda folder: " + existingLambdaSourceFolder);
        this.s3 = AmazonS3Client.builder().withRegion(AWS_REGION.id()).build();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(s3ArtifactBucket).withPrefix(existingLambdaSourceFolder + "/");
        ObjectListing listing = s3.listObjects(listObjectsRequest);
        List<S3ObjectSummary> objects = listing.getObjectSummaries();
        ArrayList<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();
        for (S3ObjectSummary obj : objects) {
            LOGGER.info("Key to delete: " + obj.getKey());
            keys.add(new DeleteObjectsRequest.KeyVersion(obj.getKey()));
        }
        DeleteObjectsRequest request = new DeleteObjectsRequest(s3ArtifactBucket).withKeys(keys);
        s3.deleteObjects(request);

    }

    //update the metrics stack
    private void updateMetricsStack(String stackName) {
        //load params map
        Map<String, String> stackParamsMap = new LinkedHashMap<>();
        try {
            DescribeStacksResponse stacks = cfn.describeStacks(DescribeStacksRequest
                    .builder()
                    .stackName(stackName)
                    .build()
            );
            if (stacks.stacks().isEmpty()) {
                outputMessage("No CloudFormation stack found with name: " + stackName);
                System.exit(2);
            }
            Stack stack = stacks.stacks().get(0);
            LOGGER.info("Load parameters from existing stack: " + stackName);
            for (Parameter parameter : stack.parameters()) {
                LOGGER.info("Parameters from existing stack: " + stackName + ", Name: " + parameter.parameterKey() + " , Value: " + parameter.parameterKey());
                stackParamsMap.put(parameter.parameterKey(), parameter.parameterValue());
            }

        } catch (Exception e) {
            outputMessage("Error loading parameters from CloudFormation stack " + stackName);
            LOGGER.error(getFullStackTrace(e));
            System.exit(2);
        }

        try {
            Map<String, String> cloudFormationParamMap = getCloudFormationParameterMap("saas-boost-metrics-analytics.yaml", stackParamsMap);
            //update the stack
            updateCloudFormationStack(stackName, cloudFormationParamMap, "saas-boost-metrics-analytics.yaml");
        } catch (Exception e) {
            outputMessage("Error updating stack " + stackName + ", message: " + e.getMessage());
            LOGGER.error(getFullStackTrace(e));
            System.exit(2);
        }
    }

    private Map<String, String> getCloudFormationParameterMap(String yamlFileName, Map<String, String> stackParamsMap) throws IOException {
        boolean isValid;//Open CFN template yaml file and prompt for values of params that are not in existing stack
        LOGGER.info("Build map of params FOR template " + yamlFileName);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        final File sbStackFile = new File(this.rootDir + File.separator + "resources" + File.separator + yamlFileName);
        if (!sbStackFile.exists()) {
            outputMessage("Unable to find file " + this.rootDir + File.separator + "resources" + File.separator + yamlFileName);
            System.exit(2);
        }

        Map<String, Object> map = mapper.readValue(sbStackFile, HashMap.class);
        Map<String, Object> parameterObjectMap = (LinkedHashMap<String, Object>) map.get("Parameters");

        ObjectMapper mapper1 = new ObjectMapper();
        Map<String, Test.CloudFormationParameter> parameterMap = new LinkedHashMap<>();
        Map<String, String> cloudFormationParamMap = new LinkedHashMap<>();

        for(Map.Entry<String, Object> entry : parameterObjectMap.entrySet()) {
            LOGGER.info("Parameter Name: " + entry.getKey());
            Test.CloudFormationParameter p = mapper1.convertValue((Map) entry.getValue(), Test.CloudFormationParameter.class);
            LOGGER.info("Parameter Values = " + p.toString());
            parameterMap.put(entry.getKey(), p);

            //set value from existing stack else Prompt for input
            if (stackParamsMap.containsKey(entry.getKey())) {
                LOGGER.info("Use existing value for Parameter: {} and don't prompt", entry.getKey());
                cloudFormationParamMap.put(entry.getKey(), stackParamsMap.get(entry.getKey()));
            } else {
                String inputValue;
                isValid = false;
                if (null == p.defaultValue) {
                    LOGGER.info("Prompt for a value for Parameter {} if default not specified", entry.getKey());
                    do {
                        System.out.print("Enter value for CloudFormation Parameter " + entry.getKey() + ": ");
                        inputValue = input.next().trim();
                        if (null == inputValue || inputValue.isEmpty()) {
                            System.out.println("Entered value is incorrect");
                        } else {
                            isValid = true;
                        }
                    } while (!isValid);
                    LOGGER.info("Using entered value: {} for Parameter: {}", inputValue, entry.getKey());
                    cloudFormationParamMap.put(entry.getKey(), inputValue);
                } else {
                    input.nextLine();
                    System.out.print("Enter value for CloudFormation Parameter " + entry.getKey() + " (Hit enter to accept default value of " + p.defaultValue + "): ");
                    inputValue = input.nextLine().trim();
                    if (null == inputValue || inputValue.isEmpty()) {
                        inputValue = p.defaultValue;
                    }
                    LOGGER.info("Using entered value: {} for Parameter: {}", inputValue, entry.getKey());
                    cloudFormationParamMap.put(entry.getKey(), inputValue);
                }
            }
        }
        return cloudFormationParamMap;
    }

    private void updateLambdaAliases() {
        List<String> aliasFunctions = new ArrayList<>(Arrays.asList("settings-get-all", "settings-get-config"));
        LambdaClient lambda = LambdaClient.builder().region(AWS_REGION).build();
        for (String lambdaFunction : aliasFunctions) {
            lambdaFunction = "sb-" + envName + "-" + lambdaFunction + "-" + AWS_REGION.id();
            outputMessage("Updating Lambda Alias for function: " + lambdaFunction);
            try {
                //increment version
                PublishVersionResponse response = lambda.publishVersion(PublishVersionRequest.builder().functionName(lambdaFunction).build());

                //update lambda
                lambda.updateAlias(UpdateAliasRequest.builder()
                        .name("v1")
                        .functionName(lambdaFunction)
                        .functionVersion(response.version())
                        .build());
            } catch (Exception e) {
                LOGGER.error("Error updating Lambda Alias " + lambdaFunction);
                LOGGER.error(getFullStackTrace(e));
            }
        }
    }

    private void updateWebApp() throws IOException {
        boolean isValid = false;
        String stackName;
        try {
            do {
                System.out.print("Enter name of the AWS SaaS Boost CloudFormation stack that has already been deployed (Ex. sb-dev): ");
                stackName = input.next().trim();
                if (null != stackName && !stackName.isEmpty()) {
                    //validate the stackName exists
                    isValid = loadSaaSBoostStack(stackName);
                    if (!isValid) {
                        outputMessage("Please try again.");
                    }
                } else {
                    outputMessage("Entered value is incorrect, please try again.");
                }
            } while (!isValid);
        } catch (Exception e) {
            LOGGER.error(getFullStackTrace(e));
            throw e;
        }

        if (null == this.envName) {
            outputMessage("Environment parameter is null from existing stack");
            System.exit(2);
        }

        outputMessage("Build web app and copy files to S3 web bucket");
        String webUrl = buildAndCopyWebApp();
        outputMessage("AWS SaaS Boost Console URL is: " + webUrl);
    }

        private void checkEnvironment() {
/*        String awsRegion = System.getenv("AWS_REGION");
        if (null == awsRegion || awsRegion.isEmpty()) {
            outputMessage("AWS_REGION environment variable must be set.");
            System.exit(2);
        }*/

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

//        outputMessage("===========================================================");
        outputMessage("Environment Checks for maven, yarn, and AWS CLI PASSED.");
        outputMessage("===========================================================");
    }

    private Map<String, String> baseStackOutputs = new HashMap<>();
    private boolean loadSaaSBoostStack(String stackName) {
        try {
            DescribeStacksResponse stacks = cfn.describeStacks(DescribeStacksRequest
                    .builder()
                    .stackName(stackName)
                    .build()
            );
            if (stacks.stacks().isEmpty()) {
                outputMessage("No CloudFormation stack found with name: " + stackName);
                return false;
            }
            Stack stack = stacks.stacks().get(0);
            for (Parameter parameter : stack.parameters()) {
                if ("SaaSBoostBucket".equals(parameter.parameterKey())) {
                    this.s3ArtifactBucket = parameter.parameterValue();
                } else if ("Environment".equals(parameter.parameterKey())) {
                    this.envName = parameter.parameterValue();
                } else if ("LambdaSourceFolder".equals(parameter.parameterKey())) {
                    this.lambdaSourceFolder = parameter.parameterValue();
                }
            }

            //base stack outputs
            // define list of outputs we require
            List<String> outputList = new ArrayList<>(Arrays.asList("PublicSubnet1", "PublicSubnet2", "PrivateSubnet1", "PrivateSubnet2", "EgressVpc", "LoggingBucket"));
            for (Output output :  stack.outputs()) {
                if (outputList.contains(output.outputKey())) {
                    baseStackOutputs.put(output.outputKey(), output.outputValue());
                    outputList.remove(output.outputKey());
                }
            }

            if (outputList.size() > 0) {
                outputMessage("Could not find outputs from stack for: " + Arrays.toString(outputList.toArray()));
                return false;
            }

        } catch (Exception e) {
            LOGGER.error(getFullStackTrace(e));
            outputMessage("Error with loading stack resource. " + e.getMessage() );
            outputMessage("Verify the stack exists and is for a AWS SaaS Boost environment.");
            return false;
        }

        if (null == this.s3ArtifactBucket || null == this.envName) {
            outputMessage("CloudFormation Stack: " + stackName + " is missing parameters such as SaasBoostBucket and Environment");
            return false;
        }
        return true;
    }

    private void start() throws IOException {
        outputMessage("===========================================================");
        outputMessage("Welcome to the AWS SaaS Boost Installer");
        outputMessage("Installer Version: " + getVersionInfo());

        checkEnvironment();
        //accountId = iam.getUser().user().arn().split(":")[4];
        accountId = sts.getCallerIdentity().account();

        boolean isValid = false;

         //get option of initial install or add metrics
        do {
            System.out.println("1. New AWS SaaS Boost install.");
            System.out.println("2. Install Metrics and Analytics in to existing AWS SaaS Boost deployment.");
            System.out.println("3. Update Web Application for existing AWS SaaS Boost deployment.");
            System.out.println("4. Update existing AWS SaaS Boost deployment.");
            System.out.println("5. Delete existing AWS SaaS Boost deployment.");
            System.out.println("6. Exit installer.");


            System.out.print("Please select an option to continue (1-6): ");
            this.installOption = input.nextInt();
            if (installOption >= 1 && installOption <= 6) {
                isValid = true;
            } else {
                System.out.println("Invalid option specified, try again.");
            }
        } while (!isValid);

        input.nextLine();

        if (6 == installOption) {
            outputMessage("Installation canceled.");
            System.exit(0);
        }

        if (5 == installOption) {
            deleteSaasBoostInstallation();
            return;
        }

        //get root directory of saas-boost download
        String currentDir = Paths.get("").toAbsolutePath().toString();
        LOGGER.info("Current dir = " + currentDir);
        do {
            System.out.print("Directory path of saas-boost download (Press Enter for '" + currentDir +"') :");
            String dirVal = input.nextLine().trim();
            if (dirVal == null || dirVal.equals("")) {
                this.rootDir = currentDir;
            } else {
                this.rootDir = dirVal;
            }
            if (null != this.rootDir) {
                try {
                    File file = new File(this.rootDir);
                    if (file.exists() && file.isDirectory()) {
                        File resources = new File(this.rootDir + File.separator + "resources");
                        if (resources.isDirectory()) {
                            isValid = true;
                        } else {
                            outputMessage("No resources directory found under '" + this.rootDir + "'. Check path.");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error with directory path: " + this.rootDir + ", " + e.getLocalizedMessage());
                    LOGGER.error(getFullStackTrace(e));
                }
            }
            if (!isValid) {
                outputMessage("Path: " + this.rootDir + " is an invalid path, try again.");
            }
        } while (!isValid);


        //set the webdir to be used for app build
        this.webDir = this.rootDir + File.separator + "client" + File.separator + "web";

        if (1 == installOption) {
            LOGGER.info("Performing new installation of AWS SaaS Boost");
            installSaaSBoost();
            outputMessage("AWS SaaS Boost Artifacts Bucket: " + s3ArtifactBucket);
            //create db password if metrics installed
            if (getBoolean(metricsAndAnalytics)) {
                outputMessage("The database password for Redshift Metrics database is stored in SSM Parameter" + dbPasswordSSMParameter);
            }
        } else if (2 == installOption) {
            LOGGER.info("Performing installation of Metrics and Analytics module into existing AWS SaaS Boost installation.");
            metricsAndAnalytics = "y";
            installMetrics(null);
            outputMessage("The database password for Redshift Metrics database is stored in SSM Parameter" + dbPasswordSSMParameter);
        } else if (3 == installOption) {
            LOGGER.info("Perform Update of the Web Application for AWS SaaS Boost");
            updateWebApp();
        } else if (4 == installOption) {
            LOGGER.info("Perform Update of AWS SaaS Boost deployment");
            updateSaaSBoost();
        }
    }

    private void updateLambdas() throws IOException {
        boolean isValid = false;
        String stackName;
        try {
            do {
                System.out.print("Enter name of the AWS SaaS Boost CloudFormation stack that has already been deployed (Ex. sb-dev): ");
                stackName = input.next().trim();
                if (null != stackName && !stackName.isEmpty()) {
                    //validate the stackName exists
                    isValid = loadSaaSBoostStack(stackName);
                    if (!isValid) {
                        outputMessage("Please try again.");
                    }
                } else {
                    outputMessage("Entered value is incorrect, please try again.");
                }
            } while (!isValid);
        } catch (Exception e) {
            LOGGER.error(getFullStackTrace(e));
            throw e;
        }

        if (null == this.envName) {
            outputMessage("Environment parameter is null from existing stack");
            System.exit(2);
        }

        outputMessage("Build Lambdas and copy zip files to S3 artifacts bucket under " + lambdaSourceFolder +"...");
        processLambdas(true);

/*
        //update lambdas with an alias need to have version incremented and alias updated.
        try {
            outputMessage("Update Lambda Aliases...");
            updateLambdaAliases();
        } catch (Exception e) {
            outputMessage("Error updating Lambda Aliases");
            LOGGER.error(getFullStackTrace(e));
        }
*/

        outputMessage("Completed update of Lambda functions for " + stackName);
    }

    private List<String> getProvisionedTenants() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("getProvisionedTenants");
        String TENANTS_TABLE="sb-" + this.envName + "-tenants";
        List<String> tenants = new ArrayList<>();
        try {
            ScanResponse response = ddb.scan(request -> request
                    .tableName(TENANTS_TABLE)
                    .filterExpression("attribute_exists(onboarding) AND onboarding <> :created AND onboarding <> :failed")
                    .expressionAttributeValues(Stream
                            .of(
                                    new AbstractMap.SimpleEntry<String, AttributeValue>(":created", AttributeValue.builder().s("created").build()),
                                    new AbstractMap.SimpleEntry<String, AttributeValue>(":failed", AttributeValue.builder().s("failed").build())
                            )
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                    )
            );
            LOGGER.info("TenantServiceDAL::getProvisionedTenants returning " + response.items().size() + " provisioned tenants");
            response.items().forEach(item ->
                    tenants.add(item.get("id").s()));
        } catch (DynamoDbException e) {
            LOGGER.error("getProvisionedTenants " + getFullStackTrace(e));
            if (null != e.getMessage() || !e.getMessage().contains("resource not found")) {
                throw new RuntimeException(e);
            } else {
                LOGGER.info("No tenants records found");
            }

        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("getProvisionedTenants exec " + totalTimeMillis);
        return tenants;
    }


    private void deleteSaasBoostInstallation() {
        String sbStackName = null;
        boolean isValid = false;
        do {
            System.out.print("Enter name of the AWS SaaS Boost CloudFormation stack you want to delete (Ex. sb-dev): ");
            sbStackName = input.next().trim();
            if (null != sbStackName && !sbStackName.isEmpty()) {
                //validate the stackName exists
                isValid = loadSaaSBoostStack(sbStackName);
                if (!isValid) {
                    outputMessage("Please try again.");
                }
            } else {
                outputMessage("Entered value is incorrect, please try again.");
            }
        } while (!isValid);

        //confirm delete by entering stack name again
        outputMessage("****** W A R N I N G");
        outputMessage("Deleting the AWS SaaS Boost environment is IRREVERSIBLE and ALL deployed tenant resources will be deleted!");
        do {
            System.out.print("If are certain you want to DELETE the stack enter the stack name to confirm: ");
            String confirmStackName = input.next().trim();
            if (null != confirmStackName && !confirmStackName.isEmpty() && sbStackName.equalsIgnoreCase(confirmStackName)) {
                System.out.println("CloudFormation stack: " + sbStackName + " will be deleted.");
                isValid = true;
            } else {
                outputMessage("Entered value is incorrect, please try again.");
            }
        } while (!isValid);

        //load tenants and delete each tenant stack
        List<String> tenants = getProvisionedTenants();
        for (String tenantId : tenants) {
            String tenantStackId = "Tenant-" + tenantId.split("-")[0];
            if (checkCloudFormationStack(tenantStackId)) {
                outputMessage("Deleting AWS SaaS Boost Tenant stack: " + tenantStackId);
                deleteCloudFormationStack(tenantStackId);
            }
        }

        //clean up ECR repo
        // format name of Export is saas-boost::sep14-us-west-2:tenantEcrRepoName
        // or we can read from SSM  /saas-boost/$SAAS_BOOST_ENV/ECR_REPO
        try {
            GetParameterResponse parameterResponse = ssm.getParameter(GetParameterRequest.builder().name("/saas-boost/" + this.envName + "/ECR_REPO").build());
            String ecrRepo = parameterResponse.parameter().value();
            outputMessage("SSM param for ECR_REPO: " + ecrRepo);
            if (!deleteEcrImages(ecrRepo)) {
                outputMessage("Error deleting images from ECR repo: " + ecrRepo);
                System.exit(2);
            };
        } catch (Exception e) {
            LOGGER.error(getFullStackTrace(e));
            outputMessage("Unable to retrieve SSM Parameter /saas-boost/" + this.envName + "/ECR_REPO. Skip delete of Repo images.");
            //System.exit(2);
        }

        //check for -metrics stack and delete it and wait
        if (checkCloudFormationStack(sbStackName + "-metrics")) {
            outputMessage("Deleting AWS SaaS Boost Metrics stack: " + sbStackName + "-metrics");
            deleteCloudFormationStack(sbStackName + "-metrics");
        }

        //delete sbStack and wait
        outputMessage("Deleting AWS SaaS Boost stack: " + sbStackName);
        deleteCloudFormationStack(sbStackName);

        //**TODO: delete all the params with /saas-boost/$ENV
        //ssm.deleteParameters(DeleteParametersRequest.builder().names().build());

        //delete the s3 artifacts bucket
        //delete the old lambdas zip files

        LOGGER.info("Clean up s3 bucket: " + s3ArtifactBucket);
        this.s3 = AmazonS3Client.builder().withRegion(AWS_REGION.id()).build();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(s3ArtifactBucket);
        ObjectListing listing = s3.listObjects(listObjectsRequest);
        ArrayList<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();
        for (S3ObjectSummary obj : listing.getObjectSummaries()) {
            LOGGER.info("Key to delete: " + obj.getKey());
            keys.add(new DeleteObjectsRequest.KeyVersion(obj.getKey()));
        }
        if (!keys.isEmpty()) {
            outputMessage("Delete files from s3 bucket: " + s3ArtifactBucket);
            DeleteObjectsRequest request = new DeleteObjectsRequest(s3ArtifactBucket).withKeys(keys);
            s3.deleteObjects(request);
        }
        outputMessage("Delete S3 bucket: " + s3ArtifactBucket);
        s3.deleteBucket(s3ArtifactBucket);
    }

    private boolean deleteEcrImages(String ecrRepo) {
        ListImagesResponse imagesResponse;
        try {
            imagesResponse = ecr.listImages(ListImagesRequest.builder().repositoryName(ecrRepo).build());
        } catch (Exception e) {
            LOGGER.error("Error with loading images for repo: " + ecrRepo, e);
            return true;
        }

        LOGGER.info("Number of ECR images loaded: " + imagesResponse.imageIds().size());

        if (!imagesResponse.imageIds().isEmpty()) {
            try {
                ecr.batchDeleteImage(BatchDeleteImageRequest.builder()
                        .repositoryName(ecrRepo)
                        .imageIds(imagesResponse.imageIds()).build());
                return true;
            } catch (Exception e) {
                LOGGER.error("Error deleting ecr images from repo: " + ecrRepo, e);
                return false;
            }
        } else {
            outputMessage("No ECR images to delete");
            return true;
        }
    }

    /*
    Initial installation of AWS SaaS Boost
    */
    private void installSaaSBoost() throws IOException {
        boolean isValid = false;

        //check if yarn.lock exists in the client/web folder
        //yarn must be run manually before installation starts as it takes a while to download packages
        String yarnLockFile = this.webDir + File.separator + "yarn.lock";
        try {
            File lockFile = new File(yarnLockFile);
            if (!lockFile.exists()) {
                outputMessage("Please run 'yarn' command from " + this.webDir + " before running this installer.");
                System.exit(2);
            }
        } catch (Exception e) {
            LOGGER.error("Error with file path: " + yarnLockFile + ", " + e.getLocalizedMessage());
            LOGGER.error(getFullStackTrace(e));
            System.exit(2);
        }

        isValid = false;
        do {
            System.out.print("Enter name of the AWS SaaS Boost environment to deploy (Ex. dev, test, uat, prod, etc.): ");
            envName = input.next();
            //outputMessage("You entered environment name: " + envName);
//            if (null != envName && envName.length() <= 10 && envName.equals(envName.toLowerCase())) {
              if (null != envName && validateEnvironmentName(envName)) {
                isValid = true;
            } else {
                outputMessage("Entered value is incorrect, maximum of 10 alphanumeric characters and lowercase, please try again.");
            }
        } while (!isValid);

        isValid = false;
        do {
            System.out.print("Enter the email address for your AWS SaaS Boost administrator: ");
            emailAddress = input.next();
            //outputMessage("You entered email address: " + emailAddress);
            if (null != emailAddress && validateEmail(emailAddress)) {
                isValid = false;
                System.out.print("Enter the email address address again to confirm: ");
                String confirmEmail = input.next();
                //outputMessage("You entered email address: " + emailAddress);
                if (null != confirmEmail && emailAddress.equals(confirmEmail)) {
                    isValid = true;
                } else {
                    outputMessage("Entered value for email address does not match " + emailAddress);
                }
            } else {
                outputMessage("Entered value for email address is incorrect or wrong format, please try again.");
            }
        } while (!isValid);

        String useDomain = "n";
        isValid = false;
        do {
            System.out.print("Would you like to setup a domain in Route 53 as a Hosted Zone for the AWS SaaS Boost environment (y or n)? ");
            useDomain = input.next();
            if ("y".equalsIgnoreCase(useDomain) || "n".equalsIgnoreCase(useDomain)) {
                //outputMessage("Use domain for tenants hosted on Route53: " + useSubdomain);
                isValid = true;
            } else {
                outputMessage("Entered value is incorrect, please try again.");
            }
        } while (!isValid);

        //if domain name is being used the lets get input of valid domain name.
        if ("y".equalsIgnoreCase(useDomain)) {
            isValid = false;
            do {
                System.out.print("Enter the domain to use (Ex. app.yourcompany.com): ");
                this.domain = input.next();
                isValid = validateDomain(domain);
                if (!isValid) {
                    outputMessage("The entered domain is an invalid format.");
                }
            } while (!isValid);
        }

        isValid = false;
        do {
            System.out.print("Would you like to install the metrics and analytics module of AWS SaaS Boost (y or n)? ");
            metricsAndAnalytics = input.next();
            if ("y".equalsIgnoreCase(metricsAndAnalytics) || "n".equalsIgnoreCase(metricsAndAnalytics)) {
                //outputMessage("Install optional Metrics and Analytics: " + this.metricsAndAnalytics);
                isValid = true;
            } else {
                outputMessage("Entered value must be y or n, please try again.");
            }
        } while (!isValid);

        //if installing Metrics then ask about quick sight.
        if ("y".equalsIgnoreCase(metricsAndAnalytics)) {
            isValid = false;
            do {
                System.out.print("Would you like to setup Amazon Quicksight for Metrics and Analytics? You must have already registered for Quicksight in your account. (y or n)? : ");
                this.setupQuickSight = input.next();
                if ("y".equalsIgnoreCase(setupQuickSight) || "n".equalsIgnoreCase(setupQuickSight)) {
                    isValid = true;
                } else {
                    outputMessage("Entered value must be y or n, please try again.");
                }
            } while (!isValid);
        }

        if ("y".equalsIgnoreCase(setupQuickSight)) {
            isValid = false;
            input.nextLine();
            do {
                System.out.print("Region where your registered for Amazon QuickSight identity (Press Enter for '" + AWS_REGION.id() + "'): ");
                String regionInput = input.nextLine().trim();
                //outputMessage("Input value is: " + quickSightUserInput);
                // Skip the newline
                //input.nextLine();
                if (regionInput == null || regionInput.equals("")) {
                    quickSightRegion = AWS_REGION;
                    isValid = true;
                } else {
                    isValid = false;
                    Region region = Region.of(regionInput);
                    if (!Region.regions().contains(region)) {
                        outputMessage("Entered value is not a region, please try again.");
                    } else {
                        isValid = true;
                        quickSightRegion = region;
                    }
                }
            } while (!isValid);

            //validate the user for quicksight
            List<User> users = getQuicksightUsers();
            Map<String, User> userMap = new HashMap<>();
            for (User user : users) {
                userMap.put(user.userName(), user);
            }
            if (users.isEmpty()) {
                outputMessage("No users found in Quicksignt. Please register in your AWS Account and try install again.");
                System.exit(2);
            }

            isValid = false;
            //input.nextLine();
            do {
                System.out.print("Amazon Quicksight user name (Press Enter for '" + users.get(0).userName() + "'): ");
                String quickSightUserInput = input.nextLine().trim();
                //outputMessage("Input value is: " + quickSightUserInput);
                // Skip the newline
                //input.nextLine();
                if (quickSightUserInput == null || quickSightUserInput.equals("")) {
                    quickSightUser = users.get(0);
                    isValid = true;
                } else {
                    isValid = false;
                    if (!userMap.containsKey(quickSightUserInput)) {
                        outputMessage("Entered value is not a valid Quicksight user in your account, please try again.");
                    } else {
                        quickSightUser = userMap.get(quickSightUserInput);
                        isValid = true;
                    }
                }
            } while (!isValid);
        }

        String inputFsx = "n";
        isValid = false;
        do {
            System.out.println("");
            System.out.println("If your application requires a FSX for Windows Filesystem, an Active Directory is required.");
            System.out.print("Would you like to provision a Managed Active Directory to use with FSX for Windows Filesystem (y or n)? ");
            inputFsx = input.next();
            if ("y".equalsIgnoreCase(inputFsx) || "n".equalsIgnoreCase(inputFsx)) {
                isValid = true;
                activeDirectory = inputFsx;
            } else {
                outputMessage("Entered value is incorrect, please try again.");
            }
        } while (!isValid);

        //always install metering and billing
        meteringAndBilling = "y";

        outputMessage("===========================================================");
        outputMessage("");
        outputMessage("Would you like to continue the installation with the following options?");
        outputMessage("AWS SaaS Boost Environment Name: " + envName);
        outputMessage("Admin Email Address: " + emailAddress);
        outputMessage("Route 53 Domain for AWS SaaS Boost environment: " + domain);
        outputMessage("Install Metrics and Analytics: " + metricsAndAnalytics);
        if ("y".equalsIgnoreCase(metricsAndAnalytics) && null != quickSightUser) {
            outputMessage("Amazon Quicksight user for setup of Metrics and Analytics: " + quickSightUser.userName());
        } else {
            outputMessage("Amazon Quicksight user for setup of Metrics and Analytics: n/a");
        }
        outputMessage("Setup Active Directory for FSX for Windows: " + activeDirectory);
        // outputMessage("Install Metering and Billing: " + this.meteringAndBilling);


        isValid = false;
        do {
            System.out.print("Enter y to continue or n to cancel: ");
            String continueInstall = input.next();
            if ("n".equalsIgnoreCase(continueInstall)) {
                outputMessage("Canceled installation of AWS SaaS Boost");
                System.exit(2);
            } else if ("y".equalsIgnoreCase(continueInstall)) {
                outputMessage("Continuing installation of AWS SaaS Boost");
                isValid = true;
            } else {
                outputMessage("Invalid option for continue installation: " + continueInstall + ", try again.");
            }
        } while (!isValid);


        outputMessage("===========================================================");
        outputMessage("Installing AWS SaaS Boost");
        outputMessage("===========================================================");


        //check for the AWS Service Roles:
        outputMessage("Check and Create AWS Service Roles");
        setupAwsServiceRoles();

        //create s3 bucket
        outputMessage("Create S3 artifacts bucket");
        createS3ArtifactBucket(envName);

        //copy yaml files
        outputMessage("Copy CloudFormation template files to S3 artifacts bucket");
        copyTemplateFilesToS3(new ArrayList<>());

        //build lambdas
        this.lambdaSourceFolder = "lambdas";
        outputMessage("Build Lambdas and copy zip files to S3 artifacts bucket...");
        processLambdas(getBoolean(meteringAndBilling));

        if (getBoolean(activeDirectory)) {
            //create secure SSM parameter for password
            UUID uniqueId = UUID.randomUUID();
            String[] parts = uniqueId.toString().split("-");  //UUID 29219402-d9e2-4727-afec-2cd61f54fa8f
            String adPassword = "AdX43Bc" + parts[0];
            ssm = SsmClient.builder()
                    .region(AWS_REGION).build();
            // /saas-boost/${Environment}/METRICS_ANALYTICS_DEPLOYED to true
            LOGGER.info("Add SSM param ACTIVE_DIRECTORY_PASSWORD with password");
            software.amazon.awssdk.services.ssm.model.Parameter passwordParam = putParameter(toParameterStore("ACTIVE_DIRECTORY_PASSWORD", adPassword ,true));
            activeDirectoryPasswordParam = passwordParam.name();
            outputMessage("Active Directory admin user password stored in secure SSM Parameter: " + activeDirectoryPasswordParam);

/*            //put ACTIVE_DIRECTORY_CREDS -- to be used when CloudFormation supports FSX in task definition.
            String creds = "{\"username\":\"admin\", \"password\":\"" + adPassword + "\"}";
            software.amazon.awssdk.services.ssm.model.Parameter credsParam = putParameter(toParameterStore("ACTIVE_DIRECTORY_CREDS", creds ,true));
            outputMessage("Active Directory creds stored in secure SSM Parameter: " + credsParam.name());*/

        }

        //execute cloudsformation
        outputMessage("Execute the CloudFormation stack");
        String stackName = "sb-" + envName;
        createSaaSBoostStack(stackName);


        //wait for completion and then build web app
        outputMessage("Copy files to S3 web site bucket");
        String webUrl = buildAndCopyWebApp();

        if (getBoolean(metricsAndAnalytics)) {
            LOGGER.info("Install metrics and analytics module");
            installMetrics(stackName);
        }

        outputMessage("Check the admin email box for the temporary password.");
        outputMessage("AWS SaaS Boost Console URL is: " + webUrl);
    }


    private Map<String, String> getMetricStackOutputs(String stackName) {
        //get the Redshift outputs from the metrics cloudformation stack

/*        LOGGER.info("Get resources for CloudFormation stack {}", stackName);
        ListStackResourcesResponse stackResourcesResponse = cfn.listStackResources(ListStackResourcesRequest.builder().stackName(stackName).build());
        String metricsStackPhysicalId = null;
        for (StackResourceSummary summary : stackResourcesResponse.stackResourceSummaries()) {
            if ("metrics".equalsIgnoreCase(summary.logicalResourceId())) {
                metricsStackPhysicalId = summary.physicalResourceId();
                break;
            }
        }

        if (null == metricsStackPhysicalId) {
            outputMessage("Unable to find metrics nested stack from CloudFormation stack: " + stackName);
            System.exit(2);
        }*/

        try {
            Map<String, String> outputs = new HashMap<>();
            DescribeStacksResponse stacksResponse = cfn.describeStacks(DescribeStacksRequest.builder().stackName(stackName).build());
            Stack stack = stacksResponse.stacks().get(0);
            for (Output output : stack.outputs()) {
                outputs.put(output.outputKey(), output.outputValue());
            }

            if (null == outputs.get("RedshiftDatabaseName")
                    || null == outputs.get("RedshiftEndpointAddress")
                    || null == outputs.get("RedshiftCluster")
                    || null == outputs.get("RedshiftEndpointPort")
                    || null == outputs.get("MetricsBucket")) {
                outputMessage("Error, missing one or more of the following outputs from CloudFormation stack: " + stackName);
                outputMessage("RedshiftDatabaseName, RedshiftCluster, RedshiftEndpointAddress, RedshiftEndpointPort, MetricsBucket");
                outputMessage(("Aborting the installation due to error"));
                System.exit(2);
            }
            return outputs;
        } catch (Exception e) {
            outputMessage("getMetricStackOutputs: Unable to load Metrics and Analytics CloudFormation stack: " + stackName);
            LOGGER.error(getFullStackTrace(e));
            System.exit(2);
        }
        return null;
    }

    private void setupQuickSight(String stackName, Map<String, String> outputs) {
        //String accountNumber = iam.getUser().user().arn().split(":")[4];
        String accountNumber = sts.getCallerIdentity().account();
        LOGGER.info("User for Quicksight: " + quickSightUser);

        //refresh the client
        quickSightClient = QuickSightClient.builder().region(AWS_REGION).build();
        LOGGER.info("Create data source in Quicksight for metrics Redshift table in Region: " + AWS_REGION.id());
        CreateDataSourceResponse createDataSourceResponse = quickSightClient.createDataSource(CreateDataSourceRequest.builder()
                .dataSourceId("sb-" + this.envName + "-metrics-source")
                .name("sb-" + this.envName + "-metrics-source")
                .awsAccountId(accountNumber)
                .type(DataSourceType.REDSHIFT)
                .dataSourceParameters(DataSourceParameters.builder()
                        .redshiftParameters(RedshiftParameters.builder()
                                .database(outputs.get("RedshiftDatabaseName"))
                                .host(outputs.get("RedshiftEndpointAddress"))
                                .clusterId(outputs.get("RedshiftCluster"))
                                .port(Integer.valueOf(outputs.get("RedshiftEndpointPort")))
                                .build())
                        .build())
                .credentials(DataSourceCredentials.builder()
                        .credentialPair(CredentialPair.builder()
                                .username("metricsadmin")
                                .password(dbPassword)
                                .build())
                        .build())
                .permissions(ResourcePermission.builder()
                        .principal(quickSightUser.arn())
                        .actions("quicksight:DescribeDataSource","quicksight:DescribeDataSourcePermissions","quicksight:PassDataSource","quicksight:UpdateDataSource","quicksight:DeleteDataSource","quicksight:UpdateDataSourcePermissions")
                        .build())
                .sslProperties(SslProperties.builder()
                        .disableSsl(false)
                        .build())
                .tags(Tag.builder()
                        .key("Name")
                        .value(stackName)
                        .build())
                .build());


        //define the physical table for Quicksight
        List<InputColumn> inputColumns = new ArrayList<>();
        List<String> stringCols = new ArrayList<>(Arrays.asList("type", "workload", "context", "tenant_id",
                "tenant_name", "tenant_tier", "metric_name", "metric_unit", "meta_data"));
        for (String col : stringCols) {
            InputColumn inputColumn = InputColumn.builder()
                    .name(col)
                    .type(InputColumnDataType.STRING)
                    .build();
            inputColumns.add(inputColumn);
        }

        InputColumn inputColumn = InputColumn.builder()
                .name("metric_value")
                .type(InputColumnDataType.INTEGER)
                .build();
        inputColumns.add(inputColumn);

         inputColumn = InputColumn.builder()
                .name("timerecorded")
                .type(InputColumnDataType.DATETIME)
                .build();
        inputColumns.add(inputColumn);

        PhysicalTable physicalTable = PhysicalTable.builder()
                .relationalTable(RelationalTable.builder()
                        .dataSourceArn(createDataSourceResponse.arn())
                        .schema("public")
                        .name("sb_metrics")
                        .inputColumns(inputColumns)
                        .build())
                .build();

        Map<String, PhysicalTable> physicalTableMap = new HashMap<>();
        physicalTableMap.put("string", physicalTable);

        LOGGER.info("Create dataset for sb_metrics table in Quicksight in Region " + AWS_REGION.id());

        quickSightClient.createDataSet(CreateDataSetRequest.builder()
                .awsAccountId(accountNumber)
                .dataSetId("sb-" + this.envName + "-metrics")
                .name("sb-" + this.envName + "-metrics")
                .physicalTableMap(physicalTableMap)
                .importMode(DataSetImportMode.DIRECT_QUERY)
                .permissions(ResourcePermission.builder()
                        .principal(quickSightUser.arn())
                        .actions("quicksight:DescribeDataSet","quicksight:DescribeDataSetPermissions","quicksight:PassDataSet","quicksight:DescribeIngestion","quicksight:ListIngestions","quicksight:UpdateDataSet","quicksight:DeleteDataSet","quicksight:CreateIngestion","quicksight:CancelIngestion","quicksight:UpdateDataSetPermissions")
                        .build())
                .tags(Tag.builder()
                        .key("Name")
                        .value(stackName)
                        .build())
                .build());
    }

    private final static String SAAS_BOOST_PREFIX = "saas-boost";

    private software.amazon.awssdk.services.ssm.model.Parameter toParameterStore(String settingName, String settingValue, boolean secure) {
        if (settingName == null || settingName.isEmpty()) {
            throw new RuntimeException("Can't create Parameter Store parameter from blank Setting name");
        }
        String parameterName = "/" + SAAS_BOOST_PREFIX + "/" + envName + "/" + settingName;
        String parameterValue = (settingValue == null || settingValue.isEmpty()) ? "N/A" : settingValue;
        software.amazon.awssdk.services.ssm.model.Parameter parameter = software.amazon.awssdk.services.ssm.model.Parameter.builder()
                .type(secure ? ParameterType.SECURE_STRING : ParameterType.STRING)
//                .type(ParameterType.STRING)
                .name(parameterName)
                .value(parameterValue)
                .build();
        return parameter;
    }

    private software.amazon.awssdk.services.ssm.model.Parameter putParameter(software.amazon.awssdk.services.ssm.model.Parameter parameter) {
        software.amazon.awssdk.services.ssm.model.Parameter updated = null;
        try {
            PutParameterResponse response = ssm.putParameter(request -> request
                    .type(parameter.type())
                    .overwrite(true)
                    .name(parameter.name())
                    .value(parameter.value())
            );
            updated = software.amazon.awssdk.services.ssm.model.Parameter.builder()
                    .name(parameter.name())
                    .value(parameter.value())
                    .type(parameter.type())
                    .version(response.version())
                    .build();
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:PutParameter error " + ssmError.getMessage());
            throw ssmError;
        }
        return updated;
    }

    private boolean getParameter(String parameterName, boolean decrypt) {
        try {
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name(parameterName)
                    .withDecryption(decrypt).build()
            );
            return true;
        } catch (ParameterNotFoundException pnf) {
            LOGGER.warn("ssm:GetParameter parameter not found {}", parameterName);
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameter error " + ssmError.getMessage());
            throw ssmError;
        }
        return false;
    }

    /*
    Create Service Roles necessary for Tenant Stack Deployment
     */
    private void setupAwsServiceRoles() {
        /*
        aws iam get-role --role-name "AWSServiceRoleForElasticLoadBalancing" || aws iam create-service-linked-role --aws-service-name "elasticloadbalancing.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForECS" || aws iam create-service-linked-role --aws-service-name "ecs.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForApplicationAutoScaling_ECSService" || aws iam create-service-linked-role --aws-service-name "ecs.application-autoscaling.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForRDS" || aws iam create-service-linked-role --aws-service-name "rds.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForAmazonFsx" || aws iam create-service-linked-role --aws-service-name "fsx.amazonaws.com"
        aws iam get-role --role-name "AWSServiceRoleForAutoScaling" || aws iam create-service-linked-role --aws-service-name "autoscaling.amazonaws.com"        
        */

        //check for the service role first
        CreateServiceLinkedRoleResponse response;
        List<String> serviceRoles = new ArrayList<>(Arrays.asList("elasticloadbalancing.amazonaws.com", "ecs.amazonaws.com",
                "ecs.application-autoscaling.amazonaws.com", "rds.amazonaws.com", "fsx.amazonaws.com", "autoscaling.amazonaws.com"));

        ListRolesResponse rolesResponse = iam.listRoles(ListRolesRequest.builder().pathPrefix("/aws-service-role").build());
        Set<String> existingRoles = new HashSet<>();
        for (Role role : rolesResponse.roles()) {
            existingRoles.add(role.path());
        }
        for (String serviceRole : serviceRoles) {
            try {
                String path = String.format("/aws-service-role/%s/",serviceRole);
                if (existingRoles.contains(path)) {
                    LOGGER.info("Service role {} already exists", serviceRole);
                    continue;
                }
                response = iam.createServiceLinkedRole(CreateServiceLinkedRoleRequest.builder()
                        .awsServiceName(serviceRole)
                        .build());
                LOGGER.info("Service role {} created", serviceRole);
            } catch (Exception e) {
                LOGGER.error("setupAwsServiceRoles: Error with service role {}", serviceRole);
                LOGGER.error(getFullStackTrace(e));
                throw e;
            }
        }

    }

    private static boolean getBoolean (String val) {
        if (null != val && "y".equalsIgnoreCase(val)) {
            return true;
        } else if (null != val && "n".equalsIgnoreCase(val)) {
            return false;
        } if (null != val && "true".equalsIgnoreCase(val)) {
            return true;
        } else if (null != val && "false".equalsIgnoreCase(val)) {
            return false;
        } else {
            return false;
        }
    }

    private void s3CopyFile(String filePath, String key) {
        File file = new File(filePath);
/*        PutObjectResponse response = s3.putObject(PutObjectRequest.builder()
                .bucket(this.s3ArtifactBucket)
                .key(key)
                .build(), file);*/

        s3.putObject(this.s3ArtifactBucket, key, file);
    }

    private void copyTemplateFilesToS3(List<String> fileNames) {
        List<String> resourceFiles = listFiles(this.rootDir + File.separator + "resources", this.resourceFileFilter);
        for(String fileName : resourceFiles) {
            if (null == fileName || fileNames.isEmpty() || fileNames.contains(fileName)) {
                final String resourceFileToCopy = this.rootDir + File.separator + "resources" + File.separator + fileName;
                LOGGER.info("S3 copy of resource file {}", resourceFileToCopy);
                s3CopyFile(resourceFileToCopy, fileName);
            }
        }
    }

    private static void printResults(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = "";
        while ((line = reader.readLine()) != null) {
            LOGGER.info(line);
        }
    }

    private static boolean validateEmail(String emailAddress) {
        String regex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        //initialize the Pattern object
        Pattern pattern = Pattern.compile(regex);
        //searching for occurrences of regex
        Matcher matcher = pattern.matcher(emailAddress);
        return matcher.matches();
    }

    private static boolean validateEnvironmentName(String envName) {
        String regex = "^[a-z0-9]*$";
        if (envName.length() > 10) {
            return false;
        }
        //initialize the Pattern object
        Pattern pattern = Pattern.compile(regex);
        //searching for occurrences of regex
        Matcher matcher = pattern.matcher(envName);
        return matcher.matches();
    }

    private static void outputMessage(String msg) {
        LOGGER.info(msg);
        System.out.println(msg);
    } 

    private void buildAndCopyLambdas(String dir, List<String> subDirs) throws IOException {
        if (null == subDirs || subDirs.size() == 0) {
            //build list of subdirs for this directory
            subDirs = listFiles(this.rootDir + File.separator + dir, null);
        }

        outputMessage(" **> Build Lambdas under directory: " + dir);

        //loop through subdirs and check for pom.xml and build the zip and copy to s3
        String command = "mvn";
        for (String subdir : subDirs) {
            //check for pom.xml
            String fileName = this.rootDir + File.separator + dir + File.separator + subdir + File.separator + "pom.xml";
//            String osFilePath = getOSFilePath(fileName);
            Path file = Paths.get(fileName);
            if(!Files.exists(file)){
                LOGGER.info("No {} file found ", fileName);
                continue;
            }

            final String lambdaDir = this.rootDir + File.separator + dir +File.separator + subdir;
            final String targetDir = lambdaDir + File.separator + "target";

           //run maven command
           try {
               executeCommand(command, null, new File(lambdaDir));
           } catch (IOException e) {
               LOGGER.error("Error running maven for {}", fileName);
               throw e;
           }

            //copy the zip file to s3
            final List<String> zipFilesList = listFiles(targetDir, this.zipFileFilter);
            if (zipFilesList.size() > 0) {
                final String lambdaZipFileName = zipFilesList.get(0);
                final String zipFilePath = targetDir + File.separator + lambdaZipFileName;
                LOGGER.info("Found Zip file to copy: " + zipFilePath);
                try {
                    this.s3CopyFile(zipFilePath, this.lambdaSourceFolder + "/" + lambdaZipFileName);
                } catch (RuntimeException e) {
                    LOGGER.error("Error running copy for {}", zipFilePath);
                    throw e;
                }
            }
        }
    }

    private static String getOSFilePath(String filePath) {
        //check what OS and format path
        String  ret = "";
        return ret;
    }


    private static List<String> listFiles(String path, FilenameFilter filter) {

        // Creates an array in which we will store the names of files and directories
        String[] pathnames;
        List<String> files = new ArrayList<>();

        // Creates a new File instance by converting the given pathname string
        // into an abstract pathname
        File f = new File(path);

        // Populates the array with names of files and directories
        pathnames = f.list(filter);
        if (null == pathnames) {
            outputMessage("Path: " + path + " has no files matching filter: " + filter + ". Check the install directory for missing files.");
            System.exit(2);
        }

        // For each pathname in the pathnames array
        for (String pathname : pathnames) {
            // Print the names of files and directories
            files.add(pathname);
        }

        return files;

    }

    private void processLambdas(boolean buildMetering) throws IOException {
        //layers
        //This has to be in specific order because apigw-helper depends on utils
        final List<String> layersList = new ArrayList<>(Arrays.asList("utils" , "apigw-helper"));
        buildAndCopyLambdas("layers", layersList);

        //functions
        buildAndCopyLambdas("functions", null);

        //custom/resources
        buildAndCopyLambdas("resources" + File.separator + "custom-resources", null);

        //services
        final List<String> services = new ArrayList<>(Arrays.asList(
                "onboarding-service",
                "tenant-service",
                "user-service",
                "settings-service",
                "metric-service",
                "quotas-service"));
        buildAndCopyLambdas("services", services);

        //metering and billing if defined
        if (buildMetering) {
            buildAndCopyLambdas("metering-billing", new ArrayList<>(Arrays.asList("lambdas")));
        }
    }

    private void createSaaSBoostStack(final String stackName) {
        //Note - most params the default is used from the CloudFormation stack
        List<Parameter> templateParameters = new ArrayList<>();
        templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(envName).build());
        templateParameters.add(Parameter.builder().parameterKey("AdminEmailAddress").parameterValue(emailAddress).build());
        templateParameters.add(Parameter.builder().parameterKey("DomainName").parameterValue(domain).build());
        templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").parameterValue(s3ArtifactBucket).build());
        templateParameters.add(Parameter.builder().parameterKey("Version").parameterValue(VERSION).build());
        templateParameters.add(Parameter.builder().parameterKey("DeployActiveDirectory").parameterValue(String.valueOf(getBoolean(activeDirectory))).build());
        templateParameters.add(Parameter.builder().parameterKey("ADPasswordParam").parameterValue(activeDirectoryPasswordParam).build());

        // Now run the onboarding stack to provision the infrastructure SaaS Boost
        LOGGER.info("createSaaSBoostStack::create stack " + stackName);

        String stackId = null;
        try {
            CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                            .stackName(stackName)
                            .onFailure("DO_NOTHING")
                            .timeoutInMinutes(90)
                            .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                            .templateURL("https://" + this.s3ArtifactBucket + ".s3.amazonaws.com/saas-boost.yaml")
                            .parameters(templateParameters)
                            .build()
            );
            stackId = cfnResponse.stackId();
            LOGGER.info("createSaaSBoostStack::stack id " + stackId);


            boolean stackCompleted = false;
            long sleepTime = 5l;
            do {
                DescribeStacksResponse response = cfn.describeStacks(DescribeStacksRequest.builder()
                        .stackName(stackName)
                        .build());
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
                        LOGGER.error("Error with sleep");
                    }
                    sleepTime = 1; //set to 1 minute after kick off of 5 minute
                }
            } while (!stackCompleted);

            //wait until the stack is created and output message stack is being created
        } catch (SdkServiceException cfnError) {
            LOGGER.error("createSaaSBoostStack::createStack failed {}", cfnError.getMessage());
            LOGGER.error(getFullStackTrace(cfnError));
            throw cfnError;
        }
    }

    private void updateCloudFormationStack(final String stackName, final Map<String, String> paramsMap, String yamlFile) {
        //params set for the stack
        List<Parameter> templateParameters = new ArrayList<>();
        for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
            LOGGER.info("Set CloudFormation Parameter: " + entry.getKey() + ", Value: " + entry.getValue());
            templateParameters.add(Parameter.builder().parameterKey(entry.getKey()).parameterValue(entry.getValue()).build());
        }
        // Now run the onboarding stack to provision the infrastructure SaaS Boost
        LOGGER.info("updateCloudFormationStack::update stack " + stackName);

        String stackId = null;
        try {
            UpdateStackResponse cfnResponse = cfn.updateStack(UpdateStackRequest.builder()
                    .stackName(stackName)
                    .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                    .templateURL("https://" + this.s3ArtifactBucket + ".s3.amazonaws.com/" + yamlFile)
                    .parameters(templateParameters)
                    .build()
            );
            stackId = cfnResponse.stackId();
            LOGGER.info("updateCloudFormationStack::stack id " + stackId);


            boolean stackCompleted = false;
            long sleepTime = 3l;
            do {
                DescribeStacksResponse response = cfn.describeStacks(DescribeStacksRequest.builder()
                        .stackName(stackName)
                        .build());
                Stack stack = response.stacks().get(0);
                if ("UPDATE_COMPLETE".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation Stack: " + stackName + " completed successfully.");
                    stackCompleted = true;
                } else if ("UPDATE_ROLLBACK_COMPLETE".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation Stack: " + stackName + " failed.");
                    throw new RuntimeException("Error with CloudFormation stack " + stackName + ". Check the events in the AWS CloudFormation Console");
                } else {
                    outputMessage("Awaiting Update of CloudFormation Stack " + stackName + " to complete.  Sleep " + sleepTime + " minute(s)...");
                    try {
                        Thread.sleep(sleepTime * 60 * 1000);
                    } catch (Exception e) {
                        LOGGER.error("Error with sleep");
                    }
                    sleepTime = 1; //set to 1 minute after kick off of 5 minute
                }
            } while (!stackCompleted);

            //wait until the stack is created and output message stack is being created
        } catch (SdkServiceException cfnError) {
            if (null != cfnError && cfnError.getMessage().contains("No updates are to be performed")) {
                outputMessage("No Updates to be performed for Stack: " + stackName);
            } else {
                LOGGER.error("updateCloudFormationStack::update stack failed {}", cfnError.getMessage());
                LOGGER.error(getFullStackTrace(cfnError));
                throw cfnError;
            }
        }
    }

    private void createMetricsStack(final String stackName) {
        List<Parameter> templateParameters = new ArrayList<>();
        templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(this.envName).build());
//        templateParameters.add(Parameter.builder().parameterKey("ClusterType").parameterValue("multi-node").build());
//        templateParameters.add(Parameter.builder().parameterKey("EncryptData").parameterValue(this.encryptData).build());
//        templateParameters.add(Parameter.builder().parameterKey("KinesisBufferInterval").parameterValue("300").build());
//        templateParameters.add(Parameter.builder().parameterKey("KinesisBufferSize").parameterValue("5").build());
        templateParameters.add(Parameter.builder().parameterKey("LambdaSourceFolder").parameterValue(this.lambdaSourceFolder).build());
//        templateParameters.add(Parameter.builder().parameterKey("MetricDBUser").parameterValue("metricsadmin").build());
        templateParameters.add(Parameter.builder().parameterKey("MetricUserPasswordSSMParameter").parameterValue(dbPasswordSSMParameter).build());
//        templateParameters.add(Parameter.builder().parameterKey("MetricUserPassword").parameterValue(dbPassword).build());
//        templateParameters.add(Parameter.builder().parameterKey("MetricsTableName").parameterValue("metrics").build());
        templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").parameterValue(this.s3ArtifactBucket).build());
        templateParameters.add(Parameter.builder().parameterKey("LoggingBucket").parameterValue(baseStackOutputs.get("LoggingBucket")).build());
        templateParameters.add(Parameter.builder().parameterKey("DatabaseName").parameterValue("sbmetrics" + this.envName).build());

        templateParameters.add(Parameter.builder().parameterKey("PublicSubnet1").parameterValue(baseStackOutputs.get("PublicSubnet1")).build());
        templateParameters.add(Parameter.builder().parameterKey("PublicSubnet2").parameterValue(baseStackOutputs.get("PublicSubnet2")).build());
        templateParameters.add(Parameter.builder().parameterKey("PrivateSubnet1").parameterValue(baseStackOutputs.get("PrivateSubnet1")).build());
        templateParameters.add(Parameter.builder().parameterKey("PrivateSubnet2").parameterValue(baseStackOutputs.get("PrivateSubnet2")).build());

        //vpc
        templateParameters.add(Parameter.builder().parameterKey("VPC").parameterValue(baseStackOutputs.get("EgressVpc")).build());

        // Now run the  stack to provision the infrastructure for Metrics and Analytics
        LOGGER.info("createMetricsStack::stack " + stackName);

        String stackId = null;
        try {
            CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                            .stackName(stackName)
                            .onFailure("DO_NOTHING")
                            .timeoutInMinutes(90)
 //                           .roleARN("arn:aws:iam::094057127497:role/sb-install-role-feb1")
                            .capabilitiesWithStrings("CAPABILITY_NAMED_IAM", "CAPABILITY_AUTO_EXPAND")
                            .templateURL("https://" + this.s3ArtifactBucket + ".s3.amazonaws.com/saas-boost-metrics-analytics.yaml")
                            .parameters(templateParameters)
                            .build()
            );
            stackId = cfnResponse.stackId();
            LOGGER.info("createMetricsStack::stack id " + stackId);


            boolean stackCompleted = false;
            long sleepTime = 5l;
/*            if ("y".equalsIgnoreCase(this.metricsAndAnalytics)) {
                sleepTime = 15l;
            }*/
            do {
                DescribeStacksResponse response = cfn.describeStacks(DescribeStacksRequest.builder()
                        .stackName(stackName)
                        .build());
                Stack stack = response.stacks().get(0);
                if ("CREATE_COMPLETE".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation Metrics Stack: " + stackName + " completed successfully.");
                    stackCompleted = true;
                } else if ("CREATE_FAILED".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("CloudFormation Metrics Stack: " + stackName + " failed.");
                    throw new RuntimeException("Error with CloudFormation Metrics stack " + stackName + ". Check the events in the AWS CloudFormation Console");
                } else {
                    outputMessage("Awaiting CloudFormation Metrics Stack " + stackName + " to complete.  Sleep " + sleepTime + " minute(s)...");
                    try {
                        Thread.sleep(sleepTime * 60 * 1000);
                    } catch (Exception e) {
                        LOGGER.error("Error with sleep");
                    }
                    sleepTime = 1; //set to 1 minute after kick off of 5 minute
                }
            } while (!stackCompleted);

            //wait until the stack is created and output message stack is being created
        } catch (SdkServiceException cfnError) {
            LOGGER.error("createMetricsStack::create stack failed {}", cfnError.getMessage());
            LOGGER.error(getFullStackTrace(cfnError));
            throw cfnError;
        }
    }

    private void deleteCloudFormationStack(final String stackName) {
        // Now run the  stack to provision the infrastructure for Metrics and Analytics
        LOGGER.info("deleteCloudFormationStack::delete stack " + stackName);
        outputMessage("Deleting CloudFormation stack: " + stackName);

        String stackId = null;
        try {
            cfn.deleteStack(DeleteStackRequest.builder().stackName(stackName).build());

            boolean stackCompleted = false;
            long sleepTime = 5l;
/*            if ("y".equalsIgnoreCase(this.metricsAndAnalytics)) {
                sleepTime = 15l;
            }*/
            do {
                DescribeStacksResponse response = cfn.describeStacks(DescribeStacksRequest.builder()
                        .stackName(stackName)
                        .build());
                Stack stack = response.stacks().get(0);
                if ("DELETE_FAILED".equalsIgnoreCase(stack.stackStatusAsString())) {
                    outputMessage("Delete CloudFormation Stack: " + stackName + " failed.");
                    throw new RuntimeException("Error with delete of CloudFormation stack " + stackName + ". Check the events in the AWS CloudFormation Console");
                } else {
                    outputMessage("Awaiting Delete CloudFormation Stack " + stackName + " to complete.  Sleep " + sleepTime + " minute(s)...");
                    try {
                        Thread.sleep(sleepTime * 60 * 1000);
                    } catch (Exception e) {
                        LOGGER.error("Error with sleep");
                    }
                    sleepTime = 1; //set to 1 minute after kick off of 5 minute
                }
            } while (!stackCompleted);

        } catch (SdkServiceException cfnError) {
            if (null == cfnError.getMessage() || !cfnError.getMessage().contains("does not exist")) {
                outputMessage("Error with deletion of CloudFormation stack: " + stackName + ", message: " + cfnError.getMessage());
                LOGGER.error("deleteCloudFormationStack::deleteStack failed {}", cfnError.getMessage());
                LOGGER.error(getFullStackTrace(cfnError));
                throw cfnError;
            }
        }
    }

    private boolean checkCloudFormationStack (final String stackName) {
        // Now run the  stack to provision the infrastructure for Metrics and Analytics
        LOGGER.info("checkCloudFormationStack:: stack " + stackName);

        String stackId = null;
        try {
            DescribeStacksResponse response = cfn.describeStacks(DescribeStacksRequest.builder()
                    .stackName(stackName)
                    .build());
            Stack stack = response.stacks().get(0);
            return true;
        //wait until the stack is created and output message stack is being created
        } catch (SdkServiceException cfnError) {
            return false;
        }
    }


    private static boolean validateDomain(String domainName) {
        String regex = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$";
        Pattern pattern = Pattern.compile(regex);
        //searching for occurrences of regex
        Matcher matcher = pattern.matcher(domainName);
        return matcher.matches();
    }

    public void createS3ArtifactBucket(String envName) {
        UUID uniqueId = UUID.randomUUID();
        String[] parts = uniqueId.toString().split("-");  //UUID 29219402-d9e2-4727-afec-2cd61f54fa8f

        s3ArtifactBucket = "sb-" + envName + "-artifacts-" + parts[0] + "-" + parts[1];
        LOGGER.info("Make S3 Artifact Bucket {}", this.s3ArtifactBucket);
        s3.createBucket(s3ArtifactBucket);

        //Set encryption for bucket
        //CreateBucketRequest bucketRequest = new CreateBucketRequest(s3ArtifactBucket);
        ServerSideEncryptionByDefault encryptionByDefault = new ServerSideEncryptionByDefault();
        encryptionByDefault.setSSEAlgorithm("AES256");
        ServerSideEncryptionConfiguration encryptionConfiguration = new ServerSideEncryptionConfiguration();
        List<ServerSideEncryptionRule> rules = new ArrayList<>();
        ServerSideEncryptionRule rule = new ServerSideEncryptionRule();
        rule.setApplyServerSideEncryptionByDefault(encryptionByDefault);
        rules.add(rule);
        encryptionConfiguration.setRules(rules);
        SetBucketEncryptionRequest encryptionRequest = new SetBucketEncryptionRequest();
        encryptionRequest.setBucketName(s3ArtifactBucket);
        encryptionRequest.setServerSideEncryptionConfiguration(encryptionConfiguration);
        s3.setBucketEncryption(encryptionRequest);


        String policy = "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Sid\": \"DenyNonHttps\",\n" +
                "            \"Effect\": \"Deny\",\n" +
                "            \"Principal\": \"*\",\n" +
                "            \"Action\": \"s3:*\",\n" +
                "            \"Resource\": [\n" +
                "                \"arn:aws:s3:::" + s3ArtifactBucket + "/*\",\n" +
                "                \"arn:aws:s3:::" + s3ArtifactBucket + "\"\n" +
                "            ],\n" +
                "            \"Condition\": {\n" +
                "                \"Bool\": {\n" +
                "                    \"aws:SecureTransport\": \"false\"\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        s3.setBucketPolicy(s3ArtifactBucket, policy);


        //We will not set logging for the bucket as the access log bucket is not created.
/*        BucketLoggingConfiguration loggingConfiguration = new BucketLoggingConfiguration();
        loggingConfiguration.setDestinationBucketName();
        loggingConfiguration.setLogFilePrefix();
        SetBucketLoggingConfigurationRequest configRequest = new SetBucketLoggingConfigurationRequest(s3ArtifactBucket,
                loggingConfiguration);
        s3.setBucketLoggingConfiguration(configRequest);*/

        outputMessage("Created S3 Artifact Bucket: " + s3ArtifactBucket);

    }

    private String buildAndCopyWebApp() throws IOException {
        //build web app and copy
        //Note that yarn command must have already been run in the client/web folder.

        //list exports from stack
        /*
        REACT_APP_SIGNOUT_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:webUrl
        REACT_APP_CALLBACK_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:webUrl

        REACT_APP_COGNITO_USERPOOL saas-boost::${ENVIRONMENT}-${AWS_REGION}:userPoolId
        REACT_APP_CLIENT_ID saas-boost::${ENVIRONMENT}-${AWS_REGION}:userPoolClientId
        REACT_APP_COGNITO_USERPOOL_BASE_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:cognitoBaseUri
        REACT_APP_API_URI saas-boost::${ENVIRONMENT}-${AWS_REGION}:publicApiUrl
        WEB_BUCKET saas-boost::${ENVIRONMENT}-${AWS_REGION}:webBucket
         */

        if (null == webDir) {
            outputMessage("Unexpected errors, webDir needs to be defined!");
            System.exit(2);
        }

        //refresh the client
        cfn = CloudFormationClient.builder().region(AWS_REGION).build();

        String nextToken = null;
        Map<String, String> exportsMap = new HashMap<>();
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
        } while (null != nextToken);

        final String prefix = "saas-boost::" + envName + "-" + AWS_REGION.id() + ":";

        //run yarn to build the react app
        outputMessage("Start build of AWS SaaS Boost React web application with yarn ...");
        ProcessBuilder pb;

        if (isWindows()) {
            pb = new ProcessBuilder("cmd", "/c", "yarn", "build");
        } else {
            pb = new ProcessBuilder("yarn", "build");
        }

        // Check to ensure the availability of the variable `prefix + "webUrl"` before proceeding to next step
        if (!exportsMap.containsKey(prefix + "webUrl")) {
            outputMessage("Unexpected errors, CloudFormation export " + prefix + "webUrl not found");
            LOGGER.info("Available exports part of stack output" + String.join(", ", exportsMap.keySet()));
            System.exit(2);
        }

        Map<String, String> env = pb.environment();
        pb.directory(new File(webDir));
        env.put("REACT_APP_SIGNOUT_URI",exportsMap.get(prefix + "webUrl"));
        env.put("REACT_APP_CALLBACK_URI", exportsMap.get(prefix + "webUrl"));
        env.put("REACT_APP_COGNITO_USERPOOL", exportsMap.get(prefix + "userPoolId"));
        env.put("REACT_APP_CLIENT_ID", exportsMap.get(prefix + "userPoolClientId"));
        env.put("REACT_APP_COGNITO_USERPOOL_BASE_URI", exportsMap.get(prefix + "cognitoBaseUri"));
        env.put("REACT_APP_API_URI", exportsMap.get(prefix + "publicApiUrl"));
        env.put("REACT_APP_AWS_ACCOUNT", accountId);
        env.put("REACT_APP_ENVIRONMENT", envName);
        env.put("REACT_APP_AWS_REGION", AWS_REGION.id());

        pb.directory(new File(webDir));
        //pb.redirectError()
        Process process = pb.start();
        printResults(process);
        try {
            process.waitFor();
            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new RuntimeException("Error building web application. Verify version of node is correct.");
            }
            outputMessage("Completed build of AWS SaaS Boost React web application.");
        } catch (InterruptedException e) {
            LOGGER.error(getFullStackTrace(e));
        } finally {
            process.destroy();
        }

        //meta data to set cache control to no-store
        ObjectMetadataProvider metaDataProvider = (file, meta) -> {
            meta.setCacheControl("no-store");
        };

        //sync files to the web bucket
        outputMessage("Copy AWS SaaS Boost web application files to s3 web bucket");
        final String webBucket = exportsMap.get(prefix + "webBucket");
        if (null == webBucket || webBucket.isEmpty()) {
            throw new RuntimeException("CloudFormation export for '" + prefix + "webBucket' not found or is blank!");
        }

        //refresh the client
        this.s3 = AmazonS3Client.builder().withRegion(AWS_REGION.id()).build();
        TransferManager xferMgr = TransferManagerBuilder.standard()
                .withS3Client(this.s3)
                .build();
        try {
            MultipleFileUpload xfer = xferMgr.uploadDirectory(webBucket,
                    null
                    , new File(webDir + File.separator + "build")
                    , true
                    , metaDataProvider);

            do {
                try {
                    outputMessage(("Sleep 10 seconds for s3 file copy to complete"));
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getLocalizedMessage());
                }
            } while (!xfer.isDone());

        } catch (AmazonServiceException e) {
            LOGGER.error(e.getErrorMessage());
            throw e;
        } finally {
            xferMgr.shutdownNow();
        }

        return exportsMap.get(prefix + "webUrl");
    }

    private static void executeCommand(String command, String[] environment, File dir) throws IOException {
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

    private static String[] runCommand(String cmd)throws IOException {
        // The actual procedure for process execution:
        //runCommand(String cmd);
        // Create a list for storing output.
        ArrayList list = new ArrayList();
        // Execute a command and get its process handle
        Process proc = Runtime.getRuntime().exec(cmd);
        // Get the handle for the processes InputStream
        InputStream istr = proc.getInputStream();
        // Create a BufferedReader and specify it reads
        // from an input stream.

        BufferedReader br = new BufferedReader(new InputStreamReader(istr));
        String str; // Temporary String variable
        // Read to Temp Variable, Check for null then
        // add to (ArrayList)list
        while ((str = br.readLine()) != null)
            list.add(str);
        // Wait for process to terminate and catch any Exceptions.
        try {
            proc.waitFor();
        }
        catch (InterruptedException e) {
            System.err.println("Process was interrupted");
        }
        // Note: proc.exitValue() returns the exit value.
        // (Use if required)
        br.close(); // Done.
        // Convert the list to a string and return
        return (String[])list.toArray(new String[0]);
    }

    private static String getFullStackTrace(Exception e) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        e.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    private List<User> getQuicksightUsers() {
        LOGGER.info("Load Quicksight users");
        if (null == quickSightClient) {
            quickSightClient = QuickSightClient.builder().region(quickSightRegion).build();
        }

        List<User> users = new ArrayList<>();
        String nextToken;
        do{
            ListUsersResponse response  = quickSightClient.listUsers(ListUsersRequest.builder()
                    .awsAccountId(accountId)
                    .namespace("default")
                    .build());
            nextToken = response.nextToken();
            users.addAll(response.userList());
        } while (null != nextToken);

        LOGGER.info("Completed load of Quicksight users");
        return users;
    }
}
