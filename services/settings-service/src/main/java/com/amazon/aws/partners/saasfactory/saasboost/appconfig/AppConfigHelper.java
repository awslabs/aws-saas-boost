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

import java.util.HashSet;
import java.util.Set;

public final class AppConfigHelper {

    private AppConfigHelper() {
        // No one should instantiate us
    }

    public static boolean isDomainChanged(AppConfig existing, AppConfig altered) {
        return (
                (existing.getDomainName() != null && !existing.getDomainName().equalsIgnoreCase(altered.getDomainName())) ||
                (altered.getDomainName() != null && !altered.getDomainName().equalsIgnoreCase(existing.getDomainName()))
        );
    }

    public static boolean isSslArnChanged(AppConfig existing, AppConfig altered) {
        return (
                (existing.getSslCertArn() != null && !existing.getSslCertArn().equalsIgnoreCase(altered.getSslCertArn())) ||
                        (altered.getSslCertArn() != null && !altered.getSslCertArn().equalsIgnoreCase(existing.getSslCertArn()))
        );
    }

    public static boolean isBillingChanged(AppConfig existing, AppConfig altered) {
        return (
                (existing.getBilling() != null && !existing.getBilling().equals(altered.getBilling())) ||
                (altered.getBilling() != null && !altered.getBilling().equals(existing.getBilling()))
        );
    }

    public static boolean isBillingFirstTime(AppConfig existing, AppConfig altered) {
        return (
                (existing.getBilling() == null || !existing.getBilling().hasApiKey()) &&
                (altered.getBilling() != null && altered.getBilling().hasApiKey())
        );
    }

    public static boolean isBillingRemoved(AppConfig existing, AppConfig altered) {
        return (
                (existing.getBilling() != null && existing.getBilling().hasApiKey()) &&
                (altered.getBilling() == null || !altered.getBilling().hasApiKey())
        );
    }

    public static boolean isComputeChanged(AppConfig existing, AppConfig altered) {
        return true;
        // TODO POEPPT
//        int existingMemory = existing.getDefaultMemory() != null ? existing.getDefaultMemory() : -1;
//        int existingCpu = existing.getDefaultCpu() != null ? existing.getDefaultCpu() : -1;
//
//        int alteredMemory = altered.getDefaultMemory() != null ? altered.getDefaultMemory() : -1;
//        int alteredCpu = altered.getDefaultCpu() != null ? altered.getDefaultCpu() : -1;
//
//        return (
//                (existing.getComputeSize() != null && existing.getComputeSize() != altered.getComputeSize()) ||
//                (existing.getComputeSize() == null && altered.getComputeSize() != null) ||
//                (existingMemory != alteredMemory) ||
//                (existingCpu != alteredCpu)
//        );
    }

    public static boolean isAutoScalingChanged(AppConfig existing, AppConfig altered) {
        return true;
        // TODO POEPPT
//        int existingMin = existing.getMinCount() != null ? existing.getMinCount() : -1;
//        int existingMax = existing.getMaxCount() != null ? existing.getMaxCount() : -1;
//
//        int alteredMin = altered.getMinCount() != null ? altered.getMinCount() : -1;
//        int alteredMax = altered.getMaxCount() != null ? altered.getMaxCount() : -1;
//
//        return ((existingMin != alteredMin) || (existingMax != alteredMax));
    }

    public static Set<String> removedServices(AppConfig existing, AppConfig altered) {
        Set<String> existingServices = new HashSet<String>(existing.getServices().keySet());
        existingServices.removeAll(altered.getServices().keySet());
        return existingServices;
    }
}
