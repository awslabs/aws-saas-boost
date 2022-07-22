package com.amazon.aws.partners.saasfactory.saasboost;

public class OIDCConfig {
    private String issuer;

    public OIDCConfig(String issuer) {
        this.issuer = issuer;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getOpenIdConfigurationUri(){
        return this.issuer + "/.well-known/openid-configuration";
    }

    public long timeTolerant() {
        return 5000;
    }
}
