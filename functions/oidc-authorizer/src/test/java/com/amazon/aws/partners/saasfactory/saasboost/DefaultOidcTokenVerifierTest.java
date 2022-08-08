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

import com.amazon.aws.partners.saasfactory.saasboost.impl.DefaultOidcTokenVerifier;
import io.jsonwebtoken.Claims;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.security.PublicKey;

@RunWith(PowerMockRunner.class)
@PrepareForTest(DefaultOidcTokenVerifier.class)
public class DefaultOidcTokenVerifierTest {
    private  DefaultOidcTokenVerifier defaultOidcTokenVerifierSpy;
    private  OIDCConfig oidcConfig;

    @Before
    public void setUp() {
        oidcConfig = new OIDCConfig("https://auth.test.com");
        defaultOidcTokenVerifierSpy = PowerMockito.spy(
                new DefaultOidcTokenVerifier(oidcConfig));
    }

    @Test
    public void verifyTest() throws Exception {
        String authorizationToken = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJZT0hTZ0tRdWQxX2FuVGlIcEtNOHozaUlQNXNlNlZrcWl3SHN3clNqbzhZIn0.eyJleHAiOjE2NTg4Mzg0NjIsImlhdCI6MTY1ODgwMjQ2MywiYXV0aF90aW1lIjoxNjU4ODAyNDYyLCJqdGkiOiJiYjE5NzdiNS0zMDlhLTQwMzItOGU0Yy1mYTU4OTRlZTZiZmQiLCJpc3MiOiJodHRwczovL2tleWNsb2FrLXNiLmRlbW8uc29sdXRpb25zLmF3cy5hMnoub3JnLmNuL2F1dGgvcmVhbG1zL3NhYXMtYm9vc3QtdGVzdCIsInN1YiI6ImMyYzE4ZDExLTJjNjItNGY3Zi04N2FmLWMyYWNhNjRhZjc5OCIsInR5cCI6IkJlYXJlciIsImF6cCI6InNhYXMtYm9vc3QtdGVzdC1jbGllbnQiLCJzZXNzaW9uX3N0YXRlIjoiOTAzYjdmYzctNmFmNC00MGNiLTk4NjAtMmI4YjdhOTIyOWVhIiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjMwMDAiXSwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCBzYWFzLWJvb3N0LWFwaTphZG1pbiIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdCIsImVtYWlsIjoidGVzdEB0ZXN0LmNvbSJ9.Z-cHW5__9OQ8jF0O-Zw3CFUY31MhEK-H4wXeGMrghMFUW3pXEX0gZ0YQS2JphikEhMzLNnA6_rl4ScYkhfVNYZwujAmxVKHw1ILb8XXLpyaUOd5L46q7PR0nxwiEs8U3WeJm1cNxihZ9LS7pveBubCXQS23sbZ_y1tANvB8Ee6Vz73ItpzJYSCISQ5KBoGpO2hVC2Y2hoe4z9XVhIrDe6qfiyT73JhL0DuQKkM3VI_8qz5_jXbp6CAEZRYzPEtmXykAL79KQukh8CzVn-Dbu73UeIXSqgQGU9KzvpXff1oC7_5XgT-IzDfZufxKDlA3IOylj6AtYQS1fJ5haNH9qgg";
        String kid = "YOHSgKQud1_anTiHpKM8z3iIP5se6VkqiwHswrSjo8Y";
        String token = authorizationToken.split(" ")[1];
        Resource resource = new Resource("GET", "settings/");

        PublicKey publicKeyMock = Mockito.mock(PublicKey.class);
        PowerMockito.doReturn(publicKeyMock).when(defaultOidcTokenVerifierSpy,
                "getPublicKeyFromIdp", kid, oidcConfig.getOpenIdConfigurationUri()
                );
        Claims claimsMock = Mockito.mock(Claims.class);
        PowerMockito.doReturn(claimsMock).when(defaultOidcTokenVerifierSpy,
                "parseToken", publicKeyMock, token
        );
        Claims claims =   defaultOidcTokenVerifierSpy.verify(authorizationToken, resource);
        Assert.assertEquals(claimsMock, claims);
        PowerMockito.verifyPrivate(defaultOidcTokenVerifierSpy).invoke("verityPermissions", claimsMock, resource);
    }
}
