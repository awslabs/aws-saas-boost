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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.CloudFormationClientBuilder;

public class MockAwsClientBuilderFactory extends AwsClientBuilderFactory {
    private final AwsClientBuilderFactory factory = mock(AwsClientBuilderFactory.class);

    public MockAwsClientBuilderFactory() {
        
    }

    public void mockCfn(CloudFormationClient cfn) {
        when(factory.cloudFormationBuilder()).thenReturn(new CloudFormationClientBuilder() {

            @Override
            public CloudFormationClientBuilder httpClient(SdkHttpClient httpClient) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClientBuilder httpClientBuilder(
                    software.amazon.awssdk.http.SdkHttpClient.Builder httpClientBuilder) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClientBuilder credentialsProvider(AwsCredentialsProvider credentialsProvider) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClientBuilder region(Region region) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClientBuilder dualstackEnabled(Boolean dualstackEndpointEnabled) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClientBuilder fipsEnabled(Boolean fipsEndpointEnabled) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClientBuilder overrideConfiguration(
                    ClientOverrideConfiguration overrideConfiguration) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClientBuilder endpointOverride(URI endpointOverride) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public CloudFormationClient build() {
                // TODO Auto-generated method stub
                return cfn;
            }
            
        });
    }
}
