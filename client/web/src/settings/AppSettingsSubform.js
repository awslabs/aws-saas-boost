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

import { SaasBoostInput } from '../components/FormComponents'

export default class AppSettingsSubform extends React.Component {
  render() {
    return (
      <Fragment>
        <Row>
          <Col lg={12} sm={12}>
            <Card>
              <CardHeader>Application</CardHeader>
              <CardBody>
                <Row>
                  <Col xs={6}>
                    <SaasBoostInput key="name" label="Name" name="name" type="text" />
                  </Col>
                  <Col xs={6}>
                    <SaasBoostInput
                      key="domainName"
                      label="Domain Name"
                      name="domainName"
                      type="text"
                      disabled={this.props.isLocked}
                    />
                    <SaasBoostInput
                      key="sslCertArn"
                      label="SSL Certificate ARN"
                      name="sslCertArn"
                      type="text"
                      disabled={this.props.isLocked}
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
}

AppSettingsSubform.propTypes = {
  isLocked: PropTypes.bool,
}
