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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.*;

public class AppConfig {

    private UUID id;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String name;
    private String domainName;
    private String hostedZone;
    private String sslCertificate;
    private Map<String, ServiceConfig> services = new LinkedHashMap<>();

    public AppConfig() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(LocalDateTime modified) {
        this.modified = modified;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getHostedZone() {
        return hostedZone;
    }

    public void setHostedZone(String hostedZone) {
        this.hostedZone = hostedZone;
    }

    public String getSslCertificate() {
        return sslCertificate;
    }

    public void setSslCertificate(String sslCertificate) {
        this.sslCertificate = sslCertificate;
    }

    public Map<String, ServiceConfig> getServices() {
        return services != null ? Map.copyOf(services) : null;
    }

    public void setServices(Map<String, ServiceConfig> services) {
        this.services = services != null ? services : new LinkedHashMap<>();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return (id == null && Utils.isBlank(name) && Utils.isBlank(domainName) && Utils.isBlank(hostedZone)
                && Utils.isBlank(sslCertificate)
                && (services == null || services.isEmpty()));
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
        return (Objects.equals(id, other.getId())
                && Objects.equals(name, other.getName())
                && Objects.equals(domainName, other.getDomainName())
                && Objects.equals(hostedZone, other.getHostedZone())
                && Objects.equals(sslCertificate, other.getSslCertificate())
                && ((services == null && other.services == null) || (servicesEqual(services, other.services))));
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
        return Objects.hash(id, created, modified, name, domainName, hostedZone, sslCertificate, services);
    }

}
