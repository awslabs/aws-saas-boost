package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.ArrayList;
import java.util.List;
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

    @Override
    protected void validate() {
        super.validate();
        List<String> invalidParameters = new ArrayList<>();
        List<String> required = List.of("Environment", "TenantId", "Tier", "CidrPrefix");
        for (String requiredParameter : required) {
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
