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

public enum OnboardingStatus {
    created,
    validating,
    validated,
    provisioning,
    provisioned,
    updating,
    updated,
    deploying,
    deployed,
    failed,
    deleting,
    deleted;

    public static OnboardingStatus fromStackStatus(String stackStatus) {
        OnboardingStatus status;
        switch (stackStatus) {
            case "REVIEW_IN_PROGRESS":
            case "CREATE_IN_PROGRESS":
                status = OnboardingStatus.provisioning;
                break;
            case "UPDATE_IN_PROGRESS":
                status = OnboardingStatus.updating;
                break;
            case "DELETE_IN_PROGRESS":
                status = OnboardingStatus.deleting;
                break;
            case "CREATE_COMPLETE":
                status = OnboardingStatus.provisioned;
                break;
            case "UPDATE_COMPLETE":
                status = OnboardingStatus.updated;
                break;
            case "DELETE_COMPLETE":
                status = OnboardingStatus.deleted;
                break;
            case "CREATE_FAILED":
            case "UPDATE_FAILED":
            case "DELETE_FAILED":
            case "UPDATE_ROLLBACK_FAILED":
            case "ROLLBACK_IN_PROGRESS":
            case "ROLLBACK_COMPLETE":
            case "ROLLBACK_FAILED":
                status = OnboardingStatus.failed;
                break;
            default:
                status = Utils.isNotBlank(stackStatus) ? OnboardingStatus.created : null;
        }
        return status;
    }
}
