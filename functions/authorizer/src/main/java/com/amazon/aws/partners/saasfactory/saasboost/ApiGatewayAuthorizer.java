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
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ApiGatewayAuthorizer implements RequestStreamHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayAuthorizer.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String IDENTITY_PROVIDER = System.getenv("IDENTITY_PROVIDER");
    static final String ADMIN_WEB_APP_CLIENT_ID = System.getenv("ADMIN_WEB_APP_CLIENT_ID");
    static final String API_APP_CLIENT_ID = System.getenv("API_APP_CLIENT_ID");
    static final String PRIVATE_API_APP_CLIENT_ID = System.getenv("PRIVATE_API_APP_CLIENT_ID");
    static final String READ_SCOPE = "saas-boost/" + SAAS_BOOST_ENV + "/read";
    static final String WRITE_SCOPE = "saas-boost/" + SAAS_BOOST_ENV + "/write";
    static final String PRIVATE_SCOPE = "saas-boost/" + SAAS_BOOST_ENV + "/private";
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
        DecodedJWT token = authorizer.verifyToken(event);
        if (token == null) {
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
            LOGGER.debug(Utils.toJson(token));

            List<String> resources = new ArrayList<>();
            String scopes = token.getClaim("scope").asString();
            if (ADMIN_WEB_APP_CLIENT_ID.equals(authorizer.getClientId(token))) {
                List<String> groups = authorizer.getGroups(token);
                if (groups != null && groups.contains("admin")) {
                    LOGGER.debug("Token includes admin group, adding read/write scopes");
                    scopes = scopes + " " + READ_SCOPE + " " + WRITE_SCOPE;
                } else {
                    LOGGER.error("Admin web app client does not contain RBAC groups!");
                }
            }
            //LOGGER.info("Access token scopes {}", scopes);
            if (scopes.contains(READ_SCOPE)) {
                LOGGER.info("Adding READ scope resources");
                resources.addAll(readApiResources(event));
            }
            if (scopes.contains(WRITE_SCOPE)) {
                LOGGER.info("Adding WRITE scope resources");
                resources.addAll(writeApiResources(event));
            }
            if (scopes.contains(PRIVATE_SCOPE)) {
                LOGGER.info("Adding PRIVATE scope resources");
                resources.addAll(privateApiResources(event));
            }

            response = AuthorizerResponse.builder()
                    .principalId(event.getAccountId())
                    .policyDocument(PolicyDocument.builder()
                            .statement(Statement.builder()
                                    .effect("Allow")
                                    .resource(resources)
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
        String arn = String.format("arn:%s:execute-api:%s:%s:%s/%s/%s/%s",
                Region.of(event.getRegion()).metadata().partition().id(),
                event.getRegion(),
                event.getAccountId(),
                event.getApiId(),
                event.getStage(),
                method,
                resource
        );
        return arn;
    }

    public static List<String> readApiResources(TokenAuthorizerRequest event) {
        Set<AbstractMap.SimpleEntry<String, String>> readResources = new LinkedHashSet<>();
        readResources.add(new AbstractMap.SimpleEntry<>("api", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("api/*", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("billing/plans", "GET"));
        //readResources.add(new AbstractMap.SimpleEntry<>("metrics/alb/*", "GET"));
        //readResources.add(new AbstractMap.SimpleEntry<>("metrics/datasets", "GET"));
        //readResources.add(new AbstractMap.SimpleEntry<>("metrics/query", "POST")); // Yes, this is a "read" resource
        readResources.add(new AbstractMap.SimpleEntry<>("onboarding", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("onboarding/*", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("settings", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("settings/config", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("settings/options", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("settings/*", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("sysusers", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("sysusers/*", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("tenants", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("tenants/*", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("tiers", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("tiers/*", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("identity", "GET"));
        readResources.add(new AbstractMap.SimpleEntry<>("identity/providers", "GET"));

        List<String> resources = new ArrayList<>();
        for (Map.Entry<String, String> resource : readResources) {
            resources.add(apiGatewayResource(event, resource.getValue(), resource.getKey()));
        }
        return resources;
    }

    public static List<String> writeApiResources(TokenAuthorizerRequest event) {
        Set<AbstractMap.SimpleEntry<String, String>> writeResources = new LinkedHashSet<>();
        writeResources.add(new AbstractMap.SimpleEntry<>("onboarding*", "POST"));
        writeResources.add(new AbstractMap.SimpleEntry<>("settings/config*", "PUT"));
        writeResources.add(new AbstractMap.SimpleEntry<>("sysusers*", "POST"));
        writeResources.add(new AbstractMap.SimpleEntry<>("sysusers/*", "DELETE"));
        writeResources.add(new AbstractMap.SimpleEntry<>("sysusers/*", "PUT"));
        writeResources.add(new AbstractMap.SimpleEntry<>("sysusers/*/disable", "PATCH"));
        writeResources.add(new AbstractMap.SimpleEntry<>("sysusers/*/enable", "PATCH"));
        writeResources.add(new AbstractMap.SimpleEntry<>("tenants/*", "DELETE"));
        writeResources.add(new AbstractMap.SimpleEntry<>("tenants/*", "PUT"));
        writeResources.add(new AbstractMap.SimpleEntry<>("tenants/*/disable", "PATCH"));
        writeResources.add(new AbstractMap.SimpleEntry<>("tenants/*/enable", "PATCH"));
        writeResources.add(new AbstractMap.SimpleEntry<>("tiers*", "POST"));
        writeResources.add(new AbstractMap.SimpleEntry<>("tiers/*", "PUT"));
        writeResources.add(new AbstractMap.SimpleEntry<>("tiers/*", "DELETE"));
        writeResources.add(new AbstractMap.SimpleEntry<>("identity*", "POST"));
        writeResources.add(new AbstractMap.SimpleEntry<>("metrics*", "POST"));

        List<String> resources = new ArrayList<>();
        for (Map.Entry<String, String> resource : writeResources) {
            resources.add(apiGatewayResource(event, resource.getValue(), resource.getKey()));
        }
        return resources;
    }

    public static List<String> privateApiResources(TokenAuthorizerRequest event) {
        Set<AbstractMap.SimpleEntry<String, String>> privateResources = new LinkedHashSet<>();
        privateResources.add(new AbstractMap.SimpleEntry<>("quotas/check", "GET"));
        privateResources.add(new AbstractMap.SimpleEntry<>("settings/config", "DELETE"));
        privateResources.add(new AbstractMap.SimpleEntry<>("settings/*/secret", "GET"));
        privateResources.add(new AbstractMap.SimpleEntry<>("tenants*", "POST"));

        List<String> resources = new ArrayList<>();
        for (Map.Entry<String, String> resource : privateResources) {
            resources.add(apiGatewayResource(event, resource.getValue(), resource.getKey()));
        }
        return resources;
    }
}