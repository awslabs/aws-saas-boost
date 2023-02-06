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

import { lazy } from 'react'

const UserListContainer = lazy(() => import('./UserListContainer'))
const UserViewContainer = lazy(() => import('./UserViewContainer'))
const UserCreateContainer = lazy(() => import('./UserCreateContainer'))

export const BaseUserRoute = '/sysusers'

export const UserRoutes = [
  {
    path: BaseUserRoute,
    exact: true,
    name: 'System Users',
    component: UserListContainer,
  },
  {
    path: BaseUserRoute + '/create',
    exact: true,
    name: 'Create a new System User',
    component: UserCreateContainer,
  },
  {
    path: BaseUserRoute + '/:username',
    exact: true,
    name: 'System User Detail',
    component: UserViewContainer,
  },
]
