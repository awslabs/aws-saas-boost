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
import Moment from 'react-moment'

import { Button } from 'reactstrap'
import { OnboardingStatus } from './OnboardingStatus'
import { OnboardingTenantLink } from './OnboardingTenantLink'

OnboardingListItemComponent.propTypes = {
  onboarding: PropTypes.object,
  clickOnboardingRequest: PropTypes.func,
  clickTenantDetails: PropTypes.func,
}

export default function OnboardingListItemComponent(props) {
  const { onboarding, clickOnboardingRequest, clickTenantDetails } = props

  return (
    <tr>
      <th scope="row">
        <Button
          className="pl-0"
          color="link"
          onClick={() => clickOnboardingRequest(onboarding.id)}
        >
          {onboarding.id}
        </Button>
      </th>
      <td>
        <OnboardingTenantLink
          tenantName={onboarding.request?.name}
          tenantId={onboarding.tenantId}
          clickTenantDetails={clickTenantDetails}
        />
      </td>
      <td>
        <OnboardingStatus status={onboarding.status} />
      </td>
      <td>
        <Moment format="LLL">{onboarding.created}</Moment>
      </td>
      <td>
        <Moment format="LLL">{onboarding.modified}</Moment>
      </td>
    </tr>
  )
}
