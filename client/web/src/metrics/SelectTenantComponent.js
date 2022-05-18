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
import { Input, Label, FormGroup } from 'reactstrap'

SelectTenantComponent.propTypes = {
  tenants: PropTypes.array,
  selectedTenant: PropTypes.object,
  selectTenant: PropTypes.func,
}

export default function SelectTenantComponent(props) {
  const { tenants, selectedTenant, selectTenant } = props
  const _selectTenant = (e) => {
    if (e.target.value !== selectedTenant) {
      selectTenant(e.target.value)
    }
  }
  let tenantOptions = []
  tenants.forEach((t) => {
    tenantOptions.push(
      <option value={t.id} key={t.id}>
        {t.name}
      </option>,
    )
  })
  return (
    <FormGroup className="form-inline mb-0">
      <Label htmlFor="stpcTenant" className="mr-2">Tenant: </Label>
      <div>
        <Input
          type="select"
          name="stpcTenant"
          id="stpcTenant"
          onChange={_selectTenant}
          defaultValue=""
        >
          <option value="">-- All Tenants --</option>
          {tenantOptions}
        </Input>
      </div>
    </FormGroup>
  )
}
