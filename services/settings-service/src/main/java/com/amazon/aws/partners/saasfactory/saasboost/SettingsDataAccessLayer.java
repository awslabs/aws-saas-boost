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
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SettingsDataAccessLayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsDataAccessLayer.class);
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String AWS_REGION = System.getenv("AWS_REGION");

    // Package private for testing
    static final String PARAMETER_STORE_PREFIX = "/saas-boost/" + SAAS_BOOST_ENV + "/";
    // e.g. /saas-boost/production/SAAS_BOOST_BUCKET
    static final Pattern SAAS_BOOST_PARAMETER_PATTERN = Pattern.compile("^" + PARAMETER_STORE_PREFIX + "(.+)$");

    private final SsmClient ssm;

    public SettingsDataAccessLayer(SsmClient ssm) {
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_ENV)) {
            throw new IllegalStateException("Missing environment variable SAAS_BOOST_ENV");
        }
        this.ssm = ssm;
        // Warm up SSM for cold start hack
        ssm.getParametersByPath(request -> request.path(PARAMETER_STORE_PREFIX + "JUNK"));
    }

    public List<Setting> getAllSettings() {
        Map<String, Setting> parameterStore = new TreeMap<>();
        String nextToken = null;
        do {
            try {
                GetParametersByPathResponse response = ssm.getParametersByPath(GetParametersByPathRequest
                        .builder()
                        .path(PARAMETER_STORE_PREFIX)
                        .recursive(false)
                        .withDecryption(false) // don't expose secrets by default
                        .nextToken(nextToken)
                        .build()
                );
                nextToken = response.nextToken();

                for (Parameter parameter : response.parameters()) {
                    LOGGER.info("SettingsServiceDAL::getAllSettings loading Parameter Store param " + parameter.name());
                    Setting setting = fromParameterStore(parameter);
                    parameterStore.put(setting.getName(), setting);
                }
            } catch (SdkServiceException ssmError) {
                LOGGER.error("ssm:GetParametersByPath error " + ssmError.getMessage());
                throw ssmError;
            }
        } while (nextToken != null && !nextToken.isEmpty());

        return List.copyOf(parameterStore.values());
    }

    public List<Setting> getNamedSettings(List<String> namedSettings) {
        List<String> parameterNames = namedSettings
                .stream()
                .map(settingName -> toParameterStore(Setting.builder().name(settingName).build()).name())
                .collect(Collectors.toList());
        List<String> batch = new ArrayList<>();
        List<Parameter> parameters = new ArrayList<>();
        try {
            for (String parameterName : parameterNames) {
                if (batch.size() < 10) {
                    batch.add(parameterName);
                } else {
                    // Batch has reached max size of 10, make the request
                    GetParametersResponse response = ssm.getParameters(request -> request.names(batch));
                    parameters.addAll(response.parameters());

                    // Clear the batch so we can fill it up for the next request
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                // get the last batch
                GetParametersResponse response = ssm.getParameters(request -> request.names(batch));
                parameters.addAll(response.parameters());
            }
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:GetParameters error", ssmError);
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
        return parameters.stream()
                .map(SettingsDataAccessLayer::fromParameterStore)
                .collect(Collectors.toList());
    }

    public Setting getSetting(String settingName) {
        return getSetting(settingName, false);
    }

    public Setting getSetting(String settingName, boolean decrypt) {
        Setting setting = null;
        try {
            Parameter parameter = toParameterStore(Setting.builder().name(settingName).build());
            GetParameterResponse response = ssm.getParameter(request -> request
                    .name(parameter.name())
                    .withDecryption(decrypt)
            );
            setting = fromParameterStore(response.parameter());
        } catch (ParameterNotFoundException pnf) {
            LOGGER.warn("Parameter {} does not exist", settingName);
        } catch (SdkServiceException ssmError) {
            LOGGER.error("Error fetching parameter", ssmError);
            throw ssmError;
        }
        return setting;
    }

    public Setting getSecret(String settingName) {
        return getSetting(settingName, true);
    }

    public String getParameterStoreReference(String settingName) {
        Setting setting = getSetting(settingName);
        return PARAMETER_STORE_PREFIX + setting.getName() + ":" + setting.getVersion();
    }

    public Setting updateSetting(Setting setting) {
        LOGGER.info("Updating setting {} to {}", setting.getName(), setting.getValue());
        if (setting.isSecure()) {
            // If we were passed the encrypted string for a secret (from the UI),
            // don't overwrite the secret with that gibberish...
            Setting existing = getSetting(setting.getName());
            if (existing != null && existing.getValue().equals(setting.getValue())) {
                // Nothing has changed, don't overwrite the value in Parameter Store
                LOGGER.info("Skipping update of secret because encrypted values are the same");
                return setting;
            }
        }
        LOGGER.info("Calling put parameter {}", setting.getName());
        Setting updated = fromParameterStore(putParameter(toParameterStore(setting)));
        if (updated.isSecure()) {
            // we don't want to return the unencrypted value, so replace this
            // setting with the encrypted representation we just placed in ParameterStore
            updated = getSetting(updated.getName());
        }
        return updated;
    }

    public void deleteSetting(Setting setting) {
        deleteParameter(toParameterStore(setting));
    }

    private Parameter putParameter(Parameter parameter) {
        Parameter updated = null;
        try {
            PutParameterResponse response = ssm.putParameter(request -> request
                    .type(parameter.type())
                    .overwrite(true)
                    .name(parameter.name())
                    .value(parameter.value())
            );
            updated = Parameter.builder()
                    .name(parameter.name())
                    .value(parameter.value())
                    .type(parameter.type())
                    .version(response.version())
                    .build();
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:PutParameter error {}", ssmError);
            throw ssmError;
        }
        return updated;
    }

    private void deleteParameter(Parameter parameter) {
        try {
            ssm.deleteParameter(request -> request
                    .name(parameter.name())
            );
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:DeleteParameter error {}", ssmError);
            throw ssmError;
        }
        return;
    }

    protected static Setting fromParameterStore(Parameter parameter) {
        Setting setting = null;
        if (parameter != null) {
            String parameterStoreName = parameter.name();
            if (Utils.isEmpty(parameterStoreName)) {
                throw new RuntimeException("Parameter name can't be empty");
            }
            String settingName = null;
            Matcher regex = SAAS_BOOST_PARAMETER_PATTERN.matcher(parameterStoreName);
            if (regex.matches()) {
                settingName = regex.group(1);
            }
            if (settingName == null) {
                throw new RuntimeException("Parameter " + parameter.name() + " does not match SaaS Boost pattern");
            }

            setting = Setting.builder()
                    .name(settingName)
                    .value(!"N/A".equals(parameter.value()) ? parameter.value() : "")
                    .readOnly(false)
                    .secure(ParameterType.SECURE_STRING == parameter.type())
                    .version(parameter.version())
                    .build();
        }
        return setting;
    }

    protected static Parameter toParameterStore(Setting setting) {
        if (setting == null || !Setting.isValidSettingName(setting.getName())) {
            throw new RuntimeException("Can't create Parameter Store parameter with invalid Setting name");
        }
        String parameterName = PARAMETER_STORE_PREFIX + setting.getName();
        String parameterValue = (Utils.isEmpty(setting.getValue())) ? "N/A" : setting.getValue();
        Parameter parameter = Parameter.builder()
                .type(setting.isSecure() ? ParameterType.SECURE_STRING : ParameterType.STRING)
                .name(parameterName)
                .value(parameterValue)
                .build();
        return parameter;
    }

}
