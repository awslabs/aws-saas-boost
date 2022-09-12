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

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.retry.AwsRetryPolicy;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;

import java.time.Duration;

public class AwsClientBuilderFactory {

    private static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER =
            RefreshingProfileDefaultCredentialsProvider.builder().build();

    private final Region awsRegion;
    private final AwsCredentialsProvider credentialsProvider;

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

    private ApiGatewayClientBuilder cachedApiGatewayBuilder;

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

    private CloudFormationClientBuilder cachedCloudFormationBuilder;

    public CloudFormationClientBuilder cloudFormationBuilder() {
        if (cachedCloudFormationBuilder == null) {
            cachedCloudFormationBuilder = decorateBuilderWithDefaults(CloudFormationClient.builder());
        }
        return cachedCloudFormationBuilder;
    }

    private EcrClientBuilder cachedEcrBuilder;

    public EcrClientBuilder ecrBuilder() {
        if (cachedEcrBuilder == null) {
            cachedEcrBuilder = decorateBuilderWithDefaults(EcrClient.builder());
        }
        return cachedEcrBuilder;
    }

    private IamClientBuilder cachedIamBuilder;

    public IamClientBuilder iamBuilder() {
        if (cachedIamBuilder == null) {
            // IAM is not regionalized: all endpoints except us-gov and aws-cn use the AWS_GLOBAL region
            // ref: https://docs.aws.amazon.com/general/latest/gr/iam-service.html
            cachedIamBuilder = decorateBuilderWithDefaults(IamClient.builder()).region(Region.AWS_GLOBAL);
        }
        return cachedIamBuilder;
    }

    private LambdaClientBuilder cachedLambdaBuilder;

    public LambdaClientBuilder lambdaBuilder() {
        if (cachedLambdaBuilder == null) {
            cachedLambdaBuilder = decorateBuilderWithDefaults(LambdaClient.builder());
        }
        return cachedLambdaBuilder;
    }

    private QuickSightClientBuilder cachedQuickSightBuilder;

    public QuickSightClientBuilder quickSightBuilder() {
        if (cachedQuickSightBuilder == null) {
            cachedQuickSightBuilder = decorateBuilderWithDefaults(QuickSightClient.builder());
        }
        return cachedQuickSightBuilder;
    }

    private S3ClientBuilder cachedS3Builder;

    public S3ClientBuilder s3Builder() {
        if (cachedS3Builder == null) {
            cachedS3Builder = decorateBuilderWithDefaults(S3Client.builder());
        }
        return cachedS3Builder;
    }

    private SsmClientBuilder cachedSsmBuilder;

    public SsmClientBuilder ssmBuilder() {
        if (cachedSsmBuilder == null) {
            cachedSsmBuilder = decorateBuilderWithDefaults(SsmClient.builder());
        }
        return cachedSsmBuilder;
    }

    private StsClientBuilder cachedStsBuilder;

    public StsClientBuilder stsBuilder() {
        if (cachedStsBuilder == null) {
            cachedStsBuilder = decorateBuilderWithDefaults(StsClient.builder());
        }
        return cachedStsBuilder;
    }

    private SecretsManagerClientBuilder cachedSecretsManagerClientBuilder;

    public SecretsManagerClientBuilder secretsManagerBuilder() {
        if (cachedSecretsManagerClientBuilder == null) {
            cachedSecretsManagerClientBuilder = decorateBuilderWithDefaults(SecretsManagerClient.builder());
        }
        return cachedSecretsManagerClientBuilder;
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
