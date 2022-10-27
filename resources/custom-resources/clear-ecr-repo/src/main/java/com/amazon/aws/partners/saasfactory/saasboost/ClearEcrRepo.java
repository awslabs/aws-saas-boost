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
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.EcrException;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.ecr.model.RepositoryNotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class ClearEcrRepo implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearEcrRepo.class);
    private final EcrClient ecr;

    public ClearEcrRepo() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.ecr = Utils.sdkClient(EcrClient.builder(), EcrClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String repo = (String) resourceProperties.get("Repo");

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();

        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    try {
                        List<ImageIdentifier> imagesToDelete = new ArrayList<>();
                        String token = null;
                        do {
                            ListImagesResponse response = ecr.listImages(request -> request.repositoryName(repo));
                            token = response.nextToken();
                            imagesToDelete.addAll(response.imageIds());
                        } while (token != null);

                        final int batchDeleteImageBatchSize = 100;
                        List<ImageIdentifier> imagesToDeleteBatch = new ArrayList<>();
                        for (ImageIdentifier image : imagesToDelete) {
                            imagesToDeleteBatch.add(image);
                            if (imagesToDeleteBatch.size() == batchDeleteImageBatchSize) {
                                ecr.batchDeleteImage(request -> request
                                        .repositoryName(repo).imageIds(imagesToDeleteBatch));
                                imagesToDeleteBatch.clear();
                            }
                        }
                        // final batch
                        ecr.batchDeleteImage(request -> request.repositoryName(repo).imageIds(imagesToDeleteBatch));

                        CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                    } catch (RepositoryNotFoundException rnfe) {
                        LOGGER.error("FAILED repository {} not found", repo);
                        LOGGER.error(Utils.getFullStackTrace(rnfe));
                        responseData.put("Reason", "Passed repository does not exist: " + repo);
                        CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                    } catch (EcrException ecrException) {
                        LOGGER.error("FAILED unexpected error {}", ecrException.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(ecrException));
                        responseData.put("Reason", ecrException.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    }
                } else {
                    LOGGER.error("FAILED unknown requestType {}", requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out {}", e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }
}