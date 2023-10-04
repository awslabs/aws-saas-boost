/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

public class IamCredentials implements IdentityProviderCredentials {

    private static final Logger LOGGER = LoggerFactory.getLogger(IamCredentials.class);
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private StsClient sts;
    private String assumedRole;

    public IamCredentials(StsClient sts, String assumedRole) {
        this.sts = sts;
        this.assumedRole = assumedRole;
    }

    private IamCredentials() {
    }

    public String getAssumedRole() {
        return assumedRole;
    }

    @Override
    public final CredentialType type() {
        return CredentialType.IAM;
    }

    @Override
    public AwsCredentialsProvider resolveCredentials() {
        StaticCredentialsProvider credentialsProvider;
        try {
            AssumeRoleResponse response = sts.assumeRole(request -> request
                    .roleArn(assumedRole)
                    .durationSeconds(900)
                    .roleSessionName("sb-" + SAAS_BOOST_ENV + "-identity-service")
            );
            //AssumedRoleUser assumedUser = response.assumedRoleUser();
            //LOGGER.info("Assumed IAM User {}", assumedUser.arn());
            //LOGGER.info("Assumed IAM Role {}", assumedUser.assumedRoleId());

            // Could use STSAssumeRoleSessionCredentialsProvider here, but this
            // lambda will timeout before we need to refresh the temporary creds
            Credentials temporaryCredentials = response.credentials();
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(
                            temporaryCredentials.accessKeyId(),
                            temporaryCredentials.secretAccessKey(),
                            temporaryCredentials.sessionToken()
                    )
            );
        } catch (SdkServiceException stsError) {
            LOGGER.error("sts::AssumeRole error {}", stsError.getMessage());
            LOGGER.error(Utils.getFullStackTrace(stsError));
            throw stsError;
        }
        return credentialsProvider;
    }
}
