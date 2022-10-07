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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Onboarding {

    private UUID id;
    private LocalDateTime created;
    private LocalDateTime modified;
    private OnboardingStatus status;
    private UUID tenantId;
    private OnboardingRequest request;
    private List<OnboardingStack> stacks = new ArrayList<>();
    private String zipFile;
    private boolean ecsClusterLocked;

    public Onboarding() {
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

    public void setZipFile(String zipFile) {
        this.zipFile = zipFile;
    }

    public String getZipFile() {
        return zipFile;
    }

    public void setStacks(List<OnboardingStack> stacks) {
        this.stacks = stacks != null ? new ArrayList<>(stacks) : new ArrayList<>();
    }

    public void addStack(OnboardingStack stack) {
        if (stack != null) {
            this.stacks.add(stack);
        }
    }

    public boolean isEcsClusterLocked() {
        return ecsClusterLocked;
    }

    public void setEcsClusterLocked(boolean locked) {
        this.ecsClusterLocked = locked;
    }

    public boolean hasBaseStacks() {
        return !getStacks()
                .stream()
                .filter(OnboardingStack::isBaseStack)
                .collect(Collectors.toList())
                .isEmpty();
    }

    public boolean hasAppStacks() {
        return !getStacks()
                .stream()
                .filter(s -> !s.isBaseStack())
                .collect(Collectors.toList())
                .isEmpty();
    }

    public boolean appStacksDeleted() {
        return !hasAppStacks() || getStacks()
                .stream()
                .filter(s -> !s.isBaseStack())
                .filter(s -> !"DELETE_COMPLETE".equals(s.getStatus()))
                .collect(Collectors.toList())
                .isEmpty();
    }

    public boolean stacksComplete() {
        return stacksComplete(false);
    }

    public boolean baseStacksComplete() {
        return stacksComplete(true);
    }

    public boolean stacksDeployed() {
        boolean deployed = true;
        for (OnboardingStack stack : getStacks()) {
            if (!stack.isDeployed()) {
                deployed = false;
                break;
            }
        }
        return deployed;
    }

    protected boolean stacksComplete(boolean baseStacks) {
        boolean complete = false;
        if (!getStacks().isEmpty()) {
            if (baseStacks && !hasBaseStacks()) {
                // If there are no base stacks, then base stacks can't be complete
                complete = false;
            } else {
                if (baseStacks) {
                    // All base stacks have to be complete
                    complete = getStacks().stream()
                            .filter(stack ->  stack.isBaseStack() && !stack.isComplete())
                            .collect(Collectors.toList())
                            .isEmpty();
                } else {
                    // All stacks have to be complete
                    complete = getStacks().stream()
                            .filter(stack -> !stack.isComplete())
                            .collect(Collectors.toList())
                            .isEmpty();
                }
            }
        }
        return complete;
    }
}
