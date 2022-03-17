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

import java.util.HashSet;
import java.util.Set;

public final class AppConfigHelper {

    private AppConfigHelper() {
        // No one should instantiate us
    }

    public static boolean isDomainChanged(AppConfig existing, AppConfig altered) {
        return ((existing.getDomainName() != null
                        && !existing.getDomainName().equalsIgnoreCase(altered.getDomainName()))
                || (altered.getDomainName() != null
                && !altered.getDomainName().equalsIgnoreCase(existing.getDomainName())));
    }

    public static boolean isSslArnChanged(AppConfig existing, AppConfig altered) {
        return ((existing.getSslCertificate() != null
                && !existing.getSslCertificate().equalsIgnoreCase(altered.getSslCertificate()))
                || (altered.getSslCertificate() != null
                && !altered.getSslCertificate().equalsIgnoreCase(existing.getSslCertificate())));
    }

    public static boolean isHostedZoneChanged(AppConfig existing, AppConfig altered) {
        return ((existing.getHostedZone() != null
                && !existing.getHostedZone().equalsIgnoreCase(altered.getHostedZone()))
                || (altered.getHostedZone() != null
                && !altered.getHostedZone().equalsIgnoreCase(existing.getHostedZone())));
    }

    public static boolean isBillingChanged(AppConfig existing, AppConfig altered) {
        return ((existing.getBilling() != null && !existing.getBilling().equals(altered.getBilling()))
                || (altered.getBilling() != null && !altered.getBilling().equals(existing.getBilling())));
    }

    public static boolean isBillingFirstTime(AppConfig existing, AppConfig altered) {
        return ((existing.getBilling() == null || !existing.getBilling().hasApiKey())
                && (altered.getBilling() != null && altered.getBilling().hasApiKey()));
    }

    public static boolean isBillingRemoved(AppConfig existing, AppConfig altered) {
        return ((existing.getBilling() != null && existing.getBilling().hasApiKey())
                && (altered.getBilling() == null || !altered.getBilling().hasApiKey()));
    }

    public static Set<String> removedServices(AppConfig existing, AppConfig altered) {
        Set<String> existingServices = new HashSet<>(existing.getServices().keySet());
        existingServices.removeAll(altered.getServices().keySet());
        return existingServices;
    }

    public static boolean isServicesChanged(AppConfig existing, AppConfig altered) {
        return !AppConfig.servicesEqual(existing.getServices(), altered.getServices());
    }
}
