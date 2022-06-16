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

import React from 'react'

const TierListContainer = React.lazy(() => import('./TierListContainer'))
const TierViewContainer = React.lazy(() => import('./TierViewContainer'))
const TierCreateContainer = React.lazy(() => import('./TierCreateContainer'))

export const TierRoutes = [
  {
    path: '/tiers',
    exact: true,
    name: 'Tiers',
    component: TierListContainer,
  },
  {
    path: '/tiers/create',
    exact: true,
    name: 'Create a new Tier',
    component: TierCreateContainer,
  },
  {
    path: '/tiers/:tierId',
    exact: true,
    name: 'Tier Detail',
    component: TierViewContainer,
  },
]
