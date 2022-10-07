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

import { cibWindows, cibAmazonAws, cilViewQuilt } from '@coreui/icons'
import EfsFilesystemOptions from './EfsFilesystemOptions'
import FsxOntapFilesystemOptions from './FsxOntapFilesystemOptions'
import FsxWindowsFilesystemOptions from './FsxWindowsFilesystemOptions'
import * as Yup from 'yup'

export const FILESYSTEM_TYPES = {
    "EFS": {
        "configId": "EFS",
        "id": "EFS",
        "name": "EFS",
        "icon": cibAmazonAws,
        "component": EfsFilesystemOptions,
        "enabled": (os, launchType) => os === "LINUX",
        "defaults": {
            mountPoint: '',
            lifecycle: 'NEVER',
            encrypt: '',
        },
        "validationSchema": Yup.object({
            mountPoint: Yup.string()
                .matches(/^(\/[a-zA-Z._-]+)*$/, 'Invalid path. Ex: /mnt')
                .max(100, "The full path can't exceed 100 characters in length")
                .test(
                    'subdirectories',
                    'The path can only include up to four subdirectories',
                    (val) => (val?.match(/\//g) || []).length <= 4
                )
                .required(),
            encrypt: Yup.bool(),
            lifecycle: Yup.string().required('Lifecycle is required'),
        })
    },
    "FSX_WINDOWS": {
        "configId": "FSX_WINDOWS",
        "id": "FSX_WINDOWS",
        "name": "FSX Windows",
        "icon": cibWindows,
        "component": FsxWindowsFilesystemOptions,
        "enabled": (os, launchType) => os === "WINDOWS",
        "defaults": {
            mountPoint: '',
            storageGb: 32,
            throughputMbs: 8,
            backupRetentionDays: 7,
            dailyBackupTime: '01:00',
            weeklyMaintenanceTime: '07:01',
            weeklyMaintenanceDay: '1',
            windowsMountDrive: 'G:',
        },
        "validationSchema": Yup.object({
            mountPoint: Yup.string()
                .matches(
                    /^[a-zA-Z]:\\(((?![<>:"/\\|?*]).)+((?<![ .])\\)?)*$/,
                    'Invalid path. Ex: C:\\data'
                )
                .required(),
            storageGb: Yup.number()
                .required()
                .min(32, 'Storage minimum is 32 GB')
                .max(1048, 'Storage maximum is 1048 GB'),
            throughputMbs: Yup.number()
                .required()
                .min(8, 'Throughput minimum is 8 MB/s')
                .max(2048, 'Throughput maximum is 2048 MB/s'),
            backupRetentionDays: Yup.number()
                .required()
                .min(7, 'Minimum retention time is 7 days')
                .max(35, 'Maximum retention time is 35 days'),
            dailyBackupTime: Yup.string()
                .required('Daily backup time is required'),
            weeklyMaintenanceTime: Yup.string()
                .required('Weekly maintenance time is required'),
            windowsMountDrive: Yup.string()
                .required('Windows mount drive is required'),
        })
    },
    "FSX_ONTAP_LINUX": {
        "configId": "FSX_ONTAP",
        "id": "FSX_ONTAP_LINUX",
        "name": "FSX Ontap",
        "icon": cilViewQuilt,
        "component": FsxOntapFilesystemOptions,
        "enabled": (os, launchType) => os === "LINUX" && launchType === "EC2",
        "defaults": {
            mountPoint: '',
            storageGb: 1024,
            throughputMbs: 128,
            backupRetentionDays: 7,
            dailyBackupTime: '01:00',
            weeklyMaintenanceTime: '07:01',
            weeklyMaintenanceDay: '1',
            volumeSize: 40
        },
        "validationSchema": Yup.object({
            mountPoint: Yup.string()
                .matches(/^(\/[a-zA-Z._-]+)*$/, 'Invalid path. Ex: /mnt')
                .max(100, "The full path can't exceed 100 characters in length")
                .test(
                    'subdirectories',
                    'The path can only include up to four subdirectories',
                    (val) => (val?.match(/\//g) || []).length <= 4
                )
                .required(),
            storageGb: Yup.number()
                .required()
                .min(1024, 'Storage minimum is 1024 GB')
                .max(196608, 'Storage maximum is 196,608 GB'),
            throughputMbs: Yup.number()
                .required()
                .min(128, 'Throughput minimum is 128 MB/s')
                .max(2048, 'Throughput maximum is 2048 MB/s'),
            backupRetentionDays: Yup.number()
                .required()
                .min(7, 'Minimum retention time is 7 days')
                .max(35, 'Maximum retention time is 35 days'),
            dailyBackupTime: Yup.string()
                .required('Daily backup time is required'),
            weeklyMaintenanceTime: Yup.string()
                .required('Weekly maintenance time is required'),
            volumeSize: Yup.number()
                .required()
                .min(0, 'Volume Size must be a positive number')
                .max(196608, 'Volume size maximum is 196,608 GB')
        })
    },
    "FSX_ONTAP_WINDOWS": {
        "configId": "FSX_ONTAP",
        "id": "FSX_ONTAP_WINDOWS",
        "name": "FSX Ontap",
        "icon": cilViewQuilt,
        "component": FsxOntapFilesystemOptions,
        "enabled": (os, launchType) => os === "WINDOWS",
        "defaults": {
            mountPoint: '',
            storageGb: 1024,
            throughputMbs: 128,
            backupRetentionDays: 7,
            dailyBackupTime: '01:00',
            weeklyMaintenanceTime: '07:01',
            weeklyMaintenanceDay: '1',
            windowsMountDrive: 'G:',
            volumeSize: 40
        },
        "validationSchema": Yup.object({
            mountPoint: Yup.string()
                .matches(
                    /^[a-zA-Z]:\\(((?![<>:"/\\|?*]).)+((?<![ .])\\)?)*$/,
                    'Invalid path. Ex: C:\\data'
                )
                .required(),
            storageGb: Yup.number()
                .required()
                .min(1024, 'Storage minimum is 1024 GB')
                .max(196608, 'Storage maximum is 196,608 GB'),
            throughputMbs: Yup.number()
                .required()
                .min(128, 'Throughput minimum is 128 MB/s')
                .max(2048, 'Throughput maximum is 2048 MB/s'),
            backupRetentionDays: Yup.number()
                .required()
                .min(7, 'Minimum retention time is 7 days')
                .max(35, 'Maximum retention time is 35 days'),
            dailyBackupTime: Yup.string()
                .required('Daily backup time is required'),
            weeklyMaintenanceTime: Yup.string()
                .required('Weekly maintenance time is required'),
            windowsMountDrive: Yup.string()
                .required('Windows mount drive is required'),
            volumeSize: Yup.number()
                .required()
                .min(0, 'Volume Size must be a positive number')
                .max(196608, 'Volume size maximum is 196,608 GB')
        })
    }
}

export const FILESYSTEM_DEFAULTS = Object.assign(Object.keys(FILESYSTEM_TYPES)
    .map(k => FILESYSTEM_TYPES[k].defaults)
    .reduce((p, c) => {
        return {...p, ...c}
    }))

export const OS_TO_FS_TYPES = {
    "LINUX": [
        FILESYSTEM_TYPES.EFS,
        // FILESYSTEM_TYPES.FSX_ONTAP_LINUX,
    ],
    "WINDOWS": [
        FILESYSTEM_TYPES.FSX_WINDOWS,
        FILESYSTEM_TYPES.FSX_ONTAP_WINDOWS,
    ]
}