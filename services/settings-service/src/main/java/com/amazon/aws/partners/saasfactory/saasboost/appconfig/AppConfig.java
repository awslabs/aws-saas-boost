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

package com.amazon.aws.partners.saasfactory.saasboost.appconfig;

import com.amazon.aws.partners.saasfactory.saasboost.Utils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.*;

@JsonDeserialize(builder = AppConfig.Builder.class)
public class AppConfig {
    private final String name;
    private final String domainName;
    private final String hostedZone;
    private final String sslCertificate;
    private final Map<String, ServiceConfig> services;
    private final BillingProvider billing;

    private AppConfig(Builder builder) {
        this.name = builder.name;
        this.domainName = builder.domainName;
        this.hostedZone = builder.hostedZone;
        this.sslCertificate = builder.sslCertificate;
        this.services = builder.services;
        this.billing = builder.billing;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(AppConfig otherAppConfig) {
        return new Builder()
                .name(otherAppConfig.name)
                .domainName(otherAppConfig.domainName)
                .hostedZone(otherAppConfig.hostedZone)
                .sslCertificate(otherAppConfig.sslCertificate)
                .services(otherAppConfig.services)
                .billing(otherAppConfig.billing);
    }

    public String getName() {
        return name;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getHostedZone() {
        return hostedZone;
    }

    public String getSslCertificate() {
        return sslCertificate;
    }

    public BillingProvider getBilling() {
        return billing;
    }

    public Map<String, ServiceConfig> getServices() {
        return services != null ? Map.copyOf(services) : null;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return (Utils.isBlank(name) && Utils.isBlank(domainName) && Utils.isBlank(hostedZone)
                && Utils.isBlank(sslCertificate)
                && (billing == null || !billing.hasApiKey())
                && (services == null || services.isEmpty())
        );
    }

    @Override
    public String toString() {
        return Utils.toJson(this);
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
                && ((hostedZone == null && other.hostedZone == null) || (hostedZone != null && hostedZone.equals(other.hostedZone)))
                && ((sslCertificate == null && other.sslCertificate == null) || (sslCertificate != null && sslCertificate.equals(other.sslCertificate)))
                && ((services == null && other.services == null) || (servicesEqual(services, other.services)))
                && ((billing == null && other.billing == null) || (billing != null && billing.equals(other.billing)))
        );
    }

    public static boolean servicesEqual(Map<String, ServiceConfig> services, Map<String, ServiceConfig> otherServices) {
        boolean equal = false;
        if (services != null && otherServices != null) {
            if (services.size() == otherServices.size()) {
                boolean entriesEqual = true;
                for (Map.Entry<String, ServiceConfig> entry : services.entrySet()) {
                    if (!otherServices.containsKey(entry.getKey())) {
                        entriesEqual = false;
                        break;
                    } else {
                        ServiceConfig service1 = entry.getValue();
                        ServiceConfig service2 = otherServices.get(entry.getKey());
                        if (service1 == null && service2 == null) {
                            continue;
                        }
                        if (service1 == null || !service1.equals(service2)) {
                            entriesEqual = false;
                            break;
                        }
                    }
                }
                equal = entriesEqual;
            }
        }
        return equal;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, domainName, hostedZone, sslCertificate, services, billing);
    }

    @JsonPOJOBuilder(withPrefix = "") // setters aren't named with[Property]
    @JsonIgnoreProperties(value = {"serviceConfig"})
    public static final class Builder {
        private String name;
        private String domainName;
        private String hostedZone;
        private String sslCertificate;
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

        public Builder hostedZone(String hostedZone) {
            this.hostedZone = hostedZone;
            return this;
        }

        public Builder sslCertificate(String sslCertificate) {
            this.sslCertificate = sslCertificate;
            return this;
        }

        public Builder services(Map<String, ServiceConfig> services) {
            this.services = services != null ? services : new HashMap<>();
            return this;
        }

        public Builder serviceConfig(ServiceConfig serviceConfig) {
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
