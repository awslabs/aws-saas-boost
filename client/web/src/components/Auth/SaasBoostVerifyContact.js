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
import CIcon from '@coreui/icons-react'
import { cilCheckAlt, cilX } from '@coreui/icons'
import {
  Container,
  Row,
  Col,
  Card,
  CardBody,
  FormGroup,
  Input,
  Label,
  Button,
  CardFooter,
} from 'reactstrap'
import { Auth } from '@aws-amplify/auth'

class SaasBoostVerifyContact extends SaasBoostAuthComponent {
  constructor(props) {
    super(props)

    this._validAuthStates = ['verifyContact']
    this.state = { verifyAttr: null }
    this.handleInputChange = this.handleInputChange.bind(this)
    this.verify = this.verify.bind(this)
    this.submit = this.submit.bind(this)
  }

  verify() {
    const { contact, checkedValue } = this.inputs
    if (!contact) {
      this.error('Neither Email nor Phone Number selected')
      return
    }

    if (!Auth || typeof Auth.verifyCurrentUserAttribute !== 'function') {
      throw new Error('No Auth module found, please ensure @aws-amplify/auth is imported')
    }

    Auth.verifyCurrentUserAttribute(checkedValue)
      .then((data) => {
        this.setState({ verifyAttr: checkedValue })
      })
      .catch((err) => this.error(err))
  }

  submit() {
    const attr = this.state.verifyAttr
    const { code } = this.inputs
    if (!Auth || typeof Auth.verifyCurrentUserAttributeSubmit !== 'function') {
      throw new Error('No Auth module found, please ensure @aws-amplify/auth is imported')
    }
    Auth.verifyCurrentUserAttributeSubmit(attr, code)
      .then((data) => {
        this.changeState('signedIn', this.props.authData)
        this.setState({ verifyAttr: null })
      })
      .catch((err) => this.error(err))
  }
  verifyView() {
    const user = this.props.authData
    if (!user) {
      console.error('No user to verify')
      return null
    }
    const { unverified } = user
    if (!unverified) {
      console.error('no unverified on user')
      return null
    }
    const { email } = unverified

    return (
      <div>
        {email ? (
          <FormGroup check inline>
            <Input
              className="form-check-inline"
              type="radio"
              id="contact-email"
              name="contact"
              key="email"
              value="email"
              onChange={this.handleInputChange}
            />
            <Label className="form-check-label" check htmlFor="contact-email">
              Email
            </Label>
          </FormGroup>
        ) : null}
      </div>
    )
  }

  submitView() {
    return (
      <div>
        <Input
          placeholder="Code"
          key="code"
          name="code"
          autoComplete="off"
          onChange={this.handleInputChange}
        />
      </div>
    )
  }
  showComponent() {
    const { authData } = this.props
    return (
      <div className="app d-flex min-vh-100 align-items-center bg-light">
        <Container>
          <Row className="justify-content-center">
            <Col md="9" lg="7" xl="6">
              <Card className="mx-4">
                <CardBody className="p-4">
                  <h1>Verify Contact Information</h1>
                  <div>Account recovery requires contact information verification.</div>
                  <div>{this.state.verifyAttr ? this.submitView() : this.verifyView()}</div>
                </CardBody>
                <CardFooter>
                  {this.state.verifyAttr ? (
                    <Button color="primary" onClick={this.submit}>
                      <CIcon icon={cilCheckAlt} /> Submit
                    </Button>
                  ) : (
                    <Button color="primary" onClick={this.verify}>
                      <CIcon icon={cilCheckAlt} /> Verify
                    </Button>
                  )}

                  <Button
                    color="secondary"
                    className="ml-3"
                    onClick={() => this.changeState('signedIn', authData)}
                  >
                    <CIcon icon={cilX} /> Skip
                  </Button>
                </CardFooter>
              </Card>
            </Col>
          </Row>
        </Container>
      </div>
    )
  }
}

export default SaasBoostVerifyContact
