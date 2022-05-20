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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.StartPipelineExecutionResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class WorkloadDeploy implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkloadDeploy.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private static final String CODE_PIPELINE_BUCKET = System.getenv("CODE_PIPELINE_BUCKET");
    private final S3Client s3;
    private final CodePipelineClient codepipeline;

    public WorkloadDeploy() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_ENV");
        }
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        if (Utils.isBlank(CODE_PIPELINE_BUCKET)) {
            throw new IllegalStateException("Missing required environment variable CODE_PIPELINE_BUCKET");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        this.codepipeline = Utils.sdkClient(CodePipelineClient.builder(), CodePipelineClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);
        if (validEvent(event)) {
            List<Deployment> deployments = getDeployments(event, context);
            if (!deployments.isEmpty()) {
                LOGGER.info("Deploying for " + deployments.size() + " tenants");
                for (Deployment deployment : deployments) {
                    try {
                        String tenantId = deployment.getTenantId();

                        // Create an imagedefinitions.json document for the newly pushed image
                        byte[] zip = codePipelineArtifact(deployment.getImageName(), deployment.getImageUri());

                        // Write the imagedefinitions.json document to the artifact bucket
                        writeToArtifactBucket(s3, CODE_PIPELINE_BUCKET, tenantId, deployment.getImageName(), zip);

                        // Trigger CodePipeline for this tenant
                        triggerPipeline(codepipeline, tenantId, deployment.getPipeline());
                    } catch (Exception e) {
                        LOGGER.error("Deployment failed {}", Utils.toJson(deployment));
                        LOGGER.error(Utils.getFullStackTrace(e));
                    }
                }
            } else {
                LOGGER.warn("No deployments to trigger");
            }
        } else {
            LOGGER.error("Unrecognized event");
        }
        return null;
    }

    List<Deployment> getDeployments(Map<String, Object> event, Context context) {
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String repo = (String) detail.get("repository-name");
        String tag = (String) detail.get("image-tag");

        // First, fetch the app config and make sure we're trying to deploy an image from a repo that's
        // in the config and for a tag that's in the config
        String serviceName = null;
        Map<String, Object> appConfig = getAppConfig(context);
        Map<String, Object> services = (Map<String, Object>) appConfig.get("services");
        for (Map.Entry<String, Object> serviceConfig : services.entrySet()) {
            Map<String, Object> service = (Map<String, Object>) serviceConfig.getValue();
            String containerRepo = (String) service.get("containerRepo");
            String containerTag = (String) service.get("containerTag");
            if (repo.equals(containerRepo)) {
                if (!tag.equals(containerTag)) {
                    LOGGER.error("Image tag in event {} does not match appConfig {}", tag, containerTag);
                } else {
                    serviceName = serviceConfig.getKey();
                }
            }
        }

        List<Deployment> deployments = new ArrayList<>();
        if (serviceName == null) {
            LOGGER.error("Can't find event repository in appConfig {}", repo);
        } else {
            List<Map<String, Object>> tenants = null;
            String source = (String) event.get("source");
            if ("aws.ecr".equals(source)) {
                tenants = getTenants(context);
            } else if ("saas-boost".equals(source)) {
                String tenantId = (String) detail.get("tenantId");
                if (Utils.isNotEmpty(tenantId)) {
                    tenants = getTenants(tenantId, context);
                }
            }
            if (tenants != null && !tenants.isEmpty()) {
                for (Map<String, Object> tenant : tenants) {
                    String tenantId = (String) tenant.get("id");
                    String pipelineKey = "SERVICE_" + Utils.toUpperSnakeCase(serviceName) + "_CODE_PIPELINE";
                    Map<String, Object> resources = (Map<String, Object>) tenant.get("resources");
                    if (resources.containsKey(pipelineKey)) {
                        Map<String, String> codePipelineResource = (Map<String, String>) resources.get(pipelineKey);
                        String imageName = imageName(tenantId, serviceName);
                        String imageUri = imageUri(event);
                        String pipeline = codePipelineResource.get("name");
                        Deployment deployment = new Deployment(tenantId, imageName, imageUri, pipeline);
                        deployments.add(deployment);
                    } else {
                        LOGGER.error("Can't find CodePipeline resource {} for tenant {}", pipelineKey, tenantId);
                    }
                }
            } else {
                LOGGER.warn("No active, provisioned tenants");
            }
        }

        return deployments;
    }

    protected Map<String, Object> getAppConfig(Context context) {
        // Fetch all of the services configured for this application
        LOGGER.info("Calling settings service get app config API");
        String getAppConfigResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                ApiGatewayHelper.getApiRequest(
                        API_GATEWAY_HOST,
                        API_GATEWAY_STAGE,
                        ApiRequest.builder()
                                .resource("settings/config")
                                .method("GET")
                                .build()
                ),
                API_TRUST_ROLE,
                context.getAwsRequestId()
        );
        Map<String, Object> appConfig = Utils.fromJson(getAppConfigResponseBody, LinkedHashMap.class);
        return appConfig;
    }

    protected List<Map<String, Object>> getTenants(Context context) {
        return getTenants(null, context);
    }

    protected List<Map<String, Object>> getTenants(String tenantId, Context context) {
        // Fetch one or all tenants
        LOGGER.info("Calling tenants service get tenants API");
        String resource = Utils.isNotEmpty(tenantId) ? "tenants/" + tenantId : "tenants?status=provisioned";
        String getTenantsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                ApiGatewayHelper.getApiRequest(
                        API_GATEWAY_HOST,
                        API_GATEWAY_STAGE,
                        ApiRequest.builder()
                                .resource(resource)
                                .method("GET")
                                .build()
                ),
                API_TRUST_ROLE,
                context.getAwsRequestId()
        );
        List<Map<String, Object>> tenants;
        if (Utils.isNotEmpty(tenantId)) {
            tenants = Collections.singletonList(Utils.fromJson(getTenantsResponseBody, LinkedHashMap.class));
        } else {
            tenants = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
        }
        return tenants;
    }

    protected static boolean validEvent(Map<String, Object> event) {
        boolean validEvent = false;
        Map<String, String> detail = (Map<String, String>) event.get("detail");
        if (detail != null) {
            if ("aws.ecr".equals(event.get("source")) && detail.containsKey("action-type")
                    && detail.containsKey("result")) {
                // Parsing an ECR image event
                String action = detail.get("action-type");
                String result = detail.get("result");
                if ("PUSH".equals(action) && "SUCCESS".equals(result)) {
                    validEvent = true;
                }
            } else if ("saas-boost".equals(event.get("source")) && detail.containsKey("tenantId")) {
                // Parsing an onboarding event
                validEvent = true;
            }
        }
        return validEvent;
    }

    protected static String imageName(String tenantId, String serviceName) {
        // Must match the name in the Task Definition for this imageUri
        // In CloudFormation we manipulate the service name to conform to rules on resource names
        serviceName = serviceName.replaceAll("[^0-9A-Za-z-]", "").toLowerCase();
        return "sb-" + SAAS_BOOST_ENV + "-tenant-" + tenantId.substring(0, tenantId.indexOf("-")) + "-" + serviceName;
    }

    protected static String imageUri(Map<String, Object> event) {
        String imageUri = null;
        Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        String accountId = (String) event.get("account");
        String region = (String) event.get("region");
        String repo = (String) detail.get("repository-name");
        String tag = (String) detail.get("image-tag");
        if (Utils.isNotBlank(accountId) && Utils.isNotBlank(region)
                && Utils.isNotBlank(repo) && Utils.isNotBlank(tag)) {
            imageUri = accountId + ".dkr.ecr." + region + ".amazonaws.com/" + repo + ":" + tag;
        }
        return imageUri;
    }

    protected static byte[] codePipelineArtifact(String imageName, String imageUri) {
        LOGGER.info("Creating imagedefinitions.json");
        String imageDefinitions = Utils.toJson(Collections.singletonList(
                Map.of("name", imageName, "imageUri", imageUri)
        ));
        LOGGER.info(imageDefinitions);

        // CodePipeline expects source input artifacts to be in a ZIP file
        LOGGER.info("Creating ZIP archive for CodePipeline");
        byte[] zip = zip(imageDefinitions);
        return zip;
    }

    protected static byte[] zip(String imagedefinitions) {
        byte[] archive;
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(stream);
            ZipEntry entry = new ZipEntry("imagedefinitions.json");
            zip.putNextEntry(entry);
            zip.write(imagedefinitions.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.close();
            archive = stream.toByteArray();
        } catch (IOException ioe) {
            LOGGER.error("Zip archive generation failed");
            throw new RuntimeException(Utils.getFullStackTrace(ioe));
        }
        return archive;
    }

    protected static void writeToArtifactBucket(S3Client s3, String bucket, String tenantId,
                                                String imageName, byte[] artifact) {
        String key = tenantId + "/" + imageName;
        LOGGER.info("Putting CodePipeline source artifact to S3 " + bucket + "/" + key);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build(),
                    RequestBody.fromBytes(artifact)
            );
        } catch (SdkServiceException s3error) {
            LOGGER.error("s3:PutObject " + Utils.getFullStackTrace(s3error));
            throw s3error;
        }
    }

    private static void triggerPipeline(CodePipelineClient codepipeline, String tenantId, String pipeline) {
        try {
            StartPipelineExecutionResponse response = codepipeline.startPipelineExecution(r -> r.name(pipeline));
            LOGGER.info("Started tenant {} pipeline {} {}", tenantId, pipeline, response.pipelineExecutionId());
        } catch (SdkServiceException codepipelineError) {
            LOGGER.error("codepipeline:StartPipeline", codepipelineError);
            LOGGER.error(Utils.getFullStackTrace(codepipelineError));
            throw codepipelineError;
        }
    }

    private static class Deployment {
        private final String tenantId;
        private final String imageName;
        private final String imageUri;
        private final String pipeline;

        public Deployment(String tenantId, String imageName, String imageUri, String pipeline) {
            this.tenantId = tenantId;
            this.imageName = imageName;
            this.imageUri = imageUri;
            this.pipeline = pipeline;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getImageName() {
            return imageName;
        }

        public String getImageUri() {
            return imageUri;
        }

        public String getPipeline() {
            return pipeline;
        }
    }

}