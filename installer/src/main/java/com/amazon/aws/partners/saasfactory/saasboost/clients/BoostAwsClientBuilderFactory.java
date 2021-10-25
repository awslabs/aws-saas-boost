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
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.ApiGatewayClientBuilder;
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
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;

public class BoostAwsClientBuilderFactory {

    private static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER =
            RefreshingProfileDefaultCredentialsProvider.builder().build();

    private final Region awsRegion;
    private final AwsCredentialsProvider credentialsProvider;

    private BoostAwsClientBuilderFactory(Builder builder) {
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
        return decorateBuilderWithDefaults(ApiGatewayClient.builder());
    }

    public CloudFormationClientBuilder cloudFormationBuilder() {
        return decorateBuilderWithDefaults(CloudFormationClient.builder());
    }

    public EcrClientBuilder ecrBuilder() {
        return decorateBuilderWithDefaults(EcrClient.builder());
    }

    public IamClientBuilder iamBuilder() {
        return decorateBuilderWithDefaults(IamClient.builder());
    }

    public LambdaClientBuilder lambdaBuilder() {
        return decorateBuilderWithDefaults(LambdaClient.builder());
    }

    public QuickSightClientBuilder quickSightBuilder() {
        return decorateBuilderWithDefaults(QuickSightClient.builder());
    }

    public S3ClientBuilder s3Builder() {
        return decorateBuilderWithDefaults(S3Client.builder());
    }

    public SsmClientBuilder ssmBuilder() {
        return decorateBuilderWithDefaults(SsmClient.builder());
    }

    public StsClientBuilder stsBuilder() {
        return decorateBuilderWithDefaults(StsClient.builder());
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

        public BoostAwsClientBuilderFactory build() {
            return new BoostAwsClientBuilderFactory(this);
        }
    }
}
