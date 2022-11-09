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
    private static final String RESPONSE_DATA_KEY_KEYCLOAK_REALM = "KeycloakRealm";
    private static final String RESPONSE_DATA_KEY_WEB_APP_CLIENT_ID = "AdminWebAppClientId";
    private static final String RESPONSE_DATA_KEY_WEB_APP_CLIENT_NAME = "AdminWebAppClientName";
    private static final String RESPONSE_DATA_KEY_API_APP_CLIENT_ID = "ApiAppClientId";
    private static final String RESPONSE_DATA_KEY_API_APP_CLIENT_NAME = "ApiAppClientName";
    private static final String RESPONSE_DATA_KEY_API_APP_CLIENT_SECRET = "ApiAppClientSecret";
    private final HttpClient httpClient = HttpClient.newBuilder().build();
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
        String adminWebAppUrl = (String) resourceProperties.get("AdminWebAppUrl");
        final String redirectUriPattern = (!adminWebAppUrl.endsWith("/*")) ? adminWebAppUrl + "/*" : adminWebAppUrl;
        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");
                    try {
                        LOGGER.info("Fetching SaaS Boost admin user credentials from Secrets Manager");
                        GetSecretValueResponse adminUserSecretValue = secrets.getSecretValue(request -> request
                                .secretId(adminUserSecretId)
                        );
                        final Map<String, String> adminUserCredentials = Utils.fromJson(
                                adminUserSecretValue.secretString(), LinkedHashMap.class);

                        LOGGER.info("Fetching Keycloak super user credentials from Secrets Manager");
                        GetSecretValueResponse keycloakSecretValue = secrets.getSecretValue(request -> request
                                .secretId(keycloakSecretId)
                        );
                        final Map<String, String> keycloakCredentials = Utils.fromJson(
                                keycloakSecretValue.secretString(), LinkedHashMap.class);

                        LOGGER.info("Executing admin password grant endpoint");
                        Map<String, Object> passwordGrant = adminPasswordGrant(keycloakHost,
                                keycloakCredentials.get("username"),
                                keycloakCredentials.get("password")
                        );
                        String bearerToken = (String) passwordGrant.get("access_token");
                        if (Utils.isBlank(bearerToken)) {
                            throw new RuntimeException("Admin password grant doesn't contain an Access Token");
                        }

                        // Admin password grant access token expires in 60 seconds!
                        LOGGER.info("Executing realm import endpoint");
                        Map<String, String> keycloakRealmSetupResults = setupKeycloak(keycloakHost, bearerToken, realm,
                                adminUserCredentials.get("username"), adminUserCredentials.get("password"),
                                adminUserCredentials.get("email"), redirectUriPattern);

                        if (keycloakRealmSetupResults != null && !keycloakRealmSetupResults.isEmpty()) {
                            responseData.putAll(keycloakRealmSetupResults);
                            // We're returning sensitive data, so be sure to use NoEcho = true
                            // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/crpg-ref-responses.html
                            CloudFormationResponse.send(event, context, "SUCCESS", responseData, true);
                        } else {
                            responseData.put("Reason", "Keycloak setup did not return app client details");
                            CloudFormationResponse.send(event, context, "FAILED", responseData);
                        }
                    } catch (SdkServiceException secretsManagerError) {
                        LOGGER.error("Secrets Manager error {}", secretsManagerError.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(secretsManagerError));
                        responseData.put("Reason", secretsManagerError.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    } catch (Exception e) {
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    }
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    protected Map<String, String> setupKeycloak(String keycloakHost, String bearerToken, String realmName,
                                                String username, String password, String email,
                                                String redirectUriPattern) {
        // Initial SaaS Boost admin user
        Map<String, Object> adminUser = buildKeycloakUser(username, email, password);

        // Public OAuth app client with PKCE for the admin web app
        String adminWebAppClientId = Utils.randomString(20);
        String adminWebAppClientName = realmName + "-admin-webapp-client";
        String adminWebAppClientDescription = "SaaS Boost Admin Web App Client";
        Map<String, Object> adminWebAppClient = buildPublicAppClient(adminWebAppClientName,
                adminWebAppClientId, redirectUriPattern, adminWebAppClientDescription);

        // Private OAuth app client with secret for service-to-service API calls
        String apiAppClientId = Utils.randomString(20);
        String apiAppClientName = realmName + "-api-client";
        String apiAppClientDescription = "SaaS Boost API App Client";
        Map<String, Object> apiAppClient = buildPrivateAppClient(apiAppClientName, apiAppClientId,
                apiAppClientDescription);

        // Keycloak realm for this SaaS Boost environment
        Map<String, Object> realm = buildRealm(realmName, List.of(adminWebAppClient, apiAppClient), List.of(adminUser));

        // Return newly generated app client data back to CloudFormation
        final Map<String, String> setupResults = new HashMap<>();
        setupResults.put(RESPONSE_DATA_KEY_KEYCLOAK_REALM, realmName);

        int importRealmResponse = postRealm(keycloakHost, bearerToken, realm);
        if (HttpURLConnection.HTTP_CREATED == importRealmResponse) {
            LOGGER.info("Successfully created realm " + realmName);
            // If the POST to /admin/realms succeeds we just get back a HTTP 201 with no body
            // Now that we have a new Keycloak realm for this SaaS Boost environment, we need
            // to setup the proper admin use role mappings and we need to fetch the generated
            // app clients (and their secrets) so we save that info as SaaS Boost settings
            // and secrets.

            // Do this first because we need the realm clients to wire up the admin user
            // permissions anyway
            List<Map<String, Object>> clients = getClients(keycloakHost, bearerToken, realmName);
            for (Map<String, Object> appClient : clients) {
                if (apiAppClientId.equals(appClient.get("clientId"))) {
                    // Get the Keycloak generated client secret
                    setupResults.put(RESPONSE_DATA_KEY_API_APP_CLIENT_NAME, (String) appClient.get("name"));
                    setupResults.put(RESPONSE_DATA_KEY_API_APP_CLIENT_ID, (String) appClient.get("clientId"));
                    setupResults.put(RESPONSE_DATA_KEY_API_APP_CLIENT_SECRET, (String) appClient.get("secret"));
                } else if (adminWebAppClientId.equals(appClient.get("clientId"))) {
                    // Confirms that the public app client got created properly
                    setupResults.put(RESPONSE_DATA_KEY_WEB_APP_CLIENT_NAME, (String) appClient.get("name"));
                    setupResults.put(RESPONSE_DATA_KEY_WEB_APP_CLIENT_ID, (String) appClient.get("clientId"));
                }
            }

            // Now setup the realm management permissions for the admin user
            int mapAdminUserServiceRolesResponse = mapAdminUserServiceRoles(keycloakHost, bearerToken, realmName,
                    username, clients);
            // The POST to map client roles to user returns a 204 instead of a 201 created...
            if (HttpURLConnection.HTTP_NO_CONTENT == mapAdminUserServiceRolesResponse) {
                LOGGER.info("Successfully mapped service roles to user " + username);
            } else {
                throw new RuntimeException("Keycloak service role mapping for admin user failed with HTTP "
                        + mapAdminUserServiceRolesResponse);
            }
        } else {
            throw new RuntimeException("Keycloak import realm failed with HTTP " + importRealmResponse);
        }

        return setupResults;
    }

    protected Map<String, Object> adminPasswordGrant(String keycloakHost, String username, String password) {
        Map<String, Object> passwordGrant;
        try {
            URI endpoint = new URI(keycloakHost
                    + "/realms/master/protocol/openid-connect/token");
            String body = "grant_type=password"
                    + "&client_id=admin-cli"
                    + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            LOGGER.info("Invoking Keycloak password grant endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
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

    protected int postRealm(String keycloakHost, String bearerToken, Map<String, Object> realm) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + bearerToken)
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Utils.toJson(realm)))
                    .build();

            LOGGER.info("Invoking Keycloak realm import endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode();
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected int mapAdminUserServiceRoles(String keycloakHost, String bearerToken, String realmName, String username,
                                           List<Map<String, Object>> clients) {
        // We need the realm management client that's auto generated with every new realm in Keycloak
        Map<String, Object> realmManagementClient = clients.stream()
                .filter(m -> "realm-management".equals(m.get("clientId")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find realm-management client in realm " + realmName));
        String clientId = (String) realmManagementClient.get("id");

        // We need the realm admin role that's owned by the realm management client
        List<Map<String, Object>> roles = getClientRoles(keycloakHost, bearerToken, realmName, clientId);
        Map<String, Object> realmAdminRole = roles.stream()
                .filter(role -> "realm-admin".equals(role.get("name")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find realm-admin role in client " + clientId));

        // We need the id of the realm admin user (not the keycloak master realm super user)
        Map<String, Object> adminUser = getUser(keycloakHost, bearerToken, realmName, username);
        String userId = (String) adminUser.get("id");
        //List<Map<String, Object>> userRoles = (List<Map<String, Object>>) adminUser.get("roles");

        // Finally we can map the manage realm role to our admin user so that access tokens retrieved by that
        // user will have permissions to do things like add and edit users
        //userRoles.add(realmAdminRole);
        return postUserClientRoleMapping(keycloakHost, bearerToken, realmName, userId, clientId, realmAdminRole);
    }

    protected List<Map<String, Object>> getClients(String keycloakHost, String bearerToken, String realmName) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/"
                    + realmName + "/clients");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + bearerToken)
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.info("Invoking Keycloak realm clients endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                List<Map<String, Object>> clients = Utils.fromJson(response.body(), ArrayList.class);
                if (clients != null) {
                    return clients;
                } else {
                    LOGGER.error("Can't parse realm clients response {}", response.body());
                    throw new RuntimeException("Invalid response from " + request.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm clients failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<Map<String, Object>> getClientRoles(String keycloakHost, String bearerToken, String realmName,
                                                       String clientId) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realmName
                    + "/clients/" + clientId
                    + "/roles"
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + bearerToken)
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.info("Invoking Keycloak realm client roles endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                List<Map<String, Object>> roles = Utils.fromJson(response.body(), ArrayList.class);
                if (roles != null) {
                    return roles;
                } else {
                    LOGGER.error("Can't parse realm client roles response {}", response.body());
                    throw new RuntimeException("Invalid response from " + request.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm client roles failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<String, Object> getUser(String keycloakHost, String bearerToken, String realmName, String username) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/"
                    + realmName + "/users?exact=true&username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + bearerToken)
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.info("Invoking Keycloak realm users endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                List<Map<String, Object>> users = Utils.fromJson(response.body(), ArrayList.class);
                if (users != null) {
                    if (users.size() == 1) {
                        return users.get(0);
                    } else {
                        LOGGER.error("Can't find user {}", username);
                        LOGGER.error(response.body());
                        throw new RuntimeException("Can't find user " + username);
                    }
                } else {
                    LOGGER.error("Can't parse realm users response {}", response.body());
                    throw new RuntimeException("Invalid response from " + request.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm users failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected int postUserClientRoleMapping(String keycloakHost, String bearerToken, String realmName, String userId,
                                            String clientId, Map<String, Object> role) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realmName
                    + "/users/" + userId
                    + "/role-mappings"
                    + "/clients/" + clientId
            );
            String body = Utils.toJson(List.of(role));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + bearerToken)
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            LOGGER.info("Invoking Keycloak user client role mapping endpoint {}", request.uri());
            LOGGER.info("POST body");
            LOGGER.info(body);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode();
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected Map<String, Object> buildKeycloakUser(String username, String email, String password) {
        LinkedHashMap<String, Object> user = new LinkedHashMap<>();
        user.put("enabled", true);
        user.put("createdTimestamp", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        user.put("username", username);
        user.put("email", email);
        user.put("emailVerified", true);
        user.put("credentials", List.of(
                Map.of("type", "password", "temporary", true, "value", password)
        ));
        user.put("requiredActions", List.of("UPDATE_PASSWORD"));
        return user;
    }

    protected Map<String, Object> buildPublicAppClient(String clientName, String clientId, String redirects,
                                                                 String description) {
        LinkedHashMap<String, Object> publicAppClient = new LinkedHashMap<>();
        publicAppClient.put("enabled", true);
        publicAppClient.put("protocol", "openid-connect");
        publicAppClient.put("name", clientName);
        publicAppClient.put("clientId", clientId);
        publicAppClient.put("description", description);
        publicAppClient.put("redirectUris", List.of(redirects));
        publicAppClient.put("standardFlowEnabled", true);
        publicAppClient.put("directAccessGrantsEnabled", false);
        publicAppClient.put("implicitFlowEnabled", false);
        publicAppClient.put("publicClient", true);
        publicAppClient.put("attributes", Map.of("pkce.code.challenge.method", "S256"));
        return publicAppClient;
    }

    protected Map<String, Object> buildPrivateAppClient(String clientName, String clientId, String description) {
        LinkedHashMap<String, Object> privateAppClient = new LinkedHashMap<>();
        privateAppClient.put("enabled", true);
        privateAppClient.put("protocol", "openid-connect");
        privateAppClient.put("name", clientName);
        privateAppClient.put("clientId", clientId);
        privateAppClient.put("description", description);
        privateAppClient.put("standardFlowEnabled", false);
        privateAppClient.put("directAccessGrantsEnabled", false);
        privateAppClient.put("implicitFlowEnabled", false);
        privateAppClient.put("serviceAccountsEnabled", true);
        privateAppClient.put("publicClient", false);
        privateAppClient.put("attributes", Map.of("use.refresh.tokens", false));
        return privateAppClient;
    }

    protected Map<String, Object> buildRealm(String realmName, List<Map<String, Object>> clients,
                                                       List<Map<String, Object>> users) {
        LinkedHashMap<String, Object> realm = new LinkedHashMap<>();
        realm.put("realm", realmName);
        realm.put("enabled", true);
        realm.put("loginTheme", "saas-boost-theme");
        realm.put("displayNameHtml", "<div class=\"kc-logo-text\"><span>" + realmName + "</span></div>");
        realm.put("users", users);
        realm.put("clients", clients);
        return realm;
    }
}
