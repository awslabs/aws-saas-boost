package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.LinkedHashMap;

public class CloudFormationEventDeserializer {

    private CloudFormationEventDeserializer() {
    }

    public static CloudFormationEvent deserialize(String snsMessage) {
        // Raw SNS message values are escaped JSON strings with \n instead of newlines and
        // single quotes instead of doubles around values
        CloudFormationEvent.Builder builder = CloudFormationEvent.builder();
        for (String keyValue : snsMessage.split("\\n")) {
            // Each line will look like Key='Value' e.g. ResourceStatus='CREATE_COMPLETE'
            // We'll be reckless and use substring instead of a regex to break it apart.
            String key = keyValue.substring(0, keyValue.indexOf("="));
            String value = keyValue.substring(keyValue.indexOf("=") + 2, keyValue.length() - 1);
            //LOGGER.info(key + " => " + value);
            if ("StackId".equals(key)) {
                builder.stackId(nullIf(value));
            } else if ("Timestamp".equals(key)) {
                builder.timestamp(nullIf(value));
            } else if ("EventId".equals(key)) {
                builder.eventId(nullIf(value));
            } else if ("LogicalResourceId".equals(key)) {
                builder.logicalResourceId(nullIf(value));
            } else if ("Namespace".equals(key)) {
                builder.namespace(nullIf(value));
            } else if ("PhysicalResourceId".equals(key)) {
                builder.physicalResourceId(nullIf(value));
            } else if ("PrincipalId".equals(key)) {
                builder.principalId(value);
            } else if ("ResourceProperties".equals(key)) {
                if (!"null".equals(value)) {
                    String json = Utils.unescapeJson(value);
                    builder.resourceProperties(Utils.fromJson(json, LinkedHashMap.class));
                }
            } else if ("ResourceStatus".equals(key)) {
                builder.resourceStatus(nullIf(value));
            } else if ("ResourceStatusReason".equals(key)) {
                builder.resourceStatusReason(nullIf(value));
            } else if ("ResourceType".equals(key)) {
                builder.resourceType(nullIf(value));
            } else if ("StackName".equals(key)) {
                builder.stackName(nullIf(value));
            } else if ("ClientRequestToken".equals(key)) {
                builder.clientRequestToken(nullIf(value));
            }
        }
        return builder.build();
    }

    protected static String nullIf(String value) {
        return "null".equals(value) ? null : value;
    }
}
