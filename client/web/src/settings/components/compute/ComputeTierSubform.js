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
import { Row, Col, Card, CardBody, CardHeader } from 'reactstrap'
import { SaasBoostSelect, SaasBoostInput } from '../../../components/FormComponents'

export default class ComputeTierSubform extends React.Component {

  render() {
    let { values, defaultValues, formikComputeTierPrefix, setFieldValue, ec2AutoScaling } = this.props

    // TODO maybe default tier implementation should take effect on the backend?
    // TODO we should only require values for the default tier
    if (!!values && !!defaultValues) {
      // set compute size if default exists and this tier doesn't
      if (!!!values.computeSize && defaultValues.computeSize) {
        // set instance to default if it doesn't exist already
        setFieldValue(formikComputeTierPrefix + '.computeSize', defaultValues.computeSize)
      }
  
      // set min if default exists and this tier doesn't
      if (!!!values.min && !!defaultValues.min) {
        // set instance to default if it doesn't exist already
        setFieldValue(formikComputeTierPrefix + '.min', defaultValues.min)
      }
  
      // set max if default exists and this tier doesn't
      if (!!!values.max && !!defaultValues.max) {
        // set instance to default if it doesn't exist already
        setFieldValue(formikComputeTierPrefix + '.max', defaultValues?.max)
      }
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
                    <SaasBoostSelect
                      type="select"
                      name={formikComputeTierPrefix + '.computeSize'}
                      id={formikComputeTierPrefix + '.computeSize'}
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
                          key={formikComputeTierPrefix + '.min'}
                          label="Minimum Instance Count"
                          name={formikComputeTierPrefix + '.min'}
                          type="number"
                          min="0"
                        />
                      </Col>
                      <Col>
                        <SaasBoostInput
                          key={formikComputeTierPrefix + '.max'}
                          label="Maximum Instance Count"
                          name={formikComputeTierPrefix + '.max'}
                          type="number"
                          min="0"
                        />
                      </Col>
                    </Row>
                  </Col>
                  {!!ec2AutoScaling && (
                  <Col xs={6}>
                    <Row>
                      <Col>
                        <SaasBoostInput
                          key={formikComputeTierPrefix + '.ec2min'}
                          label="Minimum EC2 AutoScaling Instance Count"
                          name={formikComputeTierPrefix + '.ec2min'}
                          type="number"
                          min="0"
                        />
                      </Col>
                      <Col>
                        <SaasBoostInput
                          key={formikComputeTierPrefix + '.ec2max'}
                          label="Maximum EC2 AutoScaling Instance Count"
                          name={formikComputeTierPrefix + '.ec2max'}
                          type="number"
                          min="0"
                        />
                      </Col>
                    </Row>
                  </Col>
                  )}
                </Row>
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Fragment>
    )
  }
}
