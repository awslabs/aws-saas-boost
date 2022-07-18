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
import {SaasBoostSelect} from '../components/FormComponents'

export default class DatabaseTierSubform extends React.Component {

  getInstanceOptions() {
    const engineVal = this.props.serviceValues?.engine
    const versionVal = this.props.serviceValues?.version
    const engine = this.props.dbOptions?.find((en) => en.engine === engineVal)
    if (!engine || !versionVal) {
      return null
    }
    const version = engine.versions.find((ver) => ver.version === versionVal)
    if (!version) {
      return null
    }
    const instances = version.instances
    const options = instances?.map((instance) => {
      return (
        <option value={instance.instance} key={instance.instance}>
          {instance.class} ({instance.description})
        </option>
      )
    })
    return options
  }

  render() {

    if (this.props.provisionDb && !!!this.props.tierValues?.instance && !!this.props.defaultTierValues?.instance) {
      // set instance to default if it doesn't exist already
      this.props.setFieldValue(this.props.formikDatabaseTierPrefix + '.instance', this.props.defaultTierValues?.instance)
    }

    return (
      <Fragment>
        {this.props.provisionDb && (
        <Row className="mt-3">
          <Col xs={12}>
            <Card>
              <CardHeader>Database</CardHeader>
              <CardBody>
                  <Row>
                    <Col xl={6}>
                      <SaasBoostSelect
                        disabled={
                          !!!this.props.serviceValues?.version || this.props.isLocked
                        }
                        label="Instance"
                        name={
                          this.props.formikDatabaseTierPrefix + '.instance'
                        }
                        id={this.props.formikDatabaseTierPrefix + '.instance'}
                      >
                        <option value="">Please select</option>
                        {this.getInstanceOptions()}
                      </SaasBoostSelect>
                    </Col>
                  </Row>
                {this.props.provisionDb && this.props.dbOptions?.loading && (
                  <div>Loading ....</div>
                )}
              </CardBody>
            </Card>
          </Col>
        </Row>)}
      </Fragment>
    )
  }
}

DatabaseTierSubform.propTypes = {
  values: PropTypes.object,
  provisionDb: PropTypes.bool,
  isLocked: PropTypes.bool,
  setFieldValue: PropTypes.func,
}
