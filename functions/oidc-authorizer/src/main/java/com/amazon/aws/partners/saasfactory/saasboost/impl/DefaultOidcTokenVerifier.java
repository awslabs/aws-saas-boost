package com.amazon.aws.partners.saasfactory.saasboost.impl;

import com.amazon.aws.partners.saasfactory.saasboost.OIDCConfig;

import java.util.HashMap;
import java.util.Map;

public class DefaultOidcTokenVerifier extends OidcTokenVerifierBase {
    public DefaultOidcTokenVerifier(OIDCConfig config) {
        super(config);
    }

    @Override
    protected Map<String, String> getDesiredClaims() {
        Map<String, String> desiredClaims = new HashMap<>();
        //scope=saas-boost-api:admin,gruops=admin
        String scopeOrGroups = System.getenv("OIDC_SCOPE_OR_GROUPS");
        if (scopeOrGroups == null) {
            return null;
        }
       String[] scopeOrGroupsArr =  scopeOrGroups.split(",");
       for(String sg: scopeOrGroupsArr) {
          String[] keyAndVal = sg.split("=");
           if (keyAndVal.length == 2) {
               desiredClaims.put(keyAndVal[0], keyAndVal[1]);
           }
       }
        return desiredClaims;
    }
}
