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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class KeycloakUtils {

    private KeycloakUtils() {
    }

    public static RealmRepresentation asRealm(String realmName) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(realmName);
        return realm;
    }

    public static RoleRepresentation asRole(String roleName) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(roleName);
        return role;
    }

    public static GroupRepresentation asGroup(String groupName) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(groupName);
        return group;
    }

    public static UserRepresentation asUser(String username, String email, String password) {
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setCreatedTimestamp(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli());
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(true);
        CredentialRepresentation credentials = new CredentialRepresentation();
        credentials.setType("password");
        credentials.setTemporary(true);
        credentials.setValue(password);
        user.setCredentials(List.of(credentials));
        user.setRequiredActions(List.of("UPDATE_PASSWORD"));
        return user;
    }

    public static ClientRepresentation asConfidentialClient(String clientName, String clientId,
                                                            String description, List<String> scopes) {
        ClientRepresentation client = new ClientRepresentation();
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setName(clientName);
        client.setClientId(clientId);
        client.setDescription(description);
        client.setStandardFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(false);
        client.setImplicitFlowEnabled(false);
        client.setServiceAccountsEnabled(true);
        client.setPublicClient(false);
        client.setAttributes(Map.of("use.refresh.tokens", Boolean.FALSE.toString()));
        client.setDefaultClientScopes(scopes);
        return client;
    }

    public static ClientRepresentation asPublicClient(String clientName, String clientId, String description,
                                                      String redirects) {
        ClientRepresentation client = new ClientRepresentation();
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setName(clientName);
        client.setClientId(clientId);
        client.setDescription(description);
        client.setStandardFlowEnabled(true);
        client.setDirectAccessGrantsEnabled(false);
        client.setImplicitFlowEnabled(false);
        client.setServiceAccountsEnabled(false);
        client.setPublicClient(true);
        client.setAttributes(Map.of("pkce.code.challenge.method", "S256"));
        client.setRedirectUris(List.of(redirects));
        return client;
    }

    public static ClientScopeRepresentation asClientScope(String scope, String description) {
        ClientScopeRepresentation clientScope = new ClientScopeRepresentation();
        clientScope.setName(scope);
        clientScope.setDescription(description);
        clientScope.setProtocol("openid-connect");
        clientScope.setAttributes(Map.of(
                "include.in.token.scope", Boolean.TRUE.toString(),
                "display.on.consent.screen", Boolean.FALSE.toString())
        );
        return clientScope;
    }

    public static CredentialRepresentation temporaryPassword() {
        CredentialRepresentation tempPassword = new CredentialRepresentation();
        tempPassword.setType("password");
        tempPassword.setTemporary(Boolean.TRUE);
        tempPassword.setValue(Utils.randomString(12));
        return tempPassword;
    }
}
