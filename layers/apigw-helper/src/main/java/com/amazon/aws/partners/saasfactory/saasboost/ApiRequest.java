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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.util.Collections;
import java.util.Map;

@JsonDeserialize(builder = ApiRequest.Builder.class)
public class ApiRequest {

    private String resource;
    private final SdkHttpMethod method;
    private String body;
    private String callback;
    private final Map<String, String> headers;

    private ApiRequest(Builder builder) {
        this.resource = builder.resource;
        this.method = builder.method;
        this.body = builder.body;
        this.callback = builder.callback;
        if (builder.headers != null) {
            this.headers = Collections.unmodifiableMap(builder.headers);
        } else {
            this.headers = Collections.unmodifiableMap(Collections.EMPTY_MAP);
        }
    }

    public static Builder builder() {
        return new Builder();
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

    public String getCallback() {
        return callback;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {

        private String resource;
        private SdkHttpMethod method;
        private String body;
        private String callback;
        private Map<String, String> headers;

        private Builder() {
        }

        public Builder resource(String resource) {
            if (resource != null && resource.startsWith("/")) {
                this.resource = resource.substring(1);
            } else {
                this.resource = resource;
            }
            return this;
        }

        public Builder method(String method) {
            this.method = SdkHttpMethod.fromValue(method);
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder callback(String callback) {
            this.callback = callback;
            return this;
        }

        public Builder headers(final Map<String, String> headers) {
            if (headers != null) {
                this.headers = Collections.unmodifiableMap(headers);
            }
            return this;
        }

        public ApiRequest build() {
            return new ApiRequest(this);
        }
    }
}
