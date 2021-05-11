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
package com.amazon.aws.partners.saasfactory.saasboost;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.StartPipelineExecutionResponse;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EcsDeploy implements RequestHandler<Map<String, Object>, Object> {

    private final static Logger LOGGER = LoggerFactory.getLogger(EcsDeploy.class);
    private final static String AWS_REGION = System.getenv("AWS_REGION");
    private final static String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private final static String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private final static String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private final static String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");
    private S3Client s3;
    private CodePipelineClient codepipeline;
    private EcrClient ecr;
    private String codePipelineBucket;

    public EcsDeploy() {
        long startTimeMillis = System.currentTimeMillis();
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
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.s3 = Utils.sdkClient(S3Client.builder(), S3Client.SERVICE_NAME);
        this.codepipeline = Utils.sdkClient(CodePipelineClient.builder(), CodePipelineClient.SERVICE_NAME);
        this.ecr = Utils.sdkClient(EcrClient.builder(), EcrClient.SERVICE_NAME);

        // Get the CodePipeline artifact bucket
        Map<String, String> settings = null;
        ApiRequest getSettingsRequest = ApiRequest.builder()
                .resource("settings?setting=CODE_PIPELINE_BUCKET")
                .method("GET")
                .build();
        SdkHttpFullRequest getSettingsApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, getSettingsRequest);
        LOGGER.info("Fetching CodePipeline artifacts bucket from Settings Service");
        try {
            String functionName = "sb-" + SAAS_BOOST_ENV + "-workload-deploy-" + AWS_REGION;
            String getSettingsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(getSettingsApiRequest, API_TRUST_ROLE, functionName);
            ArrayList<Map<String, String>> getSettingsResponse = Utils.fromJson(getSettingsResponseBody, ArrayList.class);
            if (null == getSettingsResponse) {
                throw new RuntimeException("responseBody is invalid");
            }            
            settings = getSettingsResponse
                    .stream()
                    .collect(Collectors.toMap(
                            setting -> setting.get("name"), setting -> setting.get("value")
                    ));
        } catch (Exception e) {
            LOGGER.error("Error invoking API /" + System.getenv("API_GATEWAY_STAGE") + "/settings");
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
        codePipelineBucket = settings.get("CODE_PIPELINE_BUCKET");
        if (Utils.isBlank(codePipelineBucket)) {
            throw new RuntimeException("Missing required SaaS Boost parameter CODE_PIPELINE_BUCKET");
        }
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
	public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        // Is this event an ECR image action or a first-time deploy custom event?
        String source = (String) event.get("source");

        List<Deployment> deployments = new ArrayList<>();
        if ("aws.ecr".equals(source)) {
            String imageUri = parseEcrEvent(event);
            // We will deploy the image tagged as latest to every provisioned tenant
            if (imageUri != null && imageUri.endsWith("latest")) {
                ApiRequest provisionedTenants = ApiRequest.builder()
                        .resource("tenants/provisioned")
                        .method("GET")
                        .build();
                SdkHttpFullRequest getTenantsApiRequest = ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, provisionedTenants);
                LOGGER.info("Fetching Provisioned tenants from tenants/provisioned");
                try {
                    String functionName = "sb-" + SAAS_BOOST_ENV + "-workload-deploy-" + AWS_REGION;
                    String getTenantsResponseBody = ApiGatewayHelper.signAndExecuteApiRequest(getTenantsApiRequest, API_TRUST_ROLE, functionName);
                    ArrayList<Map<String, Object>> getTenantsResponse = Utils.fromJson(getTenantsResponseBody, ArrayList.class);
                    if (null == getTenantsResponse) {
                        throw new RuntimeException("responseBody is invalid");
                    }                    
                    for (Map<String, Object> tenant : getTenantsResponse) {
                        String tenantId = (String) tenant.get("id");
                        String tenantCodePipeline = "tenant-" + tenantId.substring(0, tenantId.indexOf("-"));
                        Deployment deployment = new Deployment(tenantId, imageUri, tenantCodePipeline);
                        deployments.add(deployment);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error invoking API /" + API_GATEWAY_STAGE + "/tenants/provisioned");
                    LOGGER.error(Utils.getFullStackTrace(e));
                    throw new RuntimeException(e);
                }
            } else {
                LOGGER.info("Image URI {} does not end with latest, skipping deployment", imageUri);
                return null;
            }
        } else if ("tenant-onboarding".equals(source)) {
            // Check to see if there are any images in the ECR repo before triggering the pipeline
            boolean hasImageToDeploy = false;
            String repo = ((Map<String, String>) event.get("detail")).get("repository-name");
            try {
                ListImagesResponse dockerImages = ecr.listImages(request -> request.repositoryName(repo));
                //ListImagesResponse::hasImageIds will return true if the imageIds object is not null
                if (dockerImages.hasImageIds() && !dockerImages.imageIds().isEmpty()) {
                    hasImageToDeploy = true;
                }
                LOGGER.info("ecr:ListImages {}", repo);
                for (Object imageId : dockerImages.imageIds()) {
                    LOGGER.info(imageId.toString());
                }
            } catch (SdkServiceException ecrError) {
                LOGGER.error("ecr::ListImages error", ecrError.getMessage());
                LOGGER.error(Utils.getFullStackTrace(ecrError));
                throw ecrError;
            }
            if (hasImageToDeploy) {
                String imageUri = parseTenantOnboardingEvent(event);
                if (imageUri != null) {
                    Map<String, String> detail = (Map<String, String>) event.get("detail");
                    String tenantId = detail.get("tenantId");
                    //String tenantCodePipeline = detail.get("pipeline");
                    String tenantCodePipeline = "tenant-" + tenantId.substring(0, tenantId.indexOf("-"));
                    Deployment deployment = new Deployment(tenantId, imageUri, tenantCodePipeline);
                    deployments.add(deployment);
                }
            } else {
                LOGGER.info("Skipping CodePipeline for tenant onboarding. No images in ECR.");
            }
        }

        if (!deployments.isEmpty()) {
            LOGGER.info("Deploying for " + deployments.size() + " tenants");
            for (Deployment deployment : deployments) {
                String tenantId = deployment.getTenantId();

                // Create an imagedefinitions.json document for the newly pushed image
                byte[] zip = codePipelineArtifact(tenantId, deployment.getImageUri());

                // Write the imagedefinitions.json document to the artifact bucket
                writeToArtifactBucket(tenantId, zip);

                // Trigger CodePipeline for this tenant
                triggerPipeline(tenantId, deployment.getPipeline());
            }
        } else {
            LOGGER.info("No active, provisioned tenants to deploy to {}", deployments.size());
        }

        return null;
    }

    private String parseEcrEvent(Map<String, Object> event) {
        String imageUri = null;
        Map<String, String> detail = (Map<String, String>) event.get("detail");
        String action = detail.get("action-type");
        String result = detail.get("result");
        if ("PUSH".equals(action) && "SUCCESS".equals(result)) {
            LOGGER.info("Processing ECR image {}, status {}", action, result);
            String accountId = (String) event.get("account");
            String region = (String) event.get("region");
            String repo = detail.get("repository-name");
            String tag = detail.get("image-tag");
            imageUri = accountId + ".dkr.ecr." + region + ".amazonaws.com/" + repo + ":" + tag;
        }
        return imageUri;
    }

    private String parseTenantOnboardingEvent(Map<String, Object> event) {
        String imageUri = null;
        Map<String, String> detail = (Map<String, String>) event.get("detail");
        String tenantId = detail.get("tenantId");
        LOGGER.info("Processing Tenant Onboarding {}", tenantId);
        String accountId = (String) event.get("account");
        String region = (String) event.get("region");
        String repo = detail.get("repository-name");
        String tag = detail.get("image-tag");
        if (!empty(accountId) && !empty(region) && !empty(repo) && !empty(tag)) {
            imageUri = accountId + ".dkr.ecr." + region + ".amazonaws.com/" + repo + ":" + tag;
        }
        return imageUri;
    }

    private byte[] codePipelineArtifact(String tenantId, String imageUri) {
        LOGGER.info("Creating imagedefinitions.json");
        String imageName = "tenant-" + tenantId.substring(0, tenantId.indexOf("-"));
        String imageDefinitions = String.format("[{\"name\":\"%s\",\"imageUri\":\"%s\"}]", imageName, imageUri);
        LOGGER.info(imageDefinitions);

        // CodePipeline expects source input artifacts to be in a ZIP file
        LOGGER.info("Creating ZIP archive for CodePipeline");
        byte[] zip = zip(imageDefinitions);
        return zip;
    }

    private byte[] zip(String imagedefinitions) {
        byte[] archive = null;
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(stream);
            ZipEntry entry = new ZipEntry("imagedefinitions.json");
            zip.putNextEntry(entry);
            zip.write(imagedefinitions.getBytes());
            zip.closeEntry();
            zip.close();
            archive = stream.toByteArray();
        } catch (IOException ioe) {
            LOGGER.error("Zip archive generation failed");
            throw new RuntimeException(Utils.getFullStackTrace(ioe));
        }
        return archive;
    }

    private void writeToArtifactBucket(String tenantId, byte[] artifact) {
        String key = tenantId + "/tenant-" + tenantId.substring(0, tenantId.indexOf("-"));
        LOGGER.info("Putting CodePipeline source artifact to S3 " + codePipelineBucket + "/" + key);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(codePipelineBucket)
                            .key(key)
                            .build()
                    , RequestBody.fromBytes(artifact)
            );
        } catch (SdkServiceException s3error) {
            LOGGER.error("s3:PutObject " + Utils.getFullStackTrace(s3error));
            throw s3error;
        }
    }

    private void triggerPipeline(String tenantId, String pipeline) {
        try {
            StartPipelineExecutionResponse tenantPipeline = codepipeline.startPipelineExecution(r -> r.name(pipeline));
            LOGGER.info("{\"message\":\"Tenant Deploy Pipeline\",\"tenantId\":\"" + tenantId + "\",\"status\":\"Started\",\"ExecutionId\":\"" + tenantPipeline.pipelineExecutionId() + "\"}");
        } catch (SdkServiceException codepipelineError) {
            LOGGER.error("codepipeline:StartPipeline " + Utils.getFullStackTrace(codepipelineError));
            throw codepipelineError;
        }
    }

    private static boolean empty(String str) {
        boolean empty = true;
        if (str != null && !str.isBlank()) {
            empty = false;
        }
        return empty;
    }

    private static class Deployment {
        private final String tenantId;
        private final String imageUri;
        private final String pipeline;

        public Deployment(String tenantId, String imageUri, String pipeline) {
            this.tenantId = tenantId;
            this.imageUri = imageUri;
            this.pipeline = pipeline;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getImageUri() {
            return imageUri;
        }

        public String getPipeline() {
            return pipeline;
        }
    }

}