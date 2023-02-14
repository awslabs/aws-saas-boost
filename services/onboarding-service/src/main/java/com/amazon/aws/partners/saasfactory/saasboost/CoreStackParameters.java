package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.Properties;

public class CoreStackParameters extends AbstractStackParameters {

    static Properties DEFAULTS = new Properties();

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
        DEFAULTS.put("DeployActiveDirectory", "false");
        DEFAULTS.put("ADPasswordParam", "");
        DEFAULTS.put("ApplicationServices", "");
        DEFAULTS.put("AppExtensions", "");
        DEFAULTS.put("CreateMacroResources", "false");
    }

    public CoreStackParameters() {
        super(DEFAULTS);
    }
}
