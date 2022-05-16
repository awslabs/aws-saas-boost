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

import React, { Fragment } from 'react'
import { Auth } from 'aws-amplify'
import { cilUser, cilLockLocked } from '@coreui/icons'
import CIcon from '@coreui/icons-react'
import { Formik, Form as FormikForm, useField } from 'formik'
import {
  Button,
  Card,
  CardBody,
  CardGroup,
  Col,
  Container,
  Input,
  InputGroup,
  InputGroupText,
  Row,
  Label,
  FormFeedback,
  FormGroup,
  Alert,
} from 'reactstrap'
import * as Yup from 'yup'
import SaasBoostAuthComponent from './SaasBoostAuthComponent'
import { PropTypes } from 'prop-types'

const SBInput = ({ icon, label, ...props }) => {
  const [field, meta] = useField(props)
  return (
    <FormGroup>
      {label && <Label htmlFor={field.name}>{label}</Label>}
      <InputGroup className="mb-3">
        {icon && (
          <InputGroupText>
            <CIcon icon={icon}></CIcon>
          </InputGroupText>
        )}
        <Input
          {...field}
          {...props}
          invalid={meta.touched && !!meta.error}
          valid={meta.touched && !meta.error}
        />
        <FormFeedback invalid={meta.touched && meta.error ? meta.error : undefined}>
          {meta.error}
        </FormFeedback>
      </InputGroup>
    </FormGroup>
  )
}

SBInput.propTypes = {
  icon: PropTypes.array,
  label: PropTypes.string,
}

export default class SaasBoostSignIn extends SaasBoostAuthComponent {
  constructor(props) {
    super(props)
    this._validAuthStates = ['signIn', 'signedOut']

    this.credentials = { username: '', password: '' }

    this.signIn = this.signIn.bind(this)
    this.showMessage = this.showMessage.bind(this)
    this.showSignOutReason = this.showSignOutReason.bind(this)
  }

  async signIn(values, { resetForm }) {
    const { dismissSignOutReason } = this.props
    this.dismiss() //clear any existing errors

    const { username, password } = values

    if (!Auth || typeof Auth.signIn !== 'function') {
      throw new Error('No Auth module found, please ensure @aws-amplify/auth is imported')
    }

    this.setState({ loading: true })
    dismissSignOutReason()
    resetForm({ values }) // clear form validation, but leave values.
    try {
      const user = await Auth.signIn(username.trim(), password.trim())
      if (user.challengeName === 'NEW_PASSWORD_REQUIRED') {
        this.changeState('requireNewPassword', user)
      } else {
        this.checkContact(user)
      }
    } catch (err) {
      if (err.code === 'UserNotConfirmedException') {
        this.changeState('confirmSignUp', { username })
      } else if (err.code === 'PasswordResetRequiredException') {
        this.changeState('resetPassword', { username })
      } else if (err.code === 'UserNotFoundException') {
        this.error({
          ...err,
          message: 'Could not login, check username and password',
        })
      } else {
        this.error(err)
      }
    } finally {
      this.setState({ loading: false })
    }
  }

  errorMessage(err) {
    if (typeof err === 'string') {
      return err
    }
    return err.message ? err.message : JSON.stringify(err)
  }

  error(err) {
    this.setState({ error: err })
    this.triggerAuthEvent({
      type: 'error',
      data: this.errorMessage(err),
    })
  }

  /**
   * To integrate with AWS Amplify Auth module,
   * need to trigger when an Auth event happens.
   * @param event
   */
  triggerAuthEvent(event) {
    const { authState } = this.props
    if (this.props.onAuthEvent) {
      this.props.onAuthEvent(authState, event, false)
    }
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

  showSignOutReason() {
    const { signOutReason } = this.props

    return (
      !!signOutReason && (
        <Alert color="warning" isOpen={!!signOutReason}>
          {signOutReason}
        </Alert>
      )
    )
  }

  render() {
    const { authData, authState } = this.props
    if (!this._validAuthStates.includes(authState)) {
      return null
    }
    return (
      <Fragment>
        <div className="app d-flex min-vh-100 align-items-center bg-light">
          <Container>
            <Row className="justify-content-center">
              <Col md="8">
                <CardGroup>
                  <Card className="p-4">
                    <CardBody>
                      <Formik
                        initialValues={this.credentials}
                        validationSchema={Yup.object({
                          username: Yup.string().required('Required'),
                          password: Yup.string().required('Required'),
                        })}
                        onSubmit={this.signIn}
                      >
                        {(props) => (
                          <FormikForm>
                            <h1>Login</h1>
                            <p className="text-muted">Sign In to your account</p>
                            {this.showError()}
                            {this.showMessage()}
                            {this.showSignOutReason()}
                            <SBInput
                              name="username"
                              type="text"
                              placeholder="Username"
                              icon={cilUser}
                            />
                            <SBInput
                              icon={cilLockLocked}
                              name="password"
                              placeholder="Password"
                              type="password"
                            />
                            <Row>
                              <Col xs="6">
                                <Button
                                  color="primary"
                                  className="px-4"
                                  type="Submit"
                                  disabled={props.isSubmitting}
                                >
                                  Login
                                </Button>
                              </Col>
                              <Col xs="6" className="text-right">
                                <Button
                                  color="link"
                                  className="px-0"
                                  type="button"
                                  onClick={(e) => {
                                    e.preventDefault()
                                    this.changeState('forgotPassword', authData)
                                  }}
                                >
                                  Forgot password?
                                </Button>
                              </Col>
                            </Row>
                          </FormikForm>
                        )}
                      </Formik>
                    </CardBody>
                  </Card>
                  <Card
                    className="text-white bg-primary py-5 d-none d-lg-block"
                    style={{ width: '44%' }}
                  >
                    <CardBody className="text-center">
                      <div>
                        <h2>AWS SaaS Boost</h2>
                        <div>
                          <img src="/saas-boost-login.png" alt="SaasFactory" width="80%" />
                        </div>
                      </div>
                    </CardBody>
                  </Card>
                </CardGroup>
              </Col>
            </Row>
          </Container>
        </div>
      </Fragment>
    )
  }
}

export { SBInput }
