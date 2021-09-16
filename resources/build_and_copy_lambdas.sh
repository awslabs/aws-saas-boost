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

# This script builds all the Lambda code packages for AWS SaaS Boost and copies them
# to S3 where CloudFormation expects to find them
if [ "X$1" = "X" ]; then
    echo "usage: $0 artifact_bucket_name"
    exit 1
fi

S3_BUCKET=$1
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

# Build the shared layers first since the other maven projects expect
# Have to build Utils before ApiGatewayHelper
# it to be available in the local .m2 cache
cd "${SAAS_BOOST_ROOT_DIR}/layers"
for layer in utils apigw-helper; do
    echo
    echo $layer
    cd $layer
    if [ -f pom.xml ]; then
        mvn
        if [ $? -ne 0 ]; then
            echo "Error building layer $layer"
            exit 1
        fi
        cd ..
    else
        cd ..
    fi
done

cd "${SAAS_BOOST_ROOT_DIR}/functions"
for fx in $(ls -d *); do 
    echo
    echo $fx
    cd $fx
    if [ -f pom.xml ]; then
            mvn
            if [ $? -ne 0 ]; then
            echo "Error building function $fx"
            exit 1
        fi 
        cd ..
    else
        cd ..
    fi
done

cd "${SAAS_BOOST_ROOT_DIR}/resources/custom-resources"
for res in $(ls -d *); do 
    echo
    echo $res
    cd $res
    if [ -f pom.xml ]; then
        mvn
        if [ $? -ne 0 ]; then
            echo "Error building custom resource $res"
            exit 1
        fi
        cd ..
    else
        cd ..
    fi
done

cd "${SAAS_BOOST_ROOT_DIR}/services"
for svc in onboarding-service tenant-service user-service settings-service metric-service quotas-service; do
    echo
    echo $svc
    cd $svc
    if [ -f pom.xml ]; then
        mvn
        if [ $? -ne 0 ]; then
            echo "Error building service $svc"
            exit 1
        fi 
		cd ..
    else
        cd ..
    fi
done

cd "${SAAS_BOOST_ROOT_DIR}"
mvn
find . -type f -name '*-lambda.zip' -exec aws s3 cp {} s3://$S3_BUCKET/lambdas/ \;

cd "${CURRENT_DIR}"