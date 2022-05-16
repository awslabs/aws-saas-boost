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
import { Button, Card, CardHeader, CardFooter, CardBody, Row, Col, Alert } from 'reactstrap'
import { SaasBoostInput, SaasBoostCheckbox } from '../components/FormComponents'
import * as Yup from 'yup'

const initialUser = {
  username: '',
  firstName: '',
  lastName: '',
  email: '',
}

export const UserFormComponent = ({
  handleSubmit,
  handleCancel,
  user = initialUser,
  error,
  handleError,
}) => {
  const mutableUser = {
    ...user,
    emailVerified: false,
  }
  console.log(mutableUser)
  return (
    <Formik
      initialValues={mutableUser}
      onSubmit={handleSubmit}
      validationSchema={Yup.object({
        username: Yup.string().max(25, 'Must be 25 characters or less.').required('Required'),
        firstName: Yup.string().max(25, 'Must be 50 characters or less.').required('Required'),
        lastName: Yup.string().max(25, 'Must be 50 characters or less.').required('Required'),
        email: Yup.string()
          .email('Enter a valid email address')
          .max(255, 'Must be 255 characters or less.')
          .required('Required'),
        emailVerified: Yup.bool(),
      })}
    >
      {(props) => (
        <Form>
          <div className="animated fadeIn">
            <Row>
              <Col md={6}>
                {!!error && (
                  <Alert color="danger" isOpen={!!error} toggle={handleError}>
                    <h4 className="alert-heading">Error</h4>
                    <p>{error}</p>
                  </Alert>
                )}
              </Col>
            </Row>
            <Row>
              <Col md={6}>
                <Card>
                  <CardHeader>User Details</CardHeader>
                  <CardBody>
                    <SaasBoostInput
                      label="Username"
                      name="username"
                      type="text"
                      disabled={!!mutableUser.sub}
                    />
                    <Row>
                      <Col md={6}>
                        <SaasBoostInput label="First Name" name="firstName" type="text" />
                      </Col>
                      <Col md={6}>
                        <SaasBoostInput label="Last Name" name="lastName" type="text" />
                      </Col>
                    </Row>

                    <Row>
                      <Col>
                        <SaasBoostInput label="Email" name="email" type="email" />
                        {!mutableUser.created && (
                          <SaasBoostCheckbox
                            id="emailVerified"
                            name="emailVerified"
                            label="Mark email as verified for this user"
                            value={props.values?.emailVerified}
                          />
                        )}
                      </Col>
                    </Row>
                  </CardBody>
                  <CardFooter>
                    <Button color="danger" className="mr-2" onClick={handleCancel}>
                      Cancel
                    </Button>
                    <Button color="primary" type="Submit" disabled={props.isSubmitting}>
                      {props.isSubmitting ? 'Saving...' : 'Submit'}
                    </Button>
                  </CardFooter>
                </Card>
              </Col>
            </Row>
          </div>
        </Form>
      )}
    </Formik>
  )
}

UserFormComponent.propTypes = {
  user: PropTypes.object,
  error: PropTypes.string,
  values: PropTypes.object,
  handleSubmit: PropTypes.func,
  handleCancel: PropTypes.func,
  handleError: PropTypes.func,
  isSubmitting: PropTypes.bool,
}
