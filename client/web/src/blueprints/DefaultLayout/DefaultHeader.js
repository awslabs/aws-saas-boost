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

import React, { Component } from 'react'

import {
  UncontrolledDropdown,
  DropdownItem,
  DropdownMenu,
  DropdownToggle,
} from 'reactstrap'
import PropTypes from 'prop-types'

const propTypes = {
  children: PropTypes.node,
}

const defaultProps = {}

class DefaultHeader extends Component {
  render() {
    // eslint-disable-next-line
    const { handleProfileClick, handleChangePasswordClick, user } = this.props
    return (
      <>
        <UncontrolledDropdown nav direction="down">
          <DropdownToggle nav>
            <div>
              <span className="mx-2">
                <i className="icon-user"></i> {user?.username}
              </span>
            </div>
          </DropdownToggle>
          <DropdownMenu right>
            <DropdownItem header tag="div" className="text-center">
              <strong>Settings</strong>
            </DropdownItem>
            <DropdownItem onClick={() => handleProfileClick()}>
              <i className="fa fa-user"></i> Profile
            </DropdownItem>

            <DropdownItem onClick={() => handleChangePasswordClick()}>
              <i className="fa fa-shield"></i> Change Password
            </DropdownItem>
            <DropdownItem onClick={(e) => this.props.onLogout(e)}>
              <i className="fa fa-lock"></i> Sign Out
            </DropdownItem>
          </DropdownMenu>
        </UncontrolledDropdown>
      </>
    )
  }
}
DefaultHeader.propTypes = propTypes
DefaultHeader.defaultProps = defaultProps

export default DefaultHeader
