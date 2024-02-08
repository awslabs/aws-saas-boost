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
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.*;

import java.util.*;
import java.util.concurrent.*;

public class StartCodeBuild implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartCodeBuild.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private final CodeBuildClient codeBuild;

    public StartCodeBuild() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.codeBuild = Utils.sdkClient(CodeBuildClient.builder(), CodeBuildClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        final Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String project = (String) resourceProperties.get("Project");
        final String buildSpec = (String) resourceProperties.get("BuildSpec");
        final Boolean wait = Boolean.valueOf((String) resourceProperties.get("Wait"));

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    try {
                        StartBuildRequest.Builder requestBuilder = StartBuildRequest.builder();
                        requestBuilder = requestBuilder.projectName(project);
                        if (Utils.isNotBlank(buildSpec)) {
                            requestBuilder = requestBuilder.buildspecOverride(buildSpec);
                        }
                        StartBuildResponse response = codeBuild.startBuild(requestBuilder.build());
                        Build build = response.build();
                        if (StatusType.FAILED == build.buildStatus() || StatusType.FAULT == build.buildStatus()) {
                            responseData.put("Reason", "CodeBuild start build failed");
                            CloudFormationResponse.send(event, context, "FAILED", responseData);
                        } else {
                            if (wait) {
                                // wait for the build to complete
                                // note that the CodeBuild project has a defined timeout
                                LOGGER.info("Waiting for max {} minutes for build {} to complete",
                                        build.timeoutInMinutes(), build.id());
                                ReverseBackoff backoff = new ReverseBackoff(30f, 0.25f, 0.2f);
                                while (!"COMPLETED".equals(build.currentPhase())) {
                                    long delay = (long) backoff.delay() * 1000;
                                    LOGGER.info("Waiting {}ms for build {} to complete", delay, build.id());
                                    Thread.sleep(delay);
                                    build = getBuild(codeBuild, build.id());;
                                }

                                if (StatusType.SUCCEEDED == build.buildStatus()) {
                                    responseData.put("Build", build.id());
                                    responseData.put("BuildStatus", build.buildStatusAsString());
                                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                                } else {
                                    responseData.put("Reason", build.buildStatusAsString());
                                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                                }
                            } else {
                                responseData.put("Build", build.id());
                                responseData.put("BuildStatus", build.buildStatusAsString());
                                CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                            }
                        }
                    } catch (CodeBuildException codeBuildError) {
                        LOGGER.error("codebuild:StartBuild", codeBuildError.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(codeBuildError));
                        responseData.put("Reason", codeBuildError.awsErrorDetails().errorMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    } catch (Exception e) {
                        LOGGER.error("Unexpected error", e);
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    }
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    protected Build getBuild(CodeBuildClient codeBuild, String buildId) {
        Build build = null;
        try {
            BatchGetBuildsResponse buildsResponse = codeBuild.batchGetBuilds(request -> request
                    .ids(buildId)
            );
            if (buildsResponse.hasBuilds() && buildsResponse.builds().size() == 1) {
                build = buildsResponse.builds().get(0);
            }
        } catch (CodeBuildException cbe) {
            LOGGER.error(cbe.awsErrorDetails().errorMessage());
            LOGGER.error(Utils.getFullStackTrace(cbe));
            throw cbe;
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        return build;
    }

    static final class ReverseBackoff {

        private float initialDelay;
        private float reducingFactor;
        private float minDelay;
        private float delay;

        public ReverseBackoff(float initialDelay, float minDelay, float reducingFactor) {
            this.initialDelay = initialDelay;
            this.delay = this.initialDelay;
            this.minDelay = minDelay;
            this.reducingFactor = reducingFactor;
        }

        public float delay() {
            float current = Math.max(delay - (delay * reducingFactor), 0f);
            if (delay == initialDelay) {
                delay = current;
                return initialDelay;
            }
            delay = current;
            return Math.max(delay, minDelay);
        }
    }
}
