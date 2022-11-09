package com.amazon.aws.partners.saasfactory.saasboost;

import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeycloakUserDataAccessLayer implements SystemUserDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakUserDataAccessLayer.class);
    private static final String KEYCLOAK_HOST = System.getenv("KEYCLOAK_HOST");
    private static final String KEYCLOAK_REALM = System.getenv("KEYCLOAK_REALM");
    private static final String KEYCLOAK_USER_ENDPOINT = KEYCLOAK_HOST + "/admin/realms/" + KEYCLOAK_REALM + "/users";
    private final HttpClient client = HttpClient.newBuilder().build();

    public KeycloakUserDataAccessLayer() {
        if (Utils.isEmpty(KEYCLOAK_HOST)) {
            throw new IllegalStateException("Missing required environment variable KEYCLOAK_HOST");
        }
        if (Utils.isEmpty(KEYCLOAK_REALM)) {
            throw new IllegalStateException("Missing required environment variable KEYCLOAK_REALM");
        }
    }

    protected HttpRequest.Builder keycloakRequest(Map<String, Object> event, URI endpoint) {
        return HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(endpoint)
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", getBearerToken(event));
    }

    @Override
    public List<SystemUser> getUsers(Map<String, Object> event) {
        try {
            URI endpoint = URI.create(KEYCLOAK_USER_ENDPOINT);
            HttpRequest getUsers = keycloakRequest(event, endpoint).GET().build();
            LOGGER.info("Invoking Keycloak realm users endpoint {}", getUsers.uri());
            HttpResponse<String> response = client.send(getUsers,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                LOGGER.info(response.body());
                List<Map<String, Object>> users = Utils.fromJson(response.body(), ArrayList.class);
                if (users != null) {
                    return users.stream()
                            .map(KeycloakUserDataAccessLayer::toSystemUser)
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

    @Override
    public SystemUser getUser(Map<String, Object> event, String username) {
        try {
            URI endpoint = URI.create(KEYCLOAK_USER_ENDPOINT
                    + "?exact=true"
                    + "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
            );
            HttpRequest getUsers = keycloakRequest(event, endpoint).GET().build();
            LOGGER.info("Invoking Keycloak realm users endpoint {}", getUsers.uri());
            HttpResponse<String> response = client.send(getUsers,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK == response.statusCode()) {
                List<Map<String, Object>> users = Utils.fromJson(response.body(), ArrayList.class);
                if (users != null && users.size() == 1) {
                    return users.stream()
                            .map(KeycloakUserDataAccessLayer::toSystemUser)
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

    @Override
    public SystemUser updateUser(Map<String, Object> event, SystemUser user) {
        return null;
    }

    @Override
    public SystemUser enableUser(Map<String, Object> event, String username) {
        return null;
    }

    @Override
    public SystemUser disableUser(Map<String, Object> event, String username) {
        return null;
    }

    @Override
    public SystemUser insertUser(Map<String, Object> event, SystemUser user) {
        // Create new users with a temp password that must be changed on first sign in
        CredentialRepresentation tempPassword = new CredentialRepresentation();
        tempPassword.setType("password");
        tempPassword.setTemporary(Boolean.TRUE);
        tempPassword.setValue(Utils.randomString(12));

        UserRepresentation keycloakUser = toKeycloakUser(user);
        keycloakUser.setCredentials(List.of(tempPassword));
        keycloakUser.setRequiredActions(List.of("UPDATE_PASSWORD"));
        keycloakUser.setEnabled(Boolean.TRUE);

        try {
            URI endpoint = URI.create(KEYCLOAK_USER_ENDPOINT);
            String body = Utils.toJson(keycloakUser);
            HttpRequest createUser = keycloakRequest(event, endpoint)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            LOGGER.info("Invoking Keycloak realm create user endpoint {}", createUser.uri());
            HttpResponse<String> response = client.send(createUser,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_CREATED == response.statusCode()) {
                LOGGER.info("Succcessfully created user " + user.getUsername());
                // If the POST to create user succeeds we just get back a HTTP 201 with no body
                // We need to GET the newly created user to return it
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

    @Override
    public void deleteUser(Map<String, Object> event, String username) {
        // Need the Keycloak id for this username to delete it
        SystemUser user = getUser(event, username);
        try {
            URI endpoint = URI.create(KEYCLOAK_USER_ENDPOINT + "/" + user.getId());
            HttpRequest deleteUser = keycloakRequest(event, endpoint).DELETE().build();
            LOGGER.info("Invoking Keycloak realm delete user endpoint {}", deleteUser.uri());
            HttpResponse<String> response = client.send(deleteUser,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (HttpURLConnection.HTTP_OK != response.statusCode()) {
                LOGGER.error("Error deleting realm user response {}", response.statusCode());
                throw new RuntimeException("Error deleting realm user response " + deleteUser.uri());
            } else {
                LOGGER.error("Deleted user " + username);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected static String getBearerToken(Map<String, Object> event) {
        // If we got here, the API Gateway already verified the incoming JWT
        // with the issuer, so we can safely reuse it without reverification
        Map<String, String> requestHeaders = (Map<String, String>) event.get("headers");
        return requestHeaders.get("Authorization");
    }

    protected static SystemUser toSystemUser(Map<String, Object> keycloakUser) {
        SystemUser user = null;
        if (keycloakUser != null) {
            user = new SystemUser();
            user.setId((String) keycloakUser.get("id"));
            user.setCreated(LocalDateTime.ofInstant(Instant.ofEpochSecond(
                    ((Number) keycloakUser.get("createdTimestamp")).longValue()), ZoneId.of("UTC")
            ));
            // Keycloak doesn't track when a user was last modified
            user.setModified(null);
            user.setActive((Boolean) keycloakUser.get("enabled"));
            user.setUsername((String) keycloakUser.get("username"));
            user.setFirstName((String) keycloakUser.get("firstName"));
            user.setLastName((String) keycloakUser.get("lastName"));
            user.setEmail((String) keycloakUser.get("email"));
            user.setEmailVerified((Boolean) keycloakUser.get("emailVerified"));
            if (keycloakUser.containsKey("requiredActions")) {
                List<String> requiredActions = (List<String>) keycloakUser.get("requiredActions");
                if (requiredActions != null && !requiredActions.isEmpty()) {
                    user.setStatus(requiredActions.get(0));
                }
            }
        }
        return user;
    }

    protected static UserRepresentation toKeycloakUser(SystemUser user) {
        UserRepresentation keycloakUser = null;
        if (user != null) {
            keycloakUser = new UserRepresentation();
            keycloakUser.setUsername(user.getUsername());
            keycloakUser.setFirstName(user.getFirstName());
            keycloakUser.setLastName(user.getLastName());
            keycloakUser.setEnabled(user.getActive());
            keycloakUser.setEmail(user.getEmail());
            keycloakUser.setEmailVerified(Boolean.TRUE.equals(user.getEmailVerified()));
            keycloakUser.setCreatedTimestamp(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            // TODO should we attempt to map Cognito UserStatusType to Keycloak Required Actions?
            if ("FORCE_CHANGE_PASSWORD".equals(user.getStatus())) {
                keycloakUser.setRequiredActions(List.of("UPDATE_PASSWORD"));
            }
        }
        return keycloakUser;
    }
}
