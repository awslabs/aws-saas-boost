package com.amazon.aws.partners.saasfactory.saasboost;

import software.amazon.awssdk.services.cloudformation.model.Parameter;

import java.util.*;

public class AbstractStackParameters extends Properties {

    private AbstractStackParameters() {
    }

    public AbstractStackParameters(Properties defaults) {
        super(defaults);
    }

    public final List<Parameter> forCreate() {
        List<Parameter> parameters = new ArrayList<>();
        for (String parameter : stringPropertyNames()) {
            parameters.add(
                    Parameter.builder()
                            .parameterKey(parameter)
                            .parameterValue(getProperty(parameter))
                            .build()
            );
        }
        validate();
        return parameters;
    }

    public final List<Parameter> forUpdate(Map<String, String> updateParameters) {
        for (Map.Entry<String, String> updateParam : updateParameters.entrySet()) {
            setProperty(updateParam.getKey(), updateParam.getValue());
        }
        List<Parameter> parameters = new ArrayList<>();
        for (String parameter : stringPropertyNames()) {
            Parameter.Builder builder = Parameter.builder();
            builder.parameterKey(parameter);
            if (updateParameters.containsKey(parameter)) {
                builder.parameterValue(getProperty(parameter));
            } else {
                builder.usePreviousValue(true);
            }
            parameters.add(builder.build());
        }
        validate();
        return parameters;
    }

    private final void validate() {
        // CloudFormation SDK stack operations fail if any parameters are null
        List<String> invalidParameters = new ArrayList<>();
        for (String parameter : stringPropertyNames()) {
            if (null == getProperty(parameter)) {
                invalidParameters.add(parameter);
            }
        }
        if (!invalidParameters.isEmpty()) {
            throw new RuntimeException("NULL CloudFormation parameters " + String.join(",", invalidParameters));
        }
    }
}
