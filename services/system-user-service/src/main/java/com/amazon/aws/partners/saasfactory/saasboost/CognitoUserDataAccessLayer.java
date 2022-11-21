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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class CognitoUserDataAccessLayer implements SystemUserDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoUserDataAccessLayer.class);
    private static final String COGNITO_USER_POOL = System.getenv("COGNITO_USER_POOL");
    private final CognitoIdentityProviderClient cognito;

    public CognitoUserDataAccessLayer() {
        if (Utils.isBlank(COGNITO_USER_POOL)) {
            throw new IllegalStateException("Missing required environment variable COGNITO_USER_POOL");
        }
        this.cognito = Utils.sdkClient(CognitoIdentityProviderClient.builder(),
                CognitoIdentityProviderClient.SERVICE_NAME);
    }

    public List<SystemUser> getUsers(Map<String, Object> event) {
        LOGGER.info("UserServiceDAL::getUsers");
        List<SystemUser> users = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                ListUsersResponse response = cognito.listUsers(ListUsersRequest.builder()
                        .userPoolId(COGNITO_USER_POOL)
                        .paginationToken(nextToken)
                        .build()
                );
                nextToken = response.paginationToken();
                for (UserType userType : response.users()) {
                    users.add(fromUserType(userType));
                }
            } catch (SdkServiceException cognitoError) {
                LOGGER.error("cognito-idp:ListUsers", cognitoError);
                LOGGER.error(Utils.getFullStackTrace(cognitoError));
                throw cognitoError;
            }
        } while (Utils.isNotEmpty(nextToken));
        return users;
    }

    public SystemUser getUser(Map<String, Object> event, String username) {
        LOGGER.info("UserServiceDAL::getUser");
        SystemUser user = null;
        try {
            AdminGetUserResponse response = cognito.adminGetUser(request -> request
                    .username(username)
                    .userPoolId(COGNITO_USER_POOL)
            );
            // Cognito's SDK was clearly not written to be user friendly...
            UserType userType = UserType.builder()
                    .userCreateDate(response.userCreateDate())
                    .userLastModifiedDate(response.userLastModifiedDate())
                    .enabled(response.enabled())
                    .attributes(response.userAttributes())
                    .userStatus(response.userStatus())
                    .userStatus(response.userStatusAsString())
                    .username(response.username())
                    .mfaOptions(response.mfaOptions())
                    .build();
            user = fromUserType(userType);
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp:AdminGetUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            //throw cognitoError;
        }
        return user;
    }

    public SystemUser updateUser(Map<String, Object> event, SystemUser user) {
        LOGGER.info("UserServiceDAL::updateUser");
        SystemUser updated;
        try {
            cognito.adminUpdateUserAttributes(request -> request
                    .userPoolId(COGNITO_USER_POOL)
                    .username(user.getUsername())
                    .userAttributes(toAttributeTypeCollection(user))
            );
            updated = getUser(event, user.getUsername());
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp:AdminUpdateUserAttributes", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
        return updated;
    }

    public SystemUser enableUser(Map<String, Object> event, String username) {
        LOGGER.info("UserServiceDAL::enableUser");
        SystemUser enabled;
        try {
            cognito.adminEnableUser(request -> request
                    .userPoolId(COGNITO_USER_POOL)
                    .username(username)
            );
            enabled = getUser(event, username);
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp:AdminEnableUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
        return enabled;
    }

    public SystemUser disableUser(Map<String, Object> event, String username) {
        LOGGER.info("UserServiceDAL:disableUser");
        SystemUser disabled;
        try {
            cognito.adminDisableUser(request -> request
                    .userPoolId(COGNITO_USER_POOL)
                    .username(username)
            );
            disabled = getUser(event, username);
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp:AdminDisableUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
        return disabled;
    }

    public SystemUser insertUser(Map<String, Object> event, SystemUser user) {
        LOGGER.info("UserServiceDAL::insertUser");
        SystemUser inserted;
        try {
            AdminCreateUserResponse createUserResponse = cognito.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(COGNITO_USER_POOL)
                    .username(user.getUsername())
                    .userAttributes(toAttributeTypeCollection(user))
                    .build()
            );
            inserted = fromUserType(createUserResponse.user());
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp:AdminCreateUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            inserted = null;
        }
        return inserted;
    }

    public void deleteUser(Map<String, Object> event, String username) {
        LOGGER.info("UserServiceDAL::deleteUser");
        try {
            cognito.adminDeleteUser(request -> request
                    .userPoolId(COGNITO_USER_POOL)
                    .username(username)
            );
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp:AdminCreateUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
    }

    public static Collection<AttributeType> toAttributeTypeCollection(SystemUser user) {
        Collection<AttributeType> userAttributes = new ArrayList<>();
        userAttributes.add(AttributeType.builder()
                .name("email")
                .value(user.getEmail())
                .build());
        userAttributes.add(AttributeType.builder()
                .name("family_name")
                .value(user.getLastName())
                .build());
        userAttributes.add(AttributeType.builder()
                .name("given_name")
                .value(user.getFirstName())
                .build());
        userAttributes.add(AttributeType.builder()
                .name("email_verified")
                .value(user.getEmailVerified().toString())
                .build());
        return userAttributes;
    }

    public static SystemUser fromUserType(UserType userType) {
        SystemUser user = null;
        if (userType != null) {
            user = new SystemUser();
            user.setId(userType.username());
            user.setUsername(userType.username());
            user.setActive(userType.enabled());
            user.setCreated(LocalDateTime.ofInstant(userType.userCreateDate(), ZoneOffset.UTC));
            user.setModified(LocalDateTime.ofInstant(userType.userLastModifiedDate(), ZoneOffset.UTC));
            user.setStatus(userType.userStatusAsString());
            for (AttributeType userAttribute : userType.attributes()) {
                switch (userAttribute.name()) {
                    case "family_name":
                        user.setLastName(userAttribute.value());
                        break;
                    case "given_name":
                        user.setFirstName(userAttribute.value());
                        break;
                    case "email":
                        user.setEmail(userAttribute.value());
                        break;
                    case "email_verified":
                        user.setEmailVerified(Boolean.valueOf(userAttribute.value()));
                        break;
                    default:
                        break;
                }
            }
        }
        return user;
    }

}
