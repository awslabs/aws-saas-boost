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
import React from 'react'
import { Formik } from 'formik'
import * as Yup from 'yup'
import { SaasBoostInput } from '../components/FormComponents'
import { Card, CardBody, CardHeader, Col, Row } from 'reactstrap'

import config from '../config/appConfig'

export const SettingsFormComponent = (props) => {
  let initialValues = {}
  let validationSpecs = {}

  const { settings, updateSettings, loading } = props

  const readOnlySettings = []
  const editableSettings = []
  settings.map((setting) => {
    if (setting.readOnly) {
      readOnlySettings.push(
        <SaasBoostInput
          key={setting.name}
          label={setting.name}
          name={setting.name}
          value={setting.value}
          type="text"
          disabled={setting.readOnly}
        />,
      )
    } else {
      initialValues = {
        ...initialValues,
        [setting.name]: setting.value,
      }
      validationSpecs = {
        ...validationSpecs,
        [setting.name]: Yup.string().required(),
      }
      editableSettings.push(
        <SaasBoostInput key={setting.name} label={setting.name} name={setting.name} type="text" />,
      )
    }

    return {}
  })
  readOnlySettings.unshift(
    <SaasBoostInput
      key="API_URL"
      label="API_URL"
      name="API_URL"
      value={config.apiUri}
      type="text"
      disabled={true}
    />,
  )

  const validationSchema = Yup.object(validationSpecs)
  return (
    <div className="animated fadeIn">
      {loading === 'pending' && (
        <Row>
          <Col sm={12}>Loading...</Col>
        </Row>
      )}
      {loading === 'idle' && (
        <Formik
          enableReinitialize={true}
          onSubmit={updateSettings}
          initialValues={initialValues}
          validationSchema={validationSchema}
        >
          {(props) => (
            <Row>
              <Col lg={6}>
                <Card>
                  <CardHeader>
                    <i className="fa fa-gears" />
                    Settings
                  </CardHeader>
                  <CardBody>
                    <p>System wide settings displayed here for your reference.</p>
                    <>{readOnlySettings}</>
                  </CardBody>
                </Card>
              </Col>
            </Row>
          )}
        </Formik>
      )}
    </div>
  )
}

SettingsFormComponent.propTypes = {
  settings: PropTypes.array,
  updateSettings: PropTypes.func,
  loading: PropTypes.bool,
}

export default SettingsFormComponent
