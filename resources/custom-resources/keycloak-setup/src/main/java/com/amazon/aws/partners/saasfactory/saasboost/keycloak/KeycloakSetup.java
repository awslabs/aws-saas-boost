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

import com.amazon.aws.partners.saasfactory.saasboost.CloudFormationResponse;
import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.keycloak.representations.idm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.*;
import java.util.concurrent.*;

public class KeycloakSetup implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakSetup.class);
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String READ_SCOPE = "saas-boost/" + SAAS_BOOST_ENV + "/read";
    private static final String WRITE_SCOPE = "saas-boost/" + SAAS_BOOST_ENV + "/write";
    private static final String PRIVATE_SCOPE = "saas-boost/" + SAAS_BOOST_ENV + "/private";
    private static final String RESPONSE_DATA_KEY_KEYCLOAK_REALM = "KeycloakRealm";
    private static final String RESPONSE_DATA_KEY_WEB_APP_CLIENT_ID = "AdminWebAppClientId";
    private static final String RESPONSE_DATA_KEY_WEB_APP_CLIENT_NAME = "AdminWebAppClientName";
    private static final String RESPONSE_DATA_KEY_API_APP_CLIENT_ID = "ApiAppClientId";
    private static final String RESPONSE_DATA_KEY_API_APP_CLIENT_NAME = "ApiAppClientName";
    private static final String RESPONSE_DATA_KEY_API_APP_CLIENT_SECRET = "ApiAppClientSecret";
    private static final String RESPONSE_DATA_KEY_API_PRIVATE_APP_CLIENT_ID = "PrivateApiAppClientId";
    private static final String RESPONSE_DATA_KEY_API_PRIVATE_APP_CLIENT_NAME = "PrivateApiAppClientName";
    private static final String RESPONSE_DATA_KEY_API_PRIVATE_APP_CLIENT_SECRET = "PrivateApiAppClientSecret";
    private final SecretsManagerClient secrets;

    public KeycloakSetup() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_ENV");
        }
        secrets = Utils.sdkClient(SecretsManagerClient.builder(), SecretsManagerClient.SERVICE_NAME);
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        Utils.logRequestEvent(event);

        final String requestType = (String) event.get("RequestType");
        final Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        final String keycloakHost = (String) resourceProperties.get("KeycloakHost");
        final String keycloakSecretId = (String) resourceProperties.get("KeycloakCredentials");
        final String realm = (String) resourceProperties.get("Realm");
        final String adminUserSecretId = (String) resourceProperties.get("AdminUserCredentials");
        String adminWebAppUrl = (String) resourceProperties.get("AdminWebAppUrl");
        final String redirectUriPattern = (!adminWebAppUrl.endsWith("/*")) ? adminWebAppUrl + "/*" : adminWebAppUrl;
        ExecutorService service = Executors.newSingleThreadExecutor();
        Map<String, Object> responseData = new HashMap<>();
        try {
            Runnable r = () -> {
                if ("Create".equalsIgnoreCase(requestType)) {
                    LOGGER.info("CREATE");
                    try {
                        LOGGER.info("Fetching SaaS Boost admin user credentials from Secrets Manager");
                        GetSecretValueResponse adminUserSecretValue = secrets.getSecretValue(request -> request
                                .secretId(adminUserSecretId)
                        );
                        final Map<String, String> adminUserCredentials = Utils.fromJson(
                                adminUserSecretValue.secretString(), LinkedHashMap.class);

                        LOGGER.info("Fetching Keycloak super user credentials from Secrets Manager");
                        GetSecretValueResponse keycloakSecretValue = secrets.getSecretValue(request -> request
                                .secretId(keycloakSecretId)
                        );
                        final Map<String, String> keycloakCredentials = Utils.fromJson(
                                keycloakSecretValue.secretString(), LinkedHashMap.class);

                        KeycloakAdminApi keycloak = new KeycloakAdminApi(keycloakHost,
                                keycloakCredentials.get("username"),
                                keycloakCredentials.get("password"));

                        Map<String, String> keycloakRealmSetupResults = setupKeycloak(keycloak, realm,
                                adminUserCredentials.get("username"), adminUserCredentials.get("password"),
                                adminUserCredentials.get("email"), redirectUriPattern);

                        if (keycloakRealmSetupResults != null && !keycloakRealmSetupResults.isEmpty()) {
                            responseData.putAll(keycloakRealmSetupResults);
                            // We're returning sensitive data, so be sure to use NoEcho = true
                            // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/crpg-ref-responses.html
                            CloudFormationResponse.send(event, context, "SUCCESS", responseData, true);
                        } else {
                            responseData.put("Reason", "Keycloak setup did not return app client details");
                            CloudFormationResponse.send(event, context, "FAILED", responseData);
                        }
                    } catch (SdkServiceException secretsManagerError) {
                        LOGGER.error("Secrets Manager error {}", secretsManagerError.getMessage());
                        LOGGER.error(Utils.getFullStackTrace(secretsManagerError));
                        responseData.put("Reason", secretsManagerError.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    } catch (Exception e) {
                        LOGGER.error(Utils.getFullStackTrace(e));
                        responseData.put("Reason", e.getMessage());
                        CloudFormationResponse.send(event, context, "FAILED", responseData);
                    }
                } else if ("Update".equalsIgnoreCase(requestType)) {
                    LOGGER.info("UPDATE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else if ("Delete".equalsIgnoreCase(requestType)) {
                    LOGGER.info("DELETE");
                    CloudFormationResponse.send(event, context, "SUCCESS", responseData);
                } else {
                    LOGGER.error("FAILED unknown requestType " + requestType);
                    responseData.put("Reason", "Unknown RequestType " + requestType);
                    CloudFormationResponse.send(event, context, "FAILED", responseData);
                }
            };
            Future<?> f = service.submit(r);
            f.get(context.getRemainingTimeInMillis() - 1000, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException | InterruptedException | ExecutionException e) {
            // Timed out
            LOGGER.error("FAILED unexpected error or request timed out " + e.getMessage());
            LOGGER.error(Utils.getFullStackTrace(e));
            responseData.put("Reason", e.getMessage());
            CloudFormationResponse.send(event, context, "FAILED", responseData);
        } finally {
            service.shutdown();
        }
        return null;
    }

    // Setting up a new Keycloak install programmatically is a multi step process.
    // 1. Create the Keycloak realm for this SaaS Boost environment
    // 2. Create the initial "admin" user
    // 3. Create the client scopes
    // 4. Create the app clients
    // 5. Create the realm roles
    // 6. Create the user groups
    // 7. Map the roles to groups
    // 8. Add users to their groups
    // 9. Add protocol mappers to the clients to include roles and additional claims in the tokens
    protected Map<String, String> setupKeycloak(KeycloakAdminApi keycloak, String realmName, String initialUserUsername,
                                                String initialUserPassword, String initialUserEmail,
                                                String redirectUriPattern) {
        // Keycloak realm for this SaaS Boost environment
        RealmRepresentation realm = keycloak.createRealm(KeycloakUtils.asRealm(realmName));

        // Initial "admin" user of the SaaS Boost admin web app
        UserRepresentation adminUser = keycloak.createUser(realm,
                KeycloakUtils.asUser(initialUserUsername, initialUserPassword, initialUserEmail));

        // An "admin" role to enable RBAC attributes in the tokens
        RoleRepresentation adminRole = keycloak.createRole(realm, KeycloakUtils.asRole("admin"));

        // An "admin" group for super users of the admin web app
        GroupRepresentation adminGroup = keycloak.createGroup(realm, KeycloakUtils.asGroup("admin"));

        // Get the realm management client that's auto generated with every new realm in Keycloak
        ClientRepresentation realmManagementClient = keycloak.getClient(realm, "realm-management");
        // Get the realm admin client role that's owned by the realm management client
        RoleRepresentation realmAdminRole = keycloak.getClientRole(realm, realmManagementClient, "realm-admin");
        // Now map the realm admin client role to the admin group so that the access token generated
        // during admin user sign in will have realm admin permissions in order to to create/update/delete
        // other users in the realm.
        keycloak.createGroupClientRoleMapping(realm, adminGroup, realmManagementClient, realmAdminRole);

        // Map the admin role to the admin group. If we don't do this, the groups claim is not added
        // to the access token by the group membership protocol mapper.
        keycloak.createGroupRoleMapping(realm, adminGroup, adminRole);

        // Add the initial admin user to the admin group
        keycloak.addUserToGroup(realm, adminUser, adminGroup);

        ClientScopeRepresentation readScope = keycloak.createClientScope(realm, KeycloakUtils.asClientScope(
                READ_SCOPE, "SaaS Boost Public API Read Access"));
        ClientScopeRepresentation writeScope = keycloak.createClientScope(realm, KeycloakUtils.asClientScope(
                WRITE_SCOPE, "SaaS Boost Public API Write Access"));
        ClientScopeRepresentation privateScope = keycloak.createClientScope(realm, KeycloakUtils.asClientScope(
                PRIVATE_SCOPE, "SaaS Boost Private API Read/Write Access"));

        // Public OAuth app client with PKCE for the admin web app
        // This client will get the default scopes of "openid email profile"
        String adminWebAppClientId = Utils.randomString(20);
        String adminWebAppClientName = realmName + "-admin-webapp-client";
        String adminWebAppClientDescription = "SaaS Boost Admin Web App Client";
        ClientRepresentation adminWebAppClient = keycloak.createClient(realm, KeycloakUtils.asPublicClient(
                adminWebAppClientName, adminWebAppClientId, adminWebAppClientDescription, redirectUriPattern));

        // Private OAuth app client with secret for programmatic machine-to-machine API calls
        String apiAppClientId = Utils.randomString(20);
        String apiAppClientName = realmName + "-api-client";
        String apiAppClientDescription = "SaaS Boost API App Client";
        final ClientRepresentation apiAppClient = keycloak.createClient(realm, KeycloakUtils.asConfidentialClient(
                apiAppClientName, apiAppClientId, apiAppClientDescription,
                List.of(readScope.getName(), writeScope.getName())));

        // Private OAuth app client with secret for service-to-service private API calls
        String privateApiAppClientId = Utils.randomString(20);
        String privateApiAppClientName = realmName + "-private-api-client";
        String privateApiAppClientDescription = "SaaS Boost Private API App Client";
        final ClientRepresentation privateApiAppClient = keycloak.createClient(realm,
                KeycloakUtils.asConfidentialClient(privateApiAppClientName, privateApiAppClientId,
                        privateApiAppClientDescription,
                        List.of(readScope.getName(), writeScope.getName(), privateScope.getName())));

        // Add the predefined group membership user realm role mapper to the dedicated scopes
        // and mappers for the Admin Web App client so that the access token includes the user's
        // group and role membership.

        // The Group Membership protocol mapper is under the built-in microprofile-jwt
        // client scope
        ClientScopeRepresentation microProfileScope = keycloak.getClientScope(realm, "microprofile-jwt");
        ProtocolMapperRepresentation groupsProtocolMapper = keycloak.getClientScopeProtocolMapper(realm,
                microProfileScope, "groups");
        adminWebAppClient.getProtocolMappers().add(groupsProtocolMapper);
        keycloak.updateClient(realm, adminWebAppClient);

        // We need to return details of the app clients to CloudFormation
        final Map<String, String> setupResults = new HashMap<>();
        setupResults.put(RESPONSE_DATA_KEY_KEYCLOAK_REALM, realm.getRealm());
        setupResults.put(RESPONSE_DATA_KEY_WEB_APP_CLIENT_NAME, adminWebAppClient.getName());
        setupResults.put(RESPONSE_DATA_KEY_WEB_APP_CLIENT_ID, adminWebAppClient.getClientId());
        setupResults.put(RESPONSE_DATA_KEY_API_APP_CLIENT_NAME, apiAppClient.getName());
        setupResults.put(RESPONSE_DATA_KEY_API_APP_CLIENT_ID, apiAppClient.getClientId());
        setupResults.put(RESPONSE_DATA_KEY_API_APP_CLIENT_SECRET, apiAppClient.getSecret());
        setupResults.put(RESPONSE_DATA_KEY_API_PRIVATE_APP_CLIENT_NAME, privateApiAppClient.getName());
        setupResults.put(RESPONSE_DATA_KEY_API_PRIVATE_APP_CLIENT_ID, privateApiAppClient.getClientId());
        setupResults.put(RESPONSE_DATA_KEY_API_PRIVATE_APP_CLIENT_SECRET, privateApiAppClient.getSecret());

        return setupResults;
    }
}
