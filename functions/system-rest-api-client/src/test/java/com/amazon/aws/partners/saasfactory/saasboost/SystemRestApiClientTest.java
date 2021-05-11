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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.*;

public class SystemRestApiClientTest {

    static String host;
    static String protocol;
    static String stage;

    @BeforeClass
    public static void setup() {
        host = "123456789.execute-api.us-west-2.amazonaws.com";
        protocol = "https";
        stage = "v1";
    }

    @Test
    public void testUrlParse() throws MalformedURLException  {
        String resource = stage + "/settings?readOnly=false&foo=bar";

        URL url = new URL(protocol, host, resource);
        assertEquals(stage + "/settings", url.getPath());
        assertEquals("readOnly=false&foo=bar", url.getQuery());
    }

    @Test
    public void testEventBridgeJson() throws JsonProcessingException {
        String json = "{\n" +
                "    \"version\": \"0\",\n" +
                "    \"id\": \"343a3b5e-7e5d-960f-b1cc-f69ee52edaaa\",\n" +
                "    \"detail-type\": \"System API Call\",\n" +
                "    \"source\": \"saasboost\",\n" +
                "    \"account\": \"914245659875\",\n" +
                "    \"time\": \"2020-07-02T22:30:56Z\",\n" +
                "    \"region\": \"us-west-2\",\n" +
                "    \"resources\": [],\n" +
                "    \"detail\": {\n" +
                "        \"resource\": \"settings\",\n" +
                "        \"method\": \"GET\",\n" +
                "        \"body\": null\n" +
                "    }\n" +
                "}";
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        ApiRequestEvent event = mapper.readValue(json, ApiRequestEvent.class);

        System.out.println(mapper.writeValueAsString(event));
    }
}
