/*
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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TokenAuthorizerRequestTest {

    private TokenAuthorizerRequest request;

    @Before
    public void setup() {
        request = TokenAuthorizerRequest.builder()
                .methodArn("arn:aws:execute-api:us-east-1:123456789012:abcdef123/test/GET/request")
                .authorizationToken("Bearer eyJraWQiOiI1Y21PWU00b0paNXZsVXh1aDBRalhzZXBBNU02RldcL0sxcGQ2YkE3eXRjWT0iLCJhbGciOiJSUzI1NiJ9"
                        + ".eyJzdWIiOiIxNWY0OWQ5Mi1mNjBkLTQwODktYWRlOC1jYmY1YmY0ZWI1N2EiLCJpc3MiOiJodHRwczpcL1wvY29nbml0by1pZHAuZXUtd2VzdC0xLmFtYXpvbmF3cy5jb21cL2V1LXdlc3QtMV85RWJlS2lYME8iLCJjb2duaXRvOnVzZXJuYW1lIjoibWliZWFyZCtzdnMzMDNkcnlydW5AYW1hem9uLmNvbSIsImN1c3RvbTp0ZW5hbnRfaWQiOiI3N2IyYmUyMC1mMzBhLTQwYzYtYmZmZi0zNzYxMTI4MzJmOTUiLCJnaXZlbl9uYW1lIjoiTGFiIDIiLCJjdXN0b206Y29tcGFueSI6IkNvZ25pdG8gQ29uZmlybWVkIiwiYXVkIjoiNXI1MHZxY21wcDg1ZmdtcjU0bnYwOTczb3MiLCJjdXN0b206cGxhbiI6IlN0YW5kYXJkIFBsYW4iLCJldmVudF9pZCI6IjViZWUzNWMwLTM3MTMtNDA2My05NWY3LWViZWVlZGJhZmNiNyIsInRva2VuX3VzZSI6ImlkIiwiYXV0aF90aW1lIjoxNTczMTA2ODc1LCJleHAiOjE1NzMxMTA0NzUsImlhdCI6MTU3MzEwNjg3NSwiZmFtaWx5X25hbWUiOiJDb2duaXRvIiwiZW1haWwiOiJtaWJlYXJkK3N2czMwM2RyeXJ1bkBhbWF6b24uY29tIn0"
                        + ".caMbyrFGqUA2oWxiorymOek8iNhfhY9Yr6iwT2XrrDAJcRrix9NT3TzDI9fJkhsOGdnPNEhceNFRckuQOmLdjuoU0UneAc7vf3RwL1c3XCn6MvZwUFxKo3SX1liALEz7cJYZtApze5-7XHQ4X5Mo44kDwd5AbBsH-r8x_b2p7iUp1w6WjOIn1_kmLc1otnwH5BNUnXPdLWx-gaVyd2mJlc3GmJWZzzEmmWB1xy2w0osZwXcthu_lnseVcRNmKds9L2J8Y0i1mEH1ROOKbDo7RKT8k6CCq_akCJ3e4maJ9aRRrIlw3OKoZDz7YYdkpfIiT6_CXLK0BIvaPHGjndLVHw")
                .build();
    }

    @Test
    public void testParseBearerToken() {
        assertTrue(request.tokenPayload().startsWith("eyJraWQiOiI1Y2"));
    }

    @Test
    public void testPrincipalId() {
        assertEquals("123456789012", request.getAccountId());
    }

    @Test
    public void testRegion() {
        assertEquals("us-east-1", request.getRegion());
    }

    @Test
    public void testApiId() {
        assertEquals("abcdef123", request.getApiId());
    }

    @Test
    public void testStage() {
        assertEquals("test", request.getStage());
    }

    @Test
    public void testType() {
        assertEquals("TOKEN", request.getType());
    }

}