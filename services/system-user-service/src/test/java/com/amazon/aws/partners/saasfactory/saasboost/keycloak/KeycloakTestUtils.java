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
import org.keycloak.representations.idm.UserRepresentation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class KeycloakTestUtils {
    public static void assertUserListsEqual(List<UserRepresentation> expected, List<UserRepresentation> actual) {
        if ((expected == null && actual != null) || (expected != null && actual == null)) {
            assertEquals("User lists do not match", expected, actual);
        }
        if (expected != null && actual != null) {
            assertEquals("User list sizes must match", expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                UserRepresentation expectedUser = expected.get(i);
                UserRepresentation actualUser = actual.get(i);
                assertUsersEqual(expectedUser, actualUser);
            }
        }
    }

    public static void assertUsersEqual(UserRepresentation expected, UserRepresentation actual) {
        // the keycloak UserRepresentation class doesn't implement .equals :(
        assertEquals("Id should match", expected.getId(), actual.getId());
        assertEquals("CreatedTimestamp should match", expected.getCreatedTimestamp(), actual.getCreatedTimestamp());
        assertEquals("Username should match", expected.getUsername(), actual.getUsername());
        assertEquals("Enabled should match", expected.isEnabled(), actual.isEnabled());
        assertEquals("Email should match", expected.getEmail(), actual.getEmail());
        assertEquals("EmailVerified should match", expected.isEmailVerified(), actual.isEmailVerified());
        assertEquals("FirstName should match", expected.getFirstName(), actual.getFirstName());
        assertEquals("LastName should match", expected.getLastName(), actual.getLastName());
        assertEquals("RequiredActions should match", expected.getRequiredActions(), actual.getRequiredActions());
    }

    public static UserRepresentation mockKeycloakUser(String username) {
        final UserRepresentation user = new UserRepresentation();
        user.setId("id" + username);
        user.setCreatedTimestamp(0L);
        user.setEnabled(true);
        user.setUsername("name" + username);
        user.setFirstName("first" + username);
        user.setLastName("last" + username);
        user.setEmail(username + "@company.com");
        user.setEmailVerified(true);
        user.setRequiredActions(List.of());
        user.setDisableableCredentialTypes(Set.of());
        user.setNotBefore(0);
        user.setAccess(Map.of());
        return user;
    }

    public static SystemUser mockSystemUser(String username) {
        final SystemUser user = new SystemUser();
        user.setId("id" + username);
        user.setCreated(LocalDateTime.now());
        user.setModified(LocalDateTime.now());
        user.setActive(true);
        user.setUsername("name" + username);
        user.setFirstName("first" + username);
        user.setLastName("last" + username);
        user.setEmail(username + "@company.com");
        user.setEmailVerified(true);
        user.setStatus("");
        return user;
    }
}
