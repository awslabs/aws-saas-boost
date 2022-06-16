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
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import { Row, Col, Card, Button, Alert } from 'react-bootstrap'
import { SaasBoostCheckbox, SaasBoostInput, SaasBoostTextarea } from '../components/FormComponents'

const initialTier = {
  id: null,
  name: '',
  description: '',
  defaultTier: false,
}

const TierForm = (props) => {
  const {
    handleSubmit,
    handleCancel,
    tier = initialTier,
    error,
    dismissError,
  } = props

  const showError = (error, dismissError) => {
    if (!!error) {
      return (
        <Row>
          <Col md={6}>
            <Alert
              color="danger"
              isOpen={!!error}
              toggle={() => dismissError()}
            >
              <h4 className="alert-heading">Error</h4>
              <p>{error}</p>
            </Alert>
          </Col>
        </Row>
      )
    }
    return undefined
  }

  return (
    <Formik
      initialValues={tier}
      enableReinitialize={true}
      validationSchema={Yup.object({
        name: Yup.string()
          .max(100, 'Must be 100 characters or less.')
          .required('Required'),
        description: Yup.string().max(100, 'Must be 100 characters or less.'),
        defaultTier: Yup.boolean().required()
      })}
      onSubmit={handleSubmit}
    >
      {(formik) => (
        <Form>
          {tier.id && (
            <input
              type="hidden"
              name="id"
              id="id"
              value={tier.id}
            />
          )}
          <div className="animated fadeIn">
            {showError(error, dismissError)}
            <Row>
              <Col lg={6}>
                <Card>
                  <Card.Header>Tier Details</Card.Header>
                  <Card.Body>
                    <SaasBoostInput label="Name" name="name" type="text" />
                    <SaasBoostTextarea label="Description" name="description" type="text" />
                    <SaasBoostCheckbox label="Mark as Default" name="defaultTier" tooltip="If a non-default tier is missing any required application configuration, the default tier's application configuration will be used." />
                  </Card.Body>
                  <Card.Footer>
                    <Button variant="danger" onClick={handleCancel}>
                      Cancel
                    </Button>
                    <Button
                      className="ml-2"
                      variant="primary"
                      type="Submit"
                      disabled={formik.isSubmitting}
                    >
                      {formik.isSubmitting ? 'Saving...' : 'Submit'}
                    </Button>
                  </Card.Footer>
                </Card>
              </Col>
            </Row>
          </div>
        </Form>
      )}
    </Formik>
  )
}

TierForm.propTypes = {
  handleSubmit: PropTypes.func,
  handleCancel: PropTypes.func,
  dismissError: PropTypes.func,
  tier: PropTypes.object,
  error: PropTypes.string,
  config: PropTypes.object,
  plans: PropTypes.array,
}

export default TierForm
