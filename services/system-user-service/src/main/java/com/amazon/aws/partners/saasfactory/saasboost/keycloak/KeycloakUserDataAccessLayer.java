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

import com.amazon.aws.partners.saasfactory.saasboost.SystemUser;
import com.amazon.aws.partners.saasfactory.saasboost.SystemUserDataAccessLayer;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeycloakUserDataAccessLayer implements SystemUserDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakUserDataAccessLayer.class);

    private final KeycloakApi keycloak;

    public KeycloakUserDataAccessLayer() {
        this(new KeycloakApi());
    }

    public KeycloakUserDataAccessLayer(KeycloakApi keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public List<SystemUser> getUsers(Map<String, Object> event) {
        return keycloak.listUsers(event).stream()
                .map(KeycloakUserDataAccessLayer::toSystemUser)
                .collect(Collectors.toList());
    }

    @Override
    public SystemUser getUser(Map<String, Object> event, String username) {
        return toSystemUser(keycloak.getUser(event, username));
    }

    @Override
    public SystemUser updateUser(Map<String, Object> event, SystemUser user) {
        UserRepresentation currentUser = keycloak.getUser(event, user.getUsername());
        UserRepresentation updatedUser = updateUserRepresentation(user, currentUser);
        return toSystemUser(keycloak.putUser(event, updatedUser));
    }

    @Override
    public SystemUser enableUser(Map<String, Object> event, String username) {
        return enableDisable(event, username, true);
    }

    @Override
    public SystemUser disableUser(Map<String, Object> event, String username) {
        return enableDisable(event, username, false);
    }

    private SystemUser enableDisable(Map<String, Object> event, String username, boolean enable) {
        UserRepresentation currentUser = keycloak.getUser(event, username);
        currentUser.setEnabled(enable);
        return toSystemUser(keycloak.putUser(event, currentUser));
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
        keycloakUser.setGroups(List.of(keycloak.getAdminGroupPath(event)));

        UserRepresentation createdUser = keycloak.createUser(event, keycloakUser);
        return toSystemUser(createdUser);
    }

    @Override
    public void deleteUser(Map<String, Object> event, String username) {
        keycloak.deleteUser(event, username);
    }

    // VisibleForTesting
    static SystemUser toSystemUser(UserRepresentation keycloakUser) {
        SystemUser user = null;
        if (keycloakUser != null) {
            user = new SystemUser();
            user.setId(keycloakUser.getId());
            user.setCreated(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(keycloakUser.getCreatedTimestamp()),
                    ZoneId.of("UTC")));
            // Keycloak doesn't track when a user was last modified
            user.setModified(null);
            user.setActive(keycloakUser.isEnabled());
            user.setUsername(keycloakUser.getUsername());
            user.setFirstName(keycloakUser.getFirstName());
            user.setLastName(keycloakUser.getLastName());
            user.setEmail(keycloakUser.getEmail());
            user.setEmailVerified(keycloakUser.isEmailVerified());
            if (!keycloakUser.getRequiredActions().isEmpty()) {
                user.setStatus(keycloakUser.getRequiredActions().get(0));
            }
        }
        return user;
    }

    // VisibleForTesting
    static UserRepresentation toKeycloakUser(SystemUser user) {
        UserRepresentation keycloakUser = null;
        if (user != null) {
            keycloakUser = updateUserRepresentation(user, new UserRepresentation());
        }
        return keycloakUser;
    }

    // VisibleForTesting
    static UserRepresentation updateUserRepresentation(SystemUser user, UserRepresentation keycloakUser) {
        if (user != null) {
            keycloakUser.setUsername(user.getUsername());
            keycloakUser.setFirstName(user.getFirstName());
            keycloakUser.setLastName(user.getLastName());
            keycloakUser.setEnabled(user.getActive());
            keycloakUser.setEmail(user.getEmail());
            keycloakUser.setEmailVerified(Boolean.TRUE.equals(user.getEmailVerified()));
            if (user.getCreated() == null) {
                keycloakUser.setCreatedTimestamp(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli());
            } else {
                keycloakUser.setCreatedTimestamp(user.getCreated().toInstant(ZoneOffset.UTC).toEpochMilli());
            }
            // TODO should we attempt to map Cognito UserStatusType to Keycloak Required Actions?
            if ("FORCE_CHANGE_PASSWORD".equals(user.getStatus())) {
                keycloakUser.setRequiredActions(List.of("UPDATE_PASSWORD"));
            }
        }
        return keycloakUser;
    }
}
