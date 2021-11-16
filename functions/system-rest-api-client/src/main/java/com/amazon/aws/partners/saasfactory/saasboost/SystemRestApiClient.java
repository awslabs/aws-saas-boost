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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpFullRequest;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class SystemRestApiClient implements RequestStreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemRestApiClient.class);
    private static final String API_GATEWAY_HOST = System.getenv("API_GATEWAY_HOST");
    private static final String API_GATEWAY_STAGE = System.getenv("API_GATEWAY_STAGE");
    private static final String API_TRUST_ROLE = System.getenv("API_TRUST_ROLE");

    public SystemRestApiClient() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(API_GATEWAY_HOST)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_HOST");
        }
        if (Utils.isBlank(API_GATEWAY_STAGE)) {
            throw new IllegalStateException("Missing required environment variable API_GATEWAY_STAGE");
        }
        if (Utils.isBlank(API_TRUST_ROLE)) {
            throw new IllegalStateException("Missing required environment variable API_TRUST_ROLE");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) {
        // Using a RequestSteamHandler here because there doesn't seem to be a way to get
        // a hold of the internal Jackson ObjectMapper from AWS to adjust it to deal with
        // the new java.time classes used in the EventBridge event object
        ApiRequestEvent event = Utils.fromJson(input, ApiRequestEvent.class);
        if (null == event) {
            throw new RuntimeException("responseBody is invalid");
        }        
        LOGGER.info(Utils.toJson(event));

        try {
            String responseBody = ApiGatewayHelper.signAndExecuteApiRequest(
                    ApiGatewayHelper.getApiRequest(API_GATEWAY_HOST, API_GATEWAY_STAGE, event.getDetail()),
                    API_TRUST_ROLE,
                    context.getAwsRequestId()
            );
            try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                writer.write(responseBody);
                writer.flush();
            }
        } catch (Exception e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e.getMessage());
        }
    }
}
