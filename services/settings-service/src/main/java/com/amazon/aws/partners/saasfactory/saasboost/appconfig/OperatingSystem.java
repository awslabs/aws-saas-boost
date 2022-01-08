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

public enum OperatingSystem {

    LINUX("Amazon Linux 2"),
    WIN_2019_FULL("Windows Server 2019 Full"),
    WIN_2019_CORE("Windows Server 2019 Core"),
    WIN_1909_CORE("Windows Server 1909 Core Semi-Annual"),
    WIN_2016_FULL("Windows Server 2016 Full");

    private final String description;

    OperatingSystem(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isWindows() {
        return this != OperatingSystem.LINUX;
    }

    public static OperatingSystem ofDescription(String description) {
        OperatingSystem operatingSystem = null;
        for (OperatingSystem os : OperatingSystem.values()) {
            if (os.getDescription().equals(description)) {
                operatingSystem = os;
                break;
            }
        }
        return operatingSystem;
    }
}
