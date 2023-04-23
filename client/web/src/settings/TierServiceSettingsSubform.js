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
import { Dropdown, Card, Row, Col } from 'react-bootstrap'
import { PropTypes } from 'prop-types'
import FileSystemTierSubform from './components/filesystem/FileSystemTierSubform'
import DatabaseTierSubform from './DatabaseTierSubform'
import ComputeTierSubform from './components/compute/ComputeTierSubform'
import { isEC2AutoScalingRequired } from './components/compute'

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

  const [selectedTier, setSelectedTier] = useState(defaultTier)
  const ec2AutoScaling = isEC2AutoScalingRequired(serviceValues?.compute?.operatingSystem, serviceValues?.compute?.ecsLaunchType)

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
                <Col>
                  <ComputeTierSubform 
                    values={serviceValues?.compute?.tiers[selectedTier]}
                    defaultValues={serviceValues?.compute?.tiers[defaultTier]}
                    formikComputeTierPrefix={formikServicePrefix + '.compute.tiers[' + selectedTier + ']'}
                    setFieldValue={setFieldValue}
                    ec2AutoScaling={ec2AutoScaling}
                  />
                  <FileSystemTierSubform
                    isLocked={isLocked}
                    formikFilesystemTierPrefix={formikServicePrefix + '.filesystem.tiers[' + selectedTier + ']'}
                    defaultFilesystem={serviceValues?.filesystem?.tiers[defaultTier]}
                    filesystem={serviceValues?.filesystem?.tiers[selectedTier]}
                    provisionFs={serviceValues?.provisionFS}
                    containerOs={serviceValues?.compute?.operatingSystem}
                    containerLaunchType={serviceValues?.compute?.ecsLaunchType}
                    filesystemType={serviceValues?.filesystemType}
                    setFieldValue={props.setFieldValue}
                  ></FileSystemTierSubform>
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
