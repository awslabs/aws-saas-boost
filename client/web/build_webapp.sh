#!/bin/bash
#
# Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License").
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#Set stack name

if [ "X$1" = "X" ]; then
  echo "usage: $0 <environment> <region>"
  exit 1
fi

if [ "X$2" = "X" ]; then
  echo "usage: $0 <environment> <region>"
  exit 1
fi
ENVIRONMENT=$1
AWS_REGION=$2

export REACT_APP_COGNITO_USERPOOL=`aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:userPoolId'].Value" --no-paginate --output text`
echo "REACT_APP_COGNITO_USERPOOL=$REACT_APP_COGNITO_USERPOOL"
if [ "X$REACT_APP_COGNITO_USERPOOL" = "X" ]; then
    echo "Unable to read saas-boost::${ENVIRONMENT}-${AWS_REGION}:userPoolId exported value"
    exit 1
fi

export REACT_APP_SIGNOUT_URI=`aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:webUrl'].Value" --no-paginate --output text`
echo "REACT_APP_SIGNOUT_URI=$REACT_APP_SIGNOUT_URI"

export REACT_APP_CALLBACK_URI=$REACT_APP_SIGNOUT_URI
export REACT_APP_CLIENT_ID=`aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:userPoolClientId'].Value" --no-paginate --output text`
echo "REACT_APP_CLIENT_ID=$REACT_APP_CLIENT_ID"   

export REACT_APP_COGNITO_USERPOOL_BASE_URI=`aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:cognitoBaseUri'].Value" --no-paginate --output text`       
echo "REACT_APP_COGNITO_USERPOOL_BASE_URI=$REACT_APP_COGNITO_USERPOOL_BASE_URI"

export REACT_APP_API_URI=`aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:publicApiUrl'].Value" --no-paginate --output text` 
echo "REACT_APP_API_URI=$REACT_APP_API_URI"

export REACT_APP_WEB_URL=`aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:webUrl'].Value" --no-paginate --output text`
echo "REACT_APP_API_URI=$REACT_APP_WEB_URL"

export REACT_APP_AWS_ACCOUNT=`aws sts get-caller-identity --no-paginate --output text --query "Account"`
echo "REACT_APP_AWS_ACCOUNT=$REACT_APP_AWS_ACCOUNT"

export REACT_APP_ENVIRONMENT=$ENVIRONMENT
echo "REACT_APP_ENVIRONMENT=$REACT_APP_ENVIRONMENT"

export REACT_APP_AWS_REGION=$AWS_REGION
echo "REACT_APP_AWS_REGION=$REACT_APP_AWS_REGION"

env | grep REACT

echo "Build web project"
ls -l
yarn
yarn build   