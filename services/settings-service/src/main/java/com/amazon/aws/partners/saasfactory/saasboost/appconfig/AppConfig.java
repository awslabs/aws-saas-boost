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
package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.*;

@JsonDeserialize(builder = AppConfig.Builder.class)
public class AppConfig {

    private String name;
    private String domainName;
    private String sslCertArn;
    // ServiceConfig.getName() : ServiceConfig
    private Map<String, ServiceConfig> services;
    private BillingProvider billing;

    private AppConfig(Builder builder) {
        this.name = builder.name;
        this.domainName = builder.domainName;
        this.sslCertArn = builder.sslCertArn;
        this.services = builder.services;
        this.billing = builder.billing;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getSslCertArn() {
        return sslCertArn;
    }

    public BillingProvider getBilling() {
        return billing;
    }

    public Map<String, ServiceConfig> getServices() {
        return services;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        // Same reference?
        if (this == obj) {
            return true;
        }
        // Same type?
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AppConfig other = (AppConfig) obj;
        return (
                ((name == null && other.name == null) || (name != null && name.equals(other.name)))
                && ((domainName == null && other.domainName == null) || (domainName != null && domainName.equals(other.domainName)))
                && ((sslCertArn == null && other.sslCertArn == null) || (sslCertArn != null && sslCertArn.equals(other.sslCertArn)))
                && ((services == null && other.services == null) || (services != null && services.equals(other.services)))
                && ((billing == null && other.billing == null) || (billing != null && billing.equals(other.billing)))
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, domainName, sslCertArn, services, billing);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    public static final class Builder {
        private String name;
        private String domainName;
        private String sslCertArn;
        private Map<String, ServiceConfig> services;
        private BillingProvider billing;

        private Builder() {
            services = new HashMap<>();
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder domainName(String domainName) {
            this.domainName = domainName;
            return this;
        }

        public Builder sslCertArn(String sslCertArn) {
            this.sslCertArn = sslCertArn;
            return this;
        }

        public Builder services(Map<String, ServiceConfig> services) {
            this.services = services;
            return this;
        }

        public Builder addServiceConfig(ServiceConfig serviceConfig) {
            this.services.put(serviceConfig.getName(), serviceConfig);
            return this;
        }

        public Builder billing(BillingProvider billing) {
            this.billing = billing;
            return this;
        }

        public AppConfig build() {
            return new AppConfig(this);
        }
    }
}
