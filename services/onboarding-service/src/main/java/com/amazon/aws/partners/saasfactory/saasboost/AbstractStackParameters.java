package com.amazon.aws.partners.saasfactory.saasboost;

import software.amazon.awssdk.services.cloudformation.model.Parameter;

import java.util.*;

public abstract class AbstractStackParameters extends Properties {

    private AbstractStackParameters() {
    }

    public AbstractStackParameters(Properties defaults) {
        super(defaults);
    }

    @Override
    public synchronized Object setProperty(String key, String value) {
        if (value == null) {
            value = getProperty(key);
        }
        return super.setProperty(key, value);
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
        validateForCreate();
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
        validateForUpdate();
        return parameters;
    }

    protected abstract void validateForCreate();

    protected void validateForUpdate() {
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
