package com.amazon.aws.partners.saasfactory.saasboost;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Path;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class SaaSBoostArtifactsBucketTest {

    private static final String ENV_NAME = "env-name";

    @Mock
    S3Client mockS3;

    @Before
    public void reset() {
        Mockito.reset(mockS3);
    }

    @Test
    public void putFileTest() throws Exception {
        ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBodyArgumentCaptor = ArgumentCaptor.forClass(RequestBody.class);

        SaaSBoostArtifactsBucket testBucket =
                SaaSBoostArtifactsBucket.createS3ArtifactBucket(mockS3, ENV_NAME, Region.US_EAST_1);
        Path localPathToTestPut = Path.of(this.getClass().getClassLoader().getResource("template.yaml").toURI());
        Path exampleRemotePath = Path.of("dir", "dir2");
        testBucket.putFile(mockS3, localPathToTestPut, exampleRemotePath);
        Mockito.verify(mockS3).putObject(putObjectRequestArgumentCaptor.capture(), requestBodyArgumentCaptor.capture());
        assertEquals("Put object to the wrong bucket.",
                testBucket.getBucketName(), putObjectRequestArgumentCaptor.getValue().bucket());
        assertEquals("Put object to the wrong location.",
                exampleRemotePath.toString(), putObjectRequestArgumentCaptor.getValue().key());
        assertEquals("Put different length object to remote location. Wrong file?",
                localPathToTestPut.toFile().length(), requestBodyArgumentCaptor.getValue().contentLength());
    }

    @Test
    public void createBucketLocationConstraintTest() {
        ArgumentCaptor<CreateBucketRequest> createBucketRequestArgumentCaptor =
                ArgumentCaptor.forClass(CreateBucketRequest.class);

        SaaSBoostArtifactsBucket.createS3ArtifactBucket(mockS3, ENV_NAME, Region.US_EAST_1);
        Mockito.verify(mockS3).createBucket(createBucketRequestArgumentCaptor.capture());
        // expected, actual
        CreateBucketRequest capturedCreateBucketRequest = createBucketRequestArgumentCaptor.getValue();
        if (capturedCreateBucketRequest.createBucketConfiguration() != null) {
            // if no createBucketConfiguration is passed in the createBucketRequest,
            // there is implicitly no location constraint (because constraint must be part of the config)
            assertNull("No location constraint should be provided for buckets in us-east-1",
                    createBucketRequestArgumentCaptor.getValue().createBucketConfiguration().locationConstraint());
        }
        Mockito.reset(mockS3);

        SaaSBoostArtifactsBucket.createS3ArtifactBucket(mockS3, ENV_NAME, Region.US_WEST_2);
        Mockito.verify(mockS3).createBucket(createBucketRequestArgumentCaptor.capture());
        assertEquals("Location constraint should be provided for buckets in us-west-2",
                BucketLocationConstraint.US_WEST_2,
                createBucketRequestArgumentCaptor.getValue().createBucketConfiguration().locationConstraint());
    }

    @Test
    public void createBucketServerSideEncryptionTest() {
        ArgumentCaptor<PutBucketEncryptionRequest> putBucketEncryptionRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutBucketEncryptionRequest.class);
        SaaSBoostArtifactsBucket createdBucket =
                SaaSBoostArtifactsBucket.createS3ArtifactBucket(mockS3, ENV_NAME, Region.US_EAST_1);
        Mockito.verify(mockS3).putBucketEncryption(putBucketEncryptionRequestArgumentCaptor.capture());
        PutBucketEncryptionRequest capturedPutBucketEncryptionRequest =
                putBucketEncryptionRequestArgumentCaptor.getValue();
        assertEquals("Put encryption to the wrong bucket.",
                createdBucket.getBucketName(), capturedPutBucketEncryptionRequest.bucket());
        assertNotNull(capturedPutBucketEncryptionRequest.serverSideEncryptionConfiguration());
        assertNotNull(capturedPutBucketEncryptionRequest.serverSideEncryptionConfiguration().rules());
        assertTrue(capturedPutBucketEncryptionRequest.serverSideEncryptionConfiguration().rules().contains(
                ServerSideEncryptionRule.builder().applyServerSideEncryptionByDefault(
                        ServerSideEncryptionByDefault.builder().sseAlgorithm(ServerSideEncryption.AES256).build()
                ).build()));
    }

    @Test
    public void createBucketBucketPolicyTest() {
        ArgumentCaptor<PutBucketPolicyRequest> putBucketPolicyArgumentCaptor =
                ArgumentCaptor.forClass(PutBucketPolicyRequest.class);
        SaaSBoostArtifactsBucket createdBucket =
                SaaSBoostArtifactsBucket.createS3ArtifactBucket(mockS3, ENV_NAME, Region.US_EAST_1);
        Mockito.verify(mockS3).putBucketPolicy(putBucketPolicyArgumentCaptor.capture());
        PutBucketPolicyRequest capturedPutBucketPolicyRequest = putBucketPolicyArgumentCaptor.getValue();
        assertEquals("Put bucket policy to the wrong bucket.",
                createdBucket.getBucketName(), capturedPutBucketPolicyRequest.bucket());
        assertNotNull(capturedPutBucketPolicyRequest.policy());
        assertEquals("{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Sid\": \"DenyNonHttps\",\n" +
                "            \"Effect\": \"Deny\",\n" +
                "            \"Principal\": \"*\",\n" +
                "            \"Action\": \"s3:*\",\n" +
                "            \"Resource\": [\n" +
                "                \"arn:aws:s3:::" + createdBucket.getBucketName() + "/*\",\n" +
                "                \"arn:aws:s3:::" + createdBucket.getBucketName() + "\"\n" +
                "            ],\n" +
                "            \"Condition\": {\n" +
                "                \"Bool\": {\n" +
                "                    \"aws:SecureTransport\": \"false\"\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}", capturedPutBucketPolicyRequest.policy());
    }
}
