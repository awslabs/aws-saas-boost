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
import React from 'react'
import PropTypes from 'prop-types'
import { useSelector, useDispatch } from 'react-redux'
import { CContainer, CHeader, CHeaderDivider, CHeaderNav } from '@coreui/react'

import { AppBreadcrumb } from './index'
import { AppHeaderDropdown } from './header/index'

const AppHeader = (props) => {
  const dispatch = useDispatch()
  const sidebarShow = useSelector((state) => state.sidebarShow)
  const { handleProfileClick, handleChangePasswordClick, onLogout, user, router } = props
  return (
    <CHeader position="sticky" className="mb-4">
      <CContainer fluid>
        <CHeaderNav className="d-none d-md-flex me-auto" />
        <CHeaderNav>
          <AppHeaderDropdown
            user={user}
            handleChangePasswordClick={handleChangePasswordClick}
            handleProfileClick={handleProfileClick}
            onLogout={onLogout}
          />
        </CHeaderNav>
      </CContainer>
      <CHeaderDivider />
      <CContainer fluid>
        <AppBreadcrumb router={router} />
      </CContainer>
    </CHeader>
  )
}

AppHeader.propTypes = {
  handleProfileClick: PropTypes.func,
  handleChangePasswordClick: PropTypes.func,
  onLogout: PropTypes.func,
  user: PropTypes.object,
  router: PropTypes.object,
}

export default AppHeader
