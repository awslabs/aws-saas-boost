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
import React, { Fragment } from 'react'
import { Row, Col, Card, CardBody, CardHeader, FormGroup, Label } from 'reactstrap'
import {
  SaasBoostCheckbox,
  SaasBoostInput,
  SaasBoostSelect,
} from '../../../components/FormComponents'
import CIcon from '@coreui/icons-react'
import { cibWindows, cibLinux } from '@coreui/icons'
import { Field } from 'formik'

const ComputeSubform = (props) => {

  const { values, osOptions, isLocked, formikServicePrefix } = props

  const getOsSpecificOptions = () => {
    if (values?.operatingSystem === 'WINDOWS') {
      return getWinServerOptions()
    } else if (values?.operatingSystem === 'LINUX') {
      return getLaunchTypeOptions()
    }
    return null
  }

  const getWinServerOptions = () => {
    if (!osOptions) {
      return null
    }
    const winKeys = Object.keys(osOptions).filter((key) => key.startsWith('WIN'))
    const options = winKeys.map((key) => {
      var desc = osOptions[key]
      return (
        <option value={key} key={key} id={key}>
          {desc}
        </option>
      )
    })
    return values?.operatingSystem === 'WINDOWS' && osOptions ? (
      <FormGroup>
        <SaasBoostSelect
          type="select"
          name={formikServicePrefix + ".compute.windowsVersion"}
          id={formikServicePrefix + ".compute.windowsVersion"}
          label="Windows Server Version"
        >
          <option value="">Select One...</option>
          {options}
        </SaasBoostSelect>
      </FormGroup>
    ) : null
  }

  const getLaunchTypeOptions = () => {
    return values?.operatingSystem === 'LINUX' && (
     <FormGroup>
       <div className="mb-2">Container Launch Type</div>
       <FormGroup check inline>
         <Field
           className="form-check-input"
           type="radio"
           id={formikServicePrefix + ".compute.ecsLaunchType.ec2"}
           name={formikServicePrefix + ".compute.ecsLaunchType"}
           value="EC2"
           disabled={isLocked}
         />
         <Label className="form-check-label" check htmlFor={formikServicePrefix + ".compute.ecsLaunchType.ec2"}>
           EC2
         </Label>
       </FormGroup>
       <FormGroup check inline>
         <Field
           className="form-check-input"
           type="radio"
           id={formikServicePrefix + ".compute.ecsLaunchType.fargate"}
           name={formikServicePrefix + ".compute.ecsLaunchType"}
           value="FARGATE"
           disabled={isLocked}
         />
         <Label className="form-check-label" check htmlFor={formikServicePrefix + ".compute.ecsLaunchType.fargate"}>
           Fargate
         </Label>
       </FormGroup>
     </FormGroup>
    )
  }

  return (
    <Fragment>
      <Row className="mt-3">
        <Col xs={12}>
          <Card>
            <CardHeader>Compute</CardHeader>
            <CardBody>
              <Row>
                <Col xs={6}>
                  <Row>
                    <Col xs={6}>
                      <FormGroup>
                        <div className="mb-2">Container OS</div>
                        <FormGroup check inline>
                          <Field
                            className="form-check-input"
                            type="radio"
                            id={formikServicePrefix + ".compute.operatingSystem.linux"}
                            name={formikServicePrefix + ".compute.operatingSystem"}
                            value="LINUX"
                            disabled={isLocked}
                          />
                          <Label className="form-check-label" check htmlFor={formikServicePrefix + ".compute.operatingSystem.linux"}>
                            <CIcon icon={cibLinux} /> Linux
                          </Label>
                        </FormGroup>
                        <FormGroup check inline>
                          <Field
                            className="form-check-input"
                            type="radio"
                            id={formikServicePrefix + ".compute.operatingSystem.windows"}
                            name={formikServicePrefix + ".compute.operatingSystem"}
                            value="WINDOWS"
                            disabled={isLocked}
                          />
                          <Label className="form-check-label" check htmlFor={formikServicePrefix + ".compute.operatingSystem.windows"}>
                            <CIcon icon={cibWindows} /> Windows
                          </Label>
                        </FormGroup>
                      </FormGroup>
                    </Col>
                    <Col xs={6}>
                      {getOsSpecificOptions()}
                    </Col>
                  </Row>
                  <Row>
                    <Col className="d-flex align-items-center">
                      <SaasBoostCheckbox
                        key={formikServicePrefix + ".compute.ecsExecEnabled"}
                        name={formikServicePrefix + ".compute.ecsExecEnabled"}
                        label="Enable ECS Exec"
                        tooltip="Use ECS Exec to run commands in a tenant ECS container."
                        disabled={isLocked}
                      />
                    </Col>
                  </Row>
                  <Row>
                    <SaasBoostInput
                      key={formikServicePrefix + ".compute.healthCheckUrl"}
                      label="Health Check URL"
                      name={formikServicePrefix + ".compute.healthCheckUrl"}
                      type="text"
                      description="Must be relative to root (eg. '/health.html')"
                      disabled={isLocked}
                    />
                  </Row>
                </Col>
                <Col xs={6}>
                  <SaasBoostInput
                    key={formikServicePrefix + ".compute.containerRepo"}
                    label="Container Repo"
                    name={formikServicePrefix + ".compute.containerRepo"}
                    type="text"
                    disabled={true}
                  />
                  <SaasBoostInput
                    key={formikServicePrefix + ".compute.containerTag"}
                    label="Container Tag"
                    name={formikServicePrefix + ".compute.containerTag"}
                    type="text"
                  />
                  <SaasBoostInput
                    key={formikServicePrefix + ".compute.containerPort"}
                    label="Container Port"
                    name={formikServicePrefix + ".compute.containerPort"}
                    type="number"
                    disabled={isLocked}
                  />
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
    </Fragment>
  )
}

export default ComputeSubform