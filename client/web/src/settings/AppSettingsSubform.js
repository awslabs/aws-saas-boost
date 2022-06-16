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
import React, { Fragment } from 'react'
import { Row, Col, Card, CardBody, CardHeader } from 'reactstrap'

import { SaasBoostInput, SaasBoostSelect } from '../components/FormComponents'

export default class AppSettingsSubform extends React.Component {

  certificateIdFromArn(arn) {
    let arnParts = arn.split('/')
    return arnParts[arnParts.length - 1]
  }

  render() {
    return (
      <Fragment>
        <Row className="mb-3">
          <Col lg={12} sm={12}>
            <Card>
              <CardHeader>Application</CardHeader>
              <CardBody>
                <Row>
                  <Col xs={6}>
                    <SaasBoostInput
                      key="name"
                      label="Name"
                      name="name"
                      type="text"
                    />
                  </Col>
                  <Col xs={6}>
                    <SaasBoostInput
                      key="domainName"
                      label="Domain Name"
                      name="domainName"
                      type="text"
                      disabled={this.props.isLocked}
                    />
                    <SaasBoostSelect
                      type="select"
                      name="sslCertificate"
                      id="sslCertificate"
                      label={(<a href={this.props.acmConsoleLink} target="new">SSL Certificate</a>)}
                      key="sslCertificate"
                      disabled={this.props.isLocked || this.props.options?.length == 0}
                    >
                      <option value="">{this.props.certOptions?.length == 0 ? "No Valid Certificates" : "Select One..."}</option>
                      {this.props.certOptions?.map((option) => (
                        <option
                          value={option.certificateArn}
                          key={option.certificateArn}
                        >
                          {option.domainName} (ACM id: {this.certificateIdFromArn(option.certificateArn)})
                        </option>
                      ))}
                    </SaasBoostSelect>
                  </Col>
                </Row>
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Fragment>
    )
  }
}

AppSettingsSubform.propTypes = {
  isLocked: PropTypes.bool,
  certOptions: PropTypes.array,
}
