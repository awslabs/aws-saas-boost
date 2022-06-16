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
import CIcon from '@coreui/icons-react'
import {
  cilHome,
  cilLayers,
  cilPeople,
  cilPowerStandby,
  cilSpeedometer,
  cilUserPlus,
} from '@coreui/icons'
import { CNavGroup, CNavItem } from '@coreui/react'

const _nav = [
  {
    component: CNavItem,
    name: 'Summary',
    to: '/summary',
    icon: <CIcon icon={cilHome} customClassName="nav-icon" />,
  },
  {
    component: CNavGroup,
    name: 'Dashboard',
    icon: <CIcon icon={cilSpeedometer} customClassName="nav-icon" />,
    items: [
      {
        component: CNavItem,
        name: 'Requests',
        to: '/dashboard/alb',
      },
      {
        component: CNavItem,
        name: 'Compute',
        to: '/dashboard/ecs',
      },
      {
        component: CNavItem,
        name: 'Usage',
        to: '/dashboard/accesslogging',
      },
    ],
  },
  {
    component: CNavItem,
    name: 'Application',
    to: '/application',
    icon: <CIcon icon={cilPowerStandby} customClassName="nav-icon" />,
    badge: {
      color: 'danger',
      text: 'SETUP',
    },
  },
  {
    component: CNavItem,
    name: 'Onboarding',
    to: '/onboarding',
    icon: <CIcon icon={cilUserPlus} customClassName="nav-icon" />,
    disabled: true,
  },
  {
    component: CNavItem,
    name: 'Tenants',
    to: '/tenants',
    icon: <CIcon icon={cilLayers} customClassName="nav-icon" />,
    disabled: true,
  },
  {
    component: CNavItem,
    name: 'Tiers',
    to: '/tiers',
    icon: <CIcon icon={cilLayers} customClassName="nav-icon" />,
    disabled: false,
  },
  {
    component: CNavItem,
    name: 'Users',
    to: '/users',
    icon: <CIcon icon={cilPeople} customClassName="nav-icon" />,
  },
]

export default _nav
