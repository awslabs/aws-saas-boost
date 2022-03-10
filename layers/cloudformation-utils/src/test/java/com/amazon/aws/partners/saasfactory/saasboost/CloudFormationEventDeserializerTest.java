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
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CloudFormationEventDeserializerTest {

    @Test
    public void testDeserializeNoResourceProperties() {
        String snsMessage = "StackId='arn:aws:cloudformation:us-east-1:444455556666:stack/sb-env-core-D8CPRXEBJBCV/a80dacc0-7263-11ec-8a77-026f030e7a17'\n"
                + "Timestamp='2022-01-10T22:23:41.278Z'\n"
                + "EventId='f700b430-7263-11ec-b90e-0adccbefb08b'\n"
                + "LogicalResourceId='sb-env-core-D8CPRXEBJBCV'\n"
                + "Namespace='444455556666'\n"
                + "PhysicalResourceId='arn:aws:cloudformation:us-east-1:444455556666:stack/sb-env-core-D8CPRXEBJBCV/a80dacc0-7263-11ec-8a77-026f030e7a17'\n"
                + "PrincipalId='abcdef01234567890'\n"
                + "ResourceProperties='null'\n"
                + "ResourceStatus='CREATE_COMPLETE'\n"
                + "ResourceStatusReason=''\n"
                + "ResourceType='AWS::CloudFormation::Stack'\n"
                + "StackName='sb-env-core-D8CPRXEBJBCV'\n"
                + "ClientRequestToken='null'\n";

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
    }

    @Test
    public void testDeserializeResourcePropertiesJson() {
        String snsMessage = "StackId='arn:aws:cloudformation:us-west-2:444455556666:stack/sb-multi-tenant-34f463df-app-internal-106JUDH6M0O5/cb888e50-9f35-11ec-84c4-06699cbc5715'\n"
                + "Timestamp='2022-03-08T23:16:42.901Z'\n"
                + "EventId='ServiceDiscoveryNamespace-CREATE_IN_PROGRESS-2022-03-08T23:16:42.901Z'\n"
                + "LogicalResourceId='ServiceDiscoveryNamespace'\n"
                + "Namespace='444455556666'\n"
                + "ResourceProperties='{\"Vpc\":\"vpc-057dbff238eb056ed\",\"Name\":\"local\"}'\n"
                + "ResourceStatus='CREATE_IN_PROGRESS'\n"
                + "ResourceStatusReason=''\n"
                + "ResourceType='AWS::ServiceDiscovery::PrivateDnsNamespace'\n"
                + "StackName='sb-multi-tenant-34f463df-app-internal-106JUDH6M0O5'\n"
                + "ClientRequestToken='null'\n";

        CloudFormationEvent event = CloudFormationEventDeserializer.deserialize(snsMessage);
        assertEquals("arn:aws:cloudformation:us-west-2:444455556666:stack/sb-multi-tenant-34f463df-app-internal-106JUDH6M0O5/cb888e50-9f35-11ec-84c4-06699cbc5715", event.getStackId());
        assertEquals("2022-03-08T23:16:42.901Z", event.getTimestamp());
        assertEquals("ServiceDiscoveryNamespace-CREATE_IN_PROGRESS-2022-03-08T23:16:42.901Z", event.getEventId());
        assertEquals("ServiceDiscoveryNamespace", event.getLogicalResourceId());
        assertEquals("444455556666", event.getNamespace());
        assertEquals("CREATE_IN_PROGRESS", event.getResourceStatus());
        assertEquals("", event.getResourceStatusReason());
        assertEquals("AWS::ServiceDiscovery::PrivateDnsNamespace", event.getResourceType());
        assertEquals("sb-multi-tenant-34f463df-app-internal-106JUDH6M0O5", event.getStackName());
        assertNull(event.getClientRequestToken());
        assertNotNull(event.getResourceProperties());
        assertEquals(2, event.getResourceProperties().size());
        assertTrue(event.getResourceProperties().containsKey("Vpc"));
        assertEquals("vpc-057dbff238eb056ed", event.getResourceProperties().get("Vpc"));
        assertTrue(event.getResourceProperties().containsKey("Name"));
        assertEquals("local", event.getResourceProperties().get("Name"));
    }

    @Test
    public void testDeserializeResourcePropertiesNestedJson() {
        String snsMessage = "StackId='arn:aws:cloudformation:us-west-2:444455556666:stack/sb-multi-tenant-34f463df-app-feature-6NIE29OV500Y/ca8ffd80-9f35-11ec-84c4-06699cbc5715'\n"
                + "Timestamp='2022-03-08T23:17:33.184Z'\n"
                + "EventId='CodePipeline-CREATE_COMPLETE-2022-03-08T23:17:33.184Z'\n"
                + "LogicalResourceId='CodePipeline'\n"
                + "Namespace='444455556666'\n"
                + "PhysicalResourceId='sb-multi-tenant-34f463df-feature'\n"
                + "ResourceProperties='{\"ArtifactStore\":{\"Type\":\"S3\",\"Location\":\"sb-multi-codepipelinebucket-1hnsl5p5az131\"},\"Stages\":[{\"Actions\":[{\"ActionTypeId\":{\"Owner\":\"AWS\",\"Category\":\"Source\",\"Version\":\"1\",\"Provider\":\"S3\"},\"Configuration\":{\"PollForSourceChanges\":\"false\",\"S3Bucket\":\"sb-multi-codepipelinebucket-1hnsl5p5az131\",\"S3ObjectKey\":\"34f463df-67db-489b-b0bf-ed7ca70e3ba4/sb-multi-tenant-34f463df-feature\"},\"OutputArtifacts\":[{\"Name\":\"imgdef\"}],\"Name\":\"SourceAction\"}],\"Name\":\"Source\"},{\"Actions\":[{\"ActionTypeId\":{\"Owner\":\"AWS\",\"Category\":\"Invoke\",\"Version\":\"1\",\"Provider\":\"Lambda\"},\"Configuration\":{\"FunctionName\":\"sb-multi-update-ecs\",\"UserParameters\":\"{\\\"cluster\\\":\\\"sb-multi-tenant-34f463df\\\",\\\"service\\\":\\\"feature\\\",\\\"desiredCount\\\":1}\"},\"RunOrder\":\"1\",\"Name\":\"PreDeployAction\"},{\"ActionTypeId\":{\"Owner\":\"AWS\",\"Category\":\"Deploy\",\"Version\":\"1\",\"Provider\":\"ECS\"},\"Configuration\":{\"ServiceName\":\"feature\",\"FileName\":\"imagedefinitions.json\",\"ClusterName\":\"sb-multi-tenant-34f463df\"},\"InputArtifacts\":[{\"Name\":\"imgdef\"}],\"RunOrder\":\"2\",\"Name\":\"DeployAction\"}],\"Name\":\"Deploy\"}],\"RestartExecutionOnUpdate\":\"false\",\"RoleArn\":\"arn:aws:iam::444455556666:role/sb-multi-tenant-pipeline-role-us-west-2\",\"Tags\":[{\"Value\":\"34f463df-67db-489b-b0bf-ed7ca70e3ba4\",\"Key\":\"Tenant\"},{\"Value\":\"sb-multi-tenant-34f463df-app-feature-6NIE29OV500Y\",\"Key\":\"Stack\"}],\"Name\":\"sb-multi-tenant-34f463df-feature\"}'\n"
                + "ResourceStatus='CREATE_COMPLETE'\n"
                + "ResourceStatusReason=''\n"
                + "ResourceType='AWS::CodePipeline::Pipeline'\n"
                + "StackName='sb-multi-tenant-34f463df-app-feature-6NIE29OV500Y'\n"
                + "ClientRequestToken='null'\n";

        CloudFormationEvent event = CloudFormationEventDeserializer.deserialize(snsMessage);
        assertEquals("arn:aws:cloudformation:us-west-2:444455556666:stack/sb-multi-tenant-34f463df-app-feature-6NIE29OV500Y/ca8ffd80-9f35-11ec-84c4-06699cbc5715", event.getStackId());
        assertEquals("2022-03-08T23:17:33.184Z", event.getTimestamp());
        assertEquals("CodePipeline-CREATE_COMPLETE-2022-03-08T23:17:33.184Z", event.getEventId());
        assertEquals("CodePipeline", event.getLogicalResourceId());
        assertEquals("444455556666", event.getNamespace());
        assertEquals("CREATE_COMPLETE", event.getResourceStatus());
        assertEquals("", event.getResourceStatusReason());
        assertEquals("AWS::CodePipeline::Pipeline", event.getResourceType());
        assertEquals("sb-multi-tenant-34f463df-app-feature-6NIE29OV500Y", event.getStackName());
        assertNull(event.getClientRequestToken());
        assertNotNull(event.getResourceProperties());
        assertEquals(6, event.getResourceProperties().size());
        assertTrue(event.getResourceProperties().containsKey("ArtifactStore"));
        assertTrue(event.getResourceProperties().containsKey("Stages"));
        assertTrue(event.getResourceProperties().containsKey("Name"));
        assertTrue(event.getResourceProperties().containsKey("RestartExecutionOnUpdate"));
        assertTrue(event.getResourceProperties().containsKey("RoleArn"));
        assertTrue(event.getResourceProperties().containsKey("Tags"));

        List<Object> stages = (List<Object>) event.getResourceProperties().get("Stages");
        Map<String, Object> deployStage = (Map<String, Object>) stages.get(1);
        List<Object> actions = (List<Object>) deployStage.get("Actions");
        for (Object a : actions) {
            Map<String, Object> action = (Map<String, Object>) a;
            Map<String, Object> configuration = (Map<String, Object>) action.get("Configuration");
            if (configuration.containsKey("UserParameters")) {
                String userParameters = (String) configuration.get("UserParameters");
                assertNotNull(Utils.fromJson(userParameters, LinkedHashMap.class));
            }
        }
    }
}
