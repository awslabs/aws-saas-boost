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

import React from 'react'
import SaasBoostAuthComponent from './SaasBoostAuthComponent'
import { Card, CardBody, Col, Container, Row, Button, CardFooter } from 'reactstrap'
import * as Yup from 'yup'
import { Formik, Form as FormikForm } from 'formik'
import { Auth } from 'aws-amplify'

export class SaasBoostForgotPassword extends SaasBoostAuthComponent {
  _initialState = { delivery: null, error: null, username: null }
  constructor(props) {
    super(props)
    this._validAuthStates = ['forgotPassword']

    this.state = this._initialState
    this.submit = this.submit.bind(this)
  }

  submit(values, { setSubmitting, resetForm }) {
    const { username } = values || this.state.username

    this.setState({ error: null })
    if (!Auth || typeof Auth.forgotPassword !== 'function') {
      throw new Error('No Auth module found, please ensure @aws-amplify/auth is imported')
    }

    Auth.forgotPassword(username)
      .then((data) => {
        this.setState({ delivery: data.CodeDeliveryDetails, username })
      })
      .catch((err) => {
        this.setState({ error: err })
        resetForm()
        this.error(err)
      })
      .finally(() => {
        setSubmitting(false)
        this.changeState('resetPassword', {
          message: 'Expect an email with a reset password code.',
        })
      })
  }

  showComponent() {
    const { authData = {} } = this.props
    console.log(`authData: ${JSON.stringify(authData)}`)
    const initialValues = { username: '', email: '', phoneNumber: '' }

    const validationSchema = Yup.object({
      username: Yup.string().trim().required('Required'),
    })
    return (
      <div className="app d-flex min-vh-100 align-items-center bg-light">
        <Container>
          <Row className="justify-content-center">
            <Col md="9" lg="7" xl="6">
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={this.submit}
              >
                {(props) => (
                  <FormikForm>
                    <Card className="mx-4">
                      <CardBody className="p-4">
                        <h1>Reset your password</h1>
                        <div>
                          {this.showError()}
                          {this.renderUsernameField()}
                        </div>
                      </CardBody>
                      <CardFooter>
                        <Button type="submit" color="primary">
                          Submit
                        </Button>
                        <Button
                          type="link"
                          color="secondary"
                          onClick={() => {
                            this.dismiss()
                            this.changeState('signIn')
                          }}
                          className="ml-3"
                        >
                          Back to Sign In
                        </Button>
                        <Button
                          type="link"
                          color="secondary"
                          onClick={() => {
                            this.dismiss()
                            this.changeState('resetPassword')
                          }}
                          className="ml-3"
                        >
                          I Have a Code
                        </Button>
                      </CardFooter>
                    </Card>
                  </FormikForm>
                )}
              </Formik>
            </Col>
          </Row>
        </Container>
      </div>
    )
  }
}

export default SaasBoostForgotPassword
