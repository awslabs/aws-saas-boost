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
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.FailureType;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CodePipelineWaitHandler implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodePipelineWaitHandler.class);
    private final CodePipelineClient codepipeline;

    public CodePipelineWaitHandler() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.codepipeline = Utils.sdkClient(CodePipelineClient.builder(), CodePipelineClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        //logRequestEvent(event);

        Map<String, Object> job = (Map<String, Object>) event.get("CodePipeline.job");
        String jobId = (String) job.get("id");

        LOGGER.info("Processing CloudFormation wait handle for CodePipeline job {}", jobId);
        Map<String, Object> jobData = (Map<String, Object>) job.get("data");
        Map<String, Object> actionConfig = (Map<String, Object>) jobData.get("actionConfiguration");
        Map<String, String> configuration = (Map<String, String>) actionConfig.get("configuration");
        String userParameters = configuration.get("UserParameters");
        Map<String, Object> params = Utils.fromJson(userParameters, HashMap.class);
        if (null == params) {
            throw new RuntimeException("UserParameters can't be parsed as JSON");
        }

        try {
            // Pre signed S3 URL
            String waitHandle = (String) params.get("waitHandle");

            // Since there's not built-in way to have dynamic CodePipeline stage actions,
            // and because this pipeline could be triggered outside of CloudFormation, the
            // wait condition handle mey be irrelevant. Skip it if it's out of date.
            if (signalCloudFormation(waitHandle)) {
                LOGGER.info("Signaling CloudFormation wait condition handle");

                // Passed back to CloudFormation as !GetAtt WaitConditionResource.Data
                String data = "";
                // If this Lambda is invoked, the proceeding CodePipeline stage action succeeded
                boolean success = true;
                String failureReason = "";

                // Signal the CloudFormation stack that it can continue
                CloudFormationResponse.signal(waitHandle, success, jobId, data, failureReason);
            } else {
                LOGGER.info("Skipping CloudFormation signal for expired wait condition handle");
            }

            // Tell CodePipeline that it can continue
            codepipeline.putJobSuccessResult(request -> request.jobId(jobId));
        } catch (Exception e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            failJob(jobId, "Error ", context);
            throw e;
        }

        return null;
    }

    protected boolean signalCloudFormation(String signedS3Url) {
        boolean doSignal = false;
        try {
            // Being a little loose and fast here because we know there won't be multiple values for the
            // query params we're after and we know they won't be URL encoded
            URI s3PresignedUrl = URI.create(signedS3Url);
            Map<String, String> queryParams = Pattern.compile("&")
                    .splitAsStream(s3PresignedUrl.getQuery())
                    .map(param -> param.split("=", 2))
                    .collect(Collectors.toMap(
                            entry -> entry[0],
                            entry -> entry[1]
                    ));
            String creationDateTimestamp = queryParams.get("X-Amz-Date"); // Wait handle condition created at
            String expiresIn = queryParams.get("X-Amz-Expires"); // This should be 24 hrs - 1 sec or 86399

            ZoneId utc = ZoneId.of("UTC");
            DateTimeFormatter amzDateFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(utc);
            ZonedDateTime created = ZonedDateTime.parse(creationDateTimestamp, amzDateFmt);
            ZonedDateTime expires = created.plus(Long.parseLong(expiresIn), ChronoUnit.SECONDS);
            ZonedDateTime now = ZonedDateTime.now(utc);
            doSignal = now.isBefore(expires);

        } catch (Exception e) {
            LOGGER.error("Error parsing signed S3 URL", e);
            LOGGER.error(Utils.getFullStackTrace(e));
        }
        return doSignal;
    }

    private void failJob(String jobId, String message, Context context) {
        try {
            codepipeline.putJobFailureResult(request -> request
                    .jobId(jobId)
                    .failureDetails(details -> details
                            .type(FailureType.JOB_FAILED)
                            .message(message)
                            .externalExecutionId(context.getAwsRequestId())
                    )
            );
        } catch (SdkServiceException codepipelineError) {
            LOGGER.error("codepipeline::PutJobFailureResult", codepipelineError);
            LOGGER.error(Utils.getFullStackTrace(codepipelineError));
            throw codepipelineError;
        }
    }

}

