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
import org.junit.Before;
import org.junit.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static com.amazon.aws.partners.saasfactory.saasboost.keycloak.KeycloakTestUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public final class KeycloakUserDataAccessLayerTest {
    private KeycloakApi mockApi;
    private KeycloakUserDataAccessLayer dal;
    private ArgumentCaptor<UserRepresentation> userCaptor;

    @Before
    public void setup() {
        mockApi = mock(KeycloakApi.class);
        dal = new KeycloakUserDataAccessLayer(mockApi);
        userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
    }
    
    @Test
    public void enableUserTest() {
        final String username = "user";
        UserRepresentation existingUser = mockKeycloakUser(username);
        existingUser.setEnabled(false);
        doReturn(existingUser).when(mockApi).getUser(any(Map.class), any(String.class));
        doReturn(null).when(mockApi).putUser(any(Map.class), userCaptor.capture());
        dal.enableUser(Map.of(), username);
        assertTrue("User should be active", userCaptor.getValue().isEnabled());
    }

    @Test
    public void disableUserTest() {
        final String username = "user";
        UserRepresentation existingUser = mockKeycloakUser(username);
        existingUser.setEnabled(true);
        doReturn(existingUser).when(mockApi).getUser(any(Map.class), any(String.class));
        doReturn(null).when(mockApi).putUser(any(Map.class), userCaptor.capture());
        dal.disableUser(Map.of(), username);
        assertFalse("User should not be active", userCaptor.getValue().isEnabled());
    }

    @Test
    public void insertUserTest() {
        final String username = "user";
        final String groupPath = "/testadmin";
        final SystemUser user = mockSystemUser(username);
        doReturn(mockKeycloakUser(username)).when(mockApi).createUser(any(Map.class), userCaptor.capture());
        doReturn(groupPath).when(mockApi).getAdminGroupPath(any(Map.class));
        dal.insertUser(Map.of(), user);
        UserRepresentation capturedUser = userCaptor.getValue();
        assertNotNull("Created user should have credentials", capturedUser.getCredentials());
        assertEquals("Created user should have exactly 1 credential", capturedUser.getCredentials().size(), 1);
        assertEquals("Credential type should be password", capturedUser.getCredentials().get(0).getType(), "password");
        assertTrue("Credential should be temporary", capturedUser.getCredentials().get(0).isTemporary());
        assertEquals("Credential should be 12 characters long",
                capturedUser.getCredentials().get(0).getValue().length(), 12);
        assertNotNull("Created user should have required actions", capturedUser.getRequiredActions());
        assertEquals("Created user should have exactly 1 required action",
                capturedUser.getRequiredActions().size(), 1);
        assertEquals("Required action should be UPDATE_PASSWORD",
                capturedUser.getRequiredActions().get(0), "UPDATE_PASSWORD");
        assertTrue("Created user should be enabled", capturedUser.isEnabled());
        assertNotNull("Created user should have groups", capturedUser.getGroups());
        assertEquals("Created user should have exactly 1 group", capturedUser.getGroups().size(), 1);
        assertEquals("Created user should have admin group", capturedUser.getGroups().get(0), groupPath);
    }

    @Test
    public void toSystemUserTest() {
        UserRepresentation keycloakUser = mockKeycloakUser("user");
        final String requiredAction = "UPDATE_PASSWORD";
        keycloakUser.setRequiredActions(List.of(requiredAction));
        SystemUser sysUser = KeycloakUserDataAccessLayer.toSystemUser(keycloakUser);
        assertEquals("Ids should match", keycloakUser.getId(), sysUser.getId());
        assertEquals("Created Long timestamp should match", keycloakUser.getCreatedTimestamp().longValue(),
                sysUser.getCreated().toInstant(ZoneOffset.UTC).toEpochMilli());
        assertEquals("Modified should be null", null, sysUser.getModified());
        assertEquals("Active should match enabled", keycloakUser.isEnabled(), sysUser.getActive());
        assertEquals("Usernames should match", keycloakUser.getUsername(), sysUser.getUsername());
        assertEquals("FirstName should match", keycloakUser.getFirstName(), sysUser.getFirstName());
        assertEquals("LastName should match", keycloakUser.getLastName(), sysUser.getLastName());
        assertEquals("Email should match", keycloakUser.getEmail(), sysUser.getEmail());
        assertEquals("Email verified should match", keycloakUser.isEmailVerified(), sysUser.getEmailVerified());
        assertEquals("RequiredAction should match", requiredAction, sysUser.getStatus());
    }

    @Test
    public void updateUserRepresentationTest() {
        final String username = "user";
        UserRepresentation existingUser = mockKeycloakUser(username);
        UserRepresentation editedUser = mockKeycloakUser(username);
        SystemUser edits = new SystemUser();
        UnaryOperator<String> alteration = (str) -> "different" + str;
        edits.setId(alteration.apply(editedUser.getId()));
        edits.setUsername(alteration.apply(editedUser.getUsername()));
        edits.setFirstName(alteration.apply(editedUser.getFirstName()));
        edits.setLastName(alteration.apply(editedUser.getLastName()));
        edits.setActive(!editedUser.isEnabled());
        edits.setEmail(alteration.apply(editedUser.getEmail()));
        edits.setEmailVerified(!editedUser.isEmailVerified());
        edits.setCreated(LocalDateTime.now());
        editedUser = KeycloakUserDataAccessLayer.updateUserRepresentation(edits, editedUser);
        assertEquals("Id should not have changed", existingUser.getId(), editedUser.getId());
        assertNotEquals("Username should have changed", existingUser.getUsername(), editedUser.getUsername());
        assertNotEquals("FirstName should have changed", existingUser.getFirstName(), editedUser.getFirstName());
        assertNotEquals("LastName should have changed", existingUser.getLastName(), editedUser.getLastName());
        assertNotEquals("Active should have changed", existingUser.isEnabled(), editedUser.isEnabled());
        assertNotEquals("Email should have changed", existingUser.getEmail(), editedUser.getEmail());
        assertNotEquals("EmailVerified should have changed",
                existingUser.isEmailVerified(), editedUser.isEmailVerified());
        assertNotEquals("CreatedTimestamp should have changed",
                existingUser.getCreatedTimestamp(), editedUser.getCreatedTimestamp());
    }
}
