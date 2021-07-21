#!/bin/bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License").
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# What SaaS Boost installation are we working on?
SAAS_BOOST_ENV=$1
if [ -z "$SAAS_BOOST_ENV" ]; then
    read -p "Enter your AWS SaaS Boost Environment label: " SAAS_BOOST_ENV
    if [ -z "$SAAS_BOOST_ENV" ]; then
        echo "You must enter a AWS SaaS Boost Environment label to continue. Exiting."
        exit 1
    fi
fi
# Can we confirm that this AWS CLI is connected to that SaaS Boost environment?
MY_AWS_REGION=$(aws configure list | grep region | awk '{print $2}')
SB_ENV=$(aws --region ${MY_AWS_REGION} ssm get-parameter --name /saas-boost/${SAAS_BOOST_ENV}/SAAS_BOOST_ENVIRONMENT --query "Parameter.Value" --output text)
if [ "${SB_ENV}" != "${SAAS_BOOST_ENV}" ]; then
    echo "Can't find SaaS Boost environment $SAAS_BOOST_ENV in region $MY_AWS_REGION. Double-check the current AWS CLI profile and region."
    exit 1
fi

# Has the EcsShutdownServices Lambda function been setup?
LAMBDA_FX="sb-${SAAS_BOOST_ENV}-ecs-shutdown-services"
LAMBDA_ARN=$(aws lambda get-function --function-name "${LAMBDA_FX}" --query "Configuration.FunctionArn" --output text)
if [ $? != 0 ]; then
    echo "Can't find the EcsShutdownServices Lambda function in this SaaS Boost environment."
    exit 1
fi

RULE="sb-${SAAS_BOOST_ENV}-ecs-shutdown-services"
EXISTING_SCHEDULE=$(aws events describe-rule --name "${RULE}" --query "ScheduleExpression" --output text)
if [ $? == 0 ] && [ ! -z "${EXISTING_SCHEDULE}" ]; then
    read -p "Reuse the existing schedule expression ${EXISTING_SCHEDULE}? [Y/N] " REUSE_SCHEDULE
fi
if ! [[ $REUSE_SCHEDULE =~ ^[Yy] ]]; then
    # Get a cron schedule to invoke our Lambda on
    echo "Enter an EventBridge cron schedule expression (https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-create-rule-schedule.html#eb-cron-expressions)."
    read -p "[Press enter for default schedule of 12 midnight daily, cron(0 0 * * ? *)]: " SCHEDULE
    if [ -z "$SCHEDULE" ]; then
        SCHEDULE="cron(0 0 * * ? *)"
    fi
else
    SCHEDULE="${EXISTING_SCHEDULE}"
fi

# Simple pattern check -- doesn't test for EventBridge restriction on
# day-of-month and day-of-week not both being asterisks
CRON_REGEX="^cron\(.+ .+ .+ .+ .+ .+\)$"
if ! [[ $SCHEDULE =~ $CRON_REGEX ]]; then
    echo "Schedule cron expression ${SCHEDULE} is not valid."
    exit 1
fi
#echo "Schedule = $SCHEDULE"

# Now we can make or update an EventBridge rule for this schedule
RULE_ARN=$(aws events put-rule --name "${RULE}" --schedule-expression "${SCHEDULE}" --state ENABLED --description "Shuts down all tenant tasks in ECS" --query "RuleArn" --output text)
if [ $? != 0 ]; then
    echo "Error putting scheduled event rule"
    exit 1
fi
#echo $RULE_ARN
echo "Set EventBridge scheduled event rule to ${SCHEDULE}"

# Adding a Lambda permission with the same statement id is an error.
# Unfortunately, the get-policy call returns the policy JSON as an
# escaped string rather than a proper JSON structure, so we can't use
# --query like we normally would. We could always call remove-permission
# before add-permission... but, this seems more correct.
STATEMENT_ID="sb-${SAAS_BOOST_ENV}-ecs-shutdown-services-permission"
GREP_PATTERN="\"Sid\":\"${STATEMENT_ID}\""
EXISTING_PERMISSION=$(aws lambda get-policy --function-name ${LAMBDA_FX} --query "Policy" --output text | grep $GREP_PATTERN)
if [ $? == 0 ]; then
    echo "Lambda permission for EventBridge rule already exists"
else
    LAMBDA_PERMISSION=$(aws lambda add-permission --function-name ${LAMBDA_FX} --action 'lambda:InvokeFunction' --principal events.amazonaws.com --source-arn ${RULE_ARN} --statement-id ${STATEMENT_ID})
    if [ $? != 0 ]; then
        echo "Error adding Lambda permission for EventBridge rule"
        exit 1
    fi
    #echo $LAMBDA_PERMISSION
    echo "Added Lambda function permission for EventBridge rule"
fi

# Finally, wire together the EventBridge scheduled rule with the Lambda function
EVENT_TARGET=$(aws events put-targets --rule "${RULE}" --targets "Id"="EcsShutdownServicesLambda","Arn"="${LAMBDA_ARN}")
if [ $? != 0 ]; then
    echo "Error putting EventBridge target for rule ${RULE} to function ${LAMBDA_FX}"
    exit 1
fi
#echo $EVENT_TARGET
echo "Set EventBridge target for rule ${RULE} to function ${LAMBDA_FX}"

