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
import {
  Row,
  Col,
  Card,
  CardBody,
  CardHeader,
} from 'reactstrap'
import {
  SaasBoostCheckbox,
} from '../../components/FormComponents'
import 'rc-slider/assets/index.css'


export default class ObjectStoreSubform extends React.Component {
  render() {
    return (
      <Fragment>
        <Row className="mt-3">
          <Col xs={12}>
            <Card>
              <CardHeader>Object Storage</CardHeader>
              <CardBody>
                <SaasBoostCheckbox
                  id={this.props.formikServicePrefix + '.provisionObjectStorage'}
                  name={this.props.formikServicePrefix + '.provisionObjectStorage'}
                  disabled={this.props.isLocked}
                  label="Provision an S3 bucket for the application."
                  tooltip="If selected, an S3 bucket will be created on submission and provided as environment variables to the application."
                />
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Fragment>
    )
  }
}

ObjectStoreSubform.propTypes = {
  isLocked: PropTypes.bool,
  formikServicePrefix: PropTypes.string,
}