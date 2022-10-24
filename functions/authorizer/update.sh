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

LAMBDA_CODE=Authorizer-lambda.zip

#set this for V2 AWS CLI to disable paging
export AWS_PAGER=""

SAAS_BOOST_BUCKET=`aws ssm get-parameter --name "/saas-boost/${ENVIRONMENT}/SAAS_BOOST_BUCKET" --query "Parameter.Value" --output text`
echo "SaaS Boost Bucket = $SAAS_BOOST_BUCKET"
if [ "X$SAAS_BOOST_BUCKET" = "X" ]; then
    echo "/saas-boost/${ENVIRONMENT}/SAAS_BOOST_BUCKET SSM parameter not read from AWS env"
    exit 1
fi



mvn
if [ $? -ne 0 ]; then
    echo "Error building project"
    exit 1
fi

aws s3 cp target/$LAMBDA_CODE s3://$SAAS_BOOST_BUCKET/$LAMBDA_STAGE_FOLDER/

FUNCTIONS=("sb-${ENVIRONMENT}-authorizer" 
        )

for FUNCTION in ${FUNCTIONS[@]}; do
	#echo $FUNCTION
	aws lambda --region $MY_AWS_REGION update-function-code --function-name $FUNCTION --s3-bucket $SAAS_BOOST_BUCKET --s3-key $LAMBDA_STAGE_FOLDER/$LAMBDA_CODE
done
