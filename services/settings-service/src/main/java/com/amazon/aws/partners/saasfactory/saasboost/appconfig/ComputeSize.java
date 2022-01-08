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

public enum ComputeSize {

    S(512, 1024, "t3.medium"),
    M(1024, 2048, "t3.medium"),
    L(2048, 4096, "m5.xlarge"),
    XL(4096, 8192, "m5.2xlarge");

    private final int cpu;
    private final int memory;
    private final String instanceType;

    ComputeSize(int cpu, int memory, String instanceType) {
        this.cpu = cpu;
        this.memory = memory;
        this.instanceType = instanceType;
    }

    public int getCpu() {
        return cpu;
    }

    public int getMemory() {
        return memory;
    }

    public String getInstanceType() {
        return instanceType;
    }
}
