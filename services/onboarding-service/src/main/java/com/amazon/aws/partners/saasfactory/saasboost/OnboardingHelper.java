package com.amazon.aws.partners.saasfactory.saasboost;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OnboardingHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingHelper.class);
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    static final String SAAS_BOOST_PREFIX = "saas-boost";
    static final String PARAMETER_STORE_PREFIX = "/" + SAAS_BOOST_PREFIX + "/" + SAAS_BOOST_ENV;
    static final String DB_PASSWORD_SUFFIX = "DB_PASSWORD";
    private final SsmClient ssm;

    public OnboardingHelper() {
        this.ssm = Utils.sdkClient(SsmClient.builder(), SsmClient.SERVICE_NAME);
    }

    /**
     * Creates random database password for the given tenant and service and stores in parameter store.
     */
    Parameter createPasswordInParameterStore(String tenantId, String serviceName) {
        String parameterName = constructParameterName(tenantId, serviceName);
        String parameterValue = generateRandomPassword();
        Parameter parameter =
                Parameter.builder().type(ParameterType.SECURE_STRING).name(parameterName).value(parameterValue).build();
        return putParameter(parameter);
    }

    /**
     * Deletes all database passwords for the given tenant from parameter store.
     */
    void deleteAllDatabasePasswords(String tenantId) {
        String path = PARAMETER_STORE_PREFIX + "/" + tenantId + "/app/";
        List<Parameter> parameters = getParametersByPath(path, true);
        // filter param names by DB_PASSWORD_SUFFIX to ensure deleting only database passwords
        List<String> parameterNamesToDelete =
                parameters.stream().map(Parameter::name).filter(name -> name.endsWith(DB_PASSWORD_SUFFIX)).collect(Collectors.toList());
        deleteParameters(parameterNamesToDelete);
    }

    String constructParameterName(String tenantId, String serviceName) {
        return PARAMETER_STORE_PREFIX + "/" + tenantId + "/app/" + serviceName + "/" + DB_PASSWORD_SUFFIX;
    }

    /**
     * Gets all parameters by path from parameter store
     * TODO: Extract into common module (TBD), copied from ParameterStoreFacade.java
     */
    public List<Parameter> getParametersByPath(String parameterPathPrefix, boolean recursive) {
        List<Parameter> parameters = new ArrayList<>();
        String nextToken = null;
        do {
            try {
                GetParametersByPathResponse response =
                        ssm.getParametersByPath(GetParametersByPathRequest.builder().path(parameterPathPrefix).recursive(recursive).nextToken(nextToken).build());
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

    /**
     * Stores new parameter or updates existing one in parameter store
     * TODO: Extract into common module (TBD), copied from ParameterStoreFacade.java
     */
    private Parameter putParameter(Parameter parameter) {
        Parameter updated;
        try {
            PutParameterResponse response =
                    ssm.putParameter(request -> request.type(parameter.type()).overwrite(true).name(parameter.name()).value(parameter.value()));
            updated =
                    Parameter.builder().name(parameter.name()).value(parameter.value()).type(parameter.type()).version(response.version()).build();
        } catch (SdkServiceException ssmError) {
            LOGGER.error("ssm:PutParameter error " + ssmError.getMessage());
            throw ssmError;
        }
        return updated;
    }

    /**
     * Deletes parameters from parameter store
     * TODO: Extract into common module (TBD), copied from ParameterStoreFacade.java
     */
    public void deleteParameters(List<String> parametersNamesToDelete) {
        List<String> batch = new ArrayList<>();
        try {
            for (String parameterName : parametersNamesToDelete) {
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

    /**
     * Generates random password based on:
     * <a href="https://www.baeldung.com/java-generate-secure-password#using-common-lang3">RandomStringUtils</a>.
     * Password length: 16 (upper case: 2, lower case: 2, numbers: 2, special characters: 2)
     */
    private String generateRandomPassword() {
        String upperCaseLetters = RandomStringUtils.random(2, 65, 90, true, true);
        String lowerCaseLetters = RandomStringUtils.random(2, 97, 122, true, true);
        String numbers = RandomStringUtils.randomNumeric(2);
        String specialChar = RandomStringUtils.random(2, 33, 47, false, false);
        String totalChars = RandomStringUtils.randomAlphanumeric(8);
        String combinedChars =
                upperCaseLetters.concat(lowerCaseLetters).concat(numbers).concat(specialChar).concat(totalChars);
        List<Character> pwdChars = combinedChars.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
        Collections.shuffle(pwdChars);
        return pwdChars.stream().collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
    }
}
