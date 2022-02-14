package com.amazon.aws.partners.saasfactory.saasboost;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import org.junit.Test;

import java.util.Collections;

public class OnboardingStackListenerTest {

    @Test
    public void testHandleRequest() {
        SNSEvent event = new SNSEvent();
        SNSEvent.SNSRecord record = new SNSEvent.SNSRecord();
        SNSEvent.SNS sns = new SNSEvent.SNS();
        CloudFormationEvent cloudFormationEvent = CloudFormationEvent.builder()
                .stackId("arn:aws:cloudformation:us-west-2:111111111111:stack/sb-multi-tenant-6cad89f5/3e92db10-8ba1-11ec-97ef-06246f7d706f")
                .logicalResourceId("sb-multi-tenant-6cad89f5")
                .physicalResourceId("arn:aws:cloudformation:us-west-2:111111111111:stack/sb-multi-tenant-6cad89f5/3e92db10-8ba1-11ec-97ef-06246f7d706f")
                .resourceStatus("CREATE_COMPLETE")
                .stackName("sb-multi-tenant-6cad89f5")
                .resourceType("AWS::CloudFormation::Stack")
                .build();
        StringBuilder message = new StringBuilder();
        message.append("StackId='");
        message.append(cloudFormationEvent.getStackId());
        message.append("'\n");
        message.append("LogicalResourceId='");
        message.append(cloudFormationEvent.getLogicalResourceId());
        message.append("'\n");
        message.append("PhysicalResourceId='");
        message.append(cloudFormationEvent.getPhysicalResourceId());
        message.append("'\n");
        message.append("ResourceStatus='");
        message.append(cloudFormationEvent.getResourceStatus());
        message.append("'\n");
        message.append("ResourceType='");
        message.append(cloudFormationEvent.getResourceType());
        message.append("'\n");
        message.append("StackName='");
        message.append(cloudFormationEvent.getStackName());
        message.append("'");

        //System.out.println(Utils.toJson(cloudFormationEvent));
        //System.out.println(message.toString());
        sns.setMessage(message.toString());
        record.setSns(sns);
        event.setRecords(Collections.singletonList(record));
        //System.out.println(Utils.toJson(event));
    }
}
