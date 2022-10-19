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

import { Fragment } from 'react'
import {
  Button,
  Card,
  CardBody,
  CardGroup,
  Col,
  Container,
  Row,
  Alert,
} from 'reactstrap'
import { useAuth } from 'react-oidc-context'
import { Redirect } from 'react-router-dom'
import config from '../../config/appConfig'
export default function OidcSignIn({ signOutReason }) {
  const auth = useAuth()

  const signInClickHandler = () => {
    const signInParams = {
      scope: config.scope ? config.scope : 'openid profile email',
    }
    auth.signinRedirect(signInParams)
  }

  return (
    <Fragment>
      <div className="app d-flex min-vh-100 align-items-center bg-light">
        <Container>
          <Row className="justify-content-center">
            <Col md="12">
              <Row className="justify-content-center d-flex">
                <Card
                  className="text-white bg-primary py-5 d-none d-md-block"
                  style={{ width: '44%' }}
                >
                  <CardBody className="text-center">
                    <div>
                      <h2>AWS SaaS Boost</h2>
                      <div>
                        <img
                          src="/saas-boost-login.png"
                          alt="SaasFactory"
                          width="80%"
                        />
                      </div>
                    </div>
                  </CardBody>
                </Card>
              </Row>
              <Row className="justify-content-center d-flex">
                <Card 
                  className="p-4 d-md-block"
                  style={{ width: '44%' }}
                >
                  <CardBody className="d-flex align-items-center justify-content-center">
                    {!auth.isAuthenticated && (
                      <>
                        <Row>
                          <Col xs="12">
                            <Row>
                              <Col>
                                {!!signOutReason && (
                                  <Alert color="warning" isOpen={!!signOutReason}>
                                    {signOutReason}
                                  </Alert>
                                )}
                              </Col>
                            </Row>
                            <Row>
                              <Col xs="12" className="d-flex justify-content-center">
                                <Button
                                  color="primary"
                                  className="px-4"
                                  type="button"
                                  onClick={signInClickHandler}
                                >
                                  Sign In
                                </Button>
                              </Col>
                            </Row>
                            
                          </Col>
                        </Row>
                      </>
                    )}
                    {auth.isAuthenticated && <Redirect to="/"></Redirect>}
                  </CardBody>
                </Card>
              </Row>
            </Col>
          </Row>
        </Container>
      </div>
    </Fragment>
  )
}