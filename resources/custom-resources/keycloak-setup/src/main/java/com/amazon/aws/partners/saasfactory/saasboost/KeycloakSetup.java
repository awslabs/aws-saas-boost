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
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

public class KeycloakSetup implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakSetup.class);
    private final SecretsManagerClient secrets;

    public KeycloakSetup() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        secrets = Utils.sdkClient(SecretsManagerClient.builder(), SecretsManagerClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        final Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String keycloakHost = (String) resourceProperties.get("KeycloakHost");
        final String keycloakSecretId = (String) resourceProperties.get("KeycloakCredentials");
        final String realm = (String) resourceProperties.get("Realm");
        final String adminUserSecretId = (String) resourceProperties.get("AdminUserCredentials");

        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");
                    try {
                        LOGGER.info("Fetching Keycloak super user credentials from Secrets Manager");
                        GetSecretValueResponse keycloakSecretValue = secrets.getSecretValue(request -> request
                                .secretId(keycloakSecretId)
                        );
                        Map<String, String> keycloakCredentials = Utils.fromJson(keycloakSecretValue.secretString(),
                                LinkedHashMap.class);

                        LOGGER.info("Executing admin password grant endpoint");
                        String bearerToken;
                        Map<String, Object> passwordGrant = adminPasswordGrant(keycloakHost,
                                URLEncoder.encode(keycloakCredentials.get("username"), StandardCharsets.UTF_8),
                                URLEncoder.encode(keycloakCredentials.get("password"), StandardCharsets.UTF_8));
                        bearerToken = (String) passwordGrant.get("access_token");

                        if (Utils.isBlank(bearerToken)) {
                            throw new RuntimeException("Admin password grant doesn't contain an Access Token");
                        }

                        LOGGER.info("Fetching SaaS Boost admin user credentials from Secrets Manager");
                        GetSecretValueResponse adminUserSecretValue = secrets.getSecretValue(request -> request
                                .secretId(adminUserSecretId)
                        );
                        Map<String, String> adminUserCredentials = Utils.fromJson(adminUserSecretValue.secretString(),
                                LinkedHashMap.class);

                        LOGGER.info("Executing realm import endpoint");
                        importRealm(keycloakHost, bearerToken, realm,
                                adminUserCredentials.get("username"), adminUserCredentials.get("password"),
                                adminUserCredentials.get("email"));

                        success(event, context, responseData);
                    } catch (SdkServiceException secretsManagerError) {
                        LOGGER.error("Secrets Manager error {}", secretsManagerError.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(secretsManagerError));
                        responseData.put("Reason", secretsManagerError.getMessage());
                        fail(event, context, responseData);
                    } catch (Exception e) {
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.getMessage());
                        fail(event, context, responseData);
                    }
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
                    success(event, context, responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    success(event, context, responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    fail(event, context, responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            fail(event, context, responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    protected void fail(Map<String, Object> event, Context context, Map<String, Object> responseData) {
        CloudFormationResponse.send(event, context, "FAILED", responseData);
    }

    protected void success(Map<String, Object> event, Context context, Map<String, Object> responseData) {
        CloudFormationResponse.send(event, context, "SUCCESS", responseData);
    }

    protected boolean importRealm(String keycloakHost, String bearerToken, String realmName,
                                              String username, String password, String email) {
        // https://www.keycloak.org/docs-api/19.0.3/rest-api/index.html#_realms_admin_resource

        LinkedHashMap<String, Object> adminUser = new LinkedHashMap<>();
        adminUser.put("enabled", "true");
        adminUser.put("createdTimestamp", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        adminUser.put("username", username);
        adminUser.put("email", email);
        adminUser.put("emailVerified", true);
        adminUser.put("credentials", List.of(
                Map.of("type", "password", "temporary", true, "value", password)
        ));
        adminUser.put("requiredActions", List.of("UPDATE_PASSWORD"));

        LinkedHashMap<String, Object> realm = new LinkedHashMap<>();
        realm.put("realm", realmName);
        realm.put("enabled", "true");
        realm.put("users", List.of(adminUser));

        try {
            URI keycloakImportRealmEndpoint = new URI(keycloakHost + "/admin/realms");
            String body = Utils.toJson(realm);
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(keycloakImportRealmEndpoint)
                    .setHeader("Authorization", "Bearer " + bearerToken)
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            LOGGER.info("Invoking Keycloak realm import endpoint {}", request.uri());
            HttpClient client = HttpClient.newBuilder().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_CREATED == response.statusCode()) {
                LOGGER.info("Succcessfully created realm " + realmName);
                return true;
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak import realm failed HTTP " + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<String, Object> adminPasswordGrant(String keycloakHost, String username, String password) {
        Map<String, Object> passwordGrant;
        try {
            URI keycloakAdminTokenEndpoint = new URI(keycloakHost
                    + "/realms/master/protocol/openid-connect/token");
            String body = "grant_type=password"
                    + "&client_id=admin-cli"
                    + "&username=" + username
                    + "&password=" + password;
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(keycloakAdminTokenEndpoint)
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            LOGGER.info("Invoking Keycloak password grant endpoint {}", request.uri());
            HttpClient client = HttpClient.newBuilder().build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                passwordGrant = Utils.fromJson(response.body(), LinkedHashMap.class);
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak admin password grant failed HTTP " + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return passwordGrant;
    }
}
