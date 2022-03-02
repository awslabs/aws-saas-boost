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

const ServiceSettingsSubform = (props) => {
  const { formikErrors, formikService, osOptions, isLocked, serviceIndex } = props
  const getWinServerOptions = () => {
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
    return formikService.operatingSystem === 'WINDOWS' && osOptions ? (
      <FormGroup>
        <SaasBoostSelect
          type="select"
          name="windowsVersion"
          id="windowsVersion"
          label="Windows Server Version"
        >
          <option value="">Select One...</option>
          {options}
        </SaasBoostSelect>
      </FormGroup>
    ) : null
  }

  // Normally we'd let formik handle this, but we also need to change the fylesystem type
  // based on the container OS
//  const onOperatingSystemChange = (val, serviceName) => {
//    const os = val?.target?.value
//    props.formik.setFieldValue(serviceName + '.operatingSystem', os)
//    if (os === 'WINDOWS') {
//      props.formik.setFieldValue(serviceName + '.filesystem.fileSystemType', 'FSX')
//    }
//    if (os === 'LINUX') {
//      props.formik.setFieldValue(serviceName + '.filesystem.fileSystemType', 'EFS')
//    }
//  }

// onChange={(val) => onOperatingSystemChange(val, "services[" + serviceIndex + "]")}
// onChange={(val) => onOperatingSystemChange(val, "services[" + serviceIndex + "]")}

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
                        name={"services[" + serviceIndex + "].public"}
                        id={"services[" + serviceIndex + "].public"}
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
                        id="inline-radio1"

                        name={"services[" + serviceIndex + "].operatingSystem"}
                        value="LINUX"
                        disabled={isLocked}
                      />
                      <Label className="form-check-label" check htmlFor="inline-radio1">
                        <i className="fa fa-linux"></i> Linux
                      </Label>
                    </FormGroup>
                    <FormGroup check inline>
                      <Field
                        className="form-check-input"
                        type="radio"
                        id="inline-radio2"

                        name={"services[" + serviceIndex + "].operatingSystem"}
                        value="WINDOWS"
                        disabled={isLocked}
                      />
                      <Label className="form-check-label" check htmlFor="inline-radio2">
                        <i className="fa fa-windows"></i> Windows
                      </Label>
                    </FormGroup>
                    <FormFeedback
                      invalid={
                        formikErrors.operatingSystem
                          ? formikErrors.operatingSystem
                          : undefined
                      }
                    >
                      {formikErrors.operatingSystem}
                    </FormFeedback>
                  </FormGroup>
                  {getWinServerOptions()}
                </Col>
                <Col xs={6}>
                  <SaasBoostInput
                    key={"services[" + serviceIndex + "].containerRepo"}
                    label="Container Repo"
                    name={"services[" + serviceIndex + "].containerRepo"}
                    type="text"
                    value=""
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
  formikService: PropTypes.object,
  formikErrors: PropTypes.object,
  serviceIndex: PropTypes.number,
}

export default React.memo(ServiceSettingsSubform)
