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
echo "DEBUG=true" >> .env

codeBuildBuildId=$(aws codebuild list-builds-for-project --project-name sb-$myEnv-admin-web | jq -r '.ids[0]')

aws codebuild batch-get-builds --ids "$codeBuildBuildId" | jq -c '.builds[0].environment.environmentVariables[]' | while read var ; do 
    name=$(echo $var | jq -r '.name')
    value=$(echo $var | jq -r '.value')
    echo "${name}=${value}" >> .env
done

cat .env
