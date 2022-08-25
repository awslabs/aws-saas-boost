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
import software.amazon.awssdk.regions.Region;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ApiGatewayAuthorizer implements RequestStreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayAuthorizer.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String IDENTITY_PROVIDER = System.getenv("IDENTITY_PROVIDER");
    private final Authorizer authorizer;

    public ApiGatewayAuthorizer() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(IDENTITY_PROVIDER)) {
            throw new IllegalStateException("Missing required environment variable IDENTITY_PROVIDER");
        }
        // Initialize the authorizer here not in the handler so we can take advantage
        // of the JWKS cache as long as this execution environment is warm
        authorizer = AuthorizerFactory.getInstance().getAuthorizer(IDENTITY_PROVIDER);
        if (authorizer == null) {
            throw new UnsupportedOperationException("No implementation for IdP " + IDENTITY_PROVIDER);
        }
    }

    public void handleRequest(InputStream input, OutputStream output, Context context) {
        // Using a RequestSteamHandler here because there doesn't seem to be a way to get
        // a hold of the internal Jackson ObjectMapper from AWS to adjust it to deal with
        // the uppercase property names in the Policy document
        TokenAuthorizerRequest event = Utils.fromJson(input, TokenAuthorizerRequest.class);
        if (null == event) {
            throw new RuntimeException("Can't deserialize input");
        }
        LOGGER.info(Utils.toJson(event));

        AuthorizerResponse response;
        if (!authorizer.verifyToken(event)) {
            LOGGER.error("JWT not verified. Returning Not Authorized");
            response = AuthorizerResponse.builder()
                    .principalId(event.getAccountId())
                    .policyDocument(PolicyDocument.builder()
                            .statement(Statement.builder()
                                    .effect("Deny")
                                    .resource(ApiGatewayAuthorizer.apiGatewayResource(event))
                                    .build()
                            )
                            .build()
                    )
                    .context(new HashMap<>())
                    .build();
        } else {
            LOGGER.info("JWT verified. Returning Authorized.");
            response = AuthorizerResponse.builder()
                    .principalId(event.getAccountId())
                    .policyDocument(PolicyDocument.builder()
                            .statement(Statement.builder()
                                    .effect("Allow")
                                    .resource(ApiGatewayAuthorizer.apiGatewayResource(event))
                                    .build()
                            )
                            .build()
                    )
                    .context(new HashMap<>())
                    .build();
        }
        LOGGER.info(Utils.toJson(response));

        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(Utils.toJson(response));
            writer.flush();
        } catch (Exception e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String apiGatewayResource(TokenAuthorizerRequest event) {
        return apiGatewayResource(event, "*", "*");
    }

    public static String apiGatewayResource(TokenAuthorizerRequest event, String method, String resource) {
        String partition = Region.of(AWS_REGION).metadata().partition().id();
        String arn = String.format("arn:%s:execute-api:%s:%s:%s/%s/%s/%s",
                partition,
                event.getRegion(),
                event.getAccountId(),
                event.getApiId(),
                event.getStage(),
                method,
                resource
        );
        return arn;
    }
}