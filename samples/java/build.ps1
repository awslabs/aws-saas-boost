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

# Use your saas boost envirionment name
$SAAS_BOOST_ENV="test"
# Use your own profile name and region
Set-AWSCredential -ProfileName dev
Set-DefaultAWSRegion -Region us-west-2
$AWS_ACCOUNT_ID = (Get-StsCallerIdentity).Account
$AWS_REGION=(Get-DefaultAWSRegion).Region
echo $AWS_REGION
$ECR_REPO = (Get-SSMParameterValue -Name /saas-boost/$SAAS_BOOST_ENV/ECR_REPO).Parameters[0].Value
echo $ECR_REPO
$DOCKER_REPO="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO"
echo "repo = $DOCKER_REPO"
$DOCKER_TAG = "{0}:{1}" -f $DOCKER_REPO, "latest"
echo "tag = $DOCKER_TAG"
mvn clean package
aws ecr get-login-password | docker login --username AWS --password-stdin $DOCKER_REPO
echo $DOCKER_TAG
docker image build -t helloworld -f Dockerfile .
docker tag helloworld $DOCKER_TAG
docker push $DOCKER_TAG