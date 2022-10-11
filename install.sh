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

echo "Starting SaaS Boost installation..."

CURRENT_DIR=$(pwd)

# Check for utils dir
if [ ! -d "${CURRENT_DIR}/layers/utils" ]; then
        echo "Directory ${CURRENT_DIR}/layers/utils not found."
        exit 2
fi

# Check for installer dir
if [ ! -d "${CURRENT_DIR}/installer" ]; then
	echo "Directory ${CURRENT_DIR}/installer not found."
	exit 2
fi

# Check for client/web dir
if [ ! -d "${CURRENT_DIR}/client/web" ]; then
	echo "Directory ${CURRENT_DIR}/client/web not found."
	exit 2
fi

# check for Java
if ! command -v java >/dev/null 2>&1; then
	echo "Java version 11 or higher must be installed."
	exit 2
fi

# check for Maven
if ! command -v mvn >/dev/null 2>&1; then
	echo "Maven version 3 or higher must be installed."
	exit 2
fi

# check for AWS region
if [ -z $AWS_DEFAULT_REGION ]; then
	export AWS_REGION=$(aws configure list | grep region | awk '{print $2}')
        aws ec2 describe-regions | grep "$AWS_REGION" - > /dev/null 2>&1
        if [ $? -ne 0 ]; then
                echo "Invalid region set, please set a valid region using \`aws configure\`"
                exit 2
        fi
	if [ -z $AWS_REGION ]; then
		echo "AWS_REGION environment variable not set, check your AWS profile or set AWS_DEFAULT_REGION."
		exit 2
	fi
else
	export AWS_REGION=$AWS_DEFAULT_REGION
fi

cd ${CURRENT_DIR}
echo "Building maven requirements..."
mvn --quiet --non-recursive install -Dspotbugs.skip > /dev/null 2>&1
if [ $? -ne 0 ]; then
        echo "Error building parent pomfile for SaaS Boost."
        exit 2
fi

cd ${CURRENT_DIR}/layers
mvn --quiet --non-recursive install -Dspotbugs.skip > /dev/null 2>&1
if [ $? -ne 0 ]; then
        echo "Error building layers pomfile for SaaS Boost."
        exit 2
fi

cd ${CURRENT_DIR}/layers/utils
echo "Building utils..."
mvn --quiet -Dspotbugs.skip > /dev/null 2>&1
if [ $? -ne 0 ]; then
        echo "Error building utilities for SaaS Boost."
        exit 2
fi

cd ${CURRENT_DIR}/installer
echo "Building installer..."
mvn --quiet -Dspotbugs.skip > /dev/null 2>&1
if [ $? -ne 0 ]; then
	echo "Error building installer for SaaS Boost."
	exit 2
fi

cd ${CURRENT_DIR}
clear
echo "Launching installer for SaaS Boost..."

java -Djava.util.logging.config.file=logging.properties -jar ${CURRENT_DIR}/installer/target/SaaSBoostInstall-1.0.0-shaded.jar "$@"
