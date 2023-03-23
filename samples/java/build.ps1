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

$SAAS_BOOST_ENV = Read-Host -Prompt 'Please enter your AWS SaaS Boost Environment label'
$APP_NAME = Read-Host -Prompt 'Please enter the application service name to build'

$AWS_ACCOUNT_ID = (aws sts get-caller-identity | ConvertFrom-Json).Account
#Write-Output "Using AWS Account ID $AWS_ACCOUNT_ID"

$AWS_REGION = (aws configure list | Select-String -Pattern "region" | Out-String).Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)[2]
#Write-Output "Using AWS Region $AWS_REGION"

$SERVICE_JSON = (aws ssm get-parameter --name "/saas-boost/$SAAS_BOOST_ENV/app/$APP_NAME/SERVICE_JSON" | ConvertFrom-Json).Parameter.Value
#Write-Output $SERVICE_JSON

$ECR_REPO = ($SERVICE_JSON | ConvertFrom-Json).compute.containerRepo
$TAG = ($SERVICE_JSON | ConvertFrom-Json).compute.containerTag

If ("$AWS_REGION" -eq "cn-northwest-1" -or "$AWS_REGION" -eq "cn-north-1") {
    $DOCKER_REPO = "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com.cn/$ECR_REPO"
}
Else {
    $DOCKER_REPO = "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO"
}
$DOCKER_TAG = "{0}:{1}" -f $DOCKER_REPO, $TAG
#Write-Output $DOCKER_TAG

aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $DOCKER_REPO

mvn clean package
docker image build -t helloworld -f Dockerfile .
docker tag helloworld:latest $DOCKER_TAG
docker push $DOCKER_TAG
