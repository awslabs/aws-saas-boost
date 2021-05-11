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

MY_AWS_REGION=$(aws configure list | grep region | awk '{print $2}')
echo "AWS Region = $MY_AWS_REGION"

if [ "X$1" = "X" ]; then
    echo "usage: $0 <Environment>"
    exit 2
fi

ENVIRONMENT=$1
LAMBDA_CODE=ApiGatewayHelper-lambda.zip
LAMBDA_STAGE_FOLDER=lambdas

#set this for V2 AWS CLI to disable paging
export AWS_PAGER=""

SAAS_BOOST_BUCKET=$(aws ssm get-parameter --name "/saas-boost/${ENVIRONMENT}/SAAS_BOOST_BUCKET" --query "Parameter.Value" --output text)
echo "SaaS Boost Bucket = $SAAS_BOOST_BUCKET"
if [ "X$SAAS_BOOST_BUCKET" = "X" ]; then
    echo "SaaS Boost Bucket export not read from AWS env"
    exit 1
fi

# Do a fresh build of the code
mvn
if [ $? -ne 0 ]; then
    echo "Error building project"
    exit 1
fi
# And copy it up to S3
aws s3 cp target/$LAMBDA_CODE s3://$SAAS_BOOST_BUCKET/$LAMBDA_STAGE_FOLDER/

PUBLISHED_LAYER=$(aws lambda publish-layer-version --layer-name "sb-${ENVIRONMENT}-apigw-helper-${MY_AWS_REGION}" --compatible-runtimes java11 --content S3Bucket="${SAAS_BOOST_BUCKET}",S3Key="${LAMBDA_STAGE_FOLDER}/${LAMBDA_CODE}")
LAYER_VERSION=$(echo $PUBLISHED_LAYER | jq -r '.LayerVersionArn')
echo "Published new layer $LAYER_VERSION"

METRICS_SVC=("sb-${ENVIRONMENT}-metrics-query-${MY_AWS_REGION}"
)
ONBOARDING_SVC=(
	"sb-${ENVIRONMENT}-onboarding-provision-${MY_AWS_REGION}"
    # "sb-${ENVIRONMENT}-onboarding-start-${MY_AWS_REGION}"
    # "sb-${ENVIRONMENT}-onboarding-update-status-${MY_AWS_REGION}"
)
SYSTEM_API_CLIENT=("sb-${ENVIRONMENT}-private-api-client-${MY_AWS_REGION}"
)


FUNCTIONS=("${METRICS_SVC[@]}" "${ONBOARDING_SVC[@]}" "${SYSTEM_API_CLIENT[@]}")
for FX in ${FUNCTIONS[@]}; do
	aws lambda --region $MY_AWS_REGION update-function-configuration --function-name $FX --layers $LAYER_VERSION
done