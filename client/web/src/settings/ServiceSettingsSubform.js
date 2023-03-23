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
import { Row, Col, Card, CardBody, CardHeader } from 'reactstrap'
import { SaasBoostCheckbox, SaasBoostInput, SaasBoostTextarea } from '../components/FormComponents'
import { PropTypes } from 'prop-types'
import DatabaseSubform from './DatabaseSubform'
import ObjectStoreSubform from './components/ObjectStoreSubform'
import FileSystemSubform from './components/filesystem/FileSystemSubform'
import ComputeSubform from './components/compute/ComputeSubform'

const ServiceSettingsSubform = (props) => {
  const { serviceValues, osOptions, dbOptions, serviceName, onFileSelected, isLocked, serviceIndex } = props

  return (
    <>
      <Row>
        <Col xs={12}>
          <Card>
            <CardHeader>Service Settings</CardHeader>
            <CardBody>
              <Row>
                <Col xs={6}>
                  <Row>
                    <Col>
                      <SaasBoostInput
                        key={"services[" + serviceIndex + "].name"}
                        label="Service Name"
                        name={"services[" + serviceIndex + "].name"}
                        type="text"
                        autoFocus
                      />
                    </Col>
                  </Row>
                  <Row>
                    <Col>
                      <SaasBoostInput
                        key={"services[" + serviceIndex + "].path"}
                        label="Service Addressable Path"
                        name={"services[" + serviceIndex + "].path"}
                        type="text"
                      />
                    </Col>
                    <Col className="d-flex align-items-center justify-content-center">
                      <SaasBoostCheckbox
                        key={"services[" + serviceIndex + "].public"}
                        name={"services[" + serviceIndex + "].public"}
                        label="Publicly accessible?"
                      />
                    </Col>
                  </Row>
                </Col>
                <Col xs={6}>
                  <Row>
                    <SaasBoostTextarea
                      key={"services[" + serviceIndex + "].description"}
                      label="Description"
                      name={"services[" + serviceIndex + "].description"}
                      type="text"
                      rows={4}
                    />
                  </Row>
                </Col>
              </Row>
              <Row>
                <Col>
                  <ComputeSubform
                    values={serviceValues?.compute}
                    osOptions={osOptions}
                    isLocked={isLocked}
                    formikServicePrefix={'services[' + serviceIndex + ']'}
                  />
                  <FileSystemSubform
                    isLocked={isLocked}
                    formikServicePrefix={'services[' + serviceIndex + ']'}
                    filesystem={serviceValues?.filesystem}
                    provisionFs={
                      serviceValues?.provisionFS
                    }
                    containerOs={serviceValues?.compute?.operatingSystem}
                    containerLaunchType={serviceValues?.compute?.ecsLaunchType}
                    filesystemType={serviceValues?.filesystemType}
                    setFieldValue={props.setFieldValue}
                  ></FileSystemSubform>
                  <DatabaseSubform
                    isLocked={isLocked}
                    formikServicePrefix={'services[' + serviceIndex + ']'}
                    dbOptions={dbOptions}
                    provisionDb={
                      serviceValues?.provisionDb
                    }
                    values={serviceValues?.database}
                    onFileSelected={(file) => onFileSelected(serviceName, file)}
                    setFieldValue={props.setFieldValue}
                  ></DatabaseSubform>
                  <ObjectStoreSubform
                    isLocked={isLocked}
                    formikServicePrefix={'services[' + serviceIndex + ']'}
                  ></ObjectStoreSubform>
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
    </>
  )
}

ServiceSettingsSubform.propTypes = {
  osOptions: PropTypes.object,
  isLocked: PropTypes.bool,
  dbOptions: PropTypes.array,
  onFileSelected: PropTypes.func,
  serviceValues: PropTypes.object,
  serviceIndex: PropTypes.number,
}

export default React.memo(ServiceSettingsSubform)
