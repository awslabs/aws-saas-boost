package com.amazon.aws.partners.saasfactory.saasboost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.waiters.Waiter;
import software.amazon.awssdk.core.waiters.WaiterAcceptor;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.BatchGetBuildsRequest;
import software.amazon.awssdk.services.codebuild.model.BatchGetBuildsResponse;
import software.amazon.awssdk.services.codebuild.model.Build;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CodeBuildWaiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeBuildWaiter.class);
    private final CodeBuildClient client;
    private final Waiter<BatchGetBuildsResponse> buildCompleteWaiter;

    public CodeBuildWaiter(CodeBuildClient client) {
        this.client = client;
        this.buildCompleteWaiter = Waiter.builder(BatchGetBuildsResponse.class)
                .overrideConfiguration(buildCompleteWaiterConfig(null))
                .acceptors(buildCompleteWaiterAcceptors())
                .build();
    }

    WaiterResponse<BatchGetBuildsResponse> waitUntilBuildComplete(BatchGetBuildsRequest batchGetBuildsRequest) {
        return buildCompleteWaiter.run(() -> client.batchGetBuilds(batchGetBuildsRequest));
    }

    private static String errorCode(Throwable error) {
        if (error instanceof AwsServiceException) {
            return ((AwsServiceException) error).awsErrorDetails().errorCode();
        }
        return null;
    }

    private static WaiterOverrideConfiguration buildCompleteWaiterConfig(WaiterOverrideConfiguration overrideConfig) {
        Optional<WaiterOverrideConfiguration> optionalOverrideConfig = Optional.ofNullable(overrideConfig);
        int maxAttempts = optionalOverrideConfig
                .flatMap(WaiterOverrideConfiguration::maxAttempts)
                .orElse(30);
        BackoffStrategy backoffStrategy = optionalOverrideConfig
                .flatMap(WaiterOverrideConfiguration::backoffStrategy)
                .orElse(FixedDelayBackoffStrategy.create(Duration.ofSeconds(20)));
        Duration waitTimeout = optionalOverrideConfig
                .flatMap(WaiterOverrideConfiguration::waitTimeout)
                .orElse(null);
        return WaiterOverrideConfiguration.builder()
                .maxAttempts(maxAttempts)
                .backoffStrategy(backoffStrategy)
                .waitTimeout(waitTimeout)
                .build();
    }

    private static List<WaiterAcceptor<? super BatchGetBuildsResponse>> buildCompleteWaiterAcceptors() {
        List<WaiterAcceptor<? super BatchGetBuildsResponse>> result = new ArrayList<>();
        result.add(WaiterAcceptor.successOnResponseAcceptor(response -> {
            String currentPhase = null;
            if (!response.builds().isEmpty()) {
                currentPhase = response.builds().get(0).currentPhase();
            }
            long completeBuilds = response.builds().stream().filter(Build::buildComplete).count();
            LOGGER.debug("Builds {} Builds Not Found {} Complete Builds {} Current Phase {}",
                    response.builds().size(), response.buildsNotFound().size(), completeBuilds, currentPhase);
            return completeBuilds > 0;
        }));
        result.add(WaiterAcceptor.retryOnExceptionAcceptor(error ->
                Objects.equals(errorCode(error), "ResourceNotFoundException")));
        result.add(WaiterAcceptor.retryOnResponseAcceptor(response -> true));
        return result;
    }
}
