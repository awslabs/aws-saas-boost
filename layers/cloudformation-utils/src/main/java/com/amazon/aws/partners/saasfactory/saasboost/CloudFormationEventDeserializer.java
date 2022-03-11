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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;

public class CloudFormationEventDeserializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudFormationEventDeserializer.class);

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
                if (!"null".equals(value) && Utils.isNotBlank(value)) {
                    LinkedHashMap<String, Object> resourceProperties = Utils.fromJson(value, LinkedHashMap.class);
                    if (resourceProperties == null) {
                        LOGGER.error("Can't deserialize JSON {}", value);
                    }
                    builder.resourceProperties(resourceProperties);
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
