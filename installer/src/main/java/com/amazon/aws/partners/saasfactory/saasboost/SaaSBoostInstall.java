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

import com.amazon.aws.partners.saasfactory.saasboost.model.Environment;
import com.amazon.aws.partners.saasfactory.saasboost.model.EnvironmentLoadException;
import com.amazon.aws.partners.saasfactory.saasboost.model.ExistingEnvironmentFactory;
import com.amazon.aws.partners.saasfactory.saasboost.workflow.UpdateWorkflow;
import com.amazon.aws.partners.saasfactory.saasboost.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.*;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;
import software.amazon.awssdk.services.sts.StsClient;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.amazon.aws.partners.saasfactory.saasboost.Constants.AWS_REGION;
import static com.amazon.aws.partners.saasfactory.saasboost.Constants.OS;
import static com.amazon.aws.partners.saasfactory.saasboost.Constants.VERSION;
import static com.amazon.aws.partners.saasfactory.saasboost.Utils.isBlank;
import static com.amazon.aws.partners.saasfactory.saasboost.Utils.isNotBlank;
import static com.amazon.aws.partners.saasfactory.saasboost.Utils.isNotEmpty;

public class SaaSBoostInstall {

    static {
        System.setProperty("logger.timestamp",
                DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss").format(LocalDateTime.now()));
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSBoostInstall.class);

    private final ApiGatewayClient apigw;
    private final CloudFormationClient cfn;
    private final EcrClient ecr;
    private final IamClient iam;
    private final S3Client s3;
    private final SsmClient ssm;
    private final Route53Client route53;
    private final AcmClient acm;

    private final String accountId;
    private Environment environment;
    private String envName;
    private Path workingDir;
    private SaaSBoostArtifactsBucket saasBoostArtifactsBucket;
    private String lambdaSourceFolder = "lambdas";
    private String stackName;
    private Map<String, String> baseStackDetails = new HashMap<>();
    private SaaSBoostApiHelper api;

    protected enum ACTION {
        INSTALL(1, "Install AWS SaaS Boost", false),
        UPDATE(2, "Update AWS SaaS Boost", true),
        UPDATE_WEB_APP(3, "Update Admin Web Application", true),
        DELETE(4, "Delete AWS SaaS Boost", true),
        CANCEL(5, "Exit", false),
        DEBUG(6, "Debug", false);

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
        apigw = Utils.sdkClient(ApiGatewayClient.builder(), ApiGatewayClient.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        cfn = Utils.sdkClient(CloudFormationClient.builder(), CloudFormationClient.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        ecr = Utils.sdkClient(EcrClient.builder(), EcrClient.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        iam = Utils.sdkClient(IamClient.builder(), IamClient.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        ssm = Utils.sdkClient(SsmClient.builder(), SsmClient.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        route53 = Utils.sdkClient(Route53Client.builder(), Route53Client.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        acm = Utils.sdkClient(AcmClient.builder(), AcmClient.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        StsClient sts = Utils.sdkClient(StsClient.builder(), StsClient.SERVICE_NAME, ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
        accountId = sts.getCallerIdentity().account();
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
            outputMessage("Please see detailed log file installer-"
                    + System.getProperty("logger.timestamp") + ".log");
            LOGGER.error(Utils.getFullStackTrace(e));
        }
    }

    protected void debug(String existingBucket) {
        while (true) {
            System.out.print("Enter name of the AWS SaaS Boost environment to deploy (dev, test, uat, prod, etc...): ");
            this.envName = Keyboard.readString();
            if (validateEnvironmentName(this.envName)) {
                LOGGER.info("Setting SaaS Boost environment = [{}]", this.envName);
                break;
            } else {
                outputMessage("Entered value is invalid, maximum of 10 alphanumeric characters, and cannot be AWS,"
                        + " Amazon, or Cognito. Please try again.");
            }
        }
        if (existingBucket != null) {
            saasBoostArtifactsBucket = new SaaSBoostArtifactsBucket(existingBucket, AWS_REGION);
            try {
                s3.headBucket(request -> request.bucket(saasBoostArtifactsBucket.getBucketName()));
            } catch (SdkServiceException s3error) {
                outputMessage("Bucket " + existingBucket + " does not exist!");
                throw s3error;
            }
        }
//        else {
//            saasBoostArtifactsBucket = SaaSBoostArtifactsBucket.createS3ArtifactBucket(s3, envName, AWS_REGION);
//            outputMessage("Created S3 artifacts bucket: " + saasBoostArtifactsBucket);
//        }
//
//        // Copy the CloudFormation templates
//        outputMessage("Uploading CloudFormation templates to S3 artifacts bucket");
//        copyResourcesToS3();
//
//        // Compile all the source code
//        outputMessage("Compiling Lambda functions and uploading to S3 artifacts bucket. This will take some time...");
//        processLambdas();
//
//        // Copy the source files up to S3 where CloudFormation resources expect them to be
//        outputMessage("Uploading admin web app source files to S3");
//        copyAdminWebAppSourceToS3(workingDir, saasBoostArtifactsBucket.getBucketName(), s3);
        outputMessage("Log into the Application Plane AWS Account and run the CloudFormation Integration Stack");
        outputMessage(quickCreateLink());
    }

    public void start(String existingBucket) {
        outputMessage("===========================================================");
        outputMessage("Welcome to the AWS SaaS Boost Installer");
        outputMessage("Installer Version: " + VERSION);

        // Do we have Maven and the AWS CLI on the PATH?
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
                install(existingBucket);
                break;
            case UPDATE:
                workflow = new UpdateWorkflow(this.workingDir, this.environment, this.s3, this.cfn, this.apigw);
                break;
            case UPDATE_WEB_APP:
                SaaSBoostInstall.copyAdminWebAppSourceToS3(this.workingDir,
                        this.saasBoostArtifactsBucket.getBucketName(), this.s3);
                break;
            case DELETE:
                delete();
                break;
            case DEBUG:
                debug(existingBucket);
                break;
            case CANCEL:
            default:
                cancel();
        }

        if (workflow != null) {
            workflow.run();
            System.exit(workflow.getExitCode());
        }
    }

    protected void install(String existingBucket) {
        LOGGER.info("Performing new installation of AWS SaaS Boost");
        while (true) {
            System.out.print("Enter name of the AWS SaaS Boost environment to deploy (Ex. dev, test, uat, prod, etc.): ");
            this.envName = Keyboard.readString();
            if (validateEnvironmentName(this.envName)) {
                LOGGER.info("Setting SaaS Boost environment = [{}]", this.envName);
                break;
            } else {
                outputMessage("Entered value is invalid, maximum of 10 alphanumeric characters, and cannot be AWS,"
                        + " Amazon, or Cognito. Please try again.");
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
                    LOGGER.info("Setting SaaS Boost admin email = [{}]", emailAddress);
                    break;
                } else {
                    outputMessage("Entered value for email address does not match " + emailAddress);
                }
            } else {
                outputMessage("Entered value for email address is incorrect or wrong format, please try again.");
            }
        }

        String systemIdentityProvider;
        while (true) {
            System.out.print("Enter the identity provider to use for system users (Cognito, Keycloak, or Auth0) Press Enter for 'Cognito': ");
            systemIdentityProvider = Keyboard.readString();
            if (isNotBlank(systemIdentityProvider)) {
                if (systemIdentityProvider.toUpperCase().equals("COGNITO")
                        || systemIdentityProvider.toUpperCase().equals("KEYCLOAK")
                        || systemIdentityProvider.toUpperCase().equals("AUTH0")) {
                    systemIdentityProvider = systemIdentityProvider.toUpperCase();
                    LOGGER.info("Setting Identity Provider = [{}]", systemIdentityProvider);
                    break;
                } else {
                    outputMessage("Invalid identity provider. Enter either Cognito, Keycloak, or Auth0.");
                }
            } else {
                systemIdentityProvider = "COGNITO";
                LOGGER.info("Setting Identity Provider = [{}]", systemIdentityProvider);
                break;
            }
        }

        // TODO support custom domains for Cognito hosted UI?
        // https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-add-custom-domain.html
        String identityProviderCustomDomain = null;
        String identityProviderHostedZone = null;
        String identityProviderCertificate = null;
        if ("KEYCLOAK".equals(systemIdentityProvider)) {
            if ("KEYCLOAK".equals(systemIdentityProvider)) {
                System.out.println("You must provide a custom domain name with a verified TLS (SSL) certificate to install Keycloak.");
                System.out.println("You must have an existing Route53 Hosted Zone for the domain name, and DNS resolution must work.");
            }
            while (true) {
                System.out.print("Enter the domain name for the SaaS Boost control plane identity provider (e.g. keycloak.example.com): ");
                identityProviderCustomDomain = Keyboard.readString();
                if (validateDomainName(identityProviderCustomDomain)) {
                    System.out.print("Using " + identityProviderCustomDomain + " for the SaaS Boost identity provider. Continue? (y or n)? ");
                    boolean continueInstall = Keyboard.readBoolean();
                    if (continueInstall) {
                        LOGGER.info("Setting identity provider domain = [{}]", identityProviderCustomDomain);
                        break;
                    }
                } else {
                    outputMessage("Invalid domain name, please try again.");
                }
            }

            List<HostedZone> hostedZones = existingHostedZones(route53, identityProviderCustomDomain);
            if (hostedZones == null || hostedZones.isEmpty()) {
                System.out.println("Error. No Route53 Hosted Zone for the domain " + identityProviderCustomDomain);
                cancel();
            }
            while (true) {
                System.out.println("Select the Route53 Hosted Zone for the domain "
                        + identityProviderCustomDomain + ":");
                for (ListIterator<HostedZone> iter = hostedZones.listIterator(); iter.hasNext(); ) {
                    int index = iter.nextIndex();
                    HostedZone hostedZone = iter.next();
                    System.out.printf("%d. (%s) %s%n",
                            (index + 1),
                            hostedZone.id().replace("/hostedzone/", ""),
                            hostedZone.name()
                    );
                }
                System.out.print("Type the number of the hosted zone to use and press enter: ");
                Integer choice = Keyboard.readInt();
                try {
                    HostedZone zone = hostedZones.get((choice - 1));
                    // Hosted zone id will be prefixed with /hostedzone/
                    identityProviderHostedZone = zone.id().replace("/hostedzone/", "");
                    LOGGER.info("Setting identity provider domain hosted zone = [{}]", identityProviderHostedZone);
                    break;
                } catch (NullPointerException | IndexOutOfBoundsException e) {
                    outputMessage("Invalid choice, please try again. Enter the number of the hosted zone to use.");
                }
            }

            List<CertificateSummary> certificates = existingCertificates(acm, identityProviderCustomDomain);
            if (certificates == null || certificates.isEmpty()) {
                System.out.println("Error. Unable to find an ACM certificate for the domain "
                        + identityProviderCustomDomain + ". Create or import a public certificate in this Region.");
                cancel();
            } else {
                while (true) {
                    System.out.println("Select the ACM Certificate for the domain "
                            + identityProviderCustomDomain + ":");
                    for (ListIterator<CertificateSummary> iter = certificates.listIterator(); iter.hasNext(); ) {
                        int index = iter.nextIndex();
                        CertificateSummary certificate = iter.next();
                        System.out.printf("%d. %s%n",
                                (index + 1),
                                certificate.domainName()
                        );
                    }
                    System.out.print("Type the number of the certificate to use and press enter: ");
                    Integer choice = Keyboard.readInt();
                    try {
                        identityProviderCertificate = certificates.get((choice - 1)).certificateArn();
                        LOGGER.info("Setting identity provider certificate = [{}]", identityProviderCertificate);
                        break;
                    } catch (NullPointerException | IndexOutOfBoundsException e) {
                        outputMessage("Invalid choice, please try again. Enter the number of the certificate to use.");
                    }
                }
            }
        }

        String auth0ApiKey = "";
        String auth0ApiClientId = "";
        if ("AUTH0".equals(systemIdentityProvider)) {

        }

        Boolean useCustomDomainForAdminWebApp = Utils.isChinaRegion(AWS_REGION);
        if (!useCustomDomainForAdminWebApp) {
            System.out.print("Would you like to use a custom domain name for the SaaS Boost admin web console (y or n) Press Enter for 'n'? ");
            useCustomDomainForAdminWebApp = Keyboard.readBoolean();
            if (useCustomDomainForAdminWebApp == null) {
                useCustomDomainForAdminWebApp = Boolean.FALSE;
            }
        }
        String adminWebAppCustomDomain = null;
        String adminWebAppHostedZone = null;
        String adminWebAppCertificate = null;
        if (useCustomDomainForAdminWebApp) {
            if (!Utils.isChinaRegion(AWS_REGION)) {
                System.out.println("You must provide a verified TLS (SSL) certificate from ACM in the us-east-1 "
                        + " region to use a custom domain name for the SaaS Boost admin web console.");
            } else {
                System.out.println("You must provide a verified TLS (SSL) certificate from the IAM certificate store "
                        + " to use a custom domain name for the SaaS Boost admin web console.");
                System.out.println("You must also have an ICP registration for the domain. See https://www.amazonaws.cn/en/about-aws/china/#ICP_in_China for more information.");
            }
            // TODO Does this work the same way in AWS China?
            System.out.println("You must have an existing Route53 Hosted Zone for the domain name, and DNS resolution must work.");
            while (true) {
                System.out.print("Enter the domain name for the SaaS Boost admin web console (e.g. saas-boost.example.com): ");
                adminWebAppCustomDomain = Keyboard.readString();
                if (validateDomainName(adminWebAppCustomDomain)) {
                    System.out.print("Using " + adminWebAppCustomDomain + " for the SaaS Boost admin web console. Continue? (y or n)? ");
                    boolean continueInstall = Keyboard.readBoolean();
                    if (continueInstall) {
                        LOGGER.info("Setting admin web console domain = [{}]", adminWebAppCustomDomain);
                        break;
                    }
                } else {
                    outputMessage("Invalid domain name, please try again.");
                }
            }

            List<HostedZone> hostedZones = existingHostedZones(route53, adminWebAppCustomDomain);
            if (hostedZones == null || hostedZones.isEmpty()) {
                System.out.println("Error. No Route53 Hosted Zone for the domain " + adminWebAppCustomDomain);
                cancel();
            }
            while (true) {
                System.out.println("Select the Route53 Hosted Zone for the domain "
                        + adminWebAppCustomDomain + ":");
                for (ListIterator<HostedZone> iter = hostedZones.listIterator(); iter.hasNext(); ) {
                    int index = iter.nextIndex();
                    HostedZone hostedZone = iter.next();
                    System.out.printf("%d. (%s) %s%n",
                            (index + 1),
                            hostedZone.id().replace("/hostedzone/", ""),
                            hostedZone.name()
                    );
                }
                System.out.print("Type the number of the hosted zone to use and press enter: ");
                Integer choice = Keyboard.readInt();
                try {
                    HostedZone zone = hostedZones.get((choice - 1));
                    // Hosted zone id will be prefixed with /hostedzone/
                    adminWebAppHostedZone = zone.id().replace("/hostedzone/", "");
                    LOGGER.info("Setting identity provider domain hosted zone = [{}]", adminWebAppHostedZone);
                    break;
                } catch (NullPointerException | IndexOutOfBoundsException e) {
                    outputMessage("Invalid choice, please try again. Enter the number of the hosted zone to use.");
                }
            }

            if (!Utils.isChinaRegion(AWS_REGION)) {
                // CloudFront distributions can only be associated with ACM certificates in us-east-1
                List<CertificateSummary> certificates = existingCertificates(
                        AcmClient.builder().region(Region.US_EAST_1).build(), adminWebAppCustomDomain);
                if (certificates == null || certificates.isEmpty()) {
                    System.out.println("Error. Unable to find an ACM certificate in us-east-1 for the domain "
                            + adminWebAppCustomDomain + ". Create or import a public certificate in us-east-1.");
                    cancel();
                } else {
                    while (true) {
                        System.out.println("Select the ACM Certificate for the domain "
                                + adminWebAppCustomDomain + ":");
                        for (ListIterator<CertificateSummary> iter = certificates.listIterator(); iter.hasNext(); ) {
                            int index = iter.nextIndex();
                            CertificateSummary certificate = iter.next();
                            System.out.printf("%d. %s%n",
                                    (index + 1),
                                    certificate.domainName()
                            );
                        }
                        System.out.print("Type the number of the certificate to use and press enter: ");
                        Integer choice = Keyboard.readInt();
                        try {
                            adminWebAppCertificate = certificates.get((choice - 1)).certificateArn();
                            LOGGER.info("Setting admin web app certificate = [{}]", adminWebAppCertificate);
                            break;
                        } catch (NullPointerException | IndexOutOfBoundsException e) {
                            System.out.println("Invalid choice, please try again. Enter the number of the certificate to use.");
                        }
                    }
                }
            } else {
                // In the AWS China regions, CloudFront can only be associated with certificates in IAM
                List<ServerCertificateMetadata> serverCertificates = new ArrayList<>();
                ListServerCertificatesResponse serverCertificateResponse;
                String marker = null;
                do {
                    serverCertificateResponse = iam.listServerCertificates(ListServerCertificatesRequest.builder()
                            .maxItems(100)
                            .marker(marker)
                            .build()
                    );
                    marker = serverCertificateResponse.marker();
                    if (serverCertificateResponse.hasServerCertificateMetadataList()) {
                        serverCertificates.addAll(serverCertificateResponse.serverCertificateMetadataList());
                    }
                } while (serverCertificateResponse.isTruncated());
                if (serverCertificates.isEmpty()) {
                    System.out.println("Error. Unable to find IAM server certificates. Import a 3rd party certificate "
                            + "for the domain " + adminWebAppCustomDomain + " to IAM.");
                    cancel();
                } else {
                    while (true) {
                        System.out.println("Select the IAM Server Certificate for the domain "
                                + adminWebAppCertificate + ":");
                        for (ListIterator<ServerCertificateMetadata> iter = serverCertificates.listIterator();
                                iter.hasNext(); ) {
                            int index = iter.nextIndex();
                            ServerCertificateMetadata certificate = iter.next();
                            System.out.printf("%d. %s%n",
                                    (index + 1),
                                    certificate.serverCertificateName()
                            );
                        }
                        System.out.print("Type the number of the certificate to use and press enter: ");
                        Integer choice = Keyboard.readInt();
                        try {
                            adminWebAppCertificate = serverCertificates.get((choice - 1)).serverCertificateId();
                            LOGGER.info("Setting admin web app certificate = [{}]", adminWebAppCertificate);
                            break;
                        } catch (NullPointerException | IndexOutOfBoundsException e) {
                            System.out.println("Invalid choice, please try again. Enter the number of the certificate to use.");
                        }
                    }
                }
            }
        }

        String appPlaneAccountId;
        while (true) {
            System.out.print("Enter the AWS Account ID when you will install the application plane components for this SaaS Boost control plane: ");
            appPlaneAccountId = Objects.toString(Keyboard.readString(), "").replace("-", "");
            if (validateAwsAccountId(appPlaneAccountId)) {
                LOGGER.info("Setting app plane account = [{}]", appPlaneAccountId);
                break;
            } else {
                outputMessage("Entered value is invalid. Enter a 12 digit AWS Account ID. Please try again.");
            }
        }

        System.out.println();
        outputMessage("===========================================================");
        outputMessage("");
        outputMessage("Would you like to continue the installation with the following options?");
        outputMessage("AWS Account: " + this.accountId);
        outputMessage("AWS Region: " + AWS_REGION.toString());
        outputMessage("AWS SaaS Boost Environment Name: " + this.envName);
        outputMessage("Admin Email Address: " + emailAddress);
        outputMessage("System Identity Provider: " + systemIdentityProvider);
        outputMessage("Custom Domain for System Identity Provider: "
                + (isNotBlank(identityProviderCustomDomain) ? identityProviderCustomDomain : "N/A"));
        outputMessage("Custom Domain for SaaS Boost Admin Web Console: "
                + (isNotBlank(adminWebAppCustomDomain) ? adminWebAppCustomDomain : "N/A"));
        outputMessage("Application Plane Account ID: " + appPlaneAccountId);

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
            saasBoostArtifactsBucket = SaaSBoostArtifactsBucket.createS3ArtifactBucket(s3, envName, AWS_REGION
                    , appPlaneAccountId);
            outputMessage("Created S3 artifacts bucket: " + saasBoostArtifactsBucket);

            // Copy the CloudFormation templates
            outputMessage("Uploading CloudFormation templates to S3 artifacts bucket");
            copyResourcesToS3();

            // Compile all the source code
            outputMessage("Compiling Lambda functions and uploading to S3 artifacts bucket. This will take some time...");
            processLambdas();
        } else {
            outputMessage("Reusing existing artifacts bucket " + existingBucket);
            saasBoostArtifactsBucket = new SaaSBoostArtifactsBucket(existingBucket, AWS_REGION, appPlaneAccountId);
            try {
                s3.headBucket(request -> request.bucket(saasBoostArtifactsBucket.getBucketName()));
            } catch (SdkServiceException s3error) {
                outputMessage("Bucket " + existingBucket + " does not exist!");
                throw s3error;
            }
            outputMessage("Uploading CloudFormation templates to S3 artifacts bucket");
            copyResourcesToS3();
        }

        // Copy the source files up to S3 where CloudFormation resources expect them to be
        outputMessage("Uploading admin web app source files to S3");
        copyAdminWebAppSourceToS3(workingDir, saasBoostArtifactsBucket.getBucketName(), s3);

        // Copy the Api docs (SwaggerUI) source files up to S3 where CloudFormation resources expect them to be
        outputMessage("Uploading api docs source files to S3");
        copyApiDocsSourceToS3(workingDir, saasBoostArtifactsBucket.getBucketName(), s3);

        // Run CloudFormation create stack
        outputMessage("Running CloudFormation");
        this.stackName = "sb-" + envName;
        createSaaSBoostStack(stackName, emailAddress, systemIdentityProvider, identityProviderCustomDomain,
                identityProviderHostedZone, identityProviderCertificate, adminWebAppCustomDomain,
                adminWebAppHostedZone, adminWebAppCertificate, appPlaneAccountId);

        this.environment = ExistingEnvironmentFactory.findExistingEnvironment(
                ssm, cfn, this.envName, this.accountId);
        this.baseStackDetails = environment.getBaseCloudFormationStackInfo();

        outputMessage("Check the admin email inbox for the temporary password.");
        outputMessage("AWS SaaS Boost Artifacts Bucket: " + saasBoostArtifactsBucket);
        outputMessage("AWS SaaS Boost Console URL is: " + baseStackDetails.get("AdminWebUrl"));

        outputMessage("Log into the Application Plane AWS Account and run the CloudFormation Integration Stack:");
        outputMessage(quickCreateLink());
    }

    protected void delete() {
        // Confirm delete
        outputMessage("****** W A R N I N G");
        outputMessage("Deleting the AWS SaaS Boost environment is IRREVERSIBLE and "
                + "ALL deployed tenant resources will be deleted!");
        while (true) {
            System.out.print("Enter the SaaS Boost environment name to confirm: ");
            String confirmEnvName = Keyboard.readString();
            if (isNotBlank(confirmEnvName) && this.envName.equalsIgnoreCase(confirmEnvName)) {
                System.out.println("SaaS Boost environment " + this.envName + " for AWS Account " + this.accountId
                        + " in region " + AWS_REGION + " will be deleted. This action cannot be undone!");
                break;
            } else {
                outputMessage("Entered value is incorrect, please try again.");
            }
        }

        System.out.print("Are you sure you want to delete the SaaS Boost environment "
                + this.envName + "? Enter y to continue or n to cancel: ");
        boolean continueDelete = Keyboard.readBoolean();
        if (!continueDelete) {
            outputMessage("Canceled Delete of AWS SaaS Boost environment");
            System.exit(2);
        } else {
            outputMessage("Continuing Delete of AWS SaaS Boost environment " + this.envName);
        }

        // Delete all the provisioned tenants
        List<Map<String, Object>> tenants = getProvisionedTenants();
        LOGGER.debug("Deleting {} provisioned tenants", tenants.size());
        for (Map<String, Object> tenant : tenants) {
            outputMessage("Deleting AWS SaaS Boost tenant " + tenant.get("id"));
            deleteProvisionedTenant(tenant);
        }

        // Clear all the images from ECR or CloudFormation won't be able to delete the repository
        LOGGER.debug("Getting list of ECR repos to clear");
        for (String ecrRepo : getEcrRepositories()) {
            outputMessage("Deleting images from ECR repository " + ecrRepo);
            deleteEcrImages(ecrRepo);
        }

        // Clear all the Parameter Store entries for this environment that CloudFormation doesn't own
        LOGGER.debug("Deleting AppConfig");
        deleteApplicationConfig();

        // Delete the SaaS Boost stack
        outputMessage("Deleting AWS SaaS Boost stack: " + this.stackName);
        deleteCloudFormationStack(this.stackName);

        // Finally, remove the S3 artifacts bucket that this installer created outside of CloudFormation
        LOGGER.info("Clean up s3 bucket: " + saasBoostArtifactsBucket);
        cleanUpS3(s3, saasBoostArtifactsBucket.getBucketName(), null);
        s3.deleteBucket(r -> r.bucket(saasBoostArtifactsBucket.getBucketName()));

        // This installer also creates some Parameter Store entries outside of CloudFormation which are
        // needed to delete stacks via CloudFormation. delete these last.
        // TODO move these parameters to CloudFormation
        try {
            List<String> parameterNamesToDelete = ssm.describeParameters(request -> request
                    .parameterFilters(ParameterStringFilter.builder()
                    .key("Path")
                    .values("/saas-boost/" + this.envName + "/")
                    .build()
                )
            ).parameters().stream().map(ParameterMetadata::name).collect(Collectors.toList());
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
            LOGGER.error(Utils.getFullStackTrace(ssmError));
        }

        outputMessage("Delete of SaaS Boost environment " + this.envName + " complete.");
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

    protected SaaSBoostApiHelper api() {
        if (api == null) {
            SaaSBoostApiHelper.SaaSBoostApiHelperDependencyFactory init = () ->
                    Utils.sdkClient(SecretsManagerClient.builder(), SecretsManagerClient.SERVICE_NAME,
                            ApacheHttpClient.builder(), DefaultCredentialsProvider.create());
            String secretId = "/saas-boost/" + envName + "/PRIVATE_API_APP_CLIENT";
            api = new SaaSBoostApiHelper(init, secretId);
        }
        return api;
    }

    protected List<Map<String, Object>> getProvisionedTenants() {
        LOGGER.info("Calling tenant service to fetch all provisioned tenants");
        String getTenantsResponseBody = api().authorizedRequest("GET", "tenants?status=provisioned");
        List<Map<String, Object>> tenants = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
        if (tenants == null) {
            tenants = new ArrayList<>();
        }
        return tenants;
    }

    protected Map<String, Object> getTenant(String tenantId) {
        LOGGER.info("Calling tenant service to fetch tenant {}", tenantId);
        String getTenantResponseBody = api().authorizedRequest("GET", "tenants/" + tenantId);
        Map<String, Object> tenant = Utils.fromJson(getTenantResponseBody, HashMap.class);
        if (tenant == null) {
            return Collections.emptyMap();
        }
        return tenant;
    }

    protected Map<String, Object> getAppConfig() {
        LOGGER.info("Calling appConfig service to fetch appConfig");
        String getAppConfigResponseBody = api().authorizedRequest("GET", "appconfig");
        Map<String, Object> appConfig = Utils.fromJson(getAppConfigResponseBody, HashMap.class);
        if (appConfig == null) {
            return Collections.emptyMap();
        }
        return appConfig;
    }

    protected void deleteApplicationConfig() {
        LOGGER.info("Calling appConfig service to delete appConfig");
        try {
            api().authorizedRequest("DELETE", "appconfig");
        } catch (Exception apiError) {
            LOGGER.error(apiError.getMessage());
        }
    }

    protected void deleteProvisionedTenant(Map<String, Object> tenant) {
        // TODO we can parallelize to improve performance with lots of tenants
        LOGGER.info("Calling tenant service to delete tenant {}", tenant.get("id"));
        try {
            String tenantId = (String) tenant.get("id");
            api().authorizedRequest("DELETE", "tenants/" + tenant.get("id"));
            // wait for tenant to reach deleted
            final String DELETED = "deleted";
            LocalDateTime timeout = LocalDateTime.now().plus(60, ChronoUnit.MINUTES);
            String tenantStatus = (String) getTenant(tenantId).get("onboardingStatus");
            boolean deleted = tenantStatus.equalsIgnoreCase(DELETED);
            while (!deleted) {
                if (LocalDateTime.now().compareTo(timeout) > 0) {
                    // we've timed out retrying
                    outputMessage("Timed out waiting for tenant " + tenantId + " to reach deleted state. "
                            + "Please check CloudFormation in your AWS Console for more details.");
                    // if a tenant delete fails, trying to delete the rest of the stack is guaranteed to fail
                    // due to Tenant resources having cross-dependencies with other resources. stop here to let
                    // the user figure out what went wrong
                    throw new RuntimeException("Delete failed.");
                }
                outputMessage("Waiting 1 minute for tenant " + tenantId
                        + " to reach deleted from " + tenantStatus);
                Thread.sleep(60 * 1000L); // 1 minute
                tenantStatus = (String) getTenant(tenantId).get("onboardingStatus");
                deleted = tenantStatus.equalsIgnoreCase(DELETED);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        }
    }

    protected List<String> getEcrRepositories() {
        List<String> repos = new ArrayList<>();
        Map<String, Object> appConfig = getAppConfig();
        Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
        for (String serviceName : services.keySet()) {
            Map<String, Object> service = (Map<String, Object>) services.get(serviceName);
            Map<String, Object> compute = (Map<String, Object>) service.get("compute");
            repos.add((String) compute.get("containerRepo"));
        }
        return repos;
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
                    imagesToDelete.addAll(response.imageIds());
                }
                token = response.nextToken();
            } catch (SdkServiceException ecrError) {
                LOGGER.error("ecr:ListImages error", ecrError);
                LOGGER.error(Utils.getFullStackTrace(ecrError));
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
                LOGGER.error(Utils.getFullStackTrace(ecrError));
                throw ecrError;
            }
        }
    }

    // TODO Technically these may only be necessary now if we're installing Keycloak
    // Create Service Roles necessary for Tenant Stack Deployment
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
                LOGGER.error(Utils.getFullStackTrace(iamError));
                throw iamError;
            }
        }
    }

    protected void copyResourcesToS3() {
        final Path resourcesDir = workingDir.resolve(Path.of("resources"));
        try (Stream<Path> stream = Files.list(resourcesDir)) {
            Set<Path> cloudFormationTemplates = stream
                    .filter(file -> Files.isRegularFile(file)
                            && file.getFileName().toString().endsWith(".yaml"))
                    .collect(Collectors.toSet());
            outputMessage("Uploading " + cloudFormationTemplates.size() + " CloudFormation resources to S3");
            for (Path cloudFormationTemplate : cloudFormationTemplates) {
                LOGGER.info("Uploading CloudFormation template to S3 " + cloudFormationTemplate.toString() + " -> "
                        + cloudFormationTemplate.getFileName().toString());
                // TODO validate template for syntax errors before continuing with installation
                saasBoostArtifactsBucket.putFile(s3, cloudFormationTemplate, cloudFormationTemplate.getFileName());
            }
        } catch (IOException ioe) {
            LOGGER.error("Error listing resources directory", ioe);
            LOGGER.error(Utils.getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
        try (Stream<Path> stream = Files.walk(resourcesDir.resolve("keycloak"))) {
            Set<Path> keycloakResources = stream.filter(file -> Files.isRegularFile(file)).collect(Collectors.toSet());
            for (Path keycloakResource : keycloakResources) {
                Path remotePath = resourcesDir.relativize(keycloakResource);
                LOGGER.info("Uploading Keycloak resource to S3 " + keycloakResource.toString() + " -> " + remotePath);
                saasBoostArtifactsBucket.putFile(s3, keycloakResource, remotePath);
            }
        } catch (IOException ioe) {
            LOGGER.error("Error walking keycloak directory", ioe);
            LOGGER.error(Utils.getFullStackTrace(ioe));
            // TODO while this is an invalid state, maybe we only want to fail out if
            //      KEYCLOAK actually needs to be installed for this environment
            throw new RuntimeException(ioe);
        }
    }

    protected static void printResults(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.info(line);
            }
        } catch (IOException ioe) {
            LOGGER.error("Error reading from runtime exec process", ioe);
            LOGGER.error(Utils.getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
    }

    public static void outputMessage(String msg) {
        LOGGER.info(msg);
        System.out.println(msg);
    }

    protected static void checkEnvironment() {
        outputMessage("Checking maven and AWS CLI...");
        try {
            executeCommand("mvn -version", null, null);
        } catch (Exception e) {
            outputMessage("Could not execute 'mvn -version', please check your environment");
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
        outputMessage("Environment Checks for maven and AWS CLI PASSED.");
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
    }

    protected String getExistingSaaSBoostEnvironment() {
        LOGGER.info("Asking for existing SaaS Boost environment label");
        String environment = null;
        while (isBlank(environment)) {
            System.out.print("Please enter the existing SaaS Boost environment label: ");
            environment = Keyboard.readString();
            if (!validateEnvironmentName(environment)) {
                outputMessage("Entered value is invalid, maximum of 10 alphanumeric characters, and cannot be AWS,"
                        + " Amazon, or Cognito. Please try again.");
                environment = null;
            }
        }
        try {
            String envParamsExist = "/saas-boost/" + environment + "/STACK_NAME";
            ssm.getParameter(request -> request.name(envParamsExist));
        } catch (ParameterNotFoundException ssmError) {
            outputMessage("Cannot find existing SaaS Boost environment " + environment
                    + " in this AWS account and region.");
            System.exit(2);
        }
        return environment;
    }

    protected static boolean validateEmail(String email) {
        boolean valid = false;
        if (email != null) {
            valid = email.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
        }
        return valid;
    }

    protected static boolean validateEnvironmentName(String envName) {
        boolean valid = false;
        if (envName != null) {
            // Follows CloudFormation stack name rules but limits to 10 characters
            valid = envName.matches("^[a-zA-Z](?:[a-zA-Z0-9-]){0,9}$");
            if (valid) {
                valid = (!envName.equalsIgnoreCase("aws")
                        && !envName.equalsIgnoreCase("amazon")
                        && !envName.equalsIgnoreCase("cognito"));
            }
        }
        return valid;
    }

    protected static boolean validateDomainName(String domain) {
        boolean valid = false;
        if (domain != null) {
            valid = domain.matches("^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$");
        }
        return valid;
    }

    protected static boolean validateAwsAccountId(String accountId) {
        boolean valid = false;
        if (accountId != null) {
            valid = accountId.replace("-", "").matches("^[0-9]{12}$");
        }
        return valid;
    }

    protected static List<HostedZone> existingHostedZones(Route53Client route53, String domain) {
        List<HostedZone> hostedZones = new ArrayList<>();
        String nextDnsName = null;
        String nextHostedZone = null;
        ListHostedZonesByNameResponse response;
        do {
            response = route53.listHostedZonesByName(ListHostedZonesByNameRequest.builder()
                    .dnsName(nextDnsName)
                    .hostedZoneId(nextHostedZone)
                    .maxItems("100")
                    .build()
            );
            nextDnsName = response.nextDNSName();
            nextHostedZone = response.nextHostedZoneId();
            if (response.hasHostedZones()) {
                LOGGER.info("Found {} existing hosted zones", response.hostedZones().size());
                for (HostedZone hostedZone : response.hostedZones()) {
                    // The full domain name should be longer than or equal to the name
                    // of the hosted zone
                    String hostedZoneDomain = hostedZone.name();
                    if (hostedZoneDomain.endsWith(".")) {
                        hostedZoneDomain = hostedZoneDomain.substring(0, (hostedZoneDomain.length() - 1));
                    }
                    if (domain.contains(hostedZoneDomain)
                            && hostedZone.config() != null && Boolean.FALSE.equals(hostedZone.config().privateZone())) {
                        hostedZones.add(hostedZone);
                    } else {
                        LOGGER.info("Ignoring hosted zone with name {}", hostedZone.name());
                    }
                }
            }
        } while (response.isTruncated());
        // If there are multiple hosted zones for a given domain name, we will sort them
        // by "CallerReference" which appears to be a timestamp.
        Collections.sort(hostedZones, Comparator.comparing(HostedZone::callerReference));
        return hostedZones;
    }

    protected static List<CertificateSummary> existingCertificates(AcmClient acm, String domain) {
        List<CertificateSummary> certificateSummaries = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                // only list certificates that aren't expired, invalid, revoked, or otherwise unusable
                ListCertificatesResponse response = acm.listCertificates(ListCertificatesRequest.builder()
                        .certificateStatuses(List.of(CertificateStatus.PENDING_VALIDATION, CertificateStatus.ISSUED))
                        .nextToken(nextToken)
                        .build());
                if (response.hasCertificateSummaryList()) {
                    certificateSummaries.addAll(
                            response.certificateSummaryList().stream()
                                    // This works because the certificate domain will be equal to or shorter
                                    // than the full domain name
                                    .filter(cert -> {
                                        String certDomain = cert.domainName();
                                        if (certDomain.startsWith("*.")) {
                                            certDomain = certDomain.substring(2);
                                        }
                                        return domain.contains(certDomain);
                                    })
                                    .collect(Collectors.toList())
                    );
                }
                nextToken = response.nextToken();
            } catch (InvalidArgsException iae) {
                LOGGER.error("Error retrieving certificates", iae);
            }
        } while (nextToken != null);
        return certificateSummaries;
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
            // TODO make this a list of everything in the layers folder that's not utils
            sourceDirectories.add(workingDir.resolve(Path.of("layers", "apigw-helper")));
            sourceDirectories.add(workingDir.resolve(Path.of("layers", "cloudformation-utils")));
            sourceDirectories.add(workingDir.resolve(Path.of("layers", "keycloak-helper")));
            sourceDirectories.add(workingDir.resolve(Path.of("layers", "saas-boost-api-client-helper")));

            DirectoryStream<Path> functions = Files.newDirectoryStream(workingDir.resolve(Path.of("functions")), Files::isDirectory);
            functions.forEach(sourceDirectories::add);

            DirectoryStream<Path> customResources = Files.newDirectoryStream(workingDir.resolve(Path.of("resources", "custom-resources")), Files::isDirectory);
            customResources.forEach(sourceDirectories::add);

            DirectoryStream<Path> services = Files.newDirectoryStream(workingDir.resolve(Path.of("services")), Files::isDirectory);
            services.forEach(sourceDirectories::add);

            final PathMatcher filter = FileSystems.getDefault().getPathMatcher("glob:**.zip");
            outputMessage("Uploading " + sourceDirectories.size() + " Lambda functions to S3");
            for (ListIterator<Path> iter = sourceDirectories.listIterator(); iter.hasNext();) {
                int progress = iter.nextIndex();
                Path sourceDirectory = iter.next();
                if (sourceDirectory.endsWith("saas-boost-api-client-helper")) {
                    executeCommand("sh build.sh", null, sourceDirectory.toFile());
                    Path zipFile = sourceDirectory.resolve("build/SaaSBoostApiClientHelper-lambda.zip");
                    LOGGER.info("Uploading Lambda source package to S3 " + zipFile.toString() + " -> " + this.lambdaSourceFolder + "/" + zipFile.getFileName().toString());
                    System.out.printf("%2d. %s%n", (progress + 1), zipFile.getFileName().toString());
                    saasBoostArtifactsBucket.putFile(s3, zipFile,
                            Path.of(this.lambdaSourceFolder, zipFile.getFileName().toString()));
                } else if (Files.exists(sourceDirectory.resolve("pom.xml"))) {
                    executeCommand("mvn", null, sourceDirectory.toFile());
                    final Path targetDir = sourceDirectory.resolve("target");
                    try (Stream<Path> stream = Files.list(targetDir)) {
                        Set<Path> lambdaSourcePackage = stream
                                .filter(filter::matches)
                                .collect(Collectors.toSet());
                        for (Path zipFile : lambdaSourcePackage) {
                            LOGGER.info("Uploading Lambda source package to S3 " + zipFile.toString() + " -> " + this.lambdaSourceFolder + "/" + zipFile.getFileName().toString());
                            System.out.printf("%2d. %s%n", (progress + 1), zipFile.getFileName().toString());
                            saasBoostArtifactsBucket.putFile(s3, zipFile,
                                    Path.of(this.lambdaSourceFolder, zipFile.getFileName().toString()));
                        }
                    }
                } else {
                    LOGGER.warn("No POM file found in {}", sourceDirectory);
                }
            }
        } catch (IOException ioe) {
            LOGGER.error("Error processing Lambda source folders", ioe);
            LOGGER.error(Utils.getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
    }

    protected void createSaaSBoostStack(final String stackName, String adminEmail, String systemIdentityProvider,
                                        String identityProviderCustomDomain, String identityProviderHostedZone,
                                        String identityProviderCertificate, String adminWebAppCustomDomain,
                                        String adminWebAppHostedZone, String adminWebAppCertificate, String appPlaneAccount) {
        // Note - most params the default is used from the CloudFormation stack
        List<Parameter> templateParameters = new ArrayList<>();
        templateParameters.add(Parameter.builder().parameterKey("Environment").parameterValue(envName).build());
        templateParameters.add(Parameter.builder().parameterKey("AdminEmailAddress").parameterValue(adminEmail).build());
        templateParameters.add(Parameter.builder().parameterKey("SaaSBoostBucket").parameterValue(saasBoostArtifactsBucket.getBucketName()).build());
        templateParameters.add(Parameter.builder().parameterKey("Version").parameterValue(VERSION).build());
        templateParameters.add(Parameter.builder().parameterKey("SystemIdentityProvider").parameterValue(systemIdentityProvider).build());
        templateParameters.add(Parameter.builder().parameterKey("SystemIdentityProviderDomain").parameterValue(Objects.toString(identityProviderCustomDomain, "")).build());
        templateParameters.add(Parameter.builder().parameterKey("SystemIdentityProviderHostedZone").parameterValue(Objects.toString(identityProviderHostedZone, "")).build());
        templateParameters.add(Parameter.builder().parameterKey("SystemIdentityProviderCertificate").parameterValue(Objects.toString(identityProviderCertificate, "")).build());
        templateParameters.add(Parameter.builder().parameterKey("AdminWebAppDomain").parameterValue(Objects.toString(adminWebAppCustomDomain, "")).build());
        templateParameters.add(Parameter.builder().parameterKey("AdminWebAppHostedZone").parameterValue(Objects.toString(adminWebAppHostedZone, "")).build());
        templateParameters.add(Parameter.builder().parameterKey("AdminWebAppCertificate").parameterValue(Objects.toString(adminWebAppCertificate, "")).build());
        //templateParameters.add(Parameter.builder().parameterKey("ApiDomain").parameterValue(Objects.toString(apiCustomDomaine, "")).build());
        //templateParameters.add(Parameter.builder().parameterKey("ApiHostedZone").parameterValue(Objects.toString(apiHostedZone, "")).build());
        //templateParameters.add(Parameter.builder().parameterKey("ApiCertificate").parameterValue(Objects.toString(apiCertificate, "")).build());
        //templateParameters.add(Parameter.builder().parameterKey("CreateMacroResources").parameterValue(Boolean.toString(!doesCfnMacroResourceExist())).build());
        templateParameters.add(Parameter.builder().parameterKey("AppPlaneAccountId").parameterValue(appPlaneAccount).build());

        LOGGER.info("createSaaSBoostStack::create stack " + stackName);
        String stackId = null;
        try {
            CreateStackResponse cfnResponse = cfn.createStack(CreateStackRequest.builder()
                    .stackName(stackName)
                    .disableRollback(true)
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
            LOGGER.error(Utils.getFullStackTrace(cfnError));
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
            LOGGER.error(Utils.getFullStackTrace(cfnError));
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
                        LOGGER.error(Utils.getFullStackTrace(cfnError));
                        throw cfnError;
                    }
                }
            }
        } catch (SdkServiceException cfnError) {
            LOGGER.error("cloudformation:DeleteStack error", cfnError);
            LOGGER.error(Utils.getFullStackTrace(cfnError));
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
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }
        }
        return exists;
    }

    public static void copyAdminWebAppSourceToS3(Path workingDir, String artifactsBucket, S3Client s3) {
        Path webDir = workingDir.resolve(Path.of("client", "web"));
        if (!Files.isDirectory(webDir)) {
            outputMessage("Error, can't find client/web directory at " + webDir.toAbsolutePath().toString());
            System.exit(2);
        }

        // Sync files to the web bucket
        outputMessage("Synchronizing AWS SaaS Boost web application files to s3");
        List<Path> filesToUpload;
        try (Stream<Path> stream = Files.walk(webDir)) {
            filesToUpload = stream
                    .filter(file ->
                            Files.isRegularFile(file) && (
                            file.startsWith("client/web/package.json")
                            || file.startsWith("client/web/yarn.lock")
                            || file.startsWith("client/web/.npmrc")
                            || file.startsWith("client/web/public")
                            || file.startsWith("client/web/src"))
                    )
                    .collect(Collectors.toList());
            outputMessage("Uploading " + filesToUpload.size() + " files to S3");

            // Create a ZIP archive of the source files so we only call s3 put object once
            // and so we can trigger the CodeBuild project off of that single s3 event
            // (instead of triggering CodeBuild 180+ times -- once for each file put to s3).
            try {
                ByteArrayOutputStream src = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(src);
                for (Path fileToUpload : filesToUpload) {
                    // java.nio.file.Path will use OS dependent file separators
                    String fileName = fileToUpload.toFile().toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(fileName);
                    zip.putNextEntry(entry);
                    zip.write(Files.readAllBytes(fileToUpload)); // all of our files are very small
                    zip.closeEntry();
                }
                zip.close();
                try {
                    // Now copy the admin web app source files up to the artifacts bucket
                    // This will trigger a CodeBuild project to build and deploy the app
                    // if done after the initial install of SaaS Boost
                    s3.putObject(PutObjectRequest.builder()
                            .bucket(artifactsBucket)
                            .key("client/web/src.zip")
                            .build(), RequestBody.fromBytes(src.toByteArray())
                    );
                } catch (SdkServiceException s3Error) {
                    LOGGER.error("s3:PutObject error", s3Error);
                    LOGGER.error(Utils.getFullStackTrace(s3Error));
                    throw s3Error;
                }
            } catch (IOException ioe) {
                LOGGER.error("ZIP archive generation failed");
                throw new RuntimeException(Utils.getFullStackTrace(ioe));
            }
        } catch (IOException ioe) {
            LOGGER.error("Error walking client/web directory", ioe);
            LOGGER.error(Utils.getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
    }

    public static void copyApiDocsSourceToS3(Path workingDir, String artifactsBucket, S3Client s3) {
        Path webDir = workingDir.resolve(Path.of("resources", "api-docs"));
        if (!Files.isDirectory(webDir)) {
            outputMessage("Error, can't find resources/api-docs directory at " + webDir.toAbsolutePath().toString());
            System.exit(2);
        }

        // Sync files to the web bucket
        outputMessage("Synchronizing AWS SaaS Boost API Docs (Swagger) files to s3");
        List<Path> filesToUpload;
        try (Stream<Path> stream = Files.walk(webDir)) {
            filesToUpload = stream
                    .filter(file ->
                            Files.isRegularFile(file) && (
                                    file.startsWith("resources/api-docs/app.js")
                                            || file.startsWith("resources/api-docs/package.json")
                                            || file.startsWith("resources/api-docs/package-lock.json")
                                            || file.startsWith("resources/api-docs/buildspec_no_post_build.yaml")
                                            || file.startsWith("resources/api-docs/update.sh"))
                    )
                    .collect(Collectors.toList());
            outputMessage("Uploading " + filesToUpload.size() + " files to S3");

            // Create a ZIP archive of the source files so we only call s3 put object once
            // and so we can trigger the CodeBuild project off of that single s3 event
            // (instead of triggering CodeBuild 180+ times -- once for each file put to s3).
            try {
                ByteArrayOutputStream src = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(src);
                for (Path fileToUpload : filesToUpload) {
                    // java.nio.file.Path will use OS dependent file separators
                    String fileName = fileToUpload.toFile().toString().replace('\\', '/');
                    ZipEntry entry = new ZipEntry(fileName);
                    zip.putNextEntry(entry);
                    zip.write(Files.readAllBytes(fileToUpload)); // all of our files are very small
                    zip.closeEntry();
                }
                zip.close();
                try {
                    // Now copy the Swagger source files up to the artifacts bucket
                    // This will trigger a CodeBuild project to build and deploy the app
                    // if done after the initial install of SaaS Boost
                    s3.putObject(PutObjectRequest.builder()
                            .bucket(artifactsBucket)
                            .key("api-docs/src.zip")
                            .build(), RequestBody.fromBytes(src.toByteArray())
                    );
                    // Copy the swagger definition file up to the artifacts bucket separately
                    // because we use it as an event source to trigger future builds of the
                    // api docs site
                    s3.putObject(PutObjectRequest.builder()
                            .bucket(artifactsBucket)
                            .key("api-docs/swagger.json")
                            .build(), workingDir.resolve(Path.of("resources", "api-docs", "swagger.json")));
                } catch (SdkServiceException s3Error) {
                    LOGGER.error("s3:PutObject error", s3Error);
                    LOGGER.error(Utils.getFullStackTrace(s3Error));
                    throw s3Error;
                }
            } catch (IOException ioe) {
                LOGGER.error("ZIP archive generation failed");
                throw new RuntimeException(Utils.getFullStackTrace(ioe));
            }
        } catch (IOException ioe) {
            LOGGER.error("Error walking resources/api-docs directory", ioe);
            LOGGER.error(Utils.getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        }
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
            LOGGER.error(Utils.getFullStackTrace(e));
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

    protected static void cleanUpS3(S3Client s3, String bucket, String prefix) {
        // The list of objects in the bucket to delete
        List<ObjectIdentifier> toDelete = new ArrayList<>();
        if (isNotEmpty(prefix) && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        GetBucketVersioningResponse versioningResponse = s3.getBucketVersioning(request -> request.bucket(bucket));
        if (BucketVersioningStatus.ENABLED == versioningResponse.status()
                || BucketVersioningStatus.SUSPENDED == versioningResponse.status()) {
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
                listObjectResponse.deleteMarkers()
                        .stream()
                        .map(marker ->
                                ObjectIdentifier.builder()
                                        .key(marker.key())
                                        .versionId(marker.versionId())
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

    public static boolean isWindows() {
        return (OS.contains("win"));
    }

    public static boolean isMac() {
        return (OS.contains("mac"));
    }

    protected String quickCreateLink() {
        StringBuilder quickCreateLink = new StringBuilder();
        quickCreateLink.append("https://");
        quickCreateLink.append(AWS_REGION);
        quickCreateLink.append(".console.aws.amazon.com/cloudformation/home?region=");
        quickCreateLink.append(AWS_REGION);
        quickCreateLink.append("#/stacks/create/review?");
        quickCreateLink.append("templateURL=");
        quickCreateLink.append(saasBoostArtifactsBucket.getBucketUrl());
        quickCreateLink.append("saas-boost-app-integration.yaml");
        quickCreateLink.append("&stackName=");
        quickCreateLink.append("sb-");
        quickCreateLink.append(envName);
        quickCreateLink.append("-integration");
        quickCreateLink.append("&param_Environment=");
        quickCreateLink.append(envName);
        Map<String, String> params = getQuickCreateLinkParameters();
        quickCreateLink.append("&param_EventBusArn=");
        quickCreateLink.append(params.get("EVENT_BUS"));
        quickCreateLink.append("&param_ApiAppClientSecretArn=");
        quickCreateLink.append(params.get("API_APP_CLIENT_SECRET"));
        quickCreateLink.append("&param_EncryptionKeyArn=");
        quickCreateLink.append(params.get("API_APP_CLIENT_KEY"));
        quickCreateLink.append("&param_UtilsLayerArn=");
        quickCreateLink.append(params.get("UTILS_LAYER"));
        quickCreateLink.append("&param_CloudFormationUtilsLayerArn=");
        quickCreateLink.append(params.get("CFN_UTILS_LAYER"));
        quickCreateLink.append("&param_ApiHelperLayerArn=");
        quickCreateLink.append(params.get("API_CLIENT_HELPER_LAYER"));
        return quickCreateLink.toString();
    }

    protected Map<String, String> getQuickCreateLinkParameters() {
        Map<String, String> params = new HashMap<>();
        try {
            GetParametersResponse response = ssm.getParameters(request -> request
                    .names(List.of(
                            "/saas-boost/" + envName + "/EVENT_BUS",
                            "/saas-boost/" + envName + "/API_APP_CLIENT_SECRET",
                            "/saas-boost/" + envName + "/API_APP_CLIENT_KEY",
                            "/saas-boost/" + envName + "/UTILS_LAYER",
                            "/saas-boost/" + envName + "/CFN_UTILS_LAYER",
                            "/saas-boost/" + envName + "/API_CLIENT_HELPER_LAYER"
                    ))
            );
            response.parameters()
                    .stream()
                    .forEach(parameter -> params.put(
                            parameter.name().substring(parameter.name().lastIndexOf("/") + 1), parameter.value()));
            // ParameterStore only has the name of the event bus, but we need the whole ARN
            params.put("EVENT_BUS",
                    "arn:" + AWS_REGION.metadata().partition().id() + ":events:" + AWS_REGION.id()
                    + ":" + accountId + ":event-bus/" + params.get("EVENT_BUS"));
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm getParameters failed", ssmError);
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
        return params;
    }

}
