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

package com.amazon.aws.partners.saasfactory.saasboost.workflow;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * This enumerates all component types the installer may need to update during
 * the course of a SaaS Boost update.
 */
public enum UpdateAction {
    LAYERS,
    CLIENT,
    CUSTOM_RESOURCES,
    FUNCTIONS,
    METERING_BILLING,
    RESOURCES,
    SERVICES;

    private Set<String> targets = new HashSet<String>();

    /**
     * Adds a new target to this <code>UpdateAction</code>.
     * 
     * e.g. if this is a <code>SERVICE</code>, the list of targets may be
     *      <code>[ "onboarding", "tenant" ]</code>.
     * 
     * @param target the target to add
     */
    public void addTarget(String target) {
        targets.add(target);
    }

    public Set<String> getTargets() {
        return targets;
    }

    public void resetTargets() {
        targets.clear();
    }

    public String getDirectoryName() {
        if (this != CUSTOM_RESOURCES) {
            return nameToDirectory(this.name());
        }
        return Path.of(RESOURCES.getDirectoryName(), nameToDirectory(this.name())).toString();
    }

    private static String nameToDirectory(String name) {
        return name.toLowerCase().replace('_', '-');
    }

    private static String directoryToName(String dir) {
        return dir.toUpperCase().replace('-', '_');
    }

    public static UpdateAction fromDirectoryName(String directoryName) {
        try {
            return UpdateAction.valueOf(directoryToName(directoryName));
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.name() + " | " + this.getTargets();
    }
}
