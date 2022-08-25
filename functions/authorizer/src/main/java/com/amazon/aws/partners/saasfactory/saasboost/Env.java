package com.amazon.aws.partners.saasfactory.saasboost;

public class Env {
    public static final String OIDC_ISSUER = "OIDC_ISSUER";
    public static final String OIDC_PERMISSIONS = "OIDC_PERMISSIONS";
    public static final String CLOCK_SKEW_SECONDS = "CLOCK_SKEW_SECONDS";


    public static String getOidcIssuer() {
        return System.getenv(OIDC_ISSUER);
    }

    public static String getOidcPermissions() {
        //scope=saas-boost-api:admin,groups=admin
        return System.getenv(OIDC_PERMISSIONS);
    }

    public static int getClockSkewSeconds() {
        try {
            return Integer.parseInt(System.getenv(CLOCK_SKEW_SECONDS));
        } catch (Exception e) {
            return 30;
        }
    }

}
