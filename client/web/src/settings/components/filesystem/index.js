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

import { cibWindows, cibAmazonAws } from '@coreui/icons'
import EfsFilesystemOptions from './EfsFilesystemOptions'
import FsxWindowsFilesystemOptions from './FsxWindowsFilesystemOptions'
import * as Yup from 'yup'

export const FILESYSTEM_TYPES = {
    "EFS": {
        "id": "EFS",
        "name": "EFS",
        "icon": cibAmazonAws,
        "component": EfsFilesystemOptions,
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
        "id": "FSX_WINDOWS",
        "name": "FSX Windows",
        "icon": cibWindows,
        "component": FsxWindowsFilesystemOptions,
        "defaults": {
            mountPoint: '',
            storageGb: 32,
            throughputMbs: 8,
            backupRetentionDays: 7,
            dailyBackupTime: '01:00',
            weeklyMaintenanceTime: '07:01:00',
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
    }
}

export const FILESYSTEM_DEFAULTS = Object.assign(Object.keys(FILESYSTEM_TYPES)
    .map(k => FILESYSTEM_TYPES[k].defaults)
    .reduce((p, c) => {
        return {...p, ...c}
    }))

export const OS_TO_FS_TYPES = {
    "LINUX": [
        FILESYSTEM_TYPES.EFS
    ],
    "WINDOWS": [
        FILESYSTEM_TYPES.FSX_WINDOWS
    ]
}