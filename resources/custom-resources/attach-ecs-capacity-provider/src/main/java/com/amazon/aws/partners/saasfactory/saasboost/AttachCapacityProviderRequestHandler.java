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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Cluster;
import software.amazon.awssdk.services.ecs.model.ClusterNotFoundException;
import software.amazon.awssdk.services.ecs.model.DescribeClustersRequest;
import software.amazon.awssdk.services.ecs.model.PutClusterCapacityProvidersRequest;
import software.amazon.awssdk.services.ecs.model.UpdateInProgressException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AttachCapacityProviderRequestHandler implements Callable<HandleResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachCapacityProviderRequestHandler.class);

    private final RequestContext requestContext;
    private final CapacityProviderLock lock;
    private final EcsClient ecs;

    public AttachCapacityProviderRequestHandler(
            RequestContext requestContext,
            CapacityProviderLock lock,
            EcsClient ecs) {
        this.ecs = ecs;
        this.requestContext = requestContext;
        this.lock = lock;
    }

    @Override
    public HandleResult call() {
        HandleResult result = new HandleResult();
        LOGGER.info(requestContext.requestType.toUpperCase());
        if ("Create".equalsIgnoreCase(requestContext.requestType) 
                || "Update".equalsIgnoreCase(requestContext.requestType)) {
            LOGGER.info("Attaching capacity provider {} to ecs cluster {} for tenant {}", 
                    requestContext.capacityProvider, requestContext.ecsCluster, requestContext.tenantId);
            result = atomicallyUpdateCapacityProviders((capacityProviders) -> {
                if (!capacityProviders.contains(requestContext.capacityProvider)) {
                    List<String> modifiedCapacityProviders = new ArrayList<String>(capacityProviders);
                    modifiedCapacityProviders.add(requestContext.capacityProvider);
                    return modifiedCapacityProviders;
                }
                return capacityProviders;
            });
        } else if ("Delete".equalsIgnoreCase(requestContext.requestType)) {
            // unclear whether we need this.. commenting it out for testing.
            LOGGER.info("Detaching capacity provider {} from ecs cluster {} for tenant {}", 
                    requestContext.capacityProvider, requestContext.ecsCluster, requestContext.tenantId);
            result = atomicallyUpdateCapacityProviders((capacityProviders) -> {
                return capacityProviders.stream()
                        .filter((capacityProvider) -> !capacityProvider.equals(requestContext.capacityProvider))
                        .collect(Collectors.toList());
            });
            result.setSucceeded();
        } else {
            LOGGER.error("FAILED unknown requestType {}", requestContext.requestType);
            result.putFailureReason("Unknown RequestType " + requestContext.requestType);
            result.setFailed();
        }

        return result;
    }

    private HandleResult atomicallyUpdateCapacityProviders(
            Function<List<String>, List<String>> capacityProvidersMutationFunction) {
        HandleResult result = new HandleResult();
        // lock ddb
        lock.lock(requestContext);
        try {
            // read capacity providers into list
            List<String> existingCapacityProviders = getExistingCapacityProviders();
            
            List<String> mutatedCapacityProviders = capacityProvidersMutationFunction.apply(existingCapacityProviders);
            LOGGER.debug("existingCapacityProviders {} mutated to {}",
                    existingCapacityProviders, mutatedCapacityProviders);

            boolean successful = false;
            // if the mutate did nothing, no point in slowing us down to make an ECS call
            if (existingCapacityProviders.equals(mutatedCapacityProviders)) {
                successful = true;
                result.setSucceeded();
            }
            while (!successful) {
                try {
                    // set capacity providers. response doesn't really give us anything but a 
                    // description of the new cluster. exceptions are thrown on failure
                    ecs.putClusterCapacityProviders(PutClusterCapacityProvidersRequest.builder()
                            .cluster(requestContext.ecsCluster)
                            .capacityProviders(mutatedCapacityProviders)
                            .build());
                    successful = true;
                    result.setSucceeded();
                } catch (UpdateInProgressException uipe) {
                    // There's a Amazon ECS container agent update in progress on this container instance.
                    // ECS errors indicate this can be retried. Wait 10 seconds and try again.
                    LOGGER.error("Received error calling putClusterCapacityProviders", uipe);
                    LOGGER.error(Utils.getFullStackTrace(uipe));
                    LOGGER.error("Waiting 10 seconds before retrying..");
                    Thread.sleep(10 * 1000); // 10 seconds
                }
            }
        } catch (ClusterNotFoundException cnfe) {
            LOGGER.error("Could not find ecs cluster: {}", requestContext.ecsCluster);
            LOGGER.error(Utils.getFullStackTrace(cnfe));
            result.putFailureReason(cnfe.getMessage());
            result.setFailed();
        } catch (InterruptedException ie) {
            LOGGER.error("Error while waiting between putClusterCapacityProvider calls", ie.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ie));
            result.putFailureReason(ie.getMessage());
            result.setFailed();
        } finally {
            // unlock ddb
            lock.unlock(requestContext);
        }
        return result;
    }

    private List<String> getExistingCapacityProviders() {
        List<Cluster> returnedClusters = ecs.describeClusters(
                DescribeClustersRequest.builder().clusters(requestContext.ecsCluster).build()).clusters();
        if (returnedClusters.size() != 1) {
            // we only passed one cluster ARN but we received 0 or 2
            LOGGER.error("Expected 1 cluster with name {} but found {}", 
                    requestContext.ecsCluster, returnedClusters.size());
        }
        List<String> existingCapacityProviders = returnedClusters.get(0).capacityProviders();
        return existingCapacityProviders == null ? List.of() : existingCapacityProviders;
    }

}
