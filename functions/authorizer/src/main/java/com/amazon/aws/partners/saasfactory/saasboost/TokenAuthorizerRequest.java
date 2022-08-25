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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.regex.Pattern;

@JsonDeserialize(builder = TokenAuthorizerRequest.Builder.class)
public class TokenAuthorizerRequest {

    private static final Pattern BEARER_TOKEN_REGEX = Pattern.compile("^[B|b]earer +");
    private final String type;
    private final String methodArn;
    private final String authorizationToken;
    @JsonIgnore
    private final String region;
    @JsonIgnore
    private final String accountId;
    @JsonIgnore
    private final String apiId;
    @JsonIgnore
    private final String stage;

    private TokenAuthorizerRequest(Builder builder) {
        this.type = builder.type;
        this.methodArn = builder.methodArn;
        this.authorizationToken = builder.authorizationToken;

        String[] request = methodArn.split(":");
        String[] apiGatewayArn = request[5].split("/");

        region = request[3];
        accountId = request[4];
        apiId = apiGatewayArn[0];
        stage = apiGatewayArn[1];
    }

    public String getType() {
        return type;
    }

    public String getRegion() {
        return region;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getApiId() {
        return apiId;
    }

    public String getStage() {
        return stage;
    }

    public String tokenPayload() {
        return BEARER_TOKEN_REGEX.split(authorizationToken)[1];
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String type = "TOKEN";
        private String methodArn;
        private String authorizationToken;

        private Builder() {
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder methodArn(String methodArn) {
            this.methodArn = methodArn;
            return this;
        }

        public Builder authorizationToken(String authorizationToken) {
            this.authorizationToken = authorizationToken;
            return this;
        }

        public TokenAuthorizerRequest build() {
            return new TokenAuthorizerRequest(this);
        }
    }
}
