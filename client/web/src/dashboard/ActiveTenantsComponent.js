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

import PropTypes from 'prop-types'
import React from 'react'
import { Col, Card, CardBody } from 'reactstrap'

ActiveTenantsComponent.propTypes = {
  activeTenants: PropTypes.number,
  totalTenants: PropTypes.number,
}

export default function ActiveTenantsComponent(props) {
  const { activeTenants = 0, totalTenants = 10 } = props

  return (
    <Col xs={1} md={3} lg={3}>
      <Card className="text-white pb-3 bg-info">
        <CardBody className="pb-0">
          <h4>Tenants</h4>
          <dl>
            <dt className=" ">Active</dt>
            <dd className="text-value">{activeTenants}</dd>
            <dt className="">Total</dt>
            <dd className="text-value">{totalTenants}</dd>
          </dl>
        </CardBody>
      </Card>
    </Col>
  )
}
