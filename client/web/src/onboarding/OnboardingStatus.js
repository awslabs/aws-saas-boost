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
import { PropTypes } from 'prop-types'
import React from 'react'
import { Badge } from 'reactstrap'
import { capitalize } from 'lodash'

OnboardingStatus.propTypes = {
  status: PropTypes.string,
}

export function OnboardingStatus({ status }) {
  let statusColor = ''

  switch (status) {
    case 'deleted':
    case 'failed':
      statusColor = 'danger'
      break
    case 'deployed':
      statusColor = 'success'
      break
    case 'provisioning':
      statusColor = 'info'
      break
    default:
      statusColor = 'warning'
      break
  }

  return <Badge color={statusColor}>{capitalize(status)}</Badge>
}
