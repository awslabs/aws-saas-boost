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
LAMBDA_STAGE_FOLDER=$2
if [ "X$LAMBDA_STAGE_FOLDER" = "X" ]; then
	LAMBDA_STAGE_FOLDER="lambdas"
fi
LAMBDA_CODE=Utils-lambda.zip

#set this for V2 AWS CLI to disable paging
export AWS_PAGER=""

SAAS_BOOST_BUCKET=$(aws ssm get-parameter --name "/saas-boost/${ENVIRONMENT}/SAAS_BOOST_BUCKET" --query "Parameter.Value" --output text)
echo "SaaS Boost Bucket = $SAAS_BOOST_BUCKET"
if [ "X$SAAS_BOOST_BUCKET" = "X" ]; then
    echo "SaaS Boost Bucket export not read from AWS env"
    exit 1
fi

# Do a fresh build of the project
mvn
if [ $? -ne 0 ]; then
    echo "Error building project"
    exit 1
fi
# And copy it up to S3
aws s3 cp target/$LAMBDA_CODE s3://$SAAS_BOOST_BUCKET/$LAMBDA_STAGE_FOLDER/

PUBLISHED_LAYER=$(aws lambda publish-layer-version --layer-name "sb-${ENVIRONMENT}-utils" --compatible-runtimes java11 --content S3Bucket="${SAAS_BOOST_BUCKET}",S3Key="${LAMBDA_STAGE_FOLDER}/${LAMBDA_CODE}")
LAYER_VERSION=$(echo $PUBLISHED_LAYER | jq -r '.LayerVersionArn')
echo "Published new layer $LAYER_VERSION"

exit

TENANT_SVC=("sb-${ENVIRONMENT}-tenants-delete" 
	"sb-${ENVIRONMENT}-tenants-disable"
	"sb-${ENVIRONMENT}-tenants-enable"
	"sb-${ENVIRONMENT}-tenants-get-all"
	"sb-${ENVIRONMENT}-tenants-get-by-id"
	"sb-${ENVIRONMENT}-tenants-get-provisioned"
	"sb-${ENVIRONMENT}-tenants-insert"
	"sb-${ENVIRONMENT}-tenants-update-onboarding"
	"sb-${ENVIRONMENT}-tenants-update" 
)
USER_SVC=("sb-${ENVIRONMENT}-users-insert"
	"sb-${ENVIRONMENT}-users-disable"
	"sb-${ENVIRONMENT}-users-enable"
	"sb-${ENVIRONMENT}-users-delete"
	"sb-${ENVIRONMENT}-users-get-by-id"
	"sb-${ENVIRONMENT}-users-update"
	"sb-${ENVIRONMENT}-users-get-all"
	"sb-${ENVIRONMENT}-users-token"
)
ONBOARDING_SVC=("sb-${ENVIRONMENT}-onboarding-get-all"
	"sb-${ENVIRONMENT}-onboarding-start"
	"sb-${ENVIRONMENT}-onboarding-get-by-id"
	"sb-${ENVIRONMENT}-onboarding-update-status"
	"sb-${ENVIRONMENT}-onboarding-provision"
	"sb-${ENVIRONMENT}-onboarding-listener"
)
SETTINGS_SVC=("sb-${ENVIRONMENT}-settings-get-by-id"
	"sb-${ENVIRONMENT}-settings-update"
	"sb-${ENVIRONMENT}-settings-get-all"
)

METRICS_SVC=("sb-${ENVIRONMENT}-metrics-query"
	"sb-${ENVIRONMENT}-metrics-presigned-urls"
)

FUNCTIONS=("${TENANT_SVC[@]}" "${USER_SVC[@]}" "${ONBOARDING_SVC[@]}" "${SETTINGS_SVC[@]}" "${METRICS_SVC[@]}")
for FX in ${FUNCTIONS[@]}; do
	aws lambda --region $MY_AWS_REGION update-function-configuration --function-name $FX --layers $LAYER_VERSION
done