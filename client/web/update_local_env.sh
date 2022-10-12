#!/usr/bin/env bash

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

# set -o xtrace
myEnv="$1"
if [ "z$myEnv" == "z" ]; then
    read -p "What environment? " myEnv
fi

rm .env

myRegion=$(aws configure list | grep region | awk '{print $2}')
echo "AWS_DEFAULT_REGION=$myRegion" >> .env
echo "REACT_APP_AWS_REGION=$myRegion" >> .env
echo "REACT_APP_ENVIRONMENT=$myEnv" >> .env
echo "REACT_APP_AWS_ACCOUNT=786938756705" >> .env
echo "DEBUG=true" >> .env

userPoolId=$(aws cognito-idp list-user-pools --max-results 20 | jq -c '.UserPools[] | select (.Name == "'sb-${myEnv}-users'").Id' | cut -d\" -f2)
echo "REACT_APP_COGNITO_USERPOOL=$userPoolId" >> .env

poolClientId=$(aws cognito-idp list-user-pool-clients --user-pool-id ${userPoolId} | jq '.UserPoolClients[0].ClientId' | cut -d\" -f2)
echo "REACT_APP_CLIENT_ID=$poolClientId" >> .env

publicApiName="sb-${myEnv}-public-api"
publicApiGId=$(aws apigateway get-rest-apis | jq --arg publicApiName "$publicApiName" '.items[] | select (.name == $publicApiName).id' | cut -d\" -f2)
echo "REACT_APP_API_URI=https://${publicApiGId}.execute-api.${myRegion}.amazonaws.com/v1" >> .env

cat .env
