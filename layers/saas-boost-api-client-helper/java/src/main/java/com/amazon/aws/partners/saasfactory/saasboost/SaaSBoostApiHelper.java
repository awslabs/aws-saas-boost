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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.utils.StringInputStream;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SaaSBoostApiHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSBoostApiHelper.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final DateFormat JAVASCRIPT_ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX'Z'");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SdkHttpClient HTTP_CLIENT = UrlConnectionHttpClient.create();

    static {
        JAVASCRIPT_ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
        MAPPER.findAndRegisterModules();
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.setDateFormat(JAVASCRIPT_ISO8601);
        MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        MAPPER.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    }

    private final Map<String, Map<String, Object>> cache = new HashMap<>();
    private final SecretsManagerClient secrets;
    private AppClient appClient;

    public SaaSBoostApiHelper(String appClientSecretArn) {
        this(new DefaultDependencyFactory(), appClientSecretArn);
    }

    // Facilitates testing by being able to mock out the Secrets Manager dependency
    public SaaSBoostApiHelper(SaaSBoostApiHelperDependencyFactory init, String appClientSecretArn) {
        if (AWS_REGION == null || AWS_REGION.isBlank()) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        this.secrets = init.secrets();
        // Fetch the app client details from SecretsManager
        try {
            GetSecretValueResponse response = secrets.getSecretValue(request -> request
                    .secretId(appClientSecretArn)
            );
            Map<String, String> clientDetails = fromJson(response.secretString(), LinkedHashMap.class);
            appClient = AppClient.builder()
                    .clientName(clientDetails.get("client_name"))
                    .clientId(clientDetails.get("client_id"))
                    .clientSecret(clientDetails.get("client_secret"))
                    .tokenEndpoint(clientDetails.get("token_endpoint"))
                    .apiEndpoint(clientDetails.get("api_endpoint"))
                    .build();
        } catch (SdkServiceException secretsManagerError) {
            LOGGER.error(getFullStackTrace(secretsManagerError));
            throw secretsManagerError;
        }
    }

    public String authorizedRequest(String method, String resource) {
        return authorizedRequest(method, resource, null);
    }

    public String authorizedRequest(String method, String resource, String body) {
        return executeApiRequest(
                toSdkHttpFullRequest(HttpRequest.builder()
                        .protocol(appClient.getApiEndpointProtocol())
                        .host(appClient.getApiEndpointHost())
                        .stage(appClient.getApiEndpointStage())
                        .headers(Map.of("Authorization", getClientCredentialsBearerToken(appClient)))
                        .method(method)
                        .resource(resource)
                        .body(body)
                        .build()
                )
        );
    }

    public String anonymousRequest(String method, String resource) {
        return anonymousRequest(method, resource, null);
    }

    public String anonymousRequest(String method, String resource, String body) {
        return executeApiRequest(
                toSdkHttpFullRequest(HttpRequest.builder()
                        .protocol(appClient.getApiEndpointProtocol())
                        .host(appClient.getApiEndpointHost())
                        .stage(appClient.getApiEndpointStage())
                        .method(method)
                        .resource(resource)
                        .body(body)
                        .build()
                )
        );
    }

    protected String executeApiRequest(SdkHttpFullRequest apiRequest) {
        HttpExecuteRequest.Builder requestBuilder = HttpExecuteRequest.builder()
                .request(apiRequest);
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
            LOGGER.error(getFullStackTrace(ioe));
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
            LOGGER.error(getFullStackTrace(use));
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
        Map<String, Object> clientCredentialsGrant = fromJson(
                executeApiRequest(clientCredentialsRequest), LinkedHashMap.class);
        return clientCredentialsGrant;
    }

    protected Map<String, Object> getCachedClientCredentials(String key) {
        LOGGER.debug(toJson(cache));
        Map<String, Object> cached = cache.get(key);
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
        cache.put(key, Map.of(
                "expiry", Instant.now().plusSeconds(((Integer) token.get("expires_in")).longValue()),
                "token", token)
        );
    }

    protected void appendQueryParams(SdkHttpFullRequest.Builder sdkRequestBuilder, URL url)
            throws URISyntaxException {
        List<NameValuePair> queryParams = new URIBuilder(url.toURI()).getQueryParams();
        for (NameValuePair queryParam : queryParams) {
            sdkRequestBuilder.appendRawQueryParameter(queryParam.getName(), queryParam.getValue());
        }
    }

    protected void putHeaders(SdkHttpFullRequest.Builder sdkRequestBuilder, Map<String, String> headers) {
        if (sdkRequestBuilder != null && headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                sdkRequestBuilder.putHeader(header.getKey(), header.getValue());
            }
        }
    }

    private String toJson(Object obj) {
        String json = null;
        try {
            json = MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            LOGGER.error(getFullStackTrace(e));
        }
        return json;
    }

    private <T> T fromJson(String json, Class<T> serializeTo) {
        T object = null;
        try {
            object = MAPPER.readValue(json, serializeTo);
        } catch (Exception e) {
            LOGGER.error(getFullStackTrace(e));
        }
        return object;
    }

    private String getFullStackTrace(Exception e) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        e.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    interface SaaSBoostApiHelperDependencyFactory {
        SecretsManagerClient secrets();
    }

    private static final class DefaultDependencyFactory implements SaaSBoostApiHelperDependencyFactory {

        @Override
        public SecretsManagerClient secrets() {
            Region region = Region.of(AWS_REGION);
            String endpoint = "https://" + SecretsManagerClient.SERVICE_NAME + "." + region.id()
                    + "." + region.metadata().partition().dnsSuffix();

            return SecretsManagerClient.builder()
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .region(region)
                    .endpointOverride(URI.create(endpoint))
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
            this.protocol = builder.protocol;
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
                throw new RuntimeException(mue);
            }
        }

        public static final class Builder {

            private String protocol = "https";
            private String host;
            private String stage;
            private String resource;
            private SdkHttpMethod method;
            private String body;
            private Map<String, String> headers;

            private Builder() {
            }

            public HttpRequest.Builder protocol(String protocol) {
                if (protocol == null || protocol.isBlank()) {
                    throw new IllegalArgumentException("protocol can't be blank");
                }
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