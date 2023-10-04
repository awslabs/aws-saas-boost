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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CognitoApi extends AbstractIdentityProviderApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoApi.class);
    private final CognitoIdentityProviderClient cognito;
    private final String userPoolId;

    public CognitoApi(IamCredentials credentials, String userPoolId) {
        this(credentials, new DefaultDependencyFactory(userPoolId));
    }

    public CognitoApi(IamCredentials credentials, CognitoApiDependencyFactory init) {
        super(credentials);
        this.cognito = init.cognito();
        this.userPoolId = init.userPoolId();
    }

    @Override
    public List<Group> getGroups() {
        IamCredentials iam = (IamCredentials) getCredentials();
        ListGroupsResponse response = cognito.listGroups(request -> request
                .userPoolId(userPoolId)
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .credentialsProvider(iam.resolveCredentials()).build()
                )
        );
        return response.groups().stream()
                .map(groupType -> Group.builder().name(groupType.groupName()).build())
                .collect(Collectors.toList());
    }

    @Override
    public void addUserAttribute(UserAttribute attribute) {
        IamCredentials iam = (IamCredentials) getCredentials();
        AwsCredentialsProvider credentials = iam.resolveCredentials();
        DescribeUserPoolResponse response = cognito.describeUserPool(request -> request
                .userPoolId(userPoolId)
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .credentialsProvider(credentials).build())
        );
        String customAttributeName = "custom:" + attribute.getName();
        boolean exists = false;
        for (SchemaAttributeType schemaAttribute : response.userPool().schemaAttributes()) {
            if (customAttributeName.equals(schemaAttribute.name())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            cognito.addCustomAttributes(request -> request
                    .userPoolId(userPoolId)
                    .customAttributes(List.of(
                            SchemaAttributeType.builder()
                                    .attributeDataType(AttributeDataType.STRING)
                                    .name(attribute.getName())
                                    .build()
                            )
                    )
                    .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                            .credentialsProvider(credentials).build()
                    )
            );
        }
    }

    @Override
    public User createUser(User user) {
        // Cognito doesn't like NULL attribute types. It throws an error
        // instead of ignoring them.
        List<AttributeType> userAttributes = new ArrayList<>();
        if (Utils.isNotBlank(user.getEmail())) {
            userAttributes.add(AttributeType.builder().name("email").value(user.getEmail()).build());
        }
        if (Utils.isNotBlank(user.getPhoneNumber())) {
            userAttributes.add(AttributeType.builder().name("phone_number").value(user.getPhoneNumber()).build());
        }
        if (Utils.isNotBlank(user.getGivenName())) {
            userAttributes.add(AttributeType.builder().name("given_name").value(user.getGivenName()).build());
        }
        if (Utils.isNotBlank(user.getFamilyName())) {
            userAttributes.add(AttributeType.builder().name("family_name").value(user.getFamilyName()).build());
        }
        if (Utils.isNotBlank(user.getTenantId())) {
            userAttributes.add(AttributeType.builder().name("custom:tenant_id").value(user.getTenantId()).build());
        }
        if (Utils.isNotBlank(user.getTier())) {
            userAttributes.add(AttributeType.builder().name("custom:tier").value(user.getTier()).build());
        }
        IamCredentials iam = (IamCredentials) getCredentials();
        AdminCreateUserResponse response = cognito.adminCreateUser(request -> request
                .userPoolId(userPoolId)
                .username(user.getUsername())
                .temporaryPassword(user.getPassword())
                .userAttributes(userAttributes)
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .credentialsProvider(iam.resolveCredentials()).build()
                )
        );
        UserType cognitoUser = response.user();
        Map<String, String> attributes = cognitoUser.attributes().stream()
                .collect(Collectors.toMap(a -> a.name(), a -> a.value()));
        return User.builder()
                .id(attributes.getOrDefault("sub", null))
                .username(cognitoUser.username())
                .email(attributes.getOrDefault("email", null))
                .phoneNumber(attributes.getOrDefault("phone_number", null))
                .givenName(attributes.getOrDefault("given_name", null))
                .familyName(attributes.getOrDefault("family_name", null))
                .tenantId(attributes.getOrDefault("custom:tenant_id", null))
                .tier(attributes.getOrDefault("custom:tier", null))
                .build();

    }

    @Override
    public void addUserToGroup(User user, Group group) {
        IamCredentials iam = (IamCredentials) getCredentials();
        cognito.adminAddUserToGroup(request -> request
                .userPoolId(userPoolId)
                .username(user.getUsername())
                .groupName(group.getName())
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .credentialsProvider(iam.resolveCredentials()).build()
                )
        );
    }

    @Override
    public List<User> getUsers(String tenantId) {
        return Collections.emptyList();
    }

    interface CognitoApiDependencyFactory {

        String userPoolId();

        CognitoIdentityProviderClient cognito();
    }

    private static final class DefaultDependencyFactory implements CognitoApiDependencyFactory {

        private String userPoolId;

        public DefaultDependencyFactory(String userPoolId) {
            this.userPoolId = userPoolId;
        }

        @Override
        public String userPoolId() {
            return userPoolId;
        }

        @Override
        public CognitoIdentityProviderClient cognito() {
            return Utils.sdkClient(CognitoIdentityProviderClient.builder(), CognitoIdentityProviderClient.SERVICE_NAME);
        }
    }
}
