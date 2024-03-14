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
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.HttpURLConnection;
import java.util.*;
import java.util.stream.Collectors;

public class SettingsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsService.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final Map<String, String> CORS = Map.of("Access-Control-Allow-Origin", "*");
    private final SettingsDataAccessLayer dal;

    public SettingsService() {
        this(new DefaultDependencyFactory());
    }

    // Facilitates testing by being able to mock out AWS SDK dependencies
    public SettingsService(SettingsServiceDependencyFactory init) {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        LOGGER.info("Version Info: {}", Utils.version(this.getClass()));
        this.dal = init.dal();
    }

    public APIGatewayProxyResponseEvent getSettings(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        //Utils.logRequestEvent(event);
        List<Setting> settings;

        // Normal query string params are key/value pairs ?key1=val1&key2=val2
        // Multi-value params are a list of the same key with diff values ?key=val1&key=val2&key=val3
        Map<String, List<String>> multiValueQueryParams = event.getMultiValueQueryStringParameters();

        // Filter to return just a few params (ideally, less than 10)
        if (multiValueQueryParams != null && multiValueQueryParams.containsKey("setting")) {
            List<String> namedSettings = multiValueQueryParams.get("setting");
            settings = dal.getNamedSettings(namedSettings);
            if (settings.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withHeaders(CORS)
                        .withStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
            }
        } else {
            // Otherwise, return all params
            settings = dal.getAllSettings();
        }
        return new APIGatewayProxyResponseEvent()
                .withHeaders(CORS)
                .withStatusCode(HttpURLConnection.HTTP_OK)
                .withBody(Utils.toJson(settings));
    }

    public APIGatewayProxyResponseEvent getSetting(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = event.getPathParameters();
        String settingName = params.get("id");
        Setting setting = dal.getSetting(settingName);
        if (setting != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_OK)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(setting));
        } else {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
        }
        return response;
    }

    public APIGatewayProxyResponseEvent getSecret(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = event.getPathParameters();
        String settingName = params.get("id");
        Setting setting = dal.getSecret(settingName);
        if (setting != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_OK)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(setting));
        } else {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
        }
        return response;
    }

    public APIGatewayProxyResponseEvent getParameterStoreReference(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        //Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = event.getPathParameters();
        String settingName = params.get("id");
        String parameterStoreRef = dal.getParameterStoreReference(settingName);
        if (parameterStoreRef != null) {
            response = new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpURLConnection.HTTP_OK)
                    .withHeaders(CORS)
                    .withBody(Utils.toJson(Map.of("reference-key", parameterStoreRef)));
        } else {
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_NOT_FOUND);
        }
        return response;
    }

    public APIGatewayProxyResponseEvent updateSetting(APIGatewayProxyRequestEvent event, Context context) {
        if (Utils.warmup(event)) {
            LOGGER.info("Warming up");
            return new APIGatewayProxyResponseEvent().withHeaders(CORS).withStatusCode(HttpURLConnection.HTTP_OK);
        }

        Utils.logRequestEvent(event);
        APIGatewayProxyResponseEvent response;
        Map<String, String> params = event.getPathParameters();
        String key = params.get("id");
        try {
            Setting setting = Utils.fromJson(event.getBody(), Setting.class);
            if (setting == null) {
                response = new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                        .withHeaders(CORS)
                        .withBody(Utils.toJson(Map.of("message", "Invalid request body")));
            } else {
                if (setting.getName() == null || !setting.getName().equals(key)) {
                    LOGGER.error("Can't update setting {} at resource {}", setting.getName(), key);
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(Map.of("message", "Request body must include name")));
                } else {
                    setting = dal.updateSetting(setting);
                    response = new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpURLConnection.HTTP_OK)
                            .withHeaders(CORS)
                            .withBody(Utils.toJson(setting));
                }
            }
        } catch (Exception e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            response = new APIGatewayProxyResponseEvent()
                    .withHeaders(CORS)
                    .withStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
        }
        return response;
    }

    interface SettingsServiceDependencyFactory {

        SettingsDataAccessLayer dal();
    }

    private static final class DefaultDependencyFactory implements SettingsServiceDependencyFactory {

        @Override
        public SettingsDataAccessLayer dal() {
            return new SettingsDataAccessLayer(Utils.sdkClient(SsmClient.builder(), SsmClient.SERVICE_NAME));
        }
    }
}