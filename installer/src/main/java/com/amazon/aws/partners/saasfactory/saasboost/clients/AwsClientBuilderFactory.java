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

package com.amazon.aws.partners.saasfactory.saasboost.clients;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.retry.AwsRetryPolicy;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.AcmClientBuilder;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.ApiGatewayClientBuilder;
import software.amazon.awssdk.services.apigateway.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.CloudFormationClientBuilder;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.EcrClientBuilder;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.IamClientBuilder;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;
import software.amazon.awssdk.services.quicksight.QuickSightClient;
import software.amazon.awssdk.services.quicksight.QuickSightClientBuilder;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.Route53ClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;

import java.net.URI;
import java.time.Duration;

public class AwsClientBuilderFactory {

    private static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER =
            RefreshingProfileDefaultCredentialsProvider.builder().build();

    private final Region awsRegion;
    private final AwsCredentialsProvider credentialsProvider;

    private ApiGatewayClientBuilder cachedApiGatewayBuilder;
    private CloudFormationClientBuilder cachedCloudFormationBuilder;
    private EcrClientBuilder cachedEcrBuilder;
    private IamClientBuilder cachedIamBuilder;
    private LambdaClientBuilder cachedLambdaBuilder;
    private QuickSightClientBuilder cachedQuickSightBuilder;
    private S3ClientBuilder cachedS3Builder;
    private SsmClientBuilder cachedSsmBuilder;
    private StsClientBuilder cachedStsBuilder;
    private SecretsManagerClientBuilder cachedSecretsManagerClientBuilder;
    private Route53ClientBuilder cachedRoute53ClientBuilder;
    private AcmClientBuilder cachedAcmClientBuilder;

    AwsClientBuilderFactory() {
        // for testing
        this.awsRegion = null;
        this.credentialsProvider = null;
    }

    private AwsClientBuilderFactory(Builder builder) {
        // passing no region or a null region to any of the AWS Client Builders
        // leads to the default region from the configured profile being used
        this.awsRegion = builder.defaultRegion;
        this.credentialsProvider = builder.awsCredentialsProvider != null
                ? builder.awsCredentialsProvider
                : DEFAULT_CREDENTIALS_PROVIDER;
    }

    // VisibleForTesting
    <C extends SdkClient, B extends AwsClientBuilder<B, C>> B decorateBuilderWithDefaults(B builder) {
        return builder
                .credentialsProvider(credentialsProvider)
                .region(awsRegion);
    }

    public ApiGatewayClientBuilder apiGatewayBuilder() {
        if (cachedApiGatewayBuilder == null) {
            // override throttling policy to wait 5 seconds if we're throttled on CreateDeployment
            // https://docs.aws.amazon.com/apigateway/latest/developerguide/limits.html
            cachedApiGatewayBuilder = decorateBuilderWithDefaults(ApiGatewayClient.builder())
                .overrideConfiguration(config -> config.retryPolicy(AwsRetryPolicy.addRetryConditions(
                    RetryPolicy.builder().throttlingBackoffStrategy(retryPolicyContext -> {
                        if (retryPolicyContext.originalRequest() instanceof CreateDeploymentRequest) {
                            return Duration.ofSeconds(5);
                        }
                        return null;
                    }).build())));
        }
        
        return cachedApiGatewayBuilder;
    }

    public CloudFormationClientBuilder cloudFormationBuilder() {
        if (cachedCloudFormationBuilder == null) {
            cachedCloudFormationBuilder = decorateBuilderWithDefaults(CloudFormationClient.builder());
        }
        return cachedCloudFormationBuilder;
    }

    public EcrClientBuilder ecrBuilder() {
        if (cachedEcrBuilder == null) {
            cachedEcrBuilder = decorateBuilderWithDefaults(EcrClient.builder());
        }
        return cachedEcrBuilder;
    }

    public IamClientBuilder iamBuilder() {
        if (cachedIamBuilder == null) {
            Region region = Region.of(System.getenv("AWS_REGION"));
            if (Utils.isChinaRegion(region)) {
                // China's IAM endpoints are regional
                // See https://docs.amazonaws.cn/en_us/aws/latest/userguide/iam.html
                cachedIamBuilder = decorateBuilderWithDefaults(IamClient.builder());
            } else {
                // IAM in the commercial regions use the AWS_GLOBAL
                // ref: https://docs.aws.amazon.com/general/latest/gr/iam-service.html
                cachedIamBuilder = decorateBuilderWithDefaults(IamClient.builder()).region(Region.AWS_GLOBAL);
            }
        }
        return cachedIamBuilder;
    }

    public LambdaClientBuilder lambdaBuilder() {
        if (cachedLambdaBuilder == null) {
            cachedLambdaBuilder = decorateBuilderWithDefaults(LambdaClient.builder());
        }
        return cachedLambdaBuilder;
    }

    public QuickSightClientBuilder quickSightBuilder() {
        if (cachedQuickSightBuilder == null) {
            cachedQuickSightBuilder = decorateBuilderWithDefaults(QuickSightClient.builder());
        }
        return cachedQuickSightBuilder;
    }

    public S3ClientBuilder s3Builder() {
        if (cachedS3Builder == null) {
            cachedS3Builder = decorateBuilderWithDefaults(S3Client.builder());
        }
        return cachedS3Builder;
    }

    public SsmClientBuilder ssmBuilder() {
        if (cachedSsmBuilder == null) {
            cachedSsmBuilder = decorateBuilderWithDefaults(SsmClient.builder());
        }
        return cachedSsmBuilder;
    }

    public StsClientBuilder stsBuilder() {
        if (cachedStsBuilder == null) {
            cachedStsBuilder = decorateBuilderWithDefaults(StsClient.builder());
        }
        return cachedStsBuilder;
    }

    public SecretsManagerClientBuilder secretsManagerBuilder() {
        if (cachedSecretsManagerClientBuilder == null) {
            cachedSecretsManagerClientBuilder = decorateBuilderWithDefaults(SecretsManagerClient.builder());
        }
        return cachedSecretsManagerClientBuilder;
    }

    public Route53ClientBuilder route53Builder() {
        if (cachedRoute53ClientBuilder == null) {
            // Route53 is a global service and uses a different region setting than the default
            Region region;
            String endpoint;
            Builder factory = builder()
                    .region(Region.of(System.getenv("AWS_REGION")))
                    .credentialsProvider(DEFAULT_CREDENTIALS_PROVIDER);
            if (!Utils.isChinaRegion(factory.defaultRegion)) {
                region = Region.US_EAST_1;
                endpoint = "https://route53.amazonaws.com";
            } else {
                region = Region.CN_NORTHWEST_1;
                endpoint = "https://route53.amazonaws.com.cn";
            }
            cachedRoute53ClientBuilder = Route53Client.builder()
                    .region(region)
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(factory.awsCredentialsProvider);
        }
        return cachedRoute53ClientBuilder;
    }

    public AcmClientBuilder acmBuilder() {
        if (cachedAcmClientBuilder == null) {
            cachedAcmClientBuilder = decorateBuilderWithDefaults(AcmClient.builder());
        }
        return cachedAcmClientBuilder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Region defaultRegion;
        private AwsCredentialsProvider awsCredentialsProvider;

        private Builder() {

        }

        public Builder region(Region defaultRegion) {
            this.defaultRegion = defaultRegion;
            return this;
        }

        public Builder credentialsProvider(AwsCredentialsProvider awsCredentialsProvider) {
            this.awsCredentialsProvider = awsCredentialsProvider;
            return this;
        }

        public AwsClientBuilderFactory build() {
            return new AwsClientBuilderFactory(this);
        }
    }
}
