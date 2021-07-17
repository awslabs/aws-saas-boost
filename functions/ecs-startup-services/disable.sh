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

RULE="sb-${SAAS_BOOST_ENV}-ecs-startup-services"
EXISTING_RULE=$(aws events describe-rule --name "${RULE}")
if [ $? != 0 ]; then
    echo "Can't find the EventBridge rule ${RULE}"
    exit 1
else
    aws events disable-rule --name "${RULE}"
fi
