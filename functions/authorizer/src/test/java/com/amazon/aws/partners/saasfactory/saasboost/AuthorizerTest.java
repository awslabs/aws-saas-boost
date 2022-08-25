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

package com.amazon.aws.partners.saasfactory.saasboost;

import com.amazon.aws.partners.saasfactory.saasboost.io.AuthPolicy;
import com.amazon.aws.partners.saasfactory.saasboost.io.TokenAuthorizerContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class OidcAuthorizerTest {

    @Test
    public void handleRequestWithDenyTokenTest() throws JsonProcessingException {
        System.out.println("handleRequestWithEmptyTokenTest ...");
        Authorizer oidcAuthorizer = new Authorizer();
        String authorizationToken = "";
        String methodArn = "arn:aws-cn:execute-api:cn-north-1:111111111111:testapi/v1/GET/settings/";

        TokenAuthorizerContext input = new TokenAuthorizerContext(
                "TOKEN",
                authorizationToken,
                methodArn
        );
        AuthPolicy policy = oidcAuthorizer.handleRequest(input, null);

        AuthPolicy expectedPolicy = new AuthPolicy("user",
                AuthPolicy.PolicyDocument.getDenyAllPolicy("cn-north-1", "111111111111", "testapi", "v1"));

        ObjectMapper objectMapper = new ObjectMapper();

        Assert.assertEquals(objectMapper.writeValueAsString(expectedPolicy), objectMapper.writeValueAsString(policy));
    }

    @Test
    public void handleRequestWithAllowTest() throws JsonProcessingException {
        Authorizer oidcAuthorizer = new Authorizer() {
            @Override
            protected TokenVerifier getTokenVerifier() {
                return  new TokenVerifier() {
                    @Override
                    public Claims verify (String token, Resource resource) throws IllegalTokenException {
                        return new MockClaims();
                    }
                };
            }
        };

        String authorizationToken = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJZT0hTZ0tRdWQxX2FuVGlIcEtNOHozaUlQNXNlNlZrcWl3SHN3clNqbzhZIn0.eyJleHAiOjE2NTg4Mzg0NjIsImlhdCI6MTY1ODgwMjQ2MywiYXV0aF90aW1lIjoxNjU4ODAyNDYyLCJqdGkiOiJiYjE5NzdiNS0zMDlhLTQwMzItOGU0Yy1mYTU4OTRlZTZiZmQiLCJpc3MiOiJodHRwczovL2tleWNsb2FrLXNiLmRlbW8uc29sdXRpb25zLmF3cy5hMnoub3JnLmNuL2F1dGgvcmVhbG1zL3NhYXMtYm9vc3QtdGVzdCIsInN1YiI6ImMyYzE4ZDExLTJjNjItNGY3Zi04N2FmLWMyYWNhNjRhZjc5OCIsInR5cCI6IkJlYXJlciIsImF6cCI6InNhYXMtYm9vc3QtdGVzdC1jbGllbnQiLCJzZXNzaW9uX3N0YXRlIjoiOTAzYjdmYzctNmFmNC00MGNiLTk4NjAtMmI4YjdhOTIyOWVhIiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjMwMDAiXSwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCBzYWFzLWJvb3N0LWFwaTphZG1pbiIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdCIsImVtYWlsIjoidGVzdEB0ZXN0LmNvbSJ9.Z-cHW5__9OQ8jF0O-Zw3CFUY31MhEK-H4wXeGMrghMFUW3pXEX0gZ0YQS2JphikEhMzLNnA6_rl4ScYkhfVNYZwujAmxVKHw1ILb8XXLpyaUOd5L46q7PR0nxwiEs8U3WeJm1cNxihZ9LS7pveBubCXQS23sbZ_y1tANvB8Ee6Vz73ItpzJYSCISQ5KBoGpO2hVC2Y2hoe4z9XVhIrDe6qfiyT73JhL0DuQKkM3VI_8qz5_jXbp6CAEZRYzPEtmXykAL79KQukh8CzVn-Dbu73UeIXSqgQGU9KzvpXff1oC7_5XgT-IzDfZufxKDlA3IOylj6AtYQS1fJ5haNH9qgg";
        String methodArn = "arn:aws-cn:execute-api:cn-north-1:111111111111:testapi/v1/GET/settings/";

        TokenAuthorizerContext input = new TokenAuthorizerContext(
                "TOKEN",
                authorizationToken,
                methodArn
        );
        AuthPolicy policy = oidcAuthorizer.handleRequest(input, null);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthPolicy expectedPolicy = new AuthPolicy("mockSub",
                AuthPolicy.PolicyDocument.getAllowAllPolicy("cn-north-1", "111111111111",
                        "testapi", "v1"));

        Map<String, String> expectedContext = new HashMap<>();
        for(String it : Arrays.asList("issuer", "id", "sub", "email", "name", "scope", "groups")){
            expectedContext.put(it, it + "_test");
        }
        expectedPolicy.setContext(expectedContext);
        Assert.assertEquals(objectMapper.writeValueAsString(expectedPolicy), objectMapper.writeValueAsString(policy));
    }

    @Test
    public void handleRequestWithAllowTestWithOptions() throws JsonProcessingException {
        Authorizer oidcAuthorizer = new Authorizer() {
            @Override
            protected TokenVerifier getTokenVerifier() {
                return  new TokenVerifier() {
                    @Override
                    public Claims verify (String token, Resource resource) throws IllegalTokenException {
                        return new MockClaims();
                    }
                };
            }
        };

        String authorizationToken = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJZT0hTZ0tRdWQxX2FuVGlIcEtNOHozaUlQNXNlNlZrcWl3SHN3clNqbzhZIn0.eyJleHAiOjE2NTg4Mzg0NjIsImlhdCI6MTY1ODgwMjQ2MywiYXV0aF90aW1lIjoxNjU4ODAyNDYyLCJqdGkiOiJiYjE5NzdiNS0zMDlhLTQwMzItOGU0Yy1mYTU4OTRlZTZiZmQiLCJpc3MiOiJodHRwczovL2tleWNsb2FrLXNiLmRlbW8uc29sdXRpb25zLmF3cy5hMnoub3JnLmNuL2F1dGgvcmVhbG1zL3NhYXMtYm9vc3QtdGVzdCIsInN1YiI6ImMyYzE4ZDExLTJjNjItNGY3Zi04N2FmLWMyYWNhNjRhZjc5OCIsInR5cCI6IkJlYXJlciIsImF6cCI6InNhYXMtYm9vc3QtdGVzdC1jbGllbnQiLCJzZXNzaW9uX3N0YXRlIjoiOTAzYjdmYzctNmFmNC00MGNiLTk4NjAtMmI4YjdhOTIyOWVhIiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjMwMDAiXSwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCBzYWFzLWJvb3N0LWFwaTphZG1pbiIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdCIsImVtYWlsIjoidGVzdEB0ZXN0LmNvbSJ9.Z-cHW5__9OQ8jF0O-Zw3CFUY31MhEK-H4wXeGMrghMFUW3pXEX0gZ0YQS2JphikEhMzLNnA6_rl4ScYkhfVNYZwujAmxVKHw1ILb8XXLpyaUOd5L46q7PR0nxwiEs8U3WeJm1cNxihZ9LS7pveBubCXQS23sbZ_y1tANvB8Ee6Vz73ItpzJYSCISQ5KBoGpO2hVC2Y2hoe4z9XVhIrDe6qfiyT73JhL0DuQKkM3VI_8qz5_jXbp6CAEZRYzPEtmXykAL79KQukh8CzVn-Dbu73UeIXSqgQGU9KzvpXff1oC7_5XgT-IzDfZufxKDlA3IOylj6AtYQS1fJ5haNH9qgg";
        String methodArn = "arn:aws-cn:execute-api:cn-north-1:111111111111:testapi/v1/GET/settings/options";

        TokenAuthorizerContext input = new TokenAuthorizerContext(
                "TOKEN",
                authorizationToken,
                methodArn
        );
        AuthPolicy policy = oidcAuthorizer.handleRequest(input, null);
        ObjectMapper objectMapper = new ObjectMapper();
        AuthPolicy expectedPolicy = new AuthPolicy("mockSub",
                AuthPolicy.PolicyDocument.getAllowAllPolicy("cn-north-1", "111111111111",
                        "testapi", "v1"));

        Map<String, String> expectedContext = new HashMap<>();
        for(String it : Arrays.asList("issuer", "id", "sub", "email", "name", "scope", "groups")){
            expectedContext.put(it, it + "_test");
        }
        expectedPolicy.setContext(expectedContext);
        Assert.assertEquals(objectMapper.writeValueAsString(expectedPolicy), objectMapper.writeValueAsString(policy));
    }
}

class MockClaims implements Claims {

    @Override
    public String getIssuer() {
        return null;
    }

    @Override
    public Claims setIssuer(String s) {
        return null;
    }

    @Override
    public String getSubject() {
        return "mockSub";
    }

    @Override
    public Claims setSubject(String s) {
        return null;
    }

    @Override
    public String getAudience() {
        return null;
    }

    @Override
    public Claims setAudience(String s) {
        return null;
    }

    @Override
    public Date getExpiration() {
        return null;
    }

    @Override
    public Claims setExpiration(Date date) {
        return null;
    }

    @Override
    public Date getNotBefore() {
        return null;
    }

    @Override
    public Claims setNotBefore(Date date) {
        return null;
    }

    @Override
    public Date getIssuedAt() {
        return null;
    }

    @Override
    public Claims setIssuedAt(Date date) {
        return null;
    }

    @Override
    public String getId() {
        return null;
    }

    @Override
    public Claims setId(String s) {
        return null;
    }

    @Override
    public <T> T get(String s, Class<T> aClass) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public Object get(Object key) {
        //"issuer", "id", "sub", "email", "name", "scope", "scp", "groups"
        if (key.equals("scp")) {
            return  "scope_test";
        }
        return  key + "_test";
    }

    @Override
    public Object put(String key, Object value) {
        return null;
    }

    @Override
    public Object remove(Object key) {
        return null;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {

    }

    @Override
    public void clear() {

    }

    @Override
    public Set<String> keySet() {
        return null;
    }

    @Override
    public Collection<Object> values() {
        return null;
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return null;
    }
}
