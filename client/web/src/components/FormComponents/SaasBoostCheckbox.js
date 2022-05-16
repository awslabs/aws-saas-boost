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
import { useField } from 'formik'
import { PropTypes } from 'prop-types'
import { FormGroup, Input } from 'reactstrap'
import { SaasBoostTooltippedLabel } from './SaasBoostTooltippedLabel'

export const SaasBoostCheckbox = ({ label, value, tooltip, disabled, ...props }) => {
  const [field] = useField(props)

  return (
    <FormGroup check className="checkbox">
      <Input
        id={field.name}
        className="form-check-input"
        disabled={disabled}
        type="checkbox"
        {...field}
        value={value}
        checked={field.value}
      />
      <SaasBoostTooltippedLabel
        field={field}
        tooltip={tooltip}
        label={label}
        check
        className="form-check-label"
        htmlFor={field.name}
      />
    </FormGroup>
  )
}

SaasBoostCheckbox.propTypes = {
  label: PropTypes.string,
  value: PropTypes.bool,
  tooltip: PropTypes.string,
  disabled: PropTypes.bool,
}
