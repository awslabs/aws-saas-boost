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

export const LINUX = 'LINUX'
export const WINDOWS = 'WINDOWS'
export const EC2 = 'EC2'
export const FARGATE = 'FARGATE'

// EC2 AutoScaling min/max is only relevant when ECS tasks are launched on EC2 instances, 
// EC2 AutoScaling configuration is only required when the operating system is WINDOWS
// (EC2 launch by default) or the operating system is LINUX and the ECS Launch Type is EC2
export const isEC2AutoScalingRequired = (operatingSystem, ecsLaunchType) => {
    return operatingSystem === WINDOWS || (operatingSystem === LINUX && ecsLaunchType === EC2)
}