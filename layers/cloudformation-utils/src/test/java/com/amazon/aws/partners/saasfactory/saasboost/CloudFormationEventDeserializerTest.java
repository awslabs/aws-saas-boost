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

import java.util.LinkedHashMap;

import static org.junit.Assert.*;

public class CloudFormationEventDeserializerTest {

    @Test
    public void testDeserialize() {
        String snsMessage = "StackId='arn:aws:cloudformation:us-east-1:444455556666:stack/sb-env-core-D8CPRXEBJBCV/a80dacc0-7263-11ec-8a77-026f030e7a17'\nTimestamp='2022-01-10T22:23:41.278Z'\nEventId='f700b430-7263-11ec-b90e-0adccbefb08b'\nLogicalResourceId='sb-env-core-D8CPRXEBJBCV'\nNamespace='444455556666'\nPhysicalResourceId='arn:aws:cloudformation:us-east-1:444455556666:stack/sb-env-core-D8CPRXEBJBCV/a80dacc0-7263-11ec-8a77-026f030e7a17'\nPrincipalId='abcdef01234567890'\nResourceProperties='null'\nResourceStatus='CREATE_COMPLETE'\nResourceStatusReason=''\nResourceType='AWS::CloudFormation::Stack'\nStackName='sb-env-core-D8CPRXEBJBCV'\nClientRequestToken='null'\n";

        CloudFormationEvent event = CloudFormationEventDeserializer.deserialize(snsMessage);
        assertEquals("arn:aws:cloudformation:us-east-1:444455556666:stack/sb-env-core-D8CPRXEBJBCV/a80dacc0-7263-11ec-8a77-026f030e7a17", event.getStackId());
        assertEquals("2022-01-10T22:23:41.278Z", event.getTimestamp());
        assertEquals("f700b430-7263-11ec-b90e-0adccbefb08b", event.getEventId());
        assertEquals("sb-env-core-D8CPRXEBJBCV", event.getLogicalResourceId());
        assertEquals("444455556666", event.getNamespace());
        assertEquals("arn:aws:cloudformation:us-east-1:444455556666:stack/sb-env-core-D8CPRXEBJBCV/a80dacc0-7263-11ec-8a77-026f030e7a17", event.getPhysicalResourceId());
        assertEquals("abcdef01234567890", event.getPrincipalId());
        assertEquals("CREATE_COMPLETE", event.getResourceStatus());
        assertEquals("", event.getResourceStatusReason());
        assertEquals("AWS::CloudFormation::Stack", event.getResourceType());
        assertEquals("sb-env-core-D8CPRXEBJBCV", event.getStackName());
        assertNull(event.getClientRequestToken());
        assertNotNull(event.getResourceProperties());
        assertEquals(0, event.getResourceProperties().size());

        snsMessage = "StackId='arn:aws:cloudformation:us-east-1:444455556666:stack/sb-dev7-core-D8CPRXEBJBCV/a80dacc0-7263-11ec-8a77-026f030e7a17'\nTimestamp='2022-01-10T22:21:46.278Z'\nEventId='SystemRestClientExecRole-CREATE_IN_PROGRESS-2022-01-10T22:21:46.278Z'\nLogicalResourceId='SystemRestClientExecRole'\nNamespace='444455556666'\nResourceProperties='{\"Path\":\"/\",\"RoleName\":\"sb-private-api-client-role-dev7-us-east-1\",\"Policies\":[{\"PolicyName\":\"sb-private-api-client-policy-dev7-us-east-1\",\"PolicyDocument\":{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":[\"logs:PutLogEvents\"],\"Resource\":[\"arn:aws:logs:us-east-1:444455556666:log-group:*:log-stream:*\"],\"Effect\":\"Allow\"},{\"Action\":[\"logs:CreateLogStream\",\"logs:DescribeLogStreams\"],\"Resource\":[\"arn:aws:logs:us-east-1:444455556666:log-group:*\"],\"Effect\":\"Allow\"},{\"Action\":[\"sts:AssumeRole\"],\"Resource\":\"arn:aws:iam::444455556666:role/sb-private-api-trust-role-dev7-us-east-1\",\"Effect\":\"Allow\"}]}}],\"AssumeRolePolicyDocument\":{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":[\"sts:AssumeRole\"],\"Effect\":\"Allow\",\"Principal\":{\"Service\":[\"lambda.amazonaws.com\"]}}]}}'\nResourceStatus='CREATE_IN_PROGRESS'\nResourceStatusReason=''\nResourceType='AWS::IAM::Role'\nStackName='sb-dev7-core-D8CPRXEBJBCV'\nClientRequestToken='null'\n";
        event = CloudFormationEventDeserializer.deserialize(snsMessage);
        assertEquals("SystemRestClientExecRole-CREATE_IN_PROGRESS-2022-01-10T22:21:46.278Z", event.getEventId());
        assertEquals(4, event.getResourceProperties().size());
        assertTrue(event.getResourceProperties().containsKey("RoleName"));
        assertTrue(event.getResourceProperties().containsKey("Policies"));
        assertTrue(event.getResourceProperties().containsKey("Path"));
        assertTrue(event.getResourceProperties().containsKey("AssumeRolePolicyDocument"));
    }
}
