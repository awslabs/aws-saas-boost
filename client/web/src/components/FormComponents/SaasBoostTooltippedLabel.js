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
import React, { useState } from 'react'
import { Label, Tooltip } from 'reactstrap'

export const SaasBoostTooltippedLabel = ({ field, label, tooltip, id, ...props }) => {
  const [tooltipOpen, setTooltipOpen] = useState(false)
  const toggle = () => setTooltipOpen(!tooltipOpen)
  const tooltipId = id ?? `${field.name}-tooltiptarget`

  return tooltip && label ? (
    <>
      <Tooltip
        placement="top"
        isOpen={tooltipOpen}
        autohide={false}
        target={tooltipId}
        toggle={toggle}
      >
        {tooltip}
      </Tooltip>
      <Label htmlFor={field.name} id={tooltipId} style={{ borderBottom: '1px dotted black' }}>
        {label}
      </Label>
    </>
  ) : (
    <Label htmlFor={field.name}>{label}</Label>
  )
}

SaasBoostTooltippedLabel.propTypes = {
  field: PropTypes.object,
  label: PropTypes.string,
  tooltip: PropTypes.string,
  id: PropTypes.string,
}
