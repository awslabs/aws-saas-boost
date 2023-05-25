package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class CoreStackParameters extends AbstractStackParameters {

    static Properties DEFAULTS = new Properties();
    static final List<String> REQUIRED_FOR_CREATE = List.of("SaaSBoostBucket", "Environment", "LambdaSourceFolder",
            "Tier", "SystemIdentityProvider", "AdminUsername", "AdminEmailAddress", "PublicApiStage", "PrivateApiStage",
            "Version", "CreateMacroResources");

    static {
        DEFAULTS.put("SaaSBoostBucket", "");
        DEFAULTS.put("LambdaSourceFolder", "lambdas");
        DEFAULTS.put("Environment", "");
        DEFAULTS.put("SystemIdentityProvider", "COGNITO");
        DEFAULTS.put("SystemIdentityProviderDomain", "");
        DEFAULTS.put("SystemIdentityProviderHostedZone", "");
        DEFAULTS.put("SystemIdentityProviderCertificate", "");
        DEFAULTS.put("AdminWebAppDomain", "");
        DEFAULTS.put("AdminWebAppHostedZone", "");
        DEFAULTS.put("AdminWebAppCertificate", "");
        DEFAULTS.put("AdminUsername", "admin");
        DEFAULTS.put("AdminEmailAddress", "");
        DEFAULTS.put("PublicApiStage", "v1");
        DEFAULTS.put("PrivateApiStage", "v1");
        DEFAULTS.put("Version", "");
        DEFAULTS.put("ApplicationServices", "");
        DEFAULTS.put("AppExtensions", "");
        DEFAULTS.put("CreateMacroResources", "false");
    }

    public CoreStackParameters() {
        super(DEFAULTS);
    }

    @Override
    protected void validateForCreate() {
        List<String> invalidParameters = new ArrayList<>();
        for (String requiredParameter : REQUIRED_FOR_CREATE) {
            if (Utils.isBlank(getProperty(requiredParameter))) {
                invalidParameters.add(requiredParameter);
            }
        }
        if (!invalidParameters.isEmpty()) {
            throw new RuntimeException("Missing values for required parameters "
                    + String.join(",", invalidParameters));
        }
    }
}
