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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true, value = {"hasBilling"})
public class Tenant {

    protected static final List<String> PROVISIONED_STATES = List.of("provisioned", "updating", "updated",
            "deploying", "deployed", "completed");
    private UUID id;
    private LocalDateTime created;
    private LocalDateTime modified;
    private boolean active = true;
    private String tier;
    private String onboardingStatus;
    private String name;
    private String subdomain;
    private String hostname;
    private Map<String, String> attributes = new HashMap<>();
    private Map<String, Resource> resources = new HashMap<>();
    private Set<TenantAdminUser> adminUsers = new HashSet<>();

    public Tenant() {
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isProvisioned() {
        return onboardingStatus != null && PROVISIONED_STATES.contains(onboardingStatus);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Boolean getActive() {
        return Boolean.TRUE.equals(active);
    }

    public void setActive(Boolean active) {
        this.active = active != null ? active : Boolean.FALSE;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getOnboardingStatus() {
        return onboardingStatus;
    }

    public void setOnboardingStatus(String onboardingStatus) {
        this.onboardingStatus = onboardingStatus;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public Map<String, String> getAttributes() {
        return Map.copyOf(attributes);
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }

    public Map<String, Resource> getResources() {
        return Map.copyOf(resources);
    }

    public void setResources(Map<String, Resource> resources) {
        this.resources = resources != null ? resources : new HashMap<>();
    }

    public Set<TenantAdminUser> getAdminUsers() {
        return Set.copyOf(adminUsers);
    }

    public void setAdminUsers(Collection<TenantAdminUser> adminUsers) {
        if (adminUsers != null) {
            this.adminUsers.addAll(adminUsers);
        }
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Tenant other = (Tenant) obj;

        boolean attributesEqual = attributes != null && other.attributes != null;
        if (attributesEqual) {
            attributesEqual = attributes.size() == other.attributes.size();
            if (attributesEqual) {
                for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                    attributesEqual = attribute.getValue().equals(other.attributes.get(attribute.getKey()));
                    if (!attributesEqual) {
                        break;
                    }
                }
            }
        }

        boolean resourcesEqual = resources != null && other.resources != null;
        if (resourcesEqual) {
            resourcesEqual = resources.size() == other.resources.size();
            if (resourcesEqual) {
                for (Map.Entry<String, Resource> resource : resources.entrySet()) {
                    resourcesEqual = resource.getValue().equals(other.resources.get(resource.getKey()));
                    if (!resourcesEqual) {
                        break;
                    }
                }
            }
        }
        boolean adminUsersEqual = adminUsers != null && adminUsers.equals(other.adminUsers);

        return (
                Objects.equals(id, other.id)
                && Objects.equals(created, other.created)
                && Objects.equals(modified, other.modified)
                && (active == other.active)
                && Objects.equals(tier, other.tier)
                && Objects.equals(onboardingStatus, other.onboardingStatus)
                && Objects.equals(name, other.name)
                && Objects.equals(subdomain, other.subdomain)
                && Objects.equals(hostname, other.hostname)
                && ((attributes == null && other.attributes == null) || attributesEqual)
                && ((resources == null && other.resources == null) || resourcesEqual)
                && ((adminUsers == null && other.adminUsers == null) || adminUsersEqual));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, created, modified, active, tier, onboardingStatus, name, subdomain, hostname)
                + Arrays.hashCode(attributes != null ? attributes.keySet().toArray(new String[0]) : null)
                + Arrays.hashCode(attributes != null ? attributes.values().toArray(new Object[0]) : null)
                + Arrays.hashCode(resources != null ? resources.keySet().toArray(new String[0]) : null)
                + Arrays.hashCode(resources != null ? resources.values().toArray(new Resource[0]) : null)
                + Arrays.hashCode(adminUsers != null ? adminUsers.toArray(new TenantAdminUser[0]) : null);
    }

    public static class Resource {

        String name;
        String arn;
        String consoleUrl;

        public Resource() {
            this(null, null, null);
        }

        public Resource(String name, String arn, String consoleUrl) {
            this.name = name;
            this.arn = arn;
            this.consoleUrl = consoleUrl;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getArn() {
            return arn;
        }

        public void setArn(String arn) {
            this.arn = arn;
        }

        public String getConsoleUrl() {
            return consoleUrl;
        }

        public void setConsoleUrl(String consoleUrl) {
            this.consoleUrl = consoleUrl;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Resource other = (Resource) obj;
            return (
                    Objects.equals(name, other.name)
                    && Objects.equals(arn, other.arn)
                    && Objects.equals(consoleUrl, other.consoleUrl));
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, arn, consoleUrl);
        }
    }
}
