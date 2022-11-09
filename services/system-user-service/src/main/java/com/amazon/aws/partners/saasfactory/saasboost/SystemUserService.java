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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SystemUserService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemUserService.class);
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String IDENTITY_PROVIDER = System.getenv("IDENTITY_PROVIDER");
    private final SystemUserDataAccessLayer dal;

    public SystemUserService() {
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(IDENTITY_PROVIDER)) {
            throw new IllegalStateException("Missing required environment variable IDENTITY_PROVIDER");
        }
        dal = SystemUserDataAccessLayerFactory.getInstance().getDataAccessLayer(IDENTITY_PROVIDER);
        if (dal == null) {
            throw new UnsupportedOperationException("No implementation for IdP " + IDENTITY_PROVIDER);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(Map<String, Object> event, Context context) {
        //Utils.logRequestEvent(event);
        return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
    }

    public APIGatewayProxyResponseEvent getUsers(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        final long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserService::getUsers");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        try {
            List<SystemUser> users = dal.getUsers(event);
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(users));
        } catch (Exception e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(400)
                    .withBody("{\"message\":\"Get users failed\"}");
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::getUsers exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        LOGGER.info("UserService::getUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::getUser " + username);
        SystemUser user = dal.getUser(event, username);
        if (user != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(user));
        } else {
            response = new APIGatewayProxyResponseEvent().withStatusCode(404).withHeaders(CORS);
        }
        return response;
    }

    public APIGatewayProxyResponseEvent updateUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        LOGGER.info("UserService::updateUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::updateUser " + username);
        SystemUser user = Utils.fromJson((String) event.get("body"), SystemUser.class);
        if (user == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid user object\"}");
        } else {
            if (user.getUsername() == null || !user.getUsername().equals(username)) {
                String error = "Can't update user " + user.getUsername() + " at resource " + username;
                LOGGER.error(error);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"" + error + "\"}");
            } else {
                user = dal.updateUser(event, user);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(user));
            }
        }
        return response;
    }

    public APIGatewayProxyResponseEvent enableUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        LOGGER.info("UserService::enableUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::enableUser " + username);
        if (Utils.isBlank(username)) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Empty username\"}");
        } else {
            try {
                SystemUser user = dal.enableUser(event, username);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(user));
            } catch (Exception e) {
                LOGGER.error("Unable to enable user: " + e.getMessage());
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Users enable failed\"}");
            }
        }
        return response;
    }

    public APIGatewayProxyResponseEvent disableUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        LOGGER.info("UserService::disableUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::disableUser " + username);

        // Get the currently signed in user from the request context
        // to make sure they aren't trying to disable themselves
        String authorizedUser = null;
        try {
            Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
            Map<String, Object> authorizer = (Map<String, Object>) requestContext.get("authorizer");
            Map<String, Object> claims = (Map<String, Object>) authorizer.get("claims");
            authorizedUser = (String) claims.get("username");
        } catch (Exception e) {
            LOGGER.error("Can't get authorizer claims from requestContext");
            Utils.logRequestEvent(event);
        }
        if (Utils.isBlank(username)) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Empty username\"}");
        } else if (authorizedUser != null && username.equals(authorizedUser)) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Users cannot disable themselves\"}");
        } else {
            try {
                SystemUser user = dal.disableUser(event, username);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(user));
            } catch (Exception e) {
                LOGGER.error("Unable to disable user: " + e.getMessage());
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Users disable failed\"}");
            }
        }
        return response;
    }

    public APIGatewayProxyResponseEvent insertUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        LOGGER.info("UserService::insertUser");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;

        SystemUser user = Utils.fromJson((String) event.get("body"), SystemUser.class);
        if (user == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid user object\"}");
        } else {
            user = dal.insertUser(event, user);
            if (null == user) {
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"Invalid user insert request\"}");
            } else {
                LOGGER.info("UserService::insertUser username " + user.getUsername());
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(user));
            }
        }
        return response;
    }

    public APIGatewayProxyResponseEvent deleteUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        LOGGER.info("UserService::deleteUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::deleteUser " + username);
        SystemUser user = Utils.fromJson((String) event.get("body"), SystemUser.class);
        if (user == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid user object\"}");
        } else {
            if (user.getUsername() == null || !user.getUsername().equals(username)) {
                String error = "Can't delete user " + user.getUsername() + " at resource " + username;
                LOGGER.error(error);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CORS)
                        .withBody("{\"message\": \"" + error + "\"}");
            } else {
                try {
                    dal.deleteUser(event, username);
                    response = new APIGatewayProxyResponseEvent()
                            .withHeaders(CORS)
                            .withStatusCode(200);
                } catch (Exception e) {
                    LOGGER.error("Unable to delete user: " + e.getMessage());
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(400)
                            .withHeaders(CORS)
                            .withBody("{\"message\": \"User delete failed\"}");
                }
            }
        }
        return response;
    }

}
