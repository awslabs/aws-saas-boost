package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@JsonDeserialize(builder = AppClient.Builder.class)
public class AppClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppClient.class);
    private final String clientId;
    private final String clientName;
    private final String clientSecret;
    private final String tokenEndpoint;
    private final String apiEndpoint;

    private AppClient(Builder builder) {
        this.clientId = builder.clientId;
        this.clientName = builder.clientName;
        this.clientSecret = builder.clientSecret;
        this.tokenEndpoint = builder.tokenEndpoint;
        this.apiEndpoint = builder.apiEndpoint;
    }

    public String getClientCredentials() {
        // Generate a Base64 secret for HTTP Basic authorization
        return new String(Base64.getEncoder().encode((clientId + ":" + clientSecret)
                .getBytes(StandardCharsets.UTF_8)
        ), StandardCharsets.UTF_8);
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public URL getTokenEndpointUrl() {
        try {
            return new URL(getTokenEndpoint());
        } catch (MalformedURLException mue) {
            LOGGER.error("URL parse error {}", mue.getMessage());
            LOGGER.error(Utils.getFullStackTrace(mue));
            throw new RuntimeException(mue);
        }
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public URL getApiEndpointUrl() {
        try {
            return new URL(getApiEndpoint());
        } catch (MalformedURLException mue) {
            LOGGER.error("URL parse error {}", mue.getMessage());
            LOGGER.error(Utils.getFullStackTrace(mue));
            throw new RuntimeException(mue);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String clientId;
        private String clientName;
        private String clientSecret;
        private String tokenEndpoint;
        private String apiEndpoint;

        private Builder() {
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder tokenEndpoint(String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public AppClient build() {
            return new AppClient(this);
        }
    }
}
