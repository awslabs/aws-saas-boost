/**
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

package com.amazon.aws.partners.saasfactory.saasboost.clients;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;

import java.io.File;
import java.nio.file.Path;

/**
 * This class provides the exact same functionality as the {@link DefaultCredentialsProvider} but without any
 * caching to support profile files that refresh from outside the JVM. To be explicit, this
 * {@link RefreshingProfileDefaultCredentialsProvider} creates a new {@link DefaultCredentialsProvider} from scratch
 * each time {@link RefreshingProfileDefaultCredentialsProvider#resolveCredentials()} is called.
 *
 * In some cases (such as in Cloud9, see <a href=https://github.com/awslabs/aws-saas-boost/issues/137>#137</a>) the
 * credentials being returned by the credentials provider will expire and will not be able to be refreshed. For example,
 * if the credentials being used are coming from the default <pre>.aws/credentials</pre> file and are updated during the
 * lifetime of this process, the {@link software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider} will return
 * expired credentials, leading to SaaS Boost erroring out.
 *
 * This class addresses this case by creating a new {@link DefaultCredentialsProvider} (note, explicitly using the
 * builder, because the .create() function returns a static singleton) each time resolve credentials is installed.
 * This ensures that any new credentials added to the configured profile will be picked up for any resolve credentials
 * call.
 *
 * This obviously comes with a performance hit: we're creating a new object each time resolveCredentials is called
 * rather than relying on in-memory values. In experimentation this equates to a roughly 100x difference in
 * performance: the refreshingCredentialsProvider will average ~0.1-0.2 ms per resolveCredentials call vs the
 * Default in-memory's ~0.0001ms runtime (YMMV based on CPU clock speed). We have considered this performance change
 * to be acceptable: resolveCredentials should only be called once per SDK call, and so this is equivalent to a
 * linear difference in runtime performance of the installer, likely adding significantly less than one second to
 * an already very long-running process (on the order of minutes to upload Lambda function artifacts to S3 and wait
 * for CloudFormation templates to finish).
 *
 * @see <a href=https://github.com/awslabs/aws-saas-boost/issues/137>SaaS Boost Issue #137</a>
 * @see <a href=https://github.com/aws/aws-sdk-java-v2/issues/1754>AWS Java SDK v2 Issue #1754</a>
 */
public class RefreshingProfileDefaultCredentialsProvider implements AwsCredentialsProvider {
    private final String profileFilename;
    private final DefaultCredentialsProvider.Builder curriedBuilder;

    private RefreshingProfileDefaultCredentialsProvider(RefreshingProfileDefaultCredentialsProvider.Builder builder) {
        curriedBuilder = DefaultCredentialsProvider.builder();
        curriedBuilder.reuseLastProviderEnabled(builder.reuseLastProviderEnabled);
        curriedBuilder.asyncCredentialUpdateEnabled(builder.asyncCredentialUpdateEnabled);
        curriedBuilder.profileName(builder.profileName);
        profileFilename = builder.profileFilename;
    }

    /**
     * @see AwsCredentialsProvider#resolveCredentials()
     */
    @Override
    public AwsCredentials resolveCredentials() {
        if (profileFilename == null) {
            return curriedBuilder.build().resolveCredentials();
        }
        curriedBuilder.profileFile(ProfileFile.builder()
                .type(ProfileFile.Type.CREDENTIALS)
                .content(Path.of(new File(profileFilename).toURI()))
                .build());
        return curriedBuilder.build().resolveCredentials();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @see DefaultCredentialsProvider.Builder
     */
    public static class Builder {
        private String profileFilename;
        private String profileName;
        private boolean reuseLastProviderEnabled;
        private boolean asyncCredentialUpdateEnabled;

        private Builder() {

        }

        public RefreshingProfileDefaultCredentialsProvider.Builder profileFilename(String profileFilename) {
            this.profileFilename = profileFilename;
            return this;
        }

        public RefreshingProfileDefaultCredentialsProvider.Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        public RefreshingProfileDefaultCredentialsProvider.Builder reuseLastProviderEnabled(
                Boolean reuseLastProviderEnabled) {
            this.reuseLastProviderEnabled = reuseLastProviderEnabled;
            return this;
        }

        public RefreshingProfileDefaultCredentialsProvider.Builder asyncCredentialUpdateEnabled(
                Boolean asyncCredentialUpdateEnabled) {
            this.asyncCredentialUpdateEnabled = asyncCredentialUpdateEnabled;
            return this;
        }

        public RefreshingProfileDefaultCredentialsProvider build() {
            return new RefreshingProfileDefaultCredentialsProvider(this);
        }
    }
}
