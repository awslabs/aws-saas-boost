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
import { SBInput } from './SaasBoostSignIn'
import { Card, CardBody, Col, Container, Row, Button, CardFooter, Alert } from 'reactstrap'
import * as Yup from 'yup'
import { Formik, Form as FormikForm } from 'formik'
import { Auth } from 'aws-amplify'

export class SaasBoostResetPassword extends SaasBoostAuthComponent {
  _initialState = { delivery: null, error: null, username: null }
  constructor(props) {
    super(props)
    this._validAuthStates = ['resetPassword']

    this.submit = this.submit.bind(this)
    this.showMessage = this.showMessage.bind(this)
  }

  submit({ username, code, password }, { setSubmitting, resetForm }) {
    console.log('submit form')
    if (!Auth || typeof Auth.forgotPasswordSubmit !== 'function') {
      throw new Error('No Auth module found, please ensure @aws-amplify/auth is imported')
    }

    Auth.forgotPasswordSubmit(username, code, password)
      .then((data) => {
        this.changeState('signIn', { message: 'Password change successful.' })
        this.setState(this._initialState)
      })
      .catch((err) => {
        this.setState({ error: err })
        this.error(err)
      })
  }

  showMessage() {
    const { authData } = this.props

    return (
      !!authData && (
        <Alert color="success" isOpen={!!authData.message}>
          {authData.message}
        </Alert>
      )
    )
  }

  showComponent() {
    const initialValues = { username: '', code: '', password: '', passwordConfirmation: '' }
    const validationSchema = Yup.object({
      username: Yup.string().required('Required'),
      code: Yup.string().required('Required'),
      password: Yup.string().required('Required'),
      passwordConfirmation: Yup.string().oneOf([Yup.ref('password'), null], 'Passwords must match'),
    })

    return (
      <div className="app flex-row align-items-center">
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
                          {this.showMessage()}
                          <SBInput
                            placeholder="Username"
                            key="username"
                            name="username"
                            id="username"
                            autoComplete="off"
                            label="Username"
                          />
                          <SBInput
                            placeholder="Code"
                            key="code"
                            name="code"
                            id="code"
                            autoComplete="off"
                            label="Code"
                          />
                          <SBInput
                            placeholder="New Password"
                            label="New Password"
                            type="password"
                            key="password"
                            name="password"
                            autoComplete="off"
                          />
                        </div>
                        <SBInput
                          placeholder="Confirm Password"
                          label="Confirm Password"
                          type="password"
                          key="passwordConfirmation"
                          name="passwordConfirmation"
                          autoComplete="off"
                        />
                      </CardBody>
                      <CardFooter>
                        <Button type="Submit" color="primary" disabled={props.isSubmitting}>
                          Change Password
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

export default SaasBoostResetPassword
