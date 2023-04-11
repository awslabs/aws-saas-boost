package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class OnboardingBaseStackParameters extends AbstractStackParameters {

    static final Properties DEFAULTS = new Properties();
    static final List<String> REQUIRED_FOR_CREATE = List.of("Environment", "TenantId", "Tier", "CidrPrefix");

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
