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
import { NavLink } from 'reactstrap'

OnboardingTenantLink.propTypes = {
  tenantName: PropTypes.string,
  tenantId: PropTypes.string,
  clickTenantDetails: PropTypes.func,
}

//TODO Revisit this when the onboarding service is done. Seems the return shape/vals changed
export function OnboardingTenantLink({
  tenantName,
  tenantId,
  clickTenantDetails,
}) {
  return !!tenantId ? (
    <NavLink
      href="#"
      className="pl-0 pt-0"
      onClick={() => clickTenantDetails(tenantId)}
    >
      {tenantName || 'Unknown'}
    </NavLink>
  ) : (
    <span>{tenantName || 'Unknown'}</span>
  )
}
