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

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class Tier {

    private UUID id;
    private LocalDateTime created;
    private LocalDateTime modified;
    private String name;
    private String description;
    private boolean defaultTier;
    private String billingPlan;

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
        final Tier other = (Tier) obj;
        return (
                Objects.equals(id, other.id)
                && Objects.equals(name, other.name)
                && Objects.equals(created, other.created)
                && Objects.equals(modified, other.modified)
                && Objects.equals(description, other.description)
                && Objects.equals(billingPlan, other.billingPlan)
                && defaultTier == other.defaultTier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, created, modified, name, description, billingPlan, defaultTier);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBillingPlan() {
        return billingPlan;
    }

    public void setBillingPlan(String billingPlan) {
        this.billingPlan = billingPlan;
    }

    public boolean isDefaultTier() {
        return defaultTier;
    }

    public void setDefaultTier(boolean defaultTier) {
        this.defaultTier = defaultTier;
    }
}
