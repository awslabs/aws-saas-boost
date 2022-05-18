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
import { BarLoader } from 'react-spinners'
import { Container, Row, Col, Card, CardBody } from 'reactstrap'

function SBLoading(props) {
  return (
    <div className="app d-flex min-vh-100 align-items-center bg-light">
      <Container>
        <Row className="justify-content-center">
          <Col md="9" lg="7" xl="6">
            <Card className="mx-4">
              <CardBody className="p-4">
                <h2 className="text-muted text-center">Initializing Application</h2>
                <div className="m-5">
                  <BarLoader size={120} width="100%" css="display: block" />
                  <div className="text-muted text-center mt-2">Loading application settings...</div>
                </div>
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Container>
    </div>
  )
}

export default SBLoading
