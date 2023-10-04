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

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.utils.StringInputStream;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ApiGatewayHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayHelper.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final Aws4Signer SIG_V4 = Aws4Signer.create();
    private static SdkHttpClient HTTP_CLIENT = UrlConnectionHttpClient.create();
    private final Map<String, Map<String, Object>> CLIENT_CREDENTIALS_CACHE = new HashMap<>();
    private SecretsManagerClient secrets;
    private StsClient sts;
    private String protocol;
    private String host;
    private String stage;
    private AppClient appClient;
    private String signingRole;

    private ApiGatewayHelper() {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_ENV");
        }
    }

    public static ApiGatewayHelper clientCredentialsHelper(String appClientSecretArn) {
        ApiGatewayHelper helper = new ApiGatewayHelper();
        helper.secrets = Utils.sdkClient(SecretsManagerClient.builder(), SecretsManagerClient.SERVICE_NAME);
        // Fetch the app client details from SecretsManager
        try {
            GetSecretValueResponse response = helper.secrets.getSecretValue(request -> request
                    .secretId(appClientSecretArn)
            );
            Map<String, String> clientDetails = Utils.fromJson(response.secretString(), LinkedHashMap.class);
            helper.appClient = AppClient.builder()
                    .clientName(clientDetails.get("client_name"))
                    .clientId(clientDetails.get("client_id"))
                    .clientSecret(clientDetails.get("client_secret"))
                    .tokenEndpoint(clientDetails.get("token_endpoint"))
                    .apiEndpoint(clientDetails.get("api_endpoint"))
                    .build();
            helper.protocol = helper.appClient.getApiEndpointUrl().getProtocol();
            helper.host = helper.appClient.getApiEndpointUrl().getHost();
            helper.stage = helper.appClient.getApiEndpointUrl().getPath().substring(1);
        } catch (SdkServiceException secretsManagerError) {
            LOGGER.error(Utils.getFullStackTrace(secretsManagerError));
            throw secretsManagerError;
        }
        return helper;
    }

    public static ApiGatewayHelper iamCredentialsHelper(String signingRoleArn, URL apiGatewayUrl) {
        ApiGatewayHelper helper = new ApiGatewayHelper();
        helper.sts = Utils.sdkClient(StsClient.builder(), StsClient.SERVICE_NAME);
        helper.signingRole = signingRoleArn;
        helper.protocol = apiGatewayUrl.getProtocol();
        helper.host = apiGatewayUrl.getHost();
        helper.stage = apiGatewayUrl.getPath().substring(1);
        return helper;
    }

    public String authorizedRequest(String method, String resource) {
        return authorizedRequest(method, resource, null);
    }

    public String authorizedRequest(String method, String resource, String body) {
        if (appClient == null) {
            throw new IllegalStateException("Missing appClient details");
        }
        return executeApiRequest(
                toSdkHttpFullRequest(HttpRequest.builder()
                        .protocol(protocol)
                        .host(host)
                        .stage(stage)
                        .headers(Map.of("Authorization", getClientCredentialsBearerToken(appClient)))
                        .method(method)
                        .resource(resource)
                        .body(body)
                        .build()
                )
        );
    }

    public String signedRequest(String method, String resource) {
        return signedRequest(method, resource, null);
    }

    public String signedRequest(String method, String resource, String body) {
        if (Utils.isBlank(signingRole)) {
            throw new IllegalStateException("Missing IAM role ARN");
        }
        StackWalker.StackFrame frame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stack -> stack
                        .limit(8)
                        .skip(1)
                        .findFirst()
                        .orElse(null)
                );
        String assumeRoleSessionName;
        if (frame != null) {
            assumeRoleSessionName = frame.getClassName() + "." + frame.getMethodName();
        } else {
            assumeRoleSessionName = "Unknown caller in " + SAAS_BOOST_ENV;
        }
        return signAndExecuteApiRequest(
                toSdkHttpFullRequest(HttpRequest.builder()
                        .protocol(protocol)
                        .host(host)
                        .stage(stage)
                        .method(method)
                        .resource(resource)
                        .body(body)
                        .build()
                ),
                signingRole,
                assumeRoleSessionName
        );
    }

    public String anonymousRequest(String method, String resource) {
        return anonymousRequest(method, resource, null);
    }

    public String anonymousRequest(String method, String resource, String body) {
        return executeApiRequest(
                toSdkHttpFullRequest(HttpRequest.builder()
                        .protocol(protocol)
                        .host(host)
                        .stage(stage)
                        .method(method)
                        .resource(resource)
                        .body(body)
                        .build()
                )
        );
    }

    protected String signAndExecuteApiRequest(SdkHttpFullRequest apiRequest, String assumedRole, String context) {
        SdkHttpFullRequest signedApiRequest = signApiRequest(apiRequest, assumedRole, context);
        return executeApiRequest(apiRequest, signedApiRequest);
    }

    protected String executeApiRequest(SdkHttpFullRequest apiRequest) {
        return executeApiRequest(apiRequest, null);
    }

    protected String executeApiRequest(SdkHttpFullRequest apiRequest, SdkHttpFullRequest signedApiRequest) {
        HttpExecuteRequest.Builder requestBuilder = HttpExecuteRequest.builder()
                .request(signedApiRequest != null ? signedApiRequest : apiRequest);
        apiRequest.contentStreamProvider().ifPresent(requestBuilder::contentStreamProvider);
        HttpExecuteRequest apiExecuteRequest = requestBuilder.build();
        BufferedReader responseReader = null;
        String responseBody;
        try {
            LOGGER.debug("Executing API Request {}", apiExecuteRequest.httpRequest().getUri().toString());
            HttpExecuteResponse apiResponse = HTTP_CLIENT.prepareRequest(apiExecuteRequest).call();
            responseReader = new BufferedReader(new InputStreamReader(apiResponse.responseBody().get(),
                    StandardCharsets.UTF_8));
            responseBody = responseReader.lines().collect(Collectors.joining());
            //LOGGER.debug(responseBody);
            if (!apiResponse.httpResponse().isSuccessful()) {
                throw new RuntimeException("{\"statusCode\":" + apiResponse.httpResponse().statusCode()
                        + ", \"message\":\"" + apiResponse.httpResponse().statusText().orElse("") + "\"}");
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

    protected SdkHttpFullRequest toSdkHttpFullRequest(HttpRequest request) {
        SdkHttpFullRequest apiRequest;
        try {
            URL url = request.toUrl();
            SdkHttpFullRequest.Builder sdkRequestBuilder = SdkHttpFullRequest.builder()
                    .protocol(request.getProtocol())
                    .host(request.getHost())
                    .encodedPath(url.getPath())
                    .method(request.getMethod());
            appendQueryParams(sdkRequestBuilder, url);
            putHeaders(sdkRequestBuilder, request.getHeaders());
            sdkRequestBuilder.putHeader("Content-Type", "application/json; charset=utf-8");
            if (SdkHttpMethod.GET != request.getMethod() && request.getBody() != null) {
                sdkRequestBuilder.contentStreamProvider(() -> new StringInputStream(request.getBody()));
            }
            apiRequest = sdkRequestBuilder.build();
        } catch (URISyntaxException use) {
            LOGGER.error("URI parse error {}", use.getMessage());
            LOGGER.error(Utils.getFullStackTrace(use));
            throw new RuntimeException(use);
        }
        return apiRequest;
    }

    protected String getClientCredentialsBearerToken(AppClient appClient) {
        // If we've been called within the access token's expiry period, just return the cached copy
        Map<String, Object> token = getCachedClientCredentials(appClient.getClientId());
        if (token == null) {
            token = executeClientCredentialsGrant(appClient.getTokenEndpointUrl(), appClient.getClientCredentials());
            // Cache this access token until it expires
            putCachedClientCredentials(appClient.getClientId(), token);
        }
        return "Bearer " + token.get("access_token");
    }

    protected Map<String, Object> executeClientCredentialsGrant(URL tokenEndpoint, String clientSecret) {
        // POST to the OAuth provider's token endpoint a client_credentials grant
        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .protocol(tokenEndpoint.getProtocol())
                .host(tokenEndpoint.getHost())
                .encodedPath(tokenEndpoint.getPath())
                .method(SdkHttpMethod.POST);
        String body = "grant_type=client_credentials";
        requestBuilder.putHeader("Content-Type", "application/x-www-form-urlencoded");
        requestBuilder.putHeader("Authorization", "Basic " + clientSecret);
        requestBuilder.contentStreamProvider(() -> new StringInputStream(body));

        SdkHttpFullRequest clientCredentialsRequest = requestBuilder.build();
        Map<String, Object> clientCredentialsGrant = Utils.fromJson(
                executeApiRequest(clientCredentialsRequest), LinkedHashMap.class);
        return clientCredentialsGrant;
    }

    protected Map<String, Object> getCachedClientCredentials(String key) {
        LOGGER.debug(Utils.toJson(CLIENT_CREDENTIALS_CACHE));
        Map<String, Object> cached = CLIENT_CREDENTIALS_CACHE.get(key);
        if (cached != null) {
            Duration buffer = Duration.ofSeconds(2);
            if (Instant.now().plus(buffer).isBefore((Instant) cached.get("expiry"))) {
                LOGGER.debug("Client credentials cache hit {}", key);
                return (Map<String, Object>) cached.get("token");
            } else {
                LOGGER.debug("Cached credentials are expiring < 2s {}", key);
            }
        } else {
            LOGGER.debug("Client credentials cache miss {}", key);
        }
        return null;
    }

    protected void putCachedClientCredentials(String key, Map<String, Object> token) {
        LOGGER.debug("Caching client credentials for {} seconds {}", token.get("expires_in"), key);
        CLIENT_CREDENTIALS_CACHE.put(key, Map.of(
                "expiry", Instant.now().plusSeconds(((Integer) token.get("expires_in")).longValue()),
                "token", token)
        );
    }

    protected SdkHttpFullRequest signApiRequest(SdkHttpFullRequest apiRequest, String assumedRole, String context) {
        return SIG_V4.sign(apiRequest, Aws4SignerParams.builder()
                .signingName("execute-api")
                .signingRegion(Region.of(AWS_REGION))
                .awsCredentials(getAwsCredentials(assumedRole, context))
                .build());
    }

    protected AwsCredentials getAwsCredentials(final String assumedRole, final String context) {
        // Calling STS here so we can name the temporary session with
        // the request context for CloudTrail logging
        AwsCredentials awsCredentials;
        try {
            AssumeRoleResponse response = sts.assumeRole(request -> request
                    .roleArn(assumedRole)
                    .durationSeconds(900)
                    .roleSessionName(context)
            );
            //AssumedRoleUser assumedUser = response.assumedRoleUser();
            //LOGGER.info("Assumed IAM User {}", assumedUser.arn());
            //LOGGER.info("Assumed IAM Role {}", assumedUser.assumedRoleId());

            // Could use STSAssumeRoleSessionCredentialsProvider here, but this
            // lambda will timeout before we need to refresh the temporary creds
            Credentials temporaryCredentials = response.credentials();
            awsCredentials = AwsSessionCredentials.create(
                    temporaryCredentials.accessKeyId(),
                    temporaryCredentials.secretAccessKey(),
                    temporaryCredentials.sessionToken());
        } catch (SdkServiceException stsError) {
            LOGGER.error("sts::AssumeRole error {}", stsError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(stsError));
            throw stsError;
        }
        return awsCredentials;
    }

    protected static void appendQueryParams(SdkHttpFullRequest.Builder sdkRequestBuilder, URL url)
            throws URISyntaxException {
        List<NameValuePair> queryParams = new URIBuilder(url.toURI()).getQueryParams();
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

    private static final class HttpRequest {
        private final String protocol;
        private final String host;
        private final String stage;
        private final String resource;
        private final SdkHttpMethod method;
        private final String body;
        private final Map<String, String> headers;

        private HttpRequest(HttpRequest.Builder builder) {
            this.protocol = Utils.isNotBlank(builder.protocol) ? builder.protocol : "https";
            this.host = builder.host;
            this.stage = builder.stage;
            this.resource = builder.resource;
            this.method = builder.method;
            this.body = builder.body;
            if (builder.headers != null) {
                this.headers = Collections.unmodifiableMap(builder.headers);
            } else {
                this.headers = Collections.unmodifiableMap(Collections.EMPTY_MAP);
            }
        }

        public static HttpRequest.Builder builder() {
            return new HttpRequest.Builder();
        }

        public String getProtocol() {
            return protocol;
        }

        public String getHost() {
            return host;
        }

        public String getStage() {
            return stage;
        }

        public String getResource() {
            return resource;
        }

        public SdkHttpMethod getMethod() {
            return method;
        }

        public String getBody() {
            return body;
        }

        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);
        }

        public URL toUrl() {
            try {
                return new URL(protocol, host, "/" + stage + "/" + resource);
            } catch (MalformedURLException mue) {
                LOGGER.error("URL parse error {}", mue.getMessage());
                LOGGER.error(Utils.getFullStackTrace(mue));
                throw new RuntimeException(mue);
            }
        }

        public static final class Builder {

            private String protocol;
            private String host;
            private String stage;
            private String resource;
            private SdkHttpMethod method;
            private String body;
            private Map<String, String> headers;

            private Builder() {
            }

            public HttpRequest.Builder protocol(String protocol) {
                this.protocol = protocol;
                return this;
            }

            public HttpRequest.Builder host(String host) {
                this.host = host;
                return this;
            }

            public HttpRequest.Builder stage(String stage) {
                this.stage = stage;
                return this;
            }

            public HttpRequest.Builder resource(String resource) {
                if (resource != null && resource.startsWith("/")) {
                    this.resource = resource.substring(1);
                } else {
                    this.resource = resource;
                }
                return this;
            }

            public HttpRequest.Builder method(String method) {
                this.method = SdkHttpMethod.fromValue(method);
                return this;
            }

            public HttpRequest.Builder body(String body) {
                this.body = body;
                return this;
            }

            public HttpRequest.Builder headers(final Map<String, String> headers) {
                if (headers != null) {
                    this.headers = Collections.unmodifiableMap(headers);
                }
                return this;
            }

            public HttpRequest build() {
                return new HttpRequest(this);
            }
        }
    }
}
