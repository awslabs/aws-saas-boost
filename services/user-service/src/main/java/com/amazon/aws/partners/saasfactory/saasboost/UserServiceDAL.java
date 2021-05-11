/**
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserServiceDAL {

    private final static Logger LOGGER = LoggerFactory.getLogger(UserServiceDAL.class);
    private final static String COGNITO_USERPOOL_ID = System.getenv("SAAS_BOOST_USERPOOL_ID");
    private final CognitoIdentityProviderClient cognito;

    public UserServiceDAL() {
        long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(COGNITO_USERPOOL_ID)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_USERPOOL_ID");
        }
        this.cognito = Utils.sdkClient(CognitoIdentityProviderClient.builder(), CognitoIdentityProviderClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    public List<User> getUsers() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserServiceDAL::getUsers");
        List<User> users = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                ListUsersResponse response = cognito.listUsers(ListUsersRequest.builder()
                        .userPoolId(COGNITO_USERPOOL_ID)
                        .paginationToken(nextToken)
                        .build()
                );
                nextToken = response.paginationToken();
                for (UserType userType : response.users()) {
                    users.add(fromUserType(userType));
                }
            } catch (SdkServiceException cognitoError) {
                LOGGER.error("cognito-idp::ListUsers", cognitoError);
                LOGGER.error(Utils.getFullStackTrace(cognitoError));
                throw cognitoError;
            }
        } while (nextToken != null && !nextToken.isEmpty());
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserServiceDAL::getUsers exec " + totalTimeMillis);
        return users;
    }

    public User getUser(String username) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserServiceDAL::getUser");
        User user = null;
        try {
            AdminGetUserResponse response = cognito.adminGetUser(request -> request
                    .username(username)
                    .userPoolId(COGNITO_USERPOOL_ID)
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
            LOGGER.error("cognito-idp::AdminGetUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            //throw cognitoError;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserServiceDAL::getUser exec " + totalTimeMillis);
        return user;
    }

    public User updateUser(User user) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserServiceDAL::updateUser");
        User updated = null;
        try {
            AdminUpdateUserAttributesResponse response = cognito.adminUpdateUserAttributes(request -> request
                    .userPoolId(COGNITO_USERPOOL_ID)
                    .username(user.getUsername())
                    .userAttributes(toAttributeTypeCollection(user))
            );
            updated = getUser(user.getUsername());
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp::AdminUpdateUserAttributes", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserServiceDAL::updateUser exec " + totalTimeMillis);
        return updated;
    }

    public User enableUser(String username) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserServiceDAL::enableUser");
        User enabled = null;
        try {
            AdminEnableUserResponse response = cognito.adminEnableUser(request -> request
                    .userPoolId(COGNITO_USERPOOL_ID)
                    .username(username)
            );
            enabled = getUser(username);
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp::AdminEnableUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserServiceDAL::enableUser exec " + totalTimeMillis);
        return enabled;
    }

    public User disableUser(String username) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserServiceDAL::disableUser");
        User disabled = null;
        try {
            AdminDisableUserResponse response = cognito.adminDisableUser(request -> request
                    .userPoolId(COGNITO_USERPOOL_ID)
                    .username(username)
            );
            disabled = getUser(username);
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp::AdminDisableUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserServiceDAL::disableUser exec " + totalTimeMillis);
        return disabled;
    }

    public User insertUser(User user) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserServiceDAL::insertUser");
        AdminCreateUserResponse createUserResponse = null;
        try {
            createUserResponse = cognito.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(COGNITO_USERPOOL_ID)
                    .username(user.getUsername())
                    .userAttributes(toAttributeTypeCollection(user))
                    .build()
            );
            user = fromUserType(createUserResponse.user());
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp::AdminCreateUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            user = null;
        }

        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserServiceDAL::insertUser exec " + totalTimeMillis);
        return user;
    }

    public void deleteUser(String username) {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserServiceDAL::deleteUser");
        try {
            AdminDeleteUserResponse response = cognito.adminDeleteUser(request -> request
                    .userPoolId(COGNITO_USERPOOL_ID)
                    .username(username)
            );
        } catch (SdkServiceException cognitoError) {
            LOGGER.error("cognito-idp::AdminCreateUser", cognitoError);
            LOGGER.error(Utils.getFullStackTrace(cognitoError));
            throw cognitoError;
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserServiceDAL::deleteUser exec " + totalTimeMillis);
        return;
    }

    public static Collection<AttributeType> toAttributeTypeCollection(User user) {
        Collection<AttributeType> userAttributes = new ArrayList<>();
        userAttributes.add(AttributeType.builder().name("email").value(user.getEmail()).build());
        userAttributes.add(AttributeType.builder().name("family_name").value(user.getLastName()).build());
        userAttributes.add(AttributeType.builder().name("given_name").value(user.getFirstName()).build());
        userAttributes.add(AttributeType.builder().name("email_verified").value(user.getEmailVerified().toString()).build());
        return userAttributes;
    }

    public static User fromUserType(UserType userType) {
        User user = null;
        if (userType != null) {
            user = new User();
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
                }
            }
        }
        return user;
    }

    public Map<String, String> getToken(String username, String password) {
        Map<String, String> error = new HashMap<>();
        Map<String, String> returnData = new HashMap<>();
        try {
            String appClientId = appClient(COGNITO_USERPOOL_ID);
            AdminInitiateAuthResponse authResponse = null;
            try {
                authResponse = cognito.adminInitiateAuth(request -> request
                        .userPoolId(COGNITO_USERPOOL_ID)
                        .clientId(appClientId)
                        .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                        .authParameters(Stream.of(
                                new AbstractMap.SimpleEntry<String, String>("USERNAME", username),
                                new AbstractMap.SimpleEntry<String, String>("PASSWORD", password)
                                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        )
                );

                String challenge = authResponse.challengeNameAsString();
                if (challenge != null && !challenge.isEmpty()) {
                    error.put("message", challenge);
                    returnData.put("body", Utils.toJson(error));
                    returnData.put("statuscode", "401");
                } else {
                    AuthenticationResultType auth = authResponse.authenticationResult();
                    CognitoAuthResult result = CognitoAuthResult.builder()
                            .accessToken(auth.accessToken())
                            .idToken(auth.idToken())
                            .expiresIn(auth.expiresIn())
                            .refreshToken(auth.refreshToken())
                            .tokenType(auth.tokenType())
                            .build();

                    returnData.put("body", Utils.toJson(result));
                    returnData.put("statuscode", "200");
                }
            } catch (SdkServiceException cognitoError) {
                LOGGER.error("CognitoIdentity::AdminInitiateAuth", cognitoError);
                LOGGER.error(Utils.getFullStackTrace(cognitoError));
                error.put("message", cognitoError.getMessage());
                returnData.put("body", Utils.toJson(error));
                returnData.put("statuscode", "401");
            }
        } catch (Exception e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            error.put("message", e.getMessage());
            returnData.put("body", Utils.toJson(error));
            returnData.put("statuscode", "400");
        }
        return returnData;
    }

    protected String appClient(String userPoolId) {
        String appClientId = null;
        ListUserPoolClientsResponse appClientsResponse = cognito.listUserPoolClients(request -> request.userPoolId(COGNITO_USERPOOL_ID));
        List<UserPoolClientDescription> appClients = appClientsResponse.userPoolClients();
        if (appClients != null && !appClients.isEmpty()) {
            appClientId = appClients.get(0).clientId();
        }
        return appClientId;
    }
}
