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

package com.amazon.aws.partners.saasfactory.saasboost.keycloak;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class KeycloakApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakApi.class);
    private static final String KEYCLOAK_HOST = System.getenv("KEYCLOAK_HOST");
    private static final String KEYCLOAK_REALM = System.getenv("KEYCLOAK_REALM");
    // "host/admin/realms/realm/users"
    private static final String KEYCLOAK_USER_ENDPOINT_TEMPLATE = "%s/admin/realms/%s/users";
    // "host/admin/realms/realm/groups"
    private static final String KEYCLOAK_GROUP_ENDPOINT_TEMPLATE = "%s/admin/realms/%s/groups";
    
    private final HttpClient client;
    private final String userEndpoint;
    private final String groupEndpoint;

    public KeycloakApi() {
        this(KEYCLOAK_HOST, KEYCLOAK_REALM, HttpClient.newBuilder().build());
        if (Utils.isEmpty(KEYCLOAK_HOST)) {
            throw new IllegalStateException("Missing required environment variable KEYCLOAK_HOST");
        }
        if (Utils.isEmpty(KEYCLOAK_REALM)) {
            throw new IllegalStateException("Missing required environment variable KEYCLOAK_REALM");
        }
    }

    public KeycloakApi(String host, String realm, HttpClient client) {
        this.userEndpoint = String.format(KEYCLOAK_USER_ENDPOINT_TEMPLATE, host, realm);
        this.groupEndpoint = String.format(KEYCLOAK_GROUP_ENDPOINT_TEMPLATE, host, realm);
        this.client = client;
    }

    public List<UserRepresentation> listUsers(Map<String, Object> event) {
        try {
            HttpRequest getUsers = keycloakRequest(event, userEndpoint()).GET().build();
            LOGGER.info("Invoking Keycloak realm users endpoint {}", getUsers.uri());
            HttpResponse<String> response = client.send(getUsers,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                LOGGER.info("listUsers response: {}", response.body());
                List<Map<String, Object>> users = Utils.fromJson(response.body(), ArrayList.class);
                LOGGER.info("listUsers parsed users: {}", users);
                if (users != null) {
                    return users.stream()
                            .map(KeycloakApi::toKeycloakUser)
                            .collect(Collectors.toList());
                } else {
                    LOGGER.error("Can't parse realm users response {}", response.body());
                    throw new RuntimeException("Invalid response from " + getUsers.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm users failed HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public UserRepresentation getUser(Map<String, Object> event, String username) {
        try {
            HttpRequest getUsers = keycloakRequest(event, userSearchEndpoint(username)).GET().build();
            LOGGER.info("Invoking Keycloak realm users endpoint {}", getUsers.uri());
            HttpResponse<String> response = client.send(getUsers,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                LOGGER.info("getUser response: {}", response.body());
                List<Map<String, Object>> users = Utils.fromJson(response.body(), ArrayList.class);
                if (users != null && users.size() == 1) {
                    return users.stream()
                            .map(KeycloakApi::toKeycloakUser)
                            .collect(Collectors.toList())
                            .get(0);
                } else {
                    LOGGER.error("Can't parse realm users response {}", response.body());
                    throw new RuntimeException("Invalid response from " + getUsers.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm users failed HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public UserRepresentation createUser(Map<String, Object> event, UserRepresentation user) {
        try {
            String body = Utils.toJson(user);
            HttpRequest createUser = keycloakRequest(event, userEndpoint())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            LOGGER.info("Invoking Keycloak realm create user endpoint {}: {}", createUser.uri(), body);
            HttpResponse<String> response = client.send(createUser,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_CREATED == response.statusCode()) {
                LOGGER.info("Succcessfully created user " + user.getUsername());
                // If the POST to create user succeeds we just get back a HTTP 201 with no body
                // We need to GET the newly created user to return it with the created id
                return getUser(event, user.getUsername());
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak create user failed HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getAdminGroupPath(Map<String, Object> event) {
        // GET /{realm}/groups
        try {
            HttpRequest getGroups = keycloakRequest(event, groupSearchEndpoint("admin")).GET().build();
            LOGGER.info("Invoking Keycloak realm group endpoint {}", getGroups.uri());
            HttpResponse<String> response = client.send(getGroups,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                LOGGER.info("getGroups response: {}", response.body());
                List<Map<String, Object>> groups = Utils.fromJson(response.body(), ArrayList.class);
                if (groups != null) {
                    if (groups.size() == 1) {
                        return (String) groups.get(0).get("path");
                    } else {
                        LOGGER.error("Expected only exactly one group {}", response.body());
                        throw new RuntimeException("Unexpected response from " + getGroups.uri());
                    }
                } else {
                    LOGGER.error("Can't parse realm groups response {}", response.body());
                    throw new RuntimeException("Invalid response from " + getGroups.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm groups failed HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public UserRepresentation putUser(Map<String, Object> event, UserRepresentation user) {
        // PUT /{realm}/users/{id}
        try {
            // get the existing keycloak user and update
            String body = Utils.toJson(user);
            HttpRequest updateUser = keycloakRequest(event, idEndpoint(user.getId()))
                    .PUT(BodyPublishers.ofString(body)).build();
            LOGGER.info("Invoking Keycloak realm update user endpoint {}: {}", updateUser.uri(), body);
            HttpResponse<String> response = client.send(updateUser, 
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_NO_CONTENT == response.statusCode()) {
                return user;
            } else {
                LOGGER.error("Recieved HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keyclock realm users failed HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public UserRepresentation deleteUser(Map<String, Object> event, String username) {
        // Need the Keycloak id for this username to delete it
        UserRepresentation user = getUser(event, username);
        try {
            HttpRequest deleteUser = keycloakRequest(event, idEndpoint(user.getId())).DELETE().build();
            LOGGER.info("Invoking Keycloak realm delete user endpoint {}", deleteUser.uri());
            HttpResponse<String> response = client.send(deleteUser,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_NO_CONTENT == response.statusCode()) {
                LOGGER.info("Deleted user " + username);
                return user;
            } else {
                LOGGER.error("Error deleting realm user response {}", response.statusCode());
                throw new RuntimeException("Error deleting realm user response " + deleteUser.uri());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // VisibleForTesting
    static UserRepresentation toKeycloakUser(Map<String, Object> user) {
        if (user != null) {
            final UserRepresentation keycloakUser = new UserRepresentation();
            // for each "set" function in UserRepresentation, parse the mapped value from user
            Arrays.stream(UserRepresentation.class.getMethods())
                    .filter(method -> method.getName().toLowerCase().startsWith("set"))
                    .forEach(setMethod -> {
                        try {
                            String capitalizedAttributeName = setMethod.getName().substring("set".length());
                            // we need to "lowercase" the first character when we pull the attribute name this way
                            char[] cs = capitalizedAttributeName.toCharArray();
                            cs[0] = Character.toLowerCase(cs[0]);
                            String attributeName = new String(cs);
                            Class attributeType = setMethod.getParameterTypes()[0];
                            if (user.containsKey(attributeName)) {
                                if (attributeType == Long.class) {
                                    // createdTimestamp is parsed by Jackson as an Integer
                                    // but stored in UserRepresentation as a Long
                                    setMethod.invoke(keycloakUser, Long.parseLong(user.get(attributeName).toString()));
                                } else if (attributeType == Set.class) {
                                    // disableableCredentialTypes is parsed by Jackson as an
                                    // ArrayList<String> but stored in UserRepresentation as a Set<String>
                                    setMethod.invoke(keycloakUser, 
                                            new HashSet<String>((ArrayList<String>) user.get(attributeName)));
                                } else {
                                    setMethod.invoke(keycloakUser, attributeType.cast(user.get(attributeName)));
                                }
                            } else {
                                LOGGER.info("User JSON map does not contain {}, skipping.", attributeName);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error converting user map to keycloak user");
                            LOGGER.error(Utils.getFullStackTrace(e));
                            throw new RuntimeException(e);
                        }
                    });
            return keycloakUser;
        }
        return null;
    }

    /*
     * This is not a preferred implementation, since Keycloak upgrades can lead to invisible bugs where
     * user attributes are silently dropped when during normal system-user-service operation due to their
     * not being properly set during upgrade operations.
     */
    private static UserRepresentation toKeycloakUserNonExtensible(Map<String, Object> user) {
        UserRepresentation keycloakUser = null;
        if (user != null) {
            keycloakUser = new UserRepresentation();
            keycloakUser.setId((String) user.get("id"));
            keycloakUser.setCreatedTimestamp((Long) user.get("createdTimestamp"));
            keycloakUser.setEnabled((Boolean) user.get("enabled"));
            keycloakUser.setUsername((String) user.get("username"));
            keycloakUser.setFirstName((String) user.get("firstName"));
            keycloakUser.setLastName((String) user.get("lastName"));
            keycloakUser.setEmail((String) user.get("email"));
            keycloakUser.setEmailVerified((Boolean) user.get("emailVerified"));
            keycloakUser.setRequiredActions((List<String>) user.get("requiredActions"));
            keycloakUser.setDisableableCredentialTypes((Set<String>) user.get("disableableCredentialTypes"));
            keycloakUser.setNotBefore((Integer) user.get("notBefore"));
            keycloakUser.setAccess((Map<String, Boolean>) user.get("access"));
        }
        return keycloakUser;
    }

    private HttpRequest.Builder keycloakRequest(Map<String, Object> event, URI endpoint) {
        return HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(endpoint)
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", getBearerToken(event));
    }

    private static String getBearerToken(Map<String, Object> event) {
        // If we got here, the API Gateway already verified the incoming JWT
        // with the issuer, so we can safely reuse it without reverification
        Map<String, String> requestHeaders = (Map<String, String>) event.get("headers");
        return requestHeaders.get("Authorization");
    }

    private URI userEndpoint() {
        return URI.create(userEndpoint);
    }

    private URI groupSearchEndpoint(String groupName) {
        return URI.create(groupEndpoint + "?search=" + groupName);
    }

    private URI userSearchEndpoint(String username) {
        return URI.create(userEndpoint
                + "?exact=true"
                + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
    }

    private URI idEndpoint(String id) {
        return URI.create(userEndpoint + "/" + id);
    }
}