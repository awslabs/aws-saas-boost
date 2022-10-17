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
import { Row, Col, Card, CardBody, CardHeader, FormGroup, Label, FormFeedback } from 'reactstrap'
import { Field } from 'formik'
import { SaasBoostSelect, SaasBoostCheckbox, SaasBoostInput, SaasBoostTextarea } from '../components/FormComponents'
import { PropTypes } from 'prop-types'
import { cibWindows, cibLinux } from '@coreui/icons'
import CIcon from '@coreui/icons-react'
import DatabaseSubform from './DatabaseSubform'

const ServiceSettingsSubform = (props) => {
  const { formikErrors, serviceValues, osOptions, dbOptions, serviceName, onFileSelected, isLocked, serviceIndex } = props
  const getWinServerOptions = (serviceIndex) => {
    if (!osOptions) {
      return null
    }
    const winKeys = Object.keys(osOptions).filter((key) => key.startsWith('WIN'))
    const options = winKeys.map((key) => {
      var desc = osOptions[key]
      return (
        <option value={key} key={key}>
          {desc}
        </option>
      )
    })
    return serviceValues?.operatingSystem === 'WINDOWS' && osOptions ? (
      <FormGroup>
        <SaasBoostSelect
          type="select"
          name={"services[" + serviceIndex + "].windowsVersion"}
          id={"windowsVersion-" + serviceIndex}
          label="Windows Server Version"
        >
          <option value="">Select One...</option>
          {options}
        </SaasBoostSelect>
      </FormGroup>
    ) : null
  }

  const operatingSystemFeedback = !!formikErrors.services ? formikErrors.services[serviceIndex]?.operatingSystem : undefined

  const getLaunchTypeOptions = (serviceIndex) => {
    return serviceValues?.operatingSystem === 'LINUX' && (
     <FormGroup>
       <div className="mb-2">Container Launch Type</div>
       <FormGroup check inline>
         <Field
           className="form-check-input"
           type="radio"
           id={"launchtype-ec2-" + serviceIndex}
           name={"services[" + serviceIndex + "].ecsLaunchType"}
           value="EC2"
           disabled={isLocked}
         />
         <Label className="form-check-label" check htmlFor={"launchtype-ec2-" + serviceIndex}>
           EC2
         </Label>
       </FormGroup>
       <FormGroup check inline>
         <Field
           className="form-check-input"
           type="radio"
           id={"launchtype-fargate-" + serviceIndex}
           name={"services[" + serviceIndex + "].ecsLaunchType"}
           value="FARGATE"
           disabled={isLocked}
         />
         <Label className="form-check-label" check htmlFor={"launchtype-fargate-" + serviceIndex}>
           Fargate
         </Label>
       </FormGroup>
       <FormFeedback
         invalid={
           formikErrors.ecsLaunchType
             ? formikErrors.ecsLaunchType
             : undefined
         }
       >
         {formikErrors.ecsLaunchType}
       </FormFeedback>
     </FormGroup>
    )
  }

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
                      />
                    </Col>
                    <Col className="d-flex align-items-center">
                      <SaasBoostCheckbox
                        key={"services[" + serviceIndex + "].public"}
                        name={"services[" + serviceIndex + "].public"}
                        label="Publicly accessible?"
                      />
                    </Col>
                  </Row>
                  <Row>
                    <SaasBoostInput
                      key={"services[" + serviceIndex + "].path"}
                      label="Service Addressable Path"
                      name={"services[" + serviceIndex + "].path"}
                      type="text"
                    />
                  </Row>
                  <Row>
                    <SaasBoostTextarea
                      key={"services[" + serviceIndex + "].description"}
                      label="Description"
                      name={"services[" + serviceIndex + "].description"}
                      type="text"
                    />
                  </Row>
                  <FormGroup>
                    <div className="mb-2">Container OS</div>
                    <FormGroup check inline>
                      <Field
                        className="form-check-input"
                        type="radio"
                        id={"os-linux-" + serviceIndex}
                        name={"services[" + serviceIndex + "].operatingSystem"}
                        value="LINUX"
                        disabled={isLocked}
                      />
                      <Label className="form-check-label" check htmlFor={"os-linux-" + serviceIndex}>
                        <CIcon icon={cibLinux} /> Linux
                      </Label>
                    </FormGroup>
                    <FormGroup check inline>
                      <Field
                        className="form-check-input"
                        type="radio"
                        id={"os-windows-" + serviceIndex}
                        name={"services[" + serviceIndex + "].operatingSystem"}
                        value="WINDOWS"
                        disabled={isLocked}
                      />
                      <Label className="form-check-label" check htmlFor={"os-windows-" + serviceIndex}>
                      <CIcon icon={cibWindows} /> Windows
                      </Label>
                    </FormGroup>
                    <FormFeedback
                      invalid={!!operatingSystemFeedback}
                    >
                      {operatingSystemFeedback}
                    </FormFeedback>
                  </FormGroup>
                  {getLaunchTypeOptions(serviceIndex)}
                  {getWinServerOptions(serviceIndex)}
                </Col>
                <Col xs={6}>
                  <SaasBoostInput
                    key={"services[" + serviceIndex + "].containerRepo"}
                    label="Container Repo"
                    name={"services[" + serviceIndex + "].containerRepo"}
                    type="text"
                    disabled={true}
                  />
                  <SaasBoostInput
                    key={"services[" + serviceIndex + "].containerTag"}
                    label="Container Tag"
                    name={"services[" + serviceIndex + "].containerTag"}
                    type="text"
                  />
                  <SaasBoostInput
                    key={"services[" + serviceIndex + "].containerPort"}
                    label="Container Port"
                    name={"services[" + serviceIndex + "].containerPort"}
                    type="number"
                    disabled={isLocked}
                  />
                  <SaasBoostInput
                    key={"services[" + serviceIndex + "].healthCheckUrl"}
                    label="Health Check URL"
                    name={"services[" + serviceIndex + "].healthCheckUrl"}
                    type="text"
                    description="Must be relative to root (eg. '/health.html')"
                    disabled={isLocked}
                  />
                </Col>
              </Row>
              <Row>
                <Col>
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
  formikErrors: PropTypes.object,
  serviceIndex: PropTypes.number,
}

export default React.memo(ServiceSettingsSubform)
