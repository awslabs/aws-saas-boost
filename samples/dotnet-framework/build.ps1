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

Install-Module VSSetup

$VS_PATH = Get-VSSetupInstance | Select-Object -ExpandProperty InstallationPath
$MS_BUILD_PATH = "$VS_PATH\MSBuild\Current\Bin"
$Env:Path += ";$MS_BUILD_PATH"

# Set AWS CLI profile, region and SaaS Boost environment
#Set-AWSCredentials -ProfileName default
Set-DefaultAWSRegion -Region us-east-1
$SAAS_BOOST_ENV = "windows"

$AWS_ACCOUNT_ID = (Get-StsCallerIdentity).Account
$AWS_REGION = (Get-DefaultAWSRegion).Region

$ECR_REPO = (Get-SSMParameterValue -Name /saas-boost/$SAAS_BOOST_ENV/ECR_REPO).Parameters[0].Value
$DOCKER_REPO = "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO"
$DOCKER_TAG = "{0}:latest" -f $DOCKER_REPO

Write-Output "repo = $DOCKER_REPO"

MSBuild .\SaaSBoostHelloWorld\SaaSBoostHelloWorld.csproj /t:ContainerBuild /p:Configuration=Release
aws ecr --region $AWS_REGION get-login-password | docker login --username AWS --password-stdin $DOCKER_REPO

# The docker image is built as part of MSBuild
#docker image build -t saasboosthelloworld -f Dockerfile .
docker tag saasboosthelloworld $DOCKER_TAG
docker push $DOCKER_TAG
