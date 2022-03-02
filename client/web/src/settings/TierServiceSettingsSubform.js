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

import React, { useState } from 'react'
import { Row, Col, Card, CardBody, CardHeader, FormGroup, Label, FormFeedback } from 'reactstrap'
import { Field } from 'formik'
import { SaasBoostSelect, SaasBoostCheckbox, SaasBoostInput } from '../components/FormComponents'
import {
  CDropdown,
  CDropdownHeader,
  CDropdownItem,
  CDropdownMenu,
  CDropdownToggle,
} from '@coreui/react'
import { PropTypes } from 'prop-types'
import FileSystemSubform from './FileSystemSubform'
import DatabaseSubform from './DatabaseSubform'

const TierServiceSettingsSubform = (props) => {
  const { isLocked, formikServicePrefix, serviceIndex, tiers, dbOptions, onFileSelected, serviceValues } = props

  const [selectedTier, setSelectedTier] = useState(tiers[0])

  // TODO we have a tierNames list we get from the tier service?
  // TODO does that need to be controlled by the ApplicationContainer.. wherever the initialValues are set?
  // TODO I think we can override initialValues as we go along
  // TODO the only important thing is that it's set going back

  return (
    <>
      <Row>
        <Col xs={12}>
          <Card>
            <CardHeader>
              <CDropdown>
                <CDropdownToggle>{selectedTier}</CDropdownToggle>
                <CDropdownMenu>
                  {tiers.map((tierName) => (
                  <CDropdownItem
                    onClick={() => setSelectedTier(tierName)}
                    key={formikServicePrefix + '-' + tierName}
                  >
                    {tierName}
                  </CDropdownItem>
                  ))}
                </CDropdownMenu>
              </CDropdown>
            </CardHeader>
            <CardBody>
              <Row>
                <Col xs={6}>
                  <SaasBoostSelect
                    type="select"
                    name={formikServicePrefix + ".tiers[" + selectedTier + "].computeSize"}
                    id={formikServicePrefix + ".tiers[" + selectedTier + "].computeSize"}
                    label="Compute Size"
                  >
                    <option value="">Select One...</option>
                    <option value="S">Small</option>
                    <option value="M">Medium</option>
                    <option value="L">Large</option>
                    <option value="XL">X-Large</option>
                  </SaasBoostSelect>
                  <Row>
                    <Col>
                      <SaasBoostInput
                        key={formikServicePrefix + ".tiers[" + selectedTier + "].min"}
                        label="Minimum Instance Count"
                        name={formikServicePrefix + ".tiers[" + selectedTier + "].min"}
                        type="number"
                      />
                    </Col>
                    <Col>
                      <SaasBoostInput
                        key={formikServicePrefix + ".tiers[" + selectedTier + "].max"}
                        label="Maximum Instance Count"
                        name={formikServicePrefix + ".tiers[" + selectedTier + "].max"}
                        type="number"
                      />
                    </Col>
                  </Row>
                </Col>
              </Row>
              <Row>
                <Col>
                  <FileSystemSubform
                    isLocked={isLocked}
                    formikTierPrefix={formikServicePrefix + ".tiers[" + selectedTier + "]"}
                    filesystem={serviceValues?.tiers[selectedTier]?.filesystem}
                    provisionFs={serviceValues?.tiers[selectedTier]?.provisionFS}
                    containerOs={serviceValues?.operatingSystem}
                    setFieldValue={props.setFieldValue}
                  ></FileSystemSubform>
                  <DatabaseSubform
                    isLocked={isLocked}
                    formikTierPrefix={formikServicePrefix + ".tiers[" + selectedTier + "]"}
                    dbOptions={dbOptions}
                    provisionDb={serviceValues?.tiers[selectedTier]?.provisionDb}
                    values={serviceValues?.tiers[selectedTier]?.database}
                    onFileSelected={(file) => onFileSelected(serviceValues?.tiers[selectedTier]?.database, file)}
                  ></DatabaseSubform>
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
    </>
  )
}

TierServiceSettingsSubform.propTypes = {
  serviceValues: PropTypes.object,
  isLocked: PropTypes.bool,
  serviceIndex: PropTypes.number,
  formikServiceNamePrefix: PropTypes.string,
  onFileSelected: PropTypes.func,
  setFieldValue: PropTypes.func,
}

export default TierServiceSettingsSubform