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
// Based on CoreUI Template https://github.com/coreui/coreui-free-react-admin-template
// SPDX-LicenseIdentifier: MIT
import React, { useState } from 'react'
import PropTypes from 'prop-types'
import {
  CSidebar,
  CSidebarBrand,
  CSidebarNav,
  CSidebarToggler,
} from '@coreui/react'

import { AppSidebarNav } from './AppSidebarNav'

import SimpleBar from 'simplebar-react'
import 'simplebar/dist/simplebar.min.css'

const AppSidebar = (props) => {
  const [sidebarNarrow, setSidebarNarrow] = useState(false)
  const { navigation } = props
  const getText = (isNarrow) => {
    return (
      !isNarrow && (
        <>
          <span style={{ color: '#FF9900' }}>
            <strong>AWS</strong>
          </span>
          &nbsp;
          <span style={{ color: '#232F3E' }}>SaaS Boost</span>
        </>
      )
    )
  }
  return (
    <CSidebar position="fixed" narrow={sidebarNarrow}>
      <CSidebarBrand className="d-none d-md-flex bg-body" to="/">
        <img src="/saas-boost-logo.png" alt="logo" height="40" />
        {getText(sidebarNarrow)}
      </CSidebarBrand>
      <CSidebarNav>
        <SimpleBar>
          <AppSidebarNav items={navigation} />
        </SimpleBar>
      </CSidebarNav>
      <CSidebarToggler
        className="d-lg-flex"
        onClick={() => {
          setSidebarNarrow(!sidebarNarrow)
        }}
      />
    </CSidebar>
  )
}

AppSidebar.propTypes = {
  navigation: PropTypes.array,
}

export default React.memo(AppSidebar)
