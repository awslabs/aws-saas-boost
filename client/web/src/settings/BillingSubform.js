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
import { Row, Col, Card, CardHeader, CardBody } from 'reactstrap'
import { SaasBoostInput, SaasBoostCheckbox } from '../components/FormComponents'

BillingSubform.propTypes = {
  provisionBilling: PropTypes.bool,
}

export default function BillingSubform(props) {
  const { provisionBilling = false } = props
  return (
    <>
      <Row className="mb-3">
        <Col xs={12}>
          <Card>
            <CardHeader>Billing</CardHeader>
            <CardBody>
              <SaasBoostCheckbox
                name="provisionBilling"
                id="provisionBilling"
                label="Configure Billing Provider"
                value={provisionBilling}
              />
              {provisionBilling && (
                <Row>
                  <Col xl={6}>
                    <SaasBoostInput
                      key="billing.apiKey"
                      label="Please enter your Stripe Secret API Key"
                      name="billing.apiKey"
                      type="password"
                    />
                  </Col>
                </Row>
              )}
            </CardBody>
          </Card>
        </Col>
      </Row>
    </>
  )
}
