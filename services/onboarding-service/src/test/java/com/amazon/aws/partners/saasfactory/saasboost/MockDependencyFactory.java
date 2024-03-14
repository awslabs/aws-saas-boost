package com.amazon.aws.partners.saasfactory.saasboost;

import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

public class MockDependencyFactory implements OnboardingService.OnboardingServiceDependencyFactory {

    private S3Client s3;
    private EventBridgeClient eventBridge;
    private S3Presigner s3Presigner;
    private SqsClient sqs;
    private OnboardingDataAccessLayer dal;

    @Override
    public S3Client s3() {
        return s3;
    }

    @Override
    public EventBridgeClient eventBridge() {
        return eventBridge;
    }

    @Override
    public S3Presigner s3Presigner() {
        return s3Presigner;
    }

    @Override
    public SqsClient sqs() {
        return sqs;
    }

    @Override
    public OnboardingDataAccessLayer dal() {
        return dal;
    }

    public void setS3(S3Client s3) {
        this.s3 = s3;
    }

    public void setEventBridge(EventBridgeClient eventBridge) {
        this.eventBridge = eventBridge;
    }

    public void setS3Presigner(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    public void setSqs(SqsClient sqs) {
        this.sqs = sqs;
    }

    public void setDal(OnboardingDataAccessLayer dal) {
        this.dal = dal;
    }
}
