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
  cilPeople,
  cilCog,
  cilFactory,
  cilBarChart,
  cilUserPlus,
  cilGift,
  cilShieldAlt,
  cilCreditCard
} from '@coreui/icons'
import { CNavGroup, CNavItem } from '@coreui/react'
import { BaseUserRoute } from './users'

const _nav = [
  {
    component: CNavItem,
    name: 'Home',
    to: '/summary',
    icon: <CIcon icon={cilHome} customClassName="nav-icon" />,
  },
  {
    //component: CNavGroup,
    component: CNavItem,
    name: 'Metrics',
    icon: <CIcon icon={cilBarChart} customClassName="nav-icon" />,
    to: '/metrics',
    /*
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
    */
  },
  {
    component: CNavItem,
    name: 'Tiers',
    to: '/tiers',
    icon: <CIcon icon={cilGift} customClassName="nav-icon" />,
    //disabled: false,
  },
  {
    component: CNavItem,
    name: 'Billing',
    to: '/billing',
    icon: <CIcon icon={cilCreditCard} customClassName="nav-icon" />,
    //disabled: false,
  },
  {
    component: CNavItem,
    name: 'Identity',
    to: '/providers',
    icon: <CIcon icon={cilUserPlus} customClassName="nav-icon" />,
    //disabled: false,
  },
  {
    component: CNavItem,
    name: 'Tenants',
    to: '/tenants',
    icon: <CIcon icon={cilPeople} customClassName="nav-icon" />,
    //disabled: true,
  },
  {
    component: CNavItem,
    name: 'Application',
    to: '/application',
    icon: <CIcon icon={cilCog} customClassName="nav-icon" />,
    /*
    badge: {
      color: 'danger',
      text: 'SETUP',
    },
    */
  },
  {
    component: CNavItem,
    name: 'Onboarding',
    to: '/onboarding',
    icon: <CIcon icon={cilFactory} customClassName="nav-icon" />,
    //disabled: true,
  },
  {
    component: CNavItem,
    name: 'System Users',
    to: BaseUserRoute,
    icon: <CIcon icon={cilShieldAlt} customClassName="nav-icon" />,
  }
  // AppSidebar.js adds the last menu entry for redirect to api docs
]

export default _nav
