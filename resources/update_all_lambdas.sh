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

# This script builds all the Lambda code packages for SaaS Boost and copies them
# to S3 where CloudFormation expects to find them

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

#S3_BUCKET=$(aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:saasBoostBucket'].Value" --no-paginate --output text)
S3_BUCKET=$(aws ssm get-parameter --name "/saas-boost/${ENVIRONMENT}/SAAS_BOOST_BUCKET" --query "Parameter.Value" --output text)
echo "Target S3 Bucket = $S3_BUCKET"
if [ "X$S3_BUCKET" = "X" ]; then
#    echo "saas-boost::${ENVIRONMENT}-${AWS_REGION}:saasBoostBucket export not read for AWS env $ENVIRONMENT in Region $AWS_REGION"
    echo "/saas-boost/${ENVIRONMENT}/SAAS_BOOST_BUCKET Parameter not read from AWS env"    
    exit 2
fi

aws s3 ls s3://$S3_BUCKET
if [ $? -ne 0 ]; then
    echo "Error! S3 Bucket: $S3_BUCKET not readable"
    exit 1
fi

CURRENT_DIR=$(pwd)
SOURCE_DIR="$(pwd)/$(dirname "${BASH_SOURCE[0]}")"
cd "${SOURCE_DIR}"
cd ../
SAAS_BOOST_ROOT_DIR=$(pwd)

echo "SaaS Boost Root Dir = ${SAAS_BOOST_ROOT_DIR}"


# Build the shared layers first since the other maven projects expect
# Have to build Utils before ApiGatewayHelper
# it to be available in the local .m2 cache

#### There is issue for lambdas with both layers where update of one removes other.
# cd "${SAAS_BOOST_ROOT_DIR}/layers"
# for layer in utils apigw-helper; do
#     echo
#     echo $layer
#     cd $layer
#     if [ -f update_layer.sh ]; then
#         bash ./update_layer.sh $ENVIRONMENT $AWS_REGION
#         if [ $? -ne 0 ]; then
#             echo "Error deploying $layer"
#             #exit 1
#         fi
#         cd ..
#     else
#         cd ..
#     fi
# done

cd "${SAAS_BOOST_ROOT_DIR}/functions"
for fx in $(ls -d *); do 
    echo
    echo $fx
    cd $fx
    if [ -f update_service.sh ]; then
            bash ./update_service.sh $ENVIRONMENT $AWS_REGION
            if [ $? -ne 0 ]; then
            echo "Error deploying lambas for function $fx"
            #exit 1
        fi 
        cd ..
    else
        cd ..
    fi
done

# cd "${SAAS_BOOST_ROOT_DIR}/resources/custom-resources"
# for res in $(ls -d *); do 
#     echo
#     echo $res
#     cd $res
#     if [ -f update_service.sh ]; then
#             bash ./update_service.sh $ENVIRONMENT $AWS_REGION
#             if [ $? -ne 0 ]; then
#             echo "Error deploying lambas for custom resources $res"
#             exit 1
#         fi 
#         cd ..
#     else
#         cd ..
#     fi
# done

cd "${SAAS_BOOST_ROOT_DIR}/services"
for svc in onboarding-service tenant-service user-service settings-service metric-service quotas-service; do
    echo
    echo $svc
    cd $svc
    if [ -f update_service.sh ]; then
            bash ./update_service.sh $ENVIRONMENT $AWS_REGION
            if [ $? -ne 0 ]; then
            echo "Error deploying lambas for services $svc"
            #exit 1
        fi 
        cd ..
    else
        cd ..
    fi
done


cd "${SAAS_BOOST_ROOT_DIR}/metering-billing"
for svc in lambdas; do
    echo
    echo $svc
    cd $svc
    if [ -f update_service.sh ]; then
            bash ./update_service.sh $ENVIRONMENT $AWS_REGION
            if [ $? -ne 0 ]; then
            echo "Error deploying lambas for metering-billing services $svc"
            #exit 1
        fi 
        cd ..
    else
        cd ..
    fi
done


cd "${CURRENT_DIR}"