/**
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost.impl;

import com.amazon.aws.partners.saasfactory.saasboost.IllegalTokenException;
import com.amazon.aws.partners.saasfactory.saasboost.OIDCConfig;
import com.amazon.aws.partners.saasfactory.saasboost.Resource;
import com.amazon.aws.partners.saasfactory.saasboost.TokenVerifier;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public abstract class OidcTokenVerifierBase implements TokenVerifier {
    private static int CLOCK_SKEW_SECONDS = 60;
    static {
        try {
            CLOCK_SKEW_SECONDS = Integer.parseInt(System.getenv("CLOCK_SKEW_SECONDS"));
        }catch (Exception e) {
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcTokenVerifierBase.class);
    private static Map<String, PublicKey> PUB_KEY_CACHE = new HashMap<>();
    private OIDCConfig config;
    private ObjectMapper objectMapper = new ObjectMapper();

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
            JsonNode headerJson = this.objectMapper.readTree(headerDecoded);
            String kid = headerJson.get("kid").asText();
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
        JsonNode jsonNode = this.objectMapper.readTree(URI.create(openidConfiguration).toURL());
        String jwksUri = jsonNode.get("jwks_uri").asText();
        LOGGER.info("jwksUri: {}", jwksUri);
        JwkProvider provider = new UrlJwkProvider(URI.create(jwksUri).toURL());
        Jwk jwk = provider.get(kid);
        return jwk.getPublicKey();
    }

    private Claims parseToken(PublicKey pubKey, String jwtToken) {
        return Jwts.parserBuilder()
                .setSigningKey(pubKey)
                .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
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
