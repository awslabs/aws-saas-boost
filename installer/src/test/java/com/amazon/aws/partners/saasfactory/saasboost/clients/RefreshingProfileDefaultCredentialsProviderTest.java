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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.profiles.ProfileFileSystemSetting;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class RefreshingProfileDefaultCredentialsProviderTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshingProfileDefaultCredentialsProviderTest.class);

    private static final String BEFORE_ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String AFTER_ACCESS_KEY = "AKIAI44QH8DHBEXAMPLE";
    private static final String BEFORE_SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String AFTER_SECRET_KEY = "je7MtGbClwBF/2Zp9Utk/h3yCo8nvbEXAMPLEKEY";
    private static final String BEFORE_SESSION_TOKEN = "fakesessiontoken";
    private static final String AFTER_SESSION_TOKEN = "adifferentfakesessiontoken";
    private static final AwsCredentials BEFORE_PERMANENT =
            AwsBasicCredentials.create(BEFORE_ACCESS_KEY, BEFORE_SECRET_KEY);
    private static final AwsCredentials AFTER_PERMANENT =
            AwsBasicCredentials.create(AFTER_ACCESS_KEY, AFTER_SESSION_TOKEN);
    private static final AwsCredentials BEFORE_TEMPORARY =
            AwsSessionCredentials.create(BEFORE_ACCESS_KEY, BEFORE_SECRET_KEY, BEFORE_SESSION_TOKEN);
    private static final AwsCredentials AFTER_TEMPORARY =
            AwsSessionCredentials.create(AFTER_ACCESS_KEY, AFTER_SECRET_KEY, AFTER_SESSION_TOKEN);
    private static final AwsCredentials[] TEST_CREDENTIALS = new AwsCredentials[] {
            BEFORE_PERMANENT, AFTER_PERMANENT, BEFORE_TEMPORARY, AFTER_TEMPORARY
    };

    private static final String RESOURCES_LOCATION = "src/test/resources/";

    private static NoSystemPropertyCredentialsTestEnvironment cachedSystemProperties;

    private String fakeProfileFilename;

    public RefreshingProfileDefaultCredentialsProviderTest() {

    }

    @BeforeClass
    public static void cacheAndHideSystemProperties() {
        cachedSystemProperties = new NoSystemPropertyCredentialsTestEnvironment();
    }

    @AfterClass
    public static void resetSystemProperties() {
        cachedSystemProperties.close();
    }

    @After
    public void cleanUp() {
        if (!new File(fakeProfileFilename).delete()) {
            LOGGER.error("Failed to delete {} due to an underlying FileSystem error", fakeProfileFilename);
        }
    }

    /**
     * Tests whether the RefreshingDefaultCredentialsProviderTest is still necessary.
     *
     * For more information, see the class javadoc for {@link RefreshingProfileDefaultCredentialsProvider}.
     *
     * @throws IOException in case of any errors in File management
     */
    @Test
    public void defaultCredentialsProviderBugStillExists() throws IOException {
        // if we change the profile from under the DefaultCredentials provider, does it return the old credentials?
        fakeProfileFilename = getAbsoluteFakeProfileFilename("fake-credentials-default");
        ProfileUtils.updateOrCreateProfile(
                fakeProfileFilename,
                ProfileFileSystemSetting.AWS_PROFILE.defaultValue(),
                TEST_CREDENTIALS[0]);
        System.setProperty(
                ProfileFileSystemSetting.AWS_SHARED_CREDENTIALS_FILE.property(),
                fakeProfileFilename);
        System.setProperty(
                ProfileFileSystemSetting.AWS_PROFILE.property(),
                ProfileFileSystemSetting.AWS_PROFILE.defaultValue());
        DefaultCredentialsProvider defaultCredentialsProvider = DefaultCredentialsProvider.create();
        runUpdatingCredentialsProviderTest(defaultCredentialsProvider, false);
    }

    @Test
    public void refreshingCredentialsProviderFindsNewCredentials() throws IOException {
        fakeProfileFilename = getAbsoluteFakeProfileFilename("fake-credentials-refreshing");
        ProfileUtils.updateOrCreateProfile(
                fakeProfileFilename,
                ProfileFileSystemSetting.AWS_PROFILE.defaultValue(),
                TEST_CREDENTIALS[0]);
        RefreshingProfileDefaultCredentialsProvider refreshingCredentialsProvider = RefreshingProfileDefaultCredentialsProvider.builder()
                .profileFilename(fakeProfileFilename)
                .profileName(ProfileFileSystemSetting.AWS_PROFILE.defaultValue())
                .build();
        runUpdatingCredentialsProviderTest(refreshingCredentialsProvider, true);
    }

    private void runUpdatingCredentialsProviderTest(
    AwsCredentialsProvider credentialsProviderToTest,
            boolean expectUpdate) throws IOException {
        AwsCredentials expectedCredentials = TEST_CREDENTIALS[0];
        for (int i = 1 ; i < TEST_CREDENTIALS.length ; i++) {
            assertEquals(expectedCredentials, credentialsProviderToTest.resolveCredentials());
            ProfileUtils.updateOrCreateProfile(
                    fakeProfileFilename,
                    ProfileFileSystemSetting.AWS_PROFILE.defaultValue(),
                    TEST_CREDENTIALS[i]);
            if (expectUpdate) {
                expectedCredentials = TEST_CREDENTIALS[i];
            }
        }
    }

    private String getAbsoluteFakeProfileFilename(String simpleFilename) {
        return new File(RESOURCES_LOCATION + simpleFilename).getAbsolutePath();
    }

    /**
     * Hides the system property credentials when for the RefreshingDefaultCredentialsProviderTest to force
     * the profile credentials provider to be used by the DefaultCredentialsProvider.
     *
     * For reference, the {@link DefaultCredentialsProvider} reads credentials in the following manner:
     *  {@link SystemPropertyCredentialsProvider}
     *  {@link EnvironmentVariableCredentialsProvider}
     *  {@link WebIdentityTokenFileCredentialsProvider}
     *  {@link ProfileCredentialsProvider}
     *  {@link ContainerCredentialsProvider}
     *  {@link InstanceProfileCredentialsProvider}
     *
     * To force the underlying {@link DefaultCredentialsProvider} to use the {@link ProfileCredentialsProvider}, we need
     * to guarantee that the {@link SystemPropertyCredentialsProvider}, {@link EnvironmentVariableCredentialsProvider},
     * and {@link WebIdentityTokenFileCredentialsProvider} all will return no credentials. If any of these return
     * credentials then our test will fail, expecting configured Profile credentials but receiving some others.
     *
     * Luckily for the {@link SystemPropertyCredentialsProvider} and {@link WebIdentityTokenFileCredentialsProvider},
     * whether any credentials are loaded is controllable via System properties, so this
     * NoSystemPropertyCredentialsTestEnvironment class caches and unsets any System properties (using
     * {@link System#getProperty(String)}, {@link System#clearProperty(String)}, and
     * {@link System#setProperty(String, String)}).
     *
     * For the {@link EnvironmentVariableCredentialsProvider} however, because environment variables are not
     * controllable at runtime, we have no recourse. Adding environment variable credentials to this test will
     * currently cause this test to fail. If for some reason we need to add environment variable credentials to
     * tests in the installer, this test can be refactored (with the addition of PowerMock to our testing dependencies)
     * to statically mock responses to {@link System#getenv(String)} to return nothing for the credential environment
     * variable names.
     */
    private static class NoSystemPropertyCredentialsTestEnvironment implements Closeable {
        private static final String[] PROPERTIES_TO_CHECK = new String[]{
                SdkSystemSetting.AWS_ACCESS_KEY_ID.property(),
                SdkSystemSetting.AWS_SECRET_ACCESS_KEY.property(),
                SdkSystemSetting.AWS_SESSION_TOKEN.property(),
                SdkSystemSetting.AWS_ROLE_SESSION_NAME.property(),
                SdkSystemSetting.AWS_ROLE_ARN.property(),
                SdkSystemSetting.AWS_WEB_IDENTITY_TOKEN_FILE.property(),
                ProfileFileSystemSetting.AWS_SHARED_CREDENTIALS_FILE.property(),
                ProfileFileSystemSetting.AWS_PROFILE.property()
        };

        private final Map<String,String> hiddenProperties;

        public NoSystemPropertyCredentialsTestEnvironment() {
            hiddenProperties = new HashMap<>();
            for (String propertyKey : PROPERTIES_TO_CHECK) {
                String propertyValue = System.getProperty(propertyKey);
                if (propertyValue != null) {
                    hiddenProperties.put(propertyKey, propertyValue);
                    System.clearProperty(propertyKey);
                }
            }
        }

        @Override
        public void close() {
            for (Map.Entry<String, String> hiddenProperty : hiddenProperties.entrySet()) {
                System.setProperty(hiddenProperty.getKey(), hiddenProperty.getValue());
            }
        }
    }
}
