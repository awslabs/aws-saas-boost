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
import { SaasBoostSelect, SaasBoostInput } from '../components/FormComponents'
import { PropTypes } from 'prop-types'

const ContainerSettingsSubform = (props) => {
  const { osOptions, isLocked } = props
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
    return props.formik.values.operatingSystem === 'WINDOWS' && osOptions ? (
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
  const onOperatingSystemChange = (val) => {
    const os = val?.target?.value
    props.formik.setFieldValue('operatingSystem', os)
    if (os === 'WINDOWS') {
      props.formik.setFieldValue('filesystem.fileSystemType', 'FSX')
    }
    if (os === 'LINUX') {
      props.formik.setFieldValue('filesystem.fileSystemType', 'EFS')
    }
  }

  return (
    <>
      <Row>
        <Col xs={12}>
          <Card>
            <CardHeader>Container Settings</CardHeader>
            <CardBody>
              <Row>
                <Col xs={6}>
                  <SaasBoostSelect
                    type="select"
                    name="computeSize"
                    id="computeSize"
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
                        key="minCount"
                        label="Minimum Instance Count"
                        name="minCount"
                        type="number"
                      />
                    </Col>
                    <Col>
                      <SaasBoostInput
                        key="maxCount"
                        label="Maximum Instance Count"
                        name="maxCount"
                        type="number"
                      />
                    </Col>
                  </Row>
                  <FormGroup>
                    <div className="mb-2">Container OS</div>
                    <FormGroup check inline>
                      <Field
                        className="form-check-input"
                        type="radio"
                        id="inline-radio1"
                        onChange={onOperatingSystemChange}
                        name="operatingSystem"
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
                        onChange={onOperatingSystemChange}
                        name="operatingSystem"
                        value="WINDOWS"
                        disabled={isLocked}
                      />
                      <Label className="form-check-label" check htmlFor="inline-radio2">
                        <i className="fa fa-windows"></i> Windows
                      </Label>
                    </FormGroup>
                    <FormFeedback
                      invalid={
                        props.formik.errors.operatingSystem
                          ? props.formik.errors.operatingSystem
                          : undefined
                      }
                    >
                      {props.formik.errors.operatingSystem}
                    </FormFeedback>
                  </FormGroup>
                  {getWinServerOptions()}
                </Col>
                <Col xs={6}>
                  <SaasBoostInput
                    key="containerPort"
                    label="Container Port"
                    name="containerPort"
                    type="number"
                    id="containerPort"
                    tooltip="The port on which the container listens"
                    disabled={isLocked}
                  />
                  <SaasBoostInput
                    key="healthCheckURL"
                    label="Health Check URL"
                    name="healthCheckURL"
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

ContainerSettingsSubform.propTypes = {
  osOptions: PropTypes.object,
  isLocked: PropTypes.bool,
  formik: PropTypes.object,
}

export default ContainerSettingsSubform
