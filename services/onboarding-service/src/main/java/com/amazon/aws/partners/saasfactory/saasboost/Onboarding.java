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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(value = {"cloudFormationUrl"}, allowGetters = true)
public class Onboarding {

    private UUID id;
    private LocalDateTime created;
    private LocalDateTime modified;
    private OnboardingStatus status;
    private UUID tenantId;
    private OnboardingRequest request;
    private List<OnboardingStack> stacks = new ArrayList<>();
    private String zipFileUrl;

    public Onboarding() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setZipFileUrl(String zipFileUrl) {
        this.zipFileUrl = zipFileUrl;
    }

    public String getZipFileUrl() {
        return zipFileUrl;
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

    public OnboardingStatus getStatus() {
        return status;
    }

    public void setStatus(OnboardingStatus status) {
        this.status = status;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public OnboardingRequest getRequest() {
        return request;
    }

    public void setRequest(OnboardingRequest request) {
        this.request = request;
    }

    public List<OnboardingStack> getStacks() {
        return stacks;
    }

    public void setStacks(List<OnboardingStack> stacks) {
        this.stacks = stacks != null ? new ArrayList<>(stacks) : new ArrayList<>();
    }

    public void addStack(OnboardingStack stack) {
        if (stack != null) {
            this.stacks.add(stack);
        }
    }

    public boolean stacksComplete() {
        boolean complete = false;
        if (!getStacks().isEmpty()) {
            complete = true;
            for (OnboardingStack s : getStacks()) {
                if (!"CREATE_COMPLETE".equals(s.getStatus()) && !"UPDATE_COMPLETE".equals(s.getStatus())) {
                    complete = false;
                    break;
                }
            }
        }
        return complete;
    }

}
