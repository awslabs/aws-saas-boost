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
import software.amazon.awssdk.services.fsx.FSxClient;
import software.amazon.awssdk.services.fsx.model.DescribeFileSystemsResponse;
import software.amazon.awssdk.services.fsx.model.FSxException;
import software.amazon.awssdk.services.fsx.model.FileSystem;

import java.util.*;
import java.util.concurrent.*;

public class FsxDnsName implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FsxDnsName.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private final FSxClient fsx;

    public FsxDnsName() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.fsx = Utils.sdkClient(FSxClient.builder(), FSxClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        final Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String fileSystemId = (String) resourceProperties.get("FsxFileSystemId");

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType) || "Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE or UPDATE");
                    try {
                        DescribeFileSystemsResponse response = fsx.describeFileSystems(request -> request
                                .fileSystemIds(fileSystemId)
                        );
                        FileSystem fileSystem = response.fileSystems().get(0);
                        LOGGER.info("File system DNS name is {}", fileSystem.dnsName());
                        responseData.put("DnsName", fileSystem.dnsName());
                    } catch (FSxException e) {
                        LOGGER.error("fsx:DescribeFileSystems", e);
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.awsErrorDetails().errorMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    }
                    LOGGER.info("responseDate: " +  Utils.toJson(responseData));
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
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

}