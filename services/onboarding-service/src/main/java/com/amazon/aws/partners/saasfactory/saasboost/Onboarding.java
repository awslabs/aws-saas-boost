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
package com.amazon.aws.partners.saasfactory.saasboost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(value = {"cloudFormationUrl"}, allowGetters = true)
public class Onboarding {

    private UUID id;
    private LocalDateTime created;
    private LocalDateTime modified;
    private OnboardingStatus status;
    private UUID tenantId;
    private String tenantName;
    private String stackId;
    private String zipFileUrl;

    public Onboarding() {
    }

    public Onboarding(UUID id, OnboardingStatus status) {
        this(id, LocalDateTime.now(), LocalDateTime.now(), status, null, null, null);
    }

    public Onboarding(UUID id, LocalDateTime created, LocalDateTime modified, OnboardingStatus status, UUID tenantId, String tenantName, String stackId) {
        this.id = id;
        this.created = created;
        this.modified = modified;
        this.status = status;
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.stackId = stackId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setZipFileUrl(String zipFileUrl) {
        this.zipFileUrl= zipFileUrl;
    }

    public String getZipFileUrl() { return zipFileUrl;}

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

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getStackId() {
        return stackId;
    }

    public void setStackId(String stackId) {
        this.stackId = stackId;
    }

    public String getCloudFormationUrl() {
        String url = null;
        if (getStackId() != null) {
            String[] arn = getStackId().split(":");
            if (arn.length > 4) {
                String region = arn[3];
                url = String.format(
                        "https://%s.console.aws.amazon.com/cloudformation/home?region=%s#/stacks/stackinfo?filteringText=&filteringStatus=active&viewNested=true&hideStacks=false&stackId=%s",
                        region,
                        region,
                        getStackId()
                );
            }
        }
        return url;
    }
}
