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
import { CFooter } from '@coreui/react'
import { useSelector } from 'react-redux'
import PropTypes from 'prop-types'
import { selectSettingsById } from '../settings/ducks'
import * as SETTINGS from '../settings/common'

const AppFooter = (props) => {
  const { children, ...attributes } = props
  const version = useSelector((state) => selectSettingsById(state, SETTINGS.VERSION))
  const saasBoostEnvironment = useSelector((state) =>
    selectSettingsById(state, 'SAAS_BOOST_ENVIRONMENT'),
  )

  const prettyVersion = (versionParameter) => {
    let versionString = versionParameter?.value
    try {
        if (!!versionString) {
            let versionObject = JSON.parse(versionString)
            if (versionObject?.tag && versionObject?.describe && versionObject?.commit) {
                if (versionObject.tag === versionObject.describe) {
                    versionString = versionObject.tag
                } else {
                    versionString = versionObject.describe + "@" + versionObject.commit
                }
            }
        }
    } catch (e) {
        console.error("Failed parsing VERSION: '" + versionString + "' to JSON", e)
    }
    return versionString
  }

  return (
    <CFooter>
      <span>
        <a rel="noopener noreferrer" href="http://aws.amazon.com">
          AWS
        </a>
        &nbsp;&copy; Amazon.com, Inc.
      </span>
      <span className="ml-auto">
        Version {prettyVersion(version)} - {saasBoostEnvironment?.value}
      </span>
    </CFooter>
  )
}

AppFooter.propTypes = {
  children: PropTypes.array,
}

export default React.memo(AppFooter)
