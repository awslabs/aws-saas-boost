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

package com.amazon.aws.partners.saasfactory.saasboost.model;

import com.amazon.aws.partners.saasfactory.saasboost.SaaSBoostArtifactsBucket;

import java.util.Map;

/**
 * The <code>Environment</code> class represents all gathered information about a SaaS Boost environment.
 * 
 * Objects for existing environments should be created using {@link ExistingEnvironmentFactory#create}.
 * 
 * New Environments can be created using {@link Environment.Builder#build}. Instances of {@link Environment.Builder}
 * can be instantiated via the {@link Environment#builder()} static function.
 */
public final class Environment {
    private String name;
    private String accountId;
    private SaaSBoostArtifactsBucket artifactsBucket;
    private String lambdasFolderName;
    private String baseCloudFormationStackName;
    private Map<String, String> baseCloudFormationStackInfo;
    private boolean metricsAnalyticsDeployed;

    private Environment(Builder b) {
        this.name = b.name;
        this.accountId = b.accountId;
        this.artifactsBucket = b.artifactsBucket;
        this.lambdasFolderName = b.lambdasFolderName;
        this.baseCloudFormationStackName = b.baseCloudFormationStackName;
        this.baseCloudFormationStackInfo = b.baseCloudFormationStackInfo;
        this.metricsAnalyticsDeployed = b.metricsAnalyticsDeployed;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccountId() {
        return this.accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public SaaSBoostArtifactsBucket getArtifactsBucket() {
        return this.artifactsBucket;
    }

    public void setArtifactsBucket(SaaSBoostArtifactsBucket artifactsBucket) {
        this.artifactsBucket = artifactsBucket;
    }

    public String getLambdasFolderName() {
        return this.lambdasFolderName;
    }

    public void setLambdasFolderName(String lambdasFolderName) {
        this.lambdasFolderName = lambdasFolderName;
    }

    public String getBaseCloudFormationStackName() {
        return this.baseCloudFormationStackName;
    }

    public void setBaseCloudFormationStackName(String baseCloudFormationStackName) {
        this.baseCloudFormationStackName = baseCloudFormationStackName;
    }

    public Map<String, String> getBaseCloudFormationStackInfo() {
        return this.baseCloudFormationStackInfo;
    }

    public void setBaseCloudFormationStackInfo(Map<String, String> baseCloudFormationStackInfo) {
        this.baseCloudFormationStackInfo = baseCloudFormationStackInfo;
    }

    public boolean isMetricsAnalyticsDeployed() {
        return this.metricsAnalyticsDeployed;
    }

    public void setMetricsAnalyticsDeployed(boolean metricsAnalyticsDeployed) {
        this.metricsAnalyticsDeployed = metricsAnalyticsDeployed;
    }

    /**
     * Retrieves a new empty instance of the {@link Environment.Builder}.
     * 
     * @return an empty {@link Environment.Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructs a {@link Environment.Builder} using the provided {@link Environment} as a baseline.
     * 
     * It will always be true that calling <code>build</code> on the {@link Environment.Builder} returned
     * by this function will create an {@link Environment} equal to the provided {@link Environment}. In other words:
     * <code>
     * myEnvironment.equals(Environment.builder(myEnvironment).build()) // <== always true
     * </code>
     * 
     * @param baseEnvironment the {@link Environment} to base this {@link Environment.Builder} on.
     * @return the prebuilt {@link Environment.Builder}
     */
    public static Builder builder(Environment baseEnvironment) {
        return new Builder();
    }

    /**
     * A convenient `Builder` class for {@link Environment}.
     */
    public static final class Builder {

        private String name;
        private String accountId;
        private SaaSBoostArtifactsBucket artifactsBucket;
        private String lambdasFolderName;
        private String baseCloudFormationStackName;
        private Map<String, String> baseCloudFormationStackInfo;
        private boolean metricsAnalyticsDeployed;

        private Builder() {

        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder artifactsBucket(SaaSBoostArtifactsBucket artifactsBucket) {
            this.artifactsBucket = artifactsBucket;
            return this;
        }

        public Builder lambdasFolderName(String lambdasFolderName) {
            this.lambdasFolderName = lambdasFolderName;
            return this;
        }

        public Builder baseCloudFormationStackName(String baseCloudFormationStackName) {
            this.baseCloudFormationStackName = baseCloudFormationStackName;
            return this;
        }

        public Builder baseCloudFormationStackInfo(Map<String, String> baseCloudFormationStackInfo) {
            this.baseCloudFormationStackInfo = baseCloudFormationStackInfo;
            return this;
        }

        public Builder metricsAnalyticsDeployed(boolean metricsAnalyticsDeployed) {
            this.metricsAnalyticsDeployed = metricsAnalyticsDeployed;
            return this;
        }

        public Environment build() {
            return new Environment(this);
        }
    }
}
