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

WEB_BUCKET=$(aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:webBucket'].Value" --no-paginate --output text)
echo "Target S3 Web Bucket = $WEB_BUCKET"
if [ "X$WEB_BUCKET" = "X" ]; then
    echo "saas-boost::${ENVIRONMENT}-${AWS_REGION}:webBucket export not read for AWS env $ENVIRONMENT in Region $AWS_REGION"
    exit 2
fi

aws s3 sync --delete --cache-control no-store build s3://$WEB_BUCKET

WEB_URL=$(aws cloudformation list-exports --query "Exports[?Name=='saas-boost::${ENVIRONMENT}-${AWS_REGION}:webUrl'].Value" --no-paginate --output text)
echo "Web App is at: ${WEB_URL}"
