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

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Cluster;
import software.amazon.awssdk.services.ecs.model.DescribeClustersRequest;
import software.amazon.awssdk.services.ecs.model.DescribeClustersResponse;
import software.amazon.awssdk.services.ecs.model.PutClusterCapacityProvidersRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AttachEcsCapacityProviderRequestHandlerTest {

    private static final String CAPACITY_PROVIDER_1 = "capacityProvider1";
    private static final String CAPACITY_PROVIDER_2 = "capacityProvider2";
    private static final String NEW_CAPACITY_PROVIDER = "capacityProvider3";
    private static final List<String> EXISTING_PROVIDERS = List.of(CAPACITY_PROVIDER_1, CAPACITY_PROVIDER_2);
    private static final RequestContext BASE_REQUEST_CONTEXT = RequestContext.builder()
            .requestType("Create")
            .capacityProvider(NEW_CAPACITY_PROVIDER)
            .ecsCluster("ecsCluster")
            .onboardingDdbTable("onboardingDdbTable")
            .tenantId("tenant-123-456")
            .build();
    private static final ArgumentCaptor<PutClusterCapacityProvidersRequest> putRequestCaptor = 
            ArgumentCaptor.forClass(PutClusterCapacityProvidersRequest.class);

    CapacityProviderLock mockLock = mock(CapacityProviderLock.class); // already no-op?
    EcsClient mockEcs = mock(EcsClient.class);

    @Before
    public void setup() {
        // when you describe clusters to look for capacity providers in ECS you find EXISTING_PROVIDERS
        doReturn(DescribeClustersResponse.builder()
                .clusters(Cluster.builder().capacityProviders(EXISTING_PROVIDERS).build())
                .build()).when(mockEcs).describeClusters(any(DescribeClustersRequest.class));
        // right now response is ignored
        doReturn(null).when(mockEcs).putClusterCapacityProviders(putRequestCaptor.capture());
    }

    @Test
    public void testCall_basicCreate() {
        AttachCapacityProviderRequestHandler testHandler = new AttachCapacityProviderRequestHandler(
                BASE_REQUEST_CONTEXT, mockLock, mockEcs);
        List<String> expectedProviders = new ArrayList<>(EXISTING_PROVIDERS);
        expectedProviders.add(NEW_CAPACITY_PROVIDER);
        testCall(testHandler, true, expectedProviders);
    }

    @Test
    public void testCall_basicUpdate() {
        AttachCapacityProviderRequestHandler testHandler = new AttachCapacityProviderRequestHandler(
                RequestContext.builder(BASE_REQUEST_CONTEXT).requestType("Update").build(), mockLock, mockEcs);
        List<String> expectedProviders = new ArrayList<>(EXISTING_PROVIDERS);
        expectedProviders.add(NEW_CAPACITY_PROVIDER);
        testCall(testHandler, true, expectedProviders);
    }

    @Test
    public void testCall_basicDelete() {
        AttachCapacityProviderRequestHandler testHandler = new AttachCapacityProviderRequestHandler(
                RequestContext.builder(BASE_REQUEST_CONTEXT)
                        .requestType("Delete")
                        .capacityProvider(CAPACITY_PROVIDER_1)
                        .build(), mockLock, mockEcs);
        List<String> expectedProviders = new ArrayList<>(EXISTING_PROVIDERS);
        expectedProviders.remove(CAPACITY_PROVIDER_1);
        testCall(testHandler, true, expectedProviders);
    }

    @Test
    public void testCall_addExistingCapacityProvider() {
        AttachCapacityProviderRequestHandler testHandler = new AttachCapacityProviderRequestHandler(
                RequestContext.builder(BASE_REQUEST_CONTEXT)
                        .requestType("Create")
                        .capacityProvider(CAPACITY_PROVIDER_1)
                        .build(), mockLock, mockEcs);
        // pass null for expected capacity providers to indicate we shouldn't make a call to ECS
        testCall(testHandler, true, null);
    }

    @Test
    public void testCall_removeNonExistingCapacityProvider() {
        AttachCapacityProviderRequestHandler testHandler = new AttachCapacityProviderRequestHandler(
                RequestContext.builder(BASE_REQUEST_CONTEXT)
                        .requestType("Delete")
                        .capacityProvider(NEW_CAPACITY_PROVIDER)
                        .build(), mockLock, mockEcs);
        List<String> expectedProviders = new ArrayList<>(EXISTING_PROVIDERS);
        expectedProviders.remove(CAPACITY_PROVIDER_1);
        testCall(testHandler, true, null);
    }

    @Test
    public void testCall_unknownRequestType() {
        AttachCapacityProviderRequestHandler testHandler = new AttachCapacityProviderRequestHandler(
                RequestContext.builder(BASE_REQUEST_CONTEXT)
                        .requestType("UNKNOWN")
                        .capacityProvider(CAPACITY_PROVIDER_1)
                        .build(), mockLock, mockEcs);
        testCall(testHandler, false, null);
    }

    private void testCall(AttachCapacityProviderRequestHandler handler, 
            boolean expectSuccess, List<String> expectedPassedCapacityProviders) {
        // start start
        HandleResult result = handler.call();
        assertEquals(expectSuccess, result.succeeded());
        if (expectSuccess) {
            verify(mockLock, times(1)).lock(any(RequestContext.class));
            verify(mockLock, times(1)).unlock(any(RequestContext.class));
            if (expectedPassedCapacityProviders != null) {
                assertEquals(expectedPassedCapacityProviders, putRequestCaptor.getValue().capacityProviders());
            } else {
                verify(mockEcs, times(0)).putClusterCapacityProviders(any(PutClusterCapacityProvidersRequest.class));
            }
        }
    }
}