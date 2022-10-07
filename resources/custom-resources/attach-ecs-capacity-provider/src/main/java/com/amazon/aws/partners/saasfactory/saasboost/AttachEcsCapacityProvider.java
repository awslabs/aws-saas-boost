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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ecs.EcsClient;

import java.util.*;
import java.util.concurrent.*;

public class AttachEcsCapacityProvider implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachEcsCapacityProvider.class);

    private final EcsClient ecs;
    private final CapacityProviderLock lock;

    public AttachEcsCapacityProvider() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        ecs = Utils.sdkClient(EcsClient.builder(), EcsClient.SERVICE_NAME);
        lock = new CapacityProviderLock(Utils.sdkClient(DynamoDbClient.builder(), DynamoDbClient.SERVICE_NAME));
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        RequestContext requestContext = RequestContext.builder()
                .requestType((String) event.get("RequestType"))
                .capacityProvider((String) resourceProperties.get("CapacityProvider"))
                .ecsCluster((String) resourceProperties.get("ECSCluster"))
                .onboardingDdbTable((String) resourceProperties.get("OnboardingDdbTable"))
                .tenantId((String) resourceProperties.get("TenantId"))
                .build();
        HandleResult handleRequestResult = new HandleResult();
        ExecutorService service = Executors.newSingleThreadExecutor();
        try {
            Callable<HandleResult> c = new AttachCapacityProviderRequestHandler(requestContext, lock, ecs);
            Future<?> f = service.submit(c);
            handleRequestResult = (HandleResult) f.get(context.getRemainingTimeInMillis() - 1000, 
                    TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            String stackTrace = Utils.getFullStackTrace(e);
            LOGGER.error(stackTrace);
            handleRequestResult.setFailed();
            handleRequestResult.putResponseData("Reason", stackTrace);
        } finally {
            service.shutdown();
        }
        CloudFormationResponse.send(event, context,
                handleRequestResult.succeeded() ? "SUCCESS" : "FAILED",
                handleRequestResult.getResponseData());
        return null;
    }
}