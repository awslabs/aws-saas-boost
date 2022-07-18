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
import {
  SaasBoostSelect,
  SaasBoostInput,
  SaasBoostCheckbox,
  SaasBoostFileUpload,
} from '../components/FormComponents'

export default class DatabaseSubform extends React.Component {
  getEngineOptions() {
    const options = this.props.dbOptions?.map((engine) => {
      return (
        <option value={engine.engine} key={engine.engine}>
          {engine.description}
        </option>
      )
    })
    return options
  }

  getVersionOptions() {
    const engineVal = this.props.values?.engine
    const engine = this.props.dbOptions?.find((en) => en.engine === engineVal)
    if (!engine) {
      return null
    }
    const versions = engine?.versions
    const options = versions.map((version) => {
      return (
        <option value={version.version} key={version.version}>
          {version.description}
        </option>
      )
    })
    return options
  }

  versionChanged = (event) => {
    const v = event.target.value
    const engineVal = this.props.values?.engine
    const engine = this.props.dbOptions?.find((en) => en.engine === engineVal)
    if (!engine) {
      return null
    }
    const version = engine.versions.find((ver) => ver.version === v)
    this.props.values.version = v
    this.props.values.family = version.family
    this.props.setFieldValue(event.target.attributes.name.value, v)
  }

  render() {
    return (
      <Fragment>
        <Row className="mt-3">
          <Col xs={12}>
            <Card>
              <CardHeader>Database</CardHeader>
              <CardBody>
                <SaasBoostCheckbox
                  name={this.props.formikServicePrefix + '.provisionDb'}
                  id={this.props.formikServicePrefix + '.provisionDb'}
                  value={!!this.props?.provisionDb ? true : false}
                  label="Provision a database for this service"
                />
                {this.props.provisionDb && (
                  <Row>
                    <Col xl={6}>
                      <SaasBoostSelect
                        label="Engine"
                        name={this.props.formikServicePrefix + '.database.engine'}
                        id={this.props.formikServicePrefix + '.database.engine'}
                        value={this.props.values?.engine}
                        disabled={this.props.isLocked}
                      >
                        <option value="">Please select</option>
                        {this.getEngineOptions()}
                      </SaasBoostSelect>
                      <SaasBoostSelect
                        disabled={
                          !!!this.props.values?.engine || this.props.isLocked
                        }
                        onChange={this.versionChanged}
                        label="Version"
                        name={this.props.formikServicePrefix + '.database.version'}
                        id={this.props.formikServicePrefix + '.database.version'}
                        value={this.props.values?.version}
                      >
                        <option value="">Please select</option>
                        {this.getVersionOptions()}
                      </SaasBoostSelect>
                      <SaasBoostInput
                        key={this.props.formikServicePrefix + '.database.username'}
                        label="Username"
                        name={
                          this.props.formikServicePrefix + '.database.username'
                        }
                        type="text"
                        disabled={this.props.isLocked}
                      />
                      <SaasBoostInput
                        key={this.props.formikServicePrefix + '.database.password'}
                        label="Password"
                        name={
                          this.props.formikServicePrefix + '.database.password'
                        }
                        type="password"
                        disabled={this.props.isLocked}
                      />
                    </Col>
                    <Col xl={6}>
                      <Card>
                        <CardHeader>
                          {' '}
                          Database Initialization (Optional){' '}
                        </CardHeader>
                        <CardBody>
                          <SaasBoostInput
                            key={
                              this.props.formikServicePrefix + '.database.database'
                            }
                            label="Database Name"
                            name={
                              this.props.formikServicePrefix + '.database.database'
                            }
                            type="text"
                            disabled={this.props.isLocked}
                          />
                          <SaasBoostFileUpload
                            fileMask=".sql"
                            disabled={!this.props.values || this.props.isLocked}
                            label="Please select or drop a .sql file that will be used to initialize your database"
                            onFileSelected={this.props.onFileSelected}
                            fname={this.props.values?.bootstrapFilename}
                          />
                        </CardBody>
                      </Card>
                    </Col>
                  </Row>
                )}
                {this.props.provisionDb && this.props.dbOptions?.loading && (
                  <div>Loading ....</div>
                )}
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Fragment>
    )
  }
}

DatabaseSubform.propTypes = {
  dbOptions: PropTypes.array,
  values: PropTypes.object,
  provisionDb: PropTypes.bool,
  isLocked: PropTypes.bool,
  onFileSelected: PropTypes.func,
}
