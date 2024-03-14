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
import org.keycloak.representations.idm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class KeycloakAdminApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakAdminApi.class);
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final String keycloakHost;
    private Map<String, Object> passwordGrant;
    private Instant tokenExpiry;
    private String accessToken;

    public KeycloakAdminApi(String keycloakHost, String username, String password) {
        this.keycloakHost = keycloakHost;
        setPasswordGrant(adminPasswordGrant(username, password));
    }

    public KeycloakAdminApi(String keycloakHost, String bearerToken) {
        this.keycloakHost = keycloakHost;
        this.accessToken = bearerToken.replaceAll("^[B|b]earer ", "");
    }

    public ClientRepresentation getClient(RealmRepresentation realm, String clientId) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/"
                    + realm.getRealm() + "/clients"
                    + "?search=true&clientId=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak realm clients endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                ClientRepresentation[] clients = Utils.fromJson(response.body(), ClientRepresentation[].class);
                if (clients != null && clients.length == 1) {
                    return clients[0];
                } else {
                    LOGGER.error("Can't find client {}", clientId);
                    LOGGER.error(response.body());
                    throw new RuntimeException("Can't find client " + clientId);
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

    public List<ClientRepresentation> getClients(RealmRepresentation realm) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/"
                    + realm.getRealm() + "/clients");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak realm clients endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                ClientRepresentation[] clients = Utils.fromJson(response.body(), ClientRepresentation[].class);
                if (clients != null) {
                    return new ArrayList<>(Arrays.asList(clients));
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

    public ClientRepresentation createClient(RealmRepresentation realm, ClientRepresentation client) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm() + "/clients");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Utils.toJson(client)))
                    .build();
            LOGGER.debug("Invoking Keycloak client create endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                LOGGER.debug("Created client {} {}", client.getName(), client.getClientId());
                return getClient(realm, client.getClientId());
            } else {
                LOGGER.error("Expected HTTP_CREATED ({}) from client create, but got {}",
                        HttpURLConnection.HTTP_CREATED, response.statusCode());
                throw new RuntimeException("Unexpected error while creating client: " + client.getClientId());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ClientRepresentation updateClient(RealmRepresentation realm, ClientRepresentation client) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm()
                    + "/clients/" + client.getId()
            );
            String body = Utils.toJson(client);
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            LOGGER.debug("Invoking Keycloak update client endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                LOGGER.info("Updated client {} {}", client.getName(), client.getClientId());
                return getClient(realm, client.getClientId());
            } else {
                LOGGER.error("Expected HTTP_NO_CONTENT ({}) from update client, but got {}",
                        HttpURLConnection.HTTP_NO_CONTENT, response.statusCode());
                throw new RuntimeException("Unexpected error while updating client: " + response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ClientScopeRepresentation getClientScope(RealmRepresentation realm, String scopeName) {
        return getClientScopes(realm).stream()
                .filter(scope -> scopeName.equals(scope.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find client scope " + scopeName));
    }

    public List<ClientScopeRepresentation> getClientScopes(RealmRepresentation realm) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm() + "/client-scopes/"
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak realm client scopes endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                ClientScopeRepresentation[] scopes = Utils.fromJson(response.body(),
                        ClientScopeRepresentation[].class);
                if (scopes != null) {
                    return new ArrayList<>(Arrays.asList(scopes));
                } else {
                    LOGGER.error("Can't parse realm client scopes response {}", response.body());
                    throw new RuntimeException("Invalid response from " + request.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm client scopes failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ProtocolMapperRepresentation getClientScopeProtocolMapper(RealmRepresentation realm,
                                                                     ClientScopeRepresentation clientScope,
                                                                     String protocolMapper) {
        return getClientScopeProtocolMappers(realm, clientScope).stream()
                .filter(mapper -> protocolMapper.equals(mapper.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find protocol mapper " + protocolMapper));
    }

    public List<ProtocolMapperRepresentation> getClientScopeProtocolMappers(RealmRepresentation realm,
                                                                               ClientScopeRepresentation clientScope) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/client-scopes/" + clientScope.getId()
                    + "/protocol-mappers/models"
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak realm client scope protocol mappers endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                ProtocolMapperRepresentation[] protocolMappers = Utils.fromJson(response.body(),
                        ProtocolMapperRepresentation[].class);
                if (protocolMappers != null) {
                    return new ArrayList<>(Arrays.asList(protocolMappers));
                } else {
                    LOGGER.error("Can't parse protocol mappers response {}", response.body());
                    throw new RuntimeException("Invalid response from " + request.uri());
                }
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak realm client scope protocol mappers failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ClientRepresentation addClientProtocolMapperModel(RealmRepresentation realm, ClientRepresentation client,
                                                             ProtocolMapperRepresentation model) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/clients/" + client.getId()
                    + "/protocol-mappers"
                    + "/add-models"
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Utils.toJson(List.of(model))))
                    .build();
            LOGGER.debug("Invoking Keycloak client protocol mapper model {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                // let's return the client we just updated
                LOGGER.debug("Created protocol mapper model {} {}", client.getName(), model.getName());
                return getClient(realm, client.getClientId());
            } else {
                LOGGER.error("Expected HTTP_CREATED ({}) from protocol mapper model create, but got {}",
                        HttpURLConnection.HTTP_CREATED, response.statusCode());
                throw new RuntimeException("Unexpected error while creating protocol mapper model: " + model.getName());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public RoleRepresentation getRole(RealmRepresentation realm, String roleName) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm() + "/roles/"
                    + URLEncoder.encode(roleName, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak roles endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                LOGGER.debug("Found role: {}", response.body());
                return Utils.fromJson(response.body(), RoleRepresentation.class);
            } else {
                LOGGER.error("Expected HTTP_OK ({}) from get role by name, but got {}",
                        HttpURLConnection.HTTP_OK, response.statusCode());
                throw new RuntimeException("Unexpected error while getting role by name: " + response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public RoleRepresentation createRole(RealmRepresentation realm, RoleRepresentation role) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm() + "/roles");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Utils.toJson(role)))
                    .build();
            LOGGER.debug("Invoking Keycloak role create endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                // let's return the role we just created
                LOGGER.debug("Created role {}", role.getName());
                return getRole(realm, role.getName());
            } else {
                LOGGER.error("Expected HTTP_CREATED ({}) from role create, but got {}",
                        HttpURLConnection.HTTP_CREATED, response.statusCode());
                throw new RuntimeException("Unexpected error while creating role: " + role.getName());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public GroupRepresentation createGroup(RealmRepresentation realm, GroupRepresentation group) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm() + "/groups");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Utils.toJson(group)))
                    .build();
            LOGGER.debug("Invoking Keycloak group create endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_CREATED) {
                // let's return the group we just created
                LOGGER.debug("Created group {}", group.getName());
                return getGroup(realm, group.getName());
            } else {
                LOGGER.error("Expected HTTP_CREATED ({}) from group create, but got {}",
                        HttpURLConnection.HTTP_CREATED, response.statusCode());
                throw new RuntimeException("Unexpected error while creating group: " + group.getName());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public GroupRepresentation getGroup(RealmRepresentation realm, String groupName) {
        return getGroups(realm).stream()
                .filter(group -> groupName.equals(group.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find group " + groupName));
    }

    public List<GroupRepresentation> getGroups(RealmRepresentation realm) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm() + "/groups");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak list groups endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                LOGGER.debug("Found groups: {}", response.body());
                GroupRepresentation[] groups = Utils.fromJson(response.body(), GroupRepresentation[].class);
                if (groups != null) {
                    return new ArrayList<>(Arrays.asList(groups));
                } else {
                    LOGGER.error("Can't parse groups response {}", response.body());
                    throw new RuntimeException("Invalid response from " + request.uri());
                }
            } else {
                LOGGER.error("Expected HTTP_OK ({}) from list groups, but got {}",
                        HttpURLConnection.HTTP_OK, response.statusCode());
                throw new RuntimeException("Unexpected error while listing groups: " + response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public RoleRepresentation getClientRole(RealmRepresentation realm, ClientRepresentation client, String roleName) {
        return getClientRoles(realm, client).stream()
                .filter(role -> roleName.equals(role.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find client role " + roleName
                        + " for client " + client.getClientId()));
    }

    public List<RoleRepresentation> getClientRoles(RealmRepresentation realm, ClientRepresentation client) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/clients/" + client.getId()
                    + "/roles"
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak realm client roles endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                RoleRepresentation[] roles = Utils.fromJson(response.body(), RoleRepresentation[].class);
                if (roles != null) {
                    return new ArrayList<>(Arrays.asList(roles));
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

    public List<UserRepresentation> getUsers(RealmRepresentation realm) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm() + "/users");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak list groups endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                LOGGER.debug("Found users: {}", response.body());
                UserRepresentation[] users = Utils.fromJson(response.body(), UserRepresentation[].class);
                if (users != null) {
                    return new ArrayList<>(Arrays.asList(users));
                } else {
                    LOGGER.error("Can't parse users response {}", response.body());
                    throw new RuntimeException("Invalid response from " + request.uri());
                }
            } else {
                LOGGER.error("Expected HTTP_OK ({}) from list users, but got {}",
                        HttpURLConnection.HTTP_OK, response.statusCode());
                throw new RuntimeException("Unexpected error while listing users: " + response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public UserRepresentation getUser(RealmRepresentation realm, String username) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/"
                    + realm.getRealm() + "/users?exact=true&username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak realm users endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                UserRepresentation[] users = Utils.fromJson(response.body(), UserRepresentation[].class);
                if (users != null) {
                    if (users.length == 1) {
                        return users[0];
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

    public UserRepresentation createUser(RealmRepresentation realm, UserRepresentation user) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/users"
            );
            String body = Utils.toJson(user);
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            LOGGER.debug("Invoking Keycloak realm users endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_CREATED == response.statusCode()) {
                LOGGER.info("Created user " + user.getUsername());
                return getUser(realm, user.getUsername());
            } else {
                LOGGER.error("Expected HTTP_CREATED ({}) from create user, but got {}",
                        HttpURLConnection.HTTP_CREATED, response.statusCode());
                throw new RuntimeException("Keycloak users " + user.getUsername()
                        + " failed with HTTP " + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public UserRepresentation updateUser(RealmRepresentation realm, UserRepresentation user) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm()
                    + "/users/" + user.getId()
            );
            String body = Utils.toJson(user);
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            LOGGER.debug("Invoking Keycloak update user scope endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                LOGGER.info("Updated user {}", user.getUsername());
                return getUser(realm, user.getUsername());
            } else {
                LOGGER.error("Expected HTTP_NO_CONTENT ({}) from update user, but got {}",
                        HttpURLConnection.HTTP_NO_CONTENT, response.statusCode());
                throw new RuntimeException("Unexpected error while updating user: " + response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteUser(RealmRepresentation realm, UserRepresentation user) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/" + realm.getRealm()
                    + "/users/" + user.getId()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .DELETE()
                    .build();
            LOGGER.debug("Invoking Keycloak delete user scope endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                LOGGER.info("Deleted user {}", user.getUsername());
            } else {
                LOGGER.error("Expected HTTP_NO_CONTENT ({}) from delete user, but got {}",
                        HttpURLConnection.HTTP_NO_CONTENT, response.statusCode());
                throw new RuntimeException("Unexpected error while deleting user: " + response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ClientScopeRepresentation createClientScope(RealmRepresentation realm, ClientScopeRepresentation scope) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/client-scopes"
            );
            String body = Utils.toJson(scope);
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            LOGGER.debug("Invoking Keycloak realm client scopes endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_CREATED == response.statusCode()) {
                LOGGER.info("Created client scope {}", scope.getName());
                return getClientScope(realm, scope.getName());
            } else {
                LOGGER.error("Expected HTTP_CREATED ({}) from create client scope, but got {}",
                        HttpURLConnection.HTTP_CREATED, response.statusCode());
                throw new RuntimeException("Keycloak client scope " + scope.getName()
                        + " failed with HTTP " + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public RealmRepresentation getRealm(RealmRepresentation realm) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms/"
                    + URLEncoder.encode(realm.getRealm(), StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .GET()
                    .build();
            LOGGER.debug("Invoking Keycloak realms endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                LOGGER.debug("Found realm: {}", response.body());
                return Utils.fromJson(response.body(), RealmRepresentation.class);
            } else {
                LOGGER.error("Expected HTTP_OK ({}) from get realm by name, but got {}",
                        HttpURLConnection.HTTP_OK, response.statusCode());
                throw new RuntimeException("Unexpected error while getting realm by name: " + response.body());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public RealmRepresentation createRealm(RealmRepresentation realm) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin/realms");
            String requestBody = Utils.toJson(realm);
            //String requestBody = JsonSerialization.writeValueAsString(JsonSerialization.createObjectNode(realm));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            LOGGER.debug("Invoking Keycloak realm import endpoint {}", request.uri());
            LOGGER.debug(requestBody);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_CREATED == response.statusCode()) {
                LOGGER.info("Created realm {}", realm.getRealm());
                return getRealm(realm);
            } else {
                LOGGER.error("Expected HTTP_CREATED ({}) from create realm, but got {}",
                        HttpURLConnection.HTTP_CREATED, response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak create realm " + realm.getRealm()
                        + " failed with HTTP " + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void createGroupClientRoleMapping(RealmRepresentation realm, GroupRepresentation group,
                                             ClientRepresentation client, RoleRepresentation role) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/groups/" + group.getId()
                    + "/role-mappings"
                    + "/clients/" + client.getId()
            );
            String body = Utils.toJson(List.of(role));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            LOGGER.debug("Invoking Keycloak group client role mapping endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_NO_CONTENT == response.statusCode()) {
                LOGGER.info("Mapped role {} to group {} for client {}",
                        role.getName(), group.getName(), client.getName());
            } else {
                throw new RuntimeException("Keycloak clientrole mapping for group failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void createGroupRoleMapping(RealmRepresentation realm, GroupRepresentation group, RoleRepresentation role) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/groups/" + group.getId()
                    + "/role-mappings"
                    + "/realm"
            );
            // Just in case
            role.setContainerId(realm.getId());
            String body = Utils.toJson(List.of(role));
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            LOGGER.debug("Invoking Keycloak group realm role mapping endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // The POST to map realm roles to a group returns a 204 instead of a 201 created...
            if (HttpURLConnection.HTTP_NO_CONTENT == response.statusCode()) {
                LOGGER.info("Mapped role {} to group {}", role.getName(), group.getName());
            } else {
                throw new RuntimeException("Keycloak admin user group attachment failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void addUserToGroup(RealmRepresentation realm, UserRepresentation user, GroupRepresentation group) {
        try {
            URI endpoint = new URI(keycloakHost + "/admin"
                    + "/realms/" + realm.getRealm()
                    + "/users/" + user.getId()
                    + "/groups/" + group.getId()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Authorization", "Bearer " + getBearerToken())
                    .setHeader("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            LOGGER.debug("Invoking Keycloak user group attach endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // The POST to map client roles to user returns a 204 instead of a 201 created...
            if (HttpURLConnection.HTTP_NO_CONTENT == response.statusCode()) {
                LOGGER.info("Added user {} to group {}", user.getUsername(), group.getName());
            } else {
                throw new RuntimeException("Keycloak admin user group attachment failed with HTTP "
                        + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String getBearerToken() {
        // Are we using the Keycloak super user password grant and the refreshing
        // access token from the admin-cli client, or are we using the access token
        // passed through from a user sign in via the admin web app client?
        if (passwordGrant != null && passwordGrant.containsKey("access_token")) {
            //LOGGER.debug("Using admin-cli client access token");
            if (Instant.now().plus(Duration.ofSeconds(1)).isAfter(tokenExpiry)) {
                refreshBearerToken();
            }
            return (String) passwordGrant.get("access_token");
        } else if (Utils.isNotBlank(accessToken)) {
            //LOGGER.debug("Using provided access token");
            LOGGER.debug(accessToken);
            return accessToken;
        } else {
            throw new IllegalStateException("No bearer token set");
        }
    }

    private void refreshBearerToken() {
        try {
            URI endpoint = new URI(keycloakHost + "/realms/master/protocol/openid-connect/token");
            String body = "grant_type=refresh_token"
                    + "&client_id=admin-cli"
                    + "&refresh_token=" + passwordGrant.get("refresh_token");
            HttpRequest request = HttpRequest.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1) // EOF reached while reading due to chunked transfer-encoding
                    .uri(endpoint)
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            LOGGER.debug("Invoking Keycloak refresh token endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                setPasswordGrant(Utils.fromJson(response.body(), LinkedHashMap.class));
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak admin password grant failed HTTP " + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private void setPasswordGrant(Map<String, Object> passwordGrant) {
        if (passwordGrant == null || passwordGrant.isEmpty()) {
            throw new IllegalArgumentException("passwordGrant required");
        }
        this.passwordGrant = passwordGrant;
        this.tokenExpiry = Instant.now().plusSeconds(((Integer) passwordGrant.get("expires_in")).longValue());
    }

    private Map<String, Object> adminPasswordGrant(String username, String password) {
        try {
            URI endpoint = new URI(keycloakHost + "/realms/master/protocol/openid-connect/token");
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

            LOGGER.debug("Invoking Keycloak password grant endpoint {}", request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                return Utils.fromJson(response.body(), LinkedHashMap.class);
            } else {
                LOGGER.error("Received HTTP status " + response.statusCode());
                LOGGER.error(response.body());
                throw new RuntimeException("Keycloak admin password grant failed HTTP " + response.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
