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

import java.util.ArrayList;
import java.util.List;

public class ParameterStoreFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterStoreFacade.class);

    private final SsmClient ssm;

    public ParameterStoreFacade(final SsmClient ssm) {
        this.ssm = ssm;
    }

    public Parameter getParameter(String parameterName, boolean decrypt) {
        Parameter parameter = null;
        try {
            parameter = ssm.getParameter(request -> request
                    .name(parameterName)
                    .withDecryption(decrypt)).parameter();
        } catch (ParameterNotFoundException pnfe) {
            LOGGER.warn("Parameter {} does not exist", parameterName);
        } catch (SdkServiceException ssmError) {
            LOGGER.error("Error fetching parameter", ssmError);
            throw ssmError;
        }
        return parameter;
    }

    public List<Parameter> getParameters(List<String> parameterNames) {
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
        return parameters;
    }

    public List<Parameter> getParametersByPath(String parameterPathPrefix, boolean recursive, boolean decrypt) {
        List<Parameter> parameters = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                GetParametersByPathResponse response = ssm.getParametersByPath(GetParametersByPathRequest
                        .builder()
                        .path(parameterPathPrefix)
                        .recursive(recursive)
                        .withDecryption(decrypt)
                        .nextToken(nextToken)
                        .build()
                );
                nextToken = response.nextToken();
                parameters.addAll(response.parameters());
            } catch (ParameterNotFoundException notFoundException) {
                LOGGER.warn("Can't find parameters for {}", parameterPathPrefix);
            } catch (SdkServiceException ssmError) {
                LOGGER.error("ssm:GetParametersByPath error ", ssmError);
                LOGGER.error(Utils.getFullStackTrace(ssmError));
                throw ssmError;
            }
        } while (nextToken != null && !nextToken.isEmpty());
        return parameters;
    }

    public Parameter putParameter(Parameter parameter) {
        Parameter updated;
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
            LOGGER.error("ssm:PutParameter error " + ssmError.getMessage());
            throw ssmError;
        }
        return updated;
    }

    public void deleteParameter(Parameter parameter) {
        try {
            ssm.deleteParameter(request -> request
                    .name(parameter.name())
            );
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:DeleteParameter error " + ssmError.getMessage());
        }
    }

    public void deleteParameters(List<String> parametersToDelete) {
        List<String> batch = new ArrayList<>();
        try {
            for (String parameterName : parametersToDelete) {
                if (batch.size() < 10) {
                    batch.add(parameterName);
                } else {
                    DeleteParametersResponse response = ssm.deleteParameters(req -> req.names(batch));
                    if (response.hasInvalidParameters() && !response.invalidParameters().isEmpty()) {
                        LOGGER.warn("Could not delete invalid parameters " + response.invalidParameters());
                    }
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                // delete the last batch
                DeleteParametersResponse response = ssm.deleteParameters(req -> req.names(batch));
                if (response.hasInvalidParameters() && !response.invalidParameters().isEmpty()) {
                    LOGGER.warn("Could not delete invalid parameters " + response.invalidParameters());
                }
            }
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:DeleteParameters error", ssmError);
            LOGGER.error(Utils.getFullStackTrace(ssmError));
            throw ssmError;
        }
    }
}
