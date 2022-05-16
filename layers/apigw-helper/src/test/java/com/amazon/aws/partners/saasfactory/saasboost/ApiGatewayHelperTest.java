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

import org.junit.Test;
import software.amazon.awssdk.http.SdkHttpFullRequest;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ApiGatewayHelperTest {

    @Test
    public void testAppendQueryParams() throws Exception {
        ApiRequest request = ApiRequest.builder()
                .resource("settings?setting=SAAS_BOOST_STACK&setting=DOMAIN_NAME")
                .method("GET")
                .build();

        String protocol = "https";
        String host = "xxxxxxxxxx.execute-api.us-east-2.amazonaws.com";
        String stage = "v1";

        URL url = new URL(protocol, host, stage + "/" + request.getResource());

        SdkHttpFullRequest.Builder sdkRequestBuilder = SdkHttpFullRequest.builder()
                .protocol(protocol)
                .host(host)
                .encodedPath(url.getPath())
                .method(request.getMethod());

        ApiGatewayHelper.appendQueryParams(sdkRequestBuilder, url);

        Map<String, List<String>> actual = sdkRequestBuilder.rawQueryParameters();
        assertEquals("2 query params with same name", 1, actual.size());
        assertEquals("2 query params with same name", 2, actual.get("setting").size());
        assertTrue("query parameter is named", actual.containsKey("setting"));
        assertTrue("multivalue param", actual.get("setting").contains("SAAS_BOOST_STACK"));
        assertTrue("multivalue param", actual.get("setting").contains("DOMAIN_NAME"));
    }
}
