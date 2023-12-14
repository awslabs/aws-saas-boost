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

if [ -z $1 ]; then
    echo "Usage: $0 <Environment> [Lambda Folder]"
    exit 2
fi
ENVIRONMENT=$1
FUNCTIONS=($2)
LAMBDA_CODE=ApiDocs-lambda.zip

# Disable AWS CLI paging
export AWS_PAGER=""

MY_AWS_REGION=$(aws configure list | grep region | awk '{print $2}')
echo "AWS Region = $MY_AWS_REGION"

SAAS_BOOST_BUCKET=$(aws --region $MY_AWS_REGION ssm get-parameter --name "/saas-boost/${ENVIRONMENT}/SAAS_BOOST_BUCKET" --query 'Parameter.Value' --output text)
echo "SaaS Boost Bucket = $SAAS_BOOST_BUCKET"
if [ -z $SAAS_BOOST_BUCKET ]; then
    echo "Can't find SAAS_BOOST_BUCKET in Parameter Store"
    exit 1
fi
LAMBDAS_FOLDER=$(aws --region $MY_AWS_REGION ssm get-parameter --name "/saas-boost/${ENVIRONMENT}/LAMBDAS_FOLDER" --query 'Parameter.Value' --output text 2>/dev/null)
if [ -z $LAMBDAS_FOLDER ]; then
    LAMBDAS_FOLDER="lambdas/"
fi
echo "Lambdas folder = $LAMBDAS_FOLDER"
echo "Function code = $LAMBDA_CODE"

# Do a fresh build of the project
rm -rf build
mkdir build
zip -r build/$LAMBDA_CODE app.js package.json package-lock.json swagger.json node_modules

# And copy it up to S3
aws s3 cp build/$LAMBDA_CODE s3://$SAAS_BOOST_BUCKET/$LAMBDAS_FOLDER

# Make sure the function exists before trying to update it
FUNCTION="sb-${ENVIRONMENT}-api-docs"
aws lambda --region "$MY_AWS_REGION" get-function --function-name "$FUNCTION" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    printf "Updating function code for ${FUNCTION}\n"
    aws lambda --region "$MY_AWS_REGION" update-function-code --function-name "$FUNCTION" --s3-bucket "$SAAS_BOOST_BUCKET" --s3-key "${LAMBDAS_FOLDER}${LAMBDA_CODE}"
fi
