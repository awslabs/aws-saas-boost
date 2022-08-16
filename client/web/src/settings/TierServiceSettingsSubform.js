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
import { SaasBoostSelect, SaasBoostInput } from '../components/FormComponents'
import { Dropdown, Card, Row, Col } from 'react-bootstrap'
import { PropTypes } from 'prop-types'
import FileSystemSubform from './components/filesystem/FileSystemSubform'
import DatabaseTierSubform from './DatabaseTierSubform'

const TierServiceSettingsSubform = (props) => {
  const {
    tiers,
    isLocked,
    serviceValues,
    dbOptions,
    formikServicePrefix,
    defaultTier,
    setFieldValue
  } = props

  const [selectedTier, setSelectedTier] = useState(
    !!tiers && !!tiers[0] ? tiers[0].name : ''
  )

  const formikTierPrefix = formikServicePrefix + '.tiers[' + selectedTier + ']'

  if (!!serviceValues?.tiers) {
    // set compute size if default exists and this tier doesn't
    if (!!!serviceValues?.tiers[selectedTier]?.computeSize && !!serviceValues?.tiers[defaultTier]?.computeSize) {
      // set instance to default if it doesn't exist already
      setFieldValue(formikTierPrefix + '.computeSize', serviceValues?.tiers[defaultTier]?.computeSize)
    }

    // set min if default exists and this tier doesn't
    if (!!!serviceValues?.tiers[selectedTier]?.min && !!serviceValues?.tiers[defaultTier]?.min) {
      // set instance to default if it doesn't exist already
      setFieldValue(formikTierPrefix + '.min', serviceValues?.tiers[defaultTier]?.min)
    }

    // set max if default exists and this tier doesn't
    if (!!!serviceValues?.tiers[selectedTier]?.max && !!serviceValues?.tiers[defaultTier]?.max) {
      // set instance to default if it doesn't exist already
      setFieldValue(formikTierPrefix + '.max', serviceValues?.tiers[defaultTier]?.max)
    }
  }

  return (
    <>
      <Row>
        <Col>
          <Card className="mt-3">
            <Card.Header>
              <div className="d-flex align-items-center">
                <span className="mr-3">Tier settings:</span>
                <span>
                  <Dropdown>
                    <Dropdown.Toggle
                      variant="secondary"
                      style={{ color: 'white' }}
                    >
                      {selectedTier}
                    </Dropdown.Toggle>
                    <Dropdown.Menu>
                      {tiers.map((tier) => (
                        <Dropdown.Item
                          onClick={() => setSelectedTier(tier.name)}
                          key={formikServicePrefix + '-' + tier.name}
                        >
                          {tier.name}
                        </Dropdown.Item>
                      ))}
                    </Dropdown.Menu>
                  </Dropdown>
                </span>
              </div>
            </Card.Header>
            <Card.Body>
              <Row>
                <Col xs={6}>
                  <SaasBoostSelect
                    type="select"
                    name={formikTierPrefix + '.computeSize'}
                    id={formikTierPrefix + '.computeSize'}
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
                        key={formikTierPrefix + '.min'}
                        label="Minimum Instance Count"
                        name={formikTierPrefix + '.min'}
                        type="number"
                        min="0"
                      />
                    </Col>
                    <Col>
                      <SaasBoostInput
                        key={formikTierPrefix + '.max'}
                        label="Maximum Instance Count"
                        name={formikTierPrefix + '.max'}
                        type="number"
                        min="0"
                      />
                    </Col>
                  </Row>
                </Col>
              </Row>
              <Row>
                <Col>
                  <FileSystemSubform
                    isLocked={isLocked}
                    formikTierPrefix={formikTierPrefix}
                    filesystem={serviceValues?.tiers[selectedTier]?.filesystem}
                    provisionFs={
                      serviceValues?.tiers[selectedTier]?.provisionFS
                    }
                    containerOs={serviceValues?.operatingSystem}
                    containerLaunchType={serviceValues?.ecsLaunchType}
                    filesystemType={serviceValues?.tiers[selectedTier]?.filesystemType}
                    setFieldValue={props.setFieldValue}
                  ></FileSystemSubform>
                  <DatabaseTierSubform
                    serviceValues={serviceValues?.database}
                    defaultTierValues={serviceValues?.database?.tiers[defaultTier]}
                    tierValues={serviceValues?.database?.tiers[selectedTier]}
                    provisionDb={serviceValues?.provisionDb}
                    dbOptions={dbOptions}
                    formikDatabaseTierPrefix={formikServicePrefix + '.database.tiers[' + selectedTier + ']'}
                    isLocked={isLocked}
                    setFieldValue={props.setFieldValue}
                  ></DatabaseTierSubform>
                </Col>
              </Row>
            </Card.Body>
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
  setFieldValue: PropTypes.func,
  defaultTier: PropTypes.string,
}

export default TierServiceSettingsSubform
