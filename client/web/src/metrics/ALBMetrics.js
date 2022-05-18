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

import React, { lazy, useEffect, useState } from 'react'
import { Row, Col, Card, CardBody } from 'reactstrap'
import {
  dismissError,
  fetchTenantsThunk,
  selectAllTenants,
} from '../tenant/ducks'
import { useDispatch, useSelector } from 'react-redux'

const RequestCountContainer = lazy(() => import('./RequestCountContainer'))

const RequestCountFailuresContainer = lazy(() =>
  import('./RequestCountFailuresContainer')
)
const RequestCountFailures5XXContainer = lazy(() =>
  import('./RequestCountFailures5XXContainer')
)
const SelectTimePeriodComponent = lazy(() =>
  import('./SelectTimePeriodComponent')
)

const MetricTopTenantsContainer = lazy(() =>
  import('./MetricTopTenantsContainer')
)
const SelectTenantComponent = lazy(() => import('./SelectTenantComponent'))

const TenantGraphContainer = lazy(() => import('./TenantGraphContainer'))

export default function ALBMetricsContainer(props) {
  const dispatch = useDispatch()
  const tenants = useSelector(selectAllTenants)
  const activeTenants = tenants.filter((t) => t.active)

  //  "DAY_7";
  const [selectedTimePeriod, setSelectedTimePeriod] = useState('DAY_7')
  const selectTimePeriod = (period) => {
    setSelectedTimePeriod(period)
  }

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
        <Col xs={12} lg={12}>
          <Card>
            <CardBody className="py-3">
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
                  />
                </Col>
                <Col
                  lg={1}
                  sm={1}
                  className="d-inline-flex justify-content-end pt-2"
                >
                  {/*<i className="fa fa-refresh text-muted"></i>*/}
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
      <Row>
        {selectedTenant === null && (
          <>
            <RequestCountContainer
              selectedTimePeriod={selectedTimePeriod}
              tenants={activeTenants}
              {...props}
            />
            <MetricTopTenantsContainer
              id="albstats.TopTenantsRequestCount"
              timePeriodName={selectedTimePeriod}
              metric="RequestCount"
              name="Requests - Top Tenants"
              tenants={activeTenants}
            />
          </>
        )}
        {selectedTenant !== null && (
          <TenantGraphContainer
            id="albstats.tenantRequestCount"
            stat="Sum"
            timePeriodName={selectedTimePeriod}
            metric="RequestCount"
            nameSpace="AWS/ApplicationELB"
            name="Request Count"
            statsMap={false}
            tenant={selectedTenant}
            tenants={activeTenants}
          />
        )}
      </Row>
      <Row>
        {selectedTenant === null && (
          <>
            <RequestCountFailuresContainer
              selectedTimePeriod={selectedTimePeriod}
              {...props}
              tenants={activeTenants}
            />
            <MetricTopTenantsContainer
              id="albstats.HTTPCode_Target_4XX_Count"
              timePeriodName={selectedTimePeriod}
              metric="HTTPCode_Target_4XX_Count"
              name="4XX Failures - Top Tenants"
              tenants={activeTenants}
            />
          </>
        )}
        {selectedTenant !== null && (
          <TenantGraphContainer
            id="albstats.tenantRequestCountFailures4XX"
            stat="Sum"
            timePeriodName={selectedTimePeriod}
            metric="HTTPCode_Target_4XX_Count"
            nameSpace="AWS/ApplicationELB"
            name="4XX Responses Count"
            statsMap={false}
            tenant={selectedTenant}
            tenants={activeTenants}
          />
        )}
      </Row>
      <Row>
        {selectedTenant === null && (
          <>
            <RequestCountFailures5XXContainer
              selectedTimePeriod={selectedTimePeriod}
              tenants={activeTenants}
              {...props}
            />
            <MetricTopTenantsContainer
              id="albstats.HTTPCode_Target_5XX_Count"
              timePeriodName={selectedTimePeriod}
              metric="HTTPCode_Target_5XX_Count"
              name="5XX Failures - Top Tenants"
              tenants={activeTenants}
            />
          </>
        )}
        {selectedTenant !== null && (
          <TenantGraphContainer
            id="albstats.tenantRequestCountFailures5XX"
            stat="Sum"
            timePeriodName={selectedTimePeriod}
            metric="HTTPCode_Target_5XX_Count"
            nameSpace="AWS/ApplicationELB"
            name="5XX Responses Count"
            statsMap={false}
            tenants={activeTenants}
            tenant={selectedTenant}
          />
        )}
      </Row>
    </div>
  )
}
