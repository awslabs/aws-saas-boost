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

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.Test;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class ApiGatewayHelperTest {

    @Test
    public void testNameValuePairs() throws Exception {
        ApiRequest request = ApiRequest.builder()
                .resource("settings?setting=SAAS_BOOST_STACK&setting=DOMAIN_NAME")
                .method("GET")
                .build();
        System.out.println("ApiRequest resource = " + request.getResource());

        String protocol = "https";
        String host = "quwodec8vb.execute-api.us-east-2.amazonaws.com";
        String stage = "v1";
        String resource = "settings?setting=DOMAIN_NAME&setting=STACK_NAME";
        SdkHttpMethod method = SdkHttpMethod.fromValue("PUT");

        URL url = new URL(protocol, host, stage + "/" + resource);
        List<NameValuePair> queryParams = URLEncodedUtils.parse(url.toURI(), StandardCharsets.UTF_8);
        System.out.println(Arrays.deepToString(queryParams.toArray()));

        SdkHttpFullRequest.Builder sdkRequestBuilder = SdkHttpFullRequest.builder()
                .protocol(protocol)
                .host(host)
                .encodedPath(url.getPath())
                .method(method);
        if (queryParams != null) {
            for (NameValuePair queryParam : queryParams) {
                sdkRequestBuilder.appendRawQueryParameter(queryParam.getName(), queryParam.getValue());
            }
        }
        SdkHttpFullRequest apiRequest = sdkRequestBuilder.build();
        System.out.println(apiRequest.rawQueryParameters().toString());
        System.out.println(apiRequest.toString());
    }
}
