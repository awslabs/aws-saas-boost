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

import React, { lazy, useState, useEffect } from 'react'
import { Row, Col, Card, CardBody } from 'reactstrap'
import { useDispatch, useSelector } from 'react-redux'
import { accessLogMetricsUrls } from './ducks/accessLogMetrics'
import {
  dismissError,
  fetchTenantsThunk,
  selectAllTenants,
} from '../tenant/ducks'
import SelectTenantComponent from './SelectTenantComponent'

const SelectTimePeriodComponent = lazy(() =>
  import('./SelectTimePeriodComponent')
)

const AccessLoggingGraphContainer = lazy(() =>
  import('./AccessLoggingGraphContainer')
)

const AccessLogsMetricsContainer = lazy(() =>
  import('./AccessLogsMetricsContainer')
)

export default function AccessLoggingMetricsContainer(props) {
  const dispatch = useDispatch()
  const timePeriods = ['HOUR_1', 'HOUR_24', 'DAY_7']
  const [selectedTimePeriod, setSelectedTimePeriod] = useState('DAY_7')
  const selectTimePeriod = (period) => {
    setSelectedTimePeriod(period)
  }

  const refreshPage = () => {
    const thunkResponse = dispatch(accessLogMetricsUrls())
    return () => {
      if (thunkResponse.PromiseStatus === 'pending') {
        thunkResponse.abort()
      }
    }
  }

  useEffect(() => {
    refreshPage()
  }, [dispatch])

  const tenants = useSelector(selectAllTenants)
  const activeTenants = tenants.filter((t) => t.active)
  const [selectedTenant, setSelectedTenant] = useState(null)
  const selectTenant = (tenant) => {
    if (tenant === '') {
      setSelectedTenant(null)
    } else {
      setSelectedTenant(tenant)
    }
  }
  useEffect(() => {
    const fetchTenants = dispatch(fetchTenantsThunk())
    return () => {
      if (fetchTenants.PromiseStatus === 'pending') {
        fetchTenants.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch]) //TODO: Follow up on the use of this dispatch function.

  return (
    <div className="animated fadeIn">
      <Row>
        <Col xs={12}>
          <Card>
            <CardBody>
              <Row>
                <Col sm={3}>
                  <SelectTenantComponent
                    tenants={activeTenants}
                    selectTenant={selectTenant}
                    selectedTenant={selectedTenant}
                  />
                </Col>
                <Col sm={8}>
                  <SelectTimePeriodComponent
                    selectTimePeriod={selectTimePeriod}
                    timePeriods={timePeriods}
                  />
                </Col>
                <Col
                  lg={1}
                  sm={1}
                  className="d-inline-flex pt-2 justify-content-end"
                >
                  {/*<a onClick={refreshPage} href="#">*/}
                  {/*  <i className="fa fa-refresh text-muted" />*/}
                  {/*</a>*/}
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
      {selectedTenant === null && (
        <Row>
          <AccessLoggingGraphContainer
            selectedTimePeriod={selectedTimePeriod}
            metric="REQUEST_COUNT"
            title="Request Count by Endpoint"
            label="Request Count"
            colorIndex={0}
          />
          <AccessLoggingGraphContainer
            selectedTimePeriod={selectedTimePeriod}
            metric="RESPONSE_TIME"
            title="Response Time by Endpoint"
            label="Time (in seconds)"
            colorIndex={5}
          />
        </Row>
      )}
      {selectedTenant !== null && (
        <Row>
          <AccessLogsMetricsContainer
            selectedTimePeriod={selectedTimePeriod}
            metric="PATH_REQUEST_COUNT"
            title="Request count for tenant"
            label="Request Count"
            colorIndex={0}
            selectedTenant={selectedTenant}
          />
          <AccessLogsMetricsContainer
            selectedTimePeriod={selectedTimePeriod}
            metric="PATH_RESPONSE_TIME"
            title="Response time for tenant"
            label="Response Time"
            colorIndex={5}
            selectedTenant={selectedTenant}
          />
        </Row>
      )}
    </div>
  )
}
