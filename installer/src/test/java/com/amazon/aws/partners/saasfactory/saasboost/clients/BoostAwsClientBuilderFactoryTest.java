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

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.quicksight.QuickSightClientBuilder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BoostAwsClientBuilderFactoryTest {

    private static final Region DEFAULT_EXPECTED_REGION = null;
    private static final Class<? extends AwsCredentialsProvider> DEFAULT_EXPECTED_CREDENTIALS_PROVIDER_CLASS =
            RefreshingProfileDefaultCredentialsProvider.class;

    private static QuickSightClientBuilder mockBuilder;

    @BeforeClass
    public static void createMockBuilder() {
        mockBuilder = mock(QuickSightClientBuilder.class);
        when(mockBuilder.credentialsProvider(any())).thenReturn(mockBuilder);
        when(mockBuilder.region(any())).thenReturn(mockBuilder);
    }

    @After
    public void resetMockBuilder() {
        clearInvocations(mockBuilder);
    }

    @Test
    public void buildFactoryWithNoRegion() {
        // this test verifies that a null region is automatically filled with the default profile region
        // in the SDK. this is assumed by the BoostAwsClientBuilderFactory and will fail should that behavior change
        BoostAwsClientBuilderFactory.builder().build().quickSightBuilder().build();
    }

    @Test
    public void verifyBuildersHaveDefaults() {
        // for each builder, verify it has region and credentials provider as expected
        runBoostAwsClientBuilderFactoryTest(BoostAwsClientBuilderFactory.builder().build(),
                DEFAULT_EXPECTED_REGION, DEFAULT_EXPECTED_CREDENTIALS_PROVIDER_CLASS);
    }

    @Test
    public void verifyBuilderRegionOverridden() {
        Region expectedRegion = Region.AF_SOUTH_1;
        runBoostAwsClientBuilderFactoryTest(BoostAwsClientBuilderFactory.builder().region(expectedRegion).build(),
                expectedRegion, DEFAULT_EXPECTED_CREDENTIALS_PROVIDER_CLASS);
    }

    @Test
    public void verifyBuilderCredentialProviderOverridden() {
        Class<? extends AwsCredentialsProvider> expectedCredentialsProviderClass = DefaultCredentialsProvider.class;
        runBoostAwsClientBuilderFactoryTest(
                BoostAwsClientBuilderFactory.builder()
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build(),
                DEFAULT_EXPECTED_REGION, expectedCredentialsProviderClass);
    }

    private void runBoostAwsClientBuilderFactoryTest(
            BoostAwsClientBuilderFactory factory,
            Region expectedRegion,
            Class<? extends AwsCredentialsProvider> expectedCredentialsProviderClass) {
        ArgumentCaptor<AwsCredentialsProvider> credentialsProviderArgumentCaptor =
                ArgumentCaptor.forClass(AwsCredentialsProvider.class);
        factory.decorateBuilderWithDefaults(mockBuilder);

        verify(mockBuilder).region(expectedRegion);
        verify(mockBuilder).credentialsProvider(credentialsProviderArgumentCaptor.capture());
        assertEquals("Factory instantiated the wrong credentials provider",
                expectedCredentialsProviderClass,
                credentialsProviderArgumentCaptor.getValue().getClass());
    }
}
