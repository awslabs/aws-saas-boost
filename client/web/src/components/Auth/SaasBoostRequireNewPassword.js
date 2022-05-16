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
import { Auth } from 'aws-amplify'
import { Button, Card, CardBody, Col, Container, Row, CardFooter } from 'reactstrap'
import { Formik, Form as FormikForm } from 'formik'

import { SBInput } from './SaasBoostSignIn'
import * as Yup from 'yup'

export class SaasBoostRequireNewPassword extends SaasBoostAuthComponent {
  _initialState = {}
  constructor(props) {
    super(props)
    this._validAuthStates = ['requireNewPassword']
    this.state = this._initialState
    this.change = this.change.bind(this)
  }

  change(values, { resetForm }) {
    const user = this.props.authData
    const attrs = {}
    const { password } = values

    if (!Auth || typeof Auth.completeNewPassword !== 'function') {
      throw new Error('No Auth module found, please ensure @aws-amplify/auth is imported')
    }
    resetForm(values)
    Auth.completeNewPassword(user, password, attrs)
      .then((user) => {
        if (user.challengeName === 'SMS_MFA') {
          this.changeState('confirmSignIn', user)
        } else if (user.challengeName === 'MFA_SETUP') {
          this.changeState('TOTPSetup', user)
        } else {
          this.checkContact(user)
        }
      })
      .catch((err) => {
        this.setState({ error: err })
        this.error(err)
      })
  }

  showComponent() {
    const validationSchema = Yup.object({
      password: Yup.string()
        .required('Required')
        .min(6, 'Password must have a minimum of 6 characters'),
      confirmPassword: Yup.string()
        .min(6, 'Password must have a minimum of 6 characters')
        .equals([Yup.ref('password'), null], 'Password does not match')
        .required('Required'),
    })

    return (
      <div className="app d-flex min-vh-100 align-items-center bg-light">
        <Container>
          <Row className="justify-content-center">
            <Col md="9" lg="7" xl="6">
              <Formik
                initialValues={{
                  password: '',
                  confirmPassword: '',
                }}
                validationSchema={validationSchema}
                onSubmit={this.change}
              >
                {(props) => (
                  <FormikForm onSubmit={props.handleSubmit}>
                    <Card className="mx-4">
                      <CardBody className="p-4">
                        <h1>Change Password</h1>
                        {this.showError()}
                        <SBInput
                          label="New Password"
                          type="password"
                          autoFocus
                          placeholder="New Password"
                          key="password"
                          name="password"
                        />
                        <SBInput
                          label="Confirm Password"
                          type="password"
                          placeholder="Confirm Password"
                          key="confirmPassword"
                          name="confirmPassword"
                        />
                      </CardBody>
                      <CardFooter>
                        <Button type="submit" color="primary">
                          Change
                        </Button>
                        <Button
                          type="link"
                          color="secondary"
                          onClick={() => {
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

export default SaasBoostRequireNewPassword
