package com.amazon.aws.partners.saasfactory.saasboost;

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public class KeycloakKeyProvider implements RSAKeyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoKeyProvider.class);
    private static final String KEYCLOAK_HOST = System.getenv("KEYCLOAK_HOST");
    private static final String KEYCLOAK_REALM = System.getenv("KEYCLOAK_REALM");
    private final JwkProvider keyProvider;

    public KeycloakKeyProvider() {
        if (Utils.isBlank(KEYCLOAK_HOST)) {
            throw new IllegalStateException("Missing required environment variable KEYCLOAK_HOST");
        }
        if (Utils.isBlank(KEYCLOAK_REALM)) {
            throw new IllegalStateException("Missing required environment variable KEYCLOAK_REALM");
        }
        keyProvider = new JwkProviderBuilder(jwksUrl()).build();
    }

    @Override
    public RSAPublicKey getPublicKeyById(String kid) {
        try {
            return (RSAPublicKey) keyProvider.get(kid).getPublicKey();
        } catch (JwkException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return null;
    }

    @Override
    public String getPrivateKeyId() {
        return null;
    }

    // https://www.keycloak.org/docs/latest/securing_apps/index.html#_certificate_endpoint
    protected static URL jwksUrl() {
        URL url = null;
        try {
            url = new URL(KEYCLOAK_HOST + "/realms/" + KEYCLOAK_REALM + "/protocol/openid-connect/certs");
        } catch (MalformedURLException e) {
            LOGGER.error(Utils.getFullStackTrace(e));
        }
        return url;
    }
}
