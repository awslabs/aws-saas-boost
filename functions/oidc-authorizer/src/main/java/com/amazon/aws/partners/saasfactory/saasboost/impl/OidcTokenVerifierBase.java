/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost.impl;

import com.amazon.aws.partners.saasfactory.saasboost.*;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class OidcTokenVerifierBase implements TokenVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcTokenVerifierBase.class);
    private static Map<Object, PublicKey> PUB_KEY_CACHE = new HashMap<Object, PublicKey>();
    private OIDCConfig config;

    public OidcTokenVerifierBase(OIDCConfig config) {
        this.config = config;
    }

    @Override
    public Claims verify(String token, Resource resource) throws IllegalTokenException {
        if (!token.startsWith("Bearer ")) {
            throw new IllegalTokenException("Token is not Bearer token");
        }

        String jwtToken = token.split(" ")[1]; // remove Bearer
        String header = jwtToken.split("\\.")[0];
        String headerDecoded = new String(Base64.getDecoder().decode(header), StandardCharsets.UTF_8);
        LOGGER.info("headerDecoded: {}", headerDecoded);
        String openidConfiguration = config.getOpenIdConfigurationUri();
        try {
            Map headerJson = Utils.fromJson(headerDecoded, Map.class);
            String kid = headerJson.get("kid").toString();
            PublicKey pubKey;
            if (PUB_KEY_CACHE.get(kid) != null) {
                pubKey = PUB_KEY_CACHE.get(kid);
            } else {
                pubKey = getPublicKeyFromIdp(kid, openidConfiguration);
                PUB_KEY_CACHE.put(kid, pubKey);
            }
            Claims claims = parseToken(pubKey, jwtToken);
            LOGGER.info("claims: {}", claims);
            verityPermissions(claims, resource);
            return claims;
        } catch (IOException | JwkException e) {
            LOGGER.error(e.getMessage());
            throw new IllegalTokenException(e);
        }
    }

    private PublicKey getPublicKeyFromIdp(String kid, String openidConfiguration) throws IOException, JwkException {
        URLConnection c = URI.create(openidConfiguration).toURL().openConnection();
        Map jsonNode = null;
        try (InputStream inputStream = c.getInputStream()) {
            jsonNode = Utils.fromJson(inputStream, Map.class);
        }
        String jwksUri = jsonNode.get("jwks_uri").toString();
        LOGGER.info("jwksUri: {}", jwksUri);
        JwkProvider provider = new UrlJwkProvider(URI.create(jwksUri).toURL());
        Jwk jwk = provider.get(kid);
        return jwk.getPublicKey();
    }

    private Claims parseToken(PublicKey pubKey, String jwtToken) {
        return Jwts.parserBuilder()
                .setSigningKey(pubKey)
                .setAllowedClockSkewSeconds(Env.getClockSkewSeconds())
                .build()
                .parseClaimsJws(jwtToken)
                .getBody();
    }

    /**
     * Subclass should overwrite this method
     *
     * @return Desired Claims, e.g: scope=admin or groups=admin
     */
    protected abstract Map<String, String> getDesiredClaims();

    protected void verityPermissions(Claims claims, Resource resource) throws IllegalTokenException {
        LOGGER.info("verityPermissions() ...");
        Map<String, String> desiredClaims = getDesiredClaims();
        if (desiredClaims == null) {
            return;
        }

        LOGGER.info("claims: {}", claims);
        LOGGER.info("desiredClaims: {}", desiredClaims);

        for (Map.Entry<String, String> entry : desiredClaims.entrySet()) {
            String key = entry.getKey();
            String vals = entry.getValue();
            if (claims.get(key) == null) {
                LOGGER.info("cannot find " + key + " in claims");
                throw new IllegalTokenException("cannot find " + key + " in claims");
            }
            for (String val : vals.split(" ")) {
                Pattern regex = Pattern.compile("\\b" + val + "\\b");
                if (claims.get(key) instanceof String[]) {
                    if (!Arrays.asList(((String[]) claims.get(key))).contains(val)) {
                        LOGGER.info("invalid " + key + " in claims, required " + vals + ", actual array " + claims.get(key));
                        throw new IllegalTokenException("invalid " + key + " in claims");
                    }
                } else if (!regex.matcher(claims.get(key).toString()).find()) {
                    LOGGER.info("invalid " + key + " in claims, required " + val + ", actual " + claims.get(key));
                    throw new IllegalTokenException("invalid " + key + " in claims");
                }
            }
        }
    }
}