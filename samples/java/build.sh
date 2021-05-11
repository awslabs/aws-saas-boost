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

# Get the ECR repo URI from the SaaS Boost console on the Settings page, the AWS console,
# CloudFormation outputs, or Parameter Store
# docker tag saas-boost:latest ${AWS::AccountId}.dkr.ecr.${AWS::Region}.amazonaws.com/${ECR_REPO}:latest
# docker push ${AWS::AccountId}.dkr.ecr.${AWS::Region}.amazonaws.com/${ECR_REPO}:latest

read -p "Please enter your AWS SaaS Boost Environment label: " SAAS_BOOST_ENV
if [ -z "$SAAS_BOOST_ENV" ]; then
	echo "You must enter a AWS SaaS Boost Environment label to continue. Exiting."
	exit 1
fi

AWS_REGION=$(aws configure list | grep region | awk '{print $2}')
echo $AWS_REGION
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --output text --query ["Account"])
ECR_REPO=$(aws ssm get-parameter --name /saas-boost/$SAAS_BOOST_ENV/ECR_REPO --output text --query ["Parameter.Value"])
if [ -z "$ECR_REPO" ]; then
    echo "Can't get ECR repo from Parameter Store. Exiting."
    exit 1
fi
DOCKER_REPO="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO"
DOCKER_TAG="$DOCKER_REPO:latest"

AWS_CLI_VERSION=$(aws --version 2>&1 | awk -F / '{print $2}' | cut -c 1)
if [ $AWS_CLI_VERSION = '1' ]; then
	echo "Running AWS CLI version 1"
	aws ecr get-login --no-include-email --region $AWS_REGION | awk '{print $6}' | docker login -u AWS --password-stdin $DOCKER_REPO
elif [ $AWS_CLI_VERSION = '2' ]; then
	echo "Running AWS CLI version 2"
	aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $DOCKER_REPO
else
	echo "Running unknown AWS CLI version"
fi

echo $DOCKER_TAG
mvn clean package
docker image build -t helloworld -f Dockerfile .
docker tag helloworld:latest $DOCKER_TAG
docker push $DOCKER_TAG
