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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.policybuilder.iam.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Path;

import static com.amazon.aws.partners.saasfactory.saasboost.Utils.getFullStackTrace;

public class SaaSBoostArtifactsBucket {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSBoostArtifactsBucket.class);

    private final String bucketName;
    private final Region region;
    private final String appPlaneAccountId;

    public SaaSBoostArtifactsBucket(String bucketName, Region region) {
        this(bucketName, region, null);
    }

    public SaaSBoostArtifactsBucket(String bucketName, Region region, String appPlaneAccountId) {
        this.bucketName = bucketName;
        this.region = region;
        this.appPlaneAccountId = appPlaneAccountId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public Region getRegion() {
        return region;
    }

    public String getAppPlaneAccountId() {
        return appPlaneAccountId;
    }

    public String toString() {
        return getBucketName();
    }

    /**
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-bucket-intro.html">S3 Documentation</a>
     * @return the S3 URL the Bucket object represents
     */
    public String getBucketUrl() {
        return String.format("https://%s.s3.%s.%s/", bucketName, region, Utils.endpointSuffix(region));
    }

    public void putFile(S3Client s3, Path localPath, Path remotePath) {
        try {
            LOGGER.debug("Putting {} to Artifacts bucket: {}", localPath, this);
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    // java.nio.file.Path will use OS dependent file separators, so when we run the installer on
                    // Windows, the S3 key will have backslashes instead of forward slashes. The CloudFormation
                    // definitions of the Lambda functions will always use forward slashes for the S3Key property.
                    .key(remotePath.toString().replace('\\', '/'))
                    .build(), RequestBody.fromFile(localPath)
            );
        } catch (SdkServiceException s3Error) {
            LOGGER.error("s3:PutObject error {}", s3Error.getMessage());
            LOGGER.error(getFullStackTrace(s3Error));
            throw s3Error;
        }
    }

    protected static SaaSBoostArtifactsBucket createS3ArtifactBucket(S3Client s3, String envName, Region awsRegion
            , String appPlaneAccountId) {
        String s3ArtifactBucketName = "sb-" + envName + "-artifacts-" + Utils.randomString(12, "[^a-z0-9]");
        try {
            CreateBucketRequest.Builder createBucketRequestBuilder = CreateBucketRequest.builder();
            // LocationConstraint is not valid in US_EAST_1
            // https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/model/BucketLocationConstraint.html
            if (!(awsRegion.equals(Region.AWS_GLOBAL) || awsRegion.equals(Region.US_EAST_1))) {
                createBucketRequestBuilder.createBucketConfiguration(config ->
                        config.locationConstraint(BucketLocationConstraint.fromValue(awsRegion.id())));
            }
            createBucketRequestBuilder.bucket(s3ArtifactBucketName);
            LOGGER.info("Creating S3 artifact bucket {}", s3ArtifactBucketName);
            s3.createBucket(createBucketRequestBuilder.build());
            LOGGER.info("Enabling EventBridge bucket notifications {}", s3ArtifactBucketName);
            s3.putBucketNotificationConfiguration(PutBucketNotificationConfigurationRequest.builder()
                    .bucket(s3ArtifactBucketName)
                    .notificationConfiguration(NotificationConfiguration.builder()
                            .eventBridgeConfiguration(EventBridgeConfiguration.builder().build())
                            .build())
                    .build());
            LOGGER.info("Setting default bucket encryption {}", s3ArtifactBucketName);
            s3.putBucketEncryption(PutBucketEncryptionRequest.builder()
                    .bucket(s3ArtifactBucketName)
                    .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                            .rules(ServerSideEncryptionRule.builder()
                                    .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder()
                                            .sseAlgorithm(ServerSideEncryption.AES256)
                                            .build())
                                    .build())
                            .build())
                    .build());
            String partitionName = awsRegion.metadata().partition().id();
            IamPolicy policy = IamPolicy.builder()
                    .addStatement(statement -> statement
                            .sid("DenyNonHttps")
                            .effect(IamEffect.DENY)
                            .addPrincipal(IamPrincipal.ALL)
                            .addAction("s3:*")
                            .addResource("arn:" + partitionName + ":s3:::" + s3ArtifactBucketName + "/*")
                            .addResource("arn:" + partitionName + ":s3:::" + s3ArtifactBucketName)
                            .addCondition(condition -> condition
                                    .operator(IamConditionOperator.BOOL)
                                    .key("aws:SecureTransport")
                                    .value("false")
                            )
                    )
                    .addStatement(statement -> statement
                            .sid("AppPlaneAccountQuickLink")
                            .effect(IamEffect.ALLOW)
                            .addPrincipal(IamPrincipalType.AWS, "arn:aws:iam::" + appPlaneAccountId + ":root")
                            .addAction("s3:GetObject")
                            .addResource("arn:" + partitionName + ":s3:::" + s3ArtifactBucketName + "/saas-boost-app-integration.yaml")
                    )
                    .build();
            String bucketPolicy = policy.toJson(IamPolicyWriter.builder().prettyPrint(true).build());
            LOGGER.info("Creating bucket policy {}", s3ArtifactBucketName);
            LOGGER.info(bucketPolicy);
            s3.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .policy(bucketPolicy)
                    .bucket(s3ArtifactBucketName)
                    .build());
        } catch (SdkServiceException s3Error) {
            LOGGER.error("s3 error {}", s3Error.getMessage());
            LOGGER.error(getFullStackTrace(s3Error));
            //TODO delete bucket if that step worked but the other settings failed
            throw s3Error;
        }
        return new SaaSBoostArtifactsBucket(s3ArtifactBucketName, awsRegion, appPlaneAccountId);
    }
}
