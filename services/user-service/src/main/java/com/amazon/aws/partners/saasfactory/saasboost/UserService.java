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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserService implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {

    private final static Logger LOGGER = LoggerFactory.getLogger(UserService.class);
    private final static Map<String, String> CORS = Stream
            .of(new AbstractMap.SimpleEntry<String, String>("Access-Control-Allow-Origin", "*"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    private final UserServiceDAL dal;

    public UserService() {
        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = new UserServiceDAL();
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
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

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserService::getUsers");
        //Utils.logRequestEvent(event);
        List<User> users = dal.getUsers();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CORS)
                .withBody(Utils.toJson(users));
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::getUsers exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserService::getUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::getUser " + username);
        User user = dal.getUser(username);
        if (user != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(user));
        } else {
            response = new APIGatewayProxyResponseEvent().withStatusCode(404).withHeaders(CORS);
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::getUser exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent updateUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserService::updateUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::updateUser " + username);
        User user = Utils.fromJson((String) event.get("body"), User.class);
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
                user = dal.updateUser(user);
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(user));
            }
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::updateUser exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent enableUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
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
                User user = dal.enableUser(username);
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
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::enableUser exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent disableUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserService::disableUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::disableUser " + username);

        String authorizedUser = null;
        try {
            authorizedUser = (String) ((Map) ((Map) ((Map) event.get("requestContext")).get("authorizer")).get("claims")).get("username");
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
                User user = dal.disableUser(username);
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
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::disableUser exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent insertUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserService::insertUser");
        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;

        User user = Utils.fromJson((String) event.get("body"), User.class);
        if (user == null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(CORS)
                    .withBody("{\"message\": \"Invalid user object\"}");
        } else {
            user = dal.insertUser(user);
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
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::insertUser exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent deleteUser(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        LOGGER.info("UserService::deleteUser");
        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> params = (Map) event.get("pathParameters");
        String username = params.get("id");
        LOGGER.info("UserService::deleteUser " + username);
        User user = Utils.fromJson((String) event.get("body"), User.class);
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
                    dal.deleteUser(username);
                    response = new APIGatewayProxyResponseEvent()
                            .withHeaders(CORS)
                            .withStatusCode(200);
                }catch (Exception e) {
                        LOGGER.error("Unable to delete user: " + e.getMessage());
                        response = new APIGatewayProxyResponseEvent()
                                .withStatusCode(400)
                                .withHeaders(CORS)
                                .withBody("{\"message\": \"User delete failed\"}");
                    }
            }
        }
        long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
        LOGGER.info("UserService::deleteUser exec " + totalTimeMillis);
        return response;
    }

    public APIGatewayProxyResponseEvent getToken(Map<String, Object> event, Context context) {
        if (Utils.warmup(event)) {
            //LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(200);
        }

        long startTimeMillis = System.currentTimeMillis();
        APIGatewayProxyResponseEvent response = null;
        Map<String, String> error = new HashMap<>();
        try {
            Map<String, String> signIn = Utils.fromJson((String) event.get("body"), Map.class);
            if (signIn != null && !signIn.isEmpty()) {
                String username = signIn.get("username");
                String password = signIn.get("password");
                Map<String, String> retVals = dal.getToken(username, password);
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withBody(retVals.get("body"))
                        .withStatusCode(Integer.valueOf(retVals.get("statuscode")));
            } else {
                error.put("message", "request body invalid");
                response = new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(400)
                        .withBody(Utils.toJson(error));
            }
        } catch (Exception e) {
            //don't output details as it may contain password.
            //LOGGER.error(Utils.getFullStackTrace(e));
            error.put("message", e.getMessage());
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(error))
                    .withStatusCode(400);
        }
        return response;
    }
}
