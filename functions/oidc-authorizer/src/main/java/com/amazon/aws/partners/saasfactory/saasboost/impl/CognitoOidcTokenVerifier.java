package com.amazon.aws.partners.saasfactory.saasboost.impl;

import com.amazon.aws.partners.saasfactory.saasboost.OIDCConfig;

import java.util.HashMap;
import java.util.Map;

public class CognitoOidcTokenVerifier extends OidcTokenVerifierBase {
    public CognitoOidcTokenVerifier(OIDCConfig config) {
        super(config);
    }

    @Override
    protected Map<String, String> getDesiredClaims() {
        Map<String, String> desiredClaims = new HashMap<>();
        desiredClaims.put("cognito:groups", "saas-boost:admin");
        return desiredClaims;
    }
}
