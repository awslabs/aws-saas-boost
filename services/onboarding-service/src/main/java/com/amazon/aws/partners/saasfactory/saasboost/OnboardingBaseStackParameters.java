package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.Properties;

public class OnboardingBaseStackParameters extends AbstractStackParameters {

    static Properties DEFAULTS = new Properties();

    static {
        DEFAULTS.put("Environment", "");
        DEFAULTS.put("DomainName", "");
        DEFAULTS.put("HostedZoneId", "");
        DEFAULTS.put("SSLCertificateArn", "");
        DEFAULTS.put("TenantId", "");
        DEFAULTS.put("TenantSubDomain", "");
        DEFAULTS.put("CidrPrefix", "");
        DEFAULTS.put("Tier", "");
        DEFAULTS.put("PrivateServices", "false");
        DEFAULTS.put("DeployActiveDirectory", "false");
    }

    public OnboardingBaseStackParameters() {
        super(DEFAULTS);
    }

}
