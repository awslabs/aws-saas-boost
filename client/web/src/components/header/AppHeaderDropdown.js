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
import {
  CDropdown,
  CDropdownHeader,
  CDropdownItem,
  CDropdownMenu,
  CDropdownToggle,
} from '@coreui/react'
import { cilLockLocked, cilSettings, cilUser } from '@coreui/icons'
import CIcon from '@coreui/icons-react'

const AppHeaderDropdown = (props) => {
  const { handleProfileClick, onLogout, user } = props
  
  let userName = user?.profile['cognito:username']
  if (!userName) {
    userName = user?.profile?.username
  }
  if (!userName) {
    // keycloak
    userName = user?.profile?.preferred_username
  }
  if (!userName) {
    userName = user?.profile.name
  }
  if (!userName) {
    userName = user?.profile?.email
  }

  return (
    <CDropdown variant="nav-item">
      <CDropdownToggle placement="bottom-end" className="py-0" caret={false}>
        <span className="mx-2">
          <CIcon icon={cilUser} className="me-2" /> {userName}
        </span>
      </CDropdownToggle>
      <CDropdownMenu className="pt-0" placement="bottom-end">
        <CDropdownHeader className="bg-light fw-semibold py-2">
          <CIcon icon={cilSettings} className="me-2" /> Settings
        </CDropdownHeader>
        <CDropdownItem href="#" onClick={() => handleProfileClick()}>
          <CIcon icon={cilUser} className="me-2" /> Profile
        </CDropdownItem>
        <CDropdownItem href="#" onClick={() => onLogout()}>
          <CIcon icon={cilLockLocked} className="me-2" /> Sign Out
        </CDropdownItem>
      </CDropdownMenu>
    </CDropdown>
  )
}

AppHeaderDropdown.propTypes = {
  handleProfileClick: PropTypes.func,
  handleChangePasswordClick: PropTypes.func,
  onLogout: PropTypes.func,
  user: PropTypes.object,
}

export default AppHeaderDropdown
