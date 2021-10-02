package com.amazon.aws.partners.saasfactory.saasboost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.nio.file.Path;
import java.util.UUID;

import static com.amazon.aws.partners.saasfactory.saasboost.SaaSBoostInstall.getFullStackTrace;

public class SaaSBoostArtifactsBucket {

    private static Logger LOGGER = LoggerFactory.getLogger(SaaSBoostArtifactsBucket.class);

    private final S3Client s3;
    private final String bucketName;
    private final Region region;

    public SaaSBoostArtifactsBucket(S3Client s3, String bucketName, Region region) {
        this.s3 = s3;
        this.bucketName = bucketName;
        this.region = region;
    }

    public String getBucketName() {
        return bucketName;
    }

    /**
     *
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-bucket-intro.html">S3 Documentation</a>
     * @return
     */
    public String getBucketUrl() {
        return String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
    }

    public void putFile(Path localPath, Path remotePath) {
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(remotePath.toString())
                    .build(), RequestBody.fromFile(localPath)
            );
        } catch (SdkServiceException s3Error) {
            LOGGER.error("s3:PutObject error {}", s3Error.getMessage());
            LOGGER.error(getFullStackTrace(s3Error));
            throw s3Error;
        }
    }

    protected static SaaSBoostArtifactsBucket createS3ArtifactBucket(S3Client s3, String envName, Region awsRegion) {
        UUID uniqueId = UUID.randomUUID();
        String[] parts = uniqueId.toString().split("-");  //UUID 29219402-d9e2-4727-afec-2cd61f54fa8f

        String s3ArtifactBucketName = "sb-" + envName + "-artifacts-" + parts[0] + "-" + parts[1];
        LOGGER.info("Creating S3 Artifact Bucket {}", s3ArtifactBucketName);
        try {
            CreateBucketRequest.Builder createBucketRequestBuilder = CreateBucketRequest.builder();
            if (!(awsRegion.equals(Region.AWS_GLOBAL) || awsRegion.equals(Region.US_EAST_1))) {
                createBucketRequestBuilder.createBucketConfiguration(config ->
                        config.locationConstraint(BucketLocationConstraint.fromValue(awsRegion.id())));
            }
            createBucketRequestBuilder.bucket(s3ArtifactBucketName);
            s3.createBucket(createBucketRequestBuilder.build());
            s3.putBucketEncryption(request -> request
                    .serverSideEncryptionConfiguration(
                            config -> config.rules(rules -> rules
                                    .applyServerSideEncryptionByDefault(encrypt -> encrypt
                                            .sseAlgorithm(ServerSideEncryption.AES256)
                                    )
                            )
                    )
                    .bucket(s3ArtifactBucketName)
            );
            s3.putBucketPolicy(request -> request
                    .policy("{\n" +
                            "    \"Version\": \"2012-10-17\",\n" +
                            "    \"Statement\": [\n" +
                            "        {\n" +
                            "            \"Sid\": \"DenyNonHttps\",\n" +
                            "            \"Effect\": \"Deny\",\n" +
                            "            \"Principal\": \"*\",\n" +
                            "            \"Action\": \"s3:*\",\n" +
                            "            \"Resource\": [\n" +
                            "                \"arn:aws:s3:::" + s3ArtifactBucketName + "/*\",\n" +
                            "                \"arn:aws:s3:::" + s3ArtifactBucketName + "\"\n" +
                            "            ],\n" +
                            "            \"Condition\": {\n" +
                            "                \"Bool\": {\n" +
                            "                    \"aws:SecureTransport\": \"false\"\n" +
                            "                }\n" +
                            "            }\n" +
                            "        }\n" +
                            "    ]\n" +
                            "}")
                    .bucket(s3ArtifactBucketName)
            );
        } catch (SdkServiceException s3Error) {
            LOGGER.error("s3 error {}", s3Error.getMessage());
            LOGGER.error(getFullStackTrace(s3Error));
            throw s3Error;
        }
        return new SaaSBoostArtifactsBucket(s3, s3ArtifactBucketName, awsRegion);
    }
}
