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

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.utils.StringInputStream;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApiGatewayHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayHelper.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final Aws4Signer SIG_V4 = Aws4Signer.create();
    private static SdkHttpClient HTTP_CLIENT = UrlConnectionHttpClient.create();
    private static final StsClient sts = StsClient.builder()
            .httpClient(HTTP_CLIENT)
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .region(Region.of(AWS_REGION))
            .endpointOverride(URI.create("https://" + StsClient.SERVICE_NAME + "." + Region.of(AWS_REGION).toString() + ".amazonaws.com"))
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .retryPolicy(RetryPolicy.builder()
                            .backoffStrategy(BackoffStrategy.defaultStrategy())
                            .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy())
                            .numRetries(SdkDefaultRetrySetting.defaultMaxAttempts())
                            .retryCondition(RetryCondition.defaultRetryCondition())
                            .build()
                    )
                    .build()
            )
            .build();

    private ApiGatewayHelper() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_ENV");
        }
    }

    public static String signAndExecuteApiRequest(SdkHttpFullRequest apiRequest, String assumedRole, String context) {
        SdkHttpFullRequest signedApiRequest = signApiRequest(apiRequest, assumedRole, context);
        return executeApiRequest(apiRequest, signedApiRequest);
    }

    public static String executeApiRequest(SdkHttpFullRequest apiRequest) {
        return executeApiRequest(apiRequest, null);
    }

    private static String executeApiRequest(SdkHttpFullRequest apiRequest, SdkHttpFullRequest signedApiRequest) {
        HttpExecuteRequest.Builder requestBuilder = HttpExecuteRequest.builder().request(signedApiRequest != null ? signedApiRequest : apiRequest);
        apiRequest.contentStreamProvider().ifPresent(c -> requestBuilder.contentStreamProvider(c));
        HttpExecuteRequest apiExecuteRequest = requestBuilder.build();
        BufferedReader responseReader = null;
        String responseBody;
        try {
            HttpExecuteResponse apiResponse = HTTP_CLIENT.prepareRequest(apiExecuteRequest).call();
            responseReader = new BufferedReader(new InputStreamReader(apiResponse.responseBody().get(), StandardCharsets.UTF_8));
            responseBody = responseReader.lines().collect(Collectors.joining());
            LOGGER.info(responseBody);
            if (!apiResponse.httpResponse().isSuccessful()) {
                throw new RuntimeException("{\"statusCode\":" + apiResponse.httpResponse().statusCode() + ", \"message\":\"" + apiResponse.httpResponse().statusText().get() + "\"}");
            }
        } catch (IOException ioe) {
            LOGGER.error("HTTP Client error {}", ioe.getMessage());
            LOGGER.error(Utils.getFullStackTrace(ioe));
            throw new RuntimeException(ioe);
        } finally {
            if (responseReader != null) {
                try {
                    responseReader.close();
                } catch (IOException ioe) {
                    // swallow
                }
            }
        }

        return responseBody;
    }

    public static SdkHttpFullRequest getApiRequest(String host, String stage, ApiRequest request) {
        return getApiRequest(host, stage, request.getResource(), request.getMethod(), request.getHeaders(), request.getBody());
    }

    public static SdkHttpFullRequest getApiRequest(String host, String stage, String resource, SdkHttpMethod method, Map<String, String> headers, String body) {
        SdkHttpFullRequest apiRequest;
        String protocol = "https";
        try {
            URL url = new URL(protocol, host, stage + "/" + resource);
            SdkHttpFullRequest.Builder sdkRequestBuilder = SdkHttpFullRequest.builder()
                    .protocol(protocol)
                    .host(host)
                    .encodedPath(url.getPath())
                    .method(method);
            appendQueryParams(sdkRequestBuilder, url);
            putHeaders(sdkRequestBuilder, headers);
            if (body != null) {
                sdkRequestBuilder.putHeader("Content-Type", "application/json; charset=utf-8");
                sdkRequestBuilder.contentStreamProvider(() -> new StringInputStream(body));
            }
            apiRequest = sdkRequestBuilder.build();
        } catch (MalformedURLException mue) {
            LOGGER.error("URL parse error {}", mue.getMessage());
            LOGGER.error(Utils.getFullStackTrace(mue));
            throw new RuntimeException(mue);
        } catch (URISyntaxException use) {
            LOGGER.error("URI parse error {}", use.getMessage());
            LOGGER.error(Utils.getFullStackTrace(use));
            throw new RuntimeException(use);
        }
        return apiRequest;
    }

    public static SdkHttpFullRequest signApiRequest(SdkHttpFullRequest apiRequest, String assumedRole, String context) {
        Aws4SignerParams sigV4Params = Aws4SignerParams.builder()
                .signingName("execute-api")
                .signingRegion(Region.of(AWS_REGION))
                .awsCredentials(getTemporaryCredentials(assumedRole, context))
                .build();
        SdkHttpFullRequest signedApiRequest = SIG_V4.sign(apiRequest, sigV4Params);
        return signedApiRequest;
    }

    protected static AwsCredentials getTemporaryCredentials(final String assumedRole, final String context) {
        AwsCredentials systemCredentials = null;

        //LOGGER.info("Calling AssumeRole for {}", assumedRole);
        // Calling STS here instead of in the constructor so we can name the
        // temporary session with the request context for CloudTrail logging
        try {
            AssumeRoleResponse response = sts.assumeRole(request -> request
                    .roleArn(assumedRole)
                    .durationSeconds(900)
                    .roleSessionName((Utils.isNotBlank(context)) ? context : SAAS_BOOST_ENV)
            );

            //AssumedRoleUser assumedUser = response.assumedRoleUser();
            //LOGGER.info("Assumed IAM User {}", assumedUser.arn());
            //LOGGER.info("Assumed IAM Role {}", assumedUser.assumedRoleId());

            // Could use STSAssumeRoleSessionCredentialsProvider here, but this
            // lambda will timeout before we need to refresh the temporary creds
            Credentials temporaryCredentials = response.credentials();
            systemCredentials = AwsSessionCredentials.create(
                    temporaryCredentials.accessKeyId(),
                    temporaryCredentials.secretAccessKey(),
                    temporaryCredentials.sessionToken());
        } catch (SdkServiceException stsError) {
            LOGGER.error("sts::AssumeRole error {}", stsError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(stsError));
            throw stsError;
        }
        return systemCredentials;
    }

    protected static void appendQueryParams(SdkHttpFullRequest.Builder sdkRequestBuilder, URL url) throws URISyntaxException {
        List<NameValuePair> queryParams = URLEncodedUtils.parse(url.toURI(), StandardCharsets.UTF_8);
        if (queryParams != null) {
            for (NameValuePair queryParam : queryParams) {
                sdkRequestBuilder.appendRawQueryParameter(queryParam.getName(), queryParam.getValue());
            }
        }
    }

    protected static void putHeaders(SdkHttpFullRequest.Builder sdkRequestBuilder, Map<String, String> headers) {
        if (sdkRequestBuilder != null && headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                sdkRequestBuilder.putHeader(header.getKey(), header.getValue());
            }
        }
    }
}
