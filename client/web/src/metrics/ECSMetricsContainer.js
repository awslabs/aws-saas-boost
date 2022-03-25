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
import {
  fetchTenantsThunk,
  selectAllTenants,
  dismissError,
} from '../tenant/ducks'

const SelectTenantComponent = lazy(() => import('./SelectTenantComponent'))
const SelectTimePeriodComponent = lazy(() =>
  import('./SelectTimePeriodComponent')
)

const MetricTopTenantsContainer = lazy(() =>
  import('./MetricTopTenantsContainer')
)

const TenantGraphContainer = lazy(() => import('./TenantGraphContainer'))
const StatsGraphContainer = lazy(() => import('./StatsGraphContainer'))

export default function ECSMetricsConatiner(props) {
  const dispatch = useDispatch()
  const tenants = useSelector(selectAllTenants)
  const activeTenants = tenants.filter((t) => t.active)

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
                  />
                </Col>
                <Col
                  lg={1}
                  sm={1}
                  className="d-inline-flex pt-2 justify-content-end"
                >
                  {/*<a href="#" onClick={refreshPage}>*/}
                  {/*  <i className="fa fa-refresh text-muted"></i>*/}
                  {/*</a>*/}
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
      <Row>
        {selectedTenant === null && (
          <>
            <StatsGraphContainer
              id="ecsstats.CPUUtilization"
              stat="Average"
              timePeriodName={selectedTimePeriod}
              metric="CPUUtilization"
              nameSpace="AWS/ECS"
              name="CPU Utilization"
              statsMap={true}
              tenants={activeTenants}
            />
            <MetricTopTenantsContainer
              id="ecsstats.TopTenantsCPUUtilization"
              stat="Average"
              timePeriodName={selectedTimePeriod}
              metric="CPUUtilization"
              nameSpace="AWS/ECS"
              name="CPU Utilization - Top Tenants"
              tenants={activeTenants}
            />
          </>
        )}
        {selectedTenant !== null && (
          <TenantGraphContainer
            id="ecsstats.tenantCPUUtilization"
            stat="Average"
            timePeriodName={selectedTimePeriod}
            metric="CPUUtilization"
            nameSpace="AWS/ECS"
            name="CPU Utilization"
            statsMap={false}
            tenant={selectedTenant}
            tenants={activeTenants}
          />
        )}
      </Row>
      <Row>
        {selectedTenant === null && (
          <>
            <StatsGraphContainer
              id="ecsstats.MemoryUtilization"
              stat="Average"
              timePeriodName={selectedTimePeriod}
              metric="MemoryUtilization"
              nameSpace="AWS/ECS"
              name="Memory Utilization"
              statsMap={true}
              tenants={activeTenants}
            />
            <MetricTopTenantsContainer
              id="ecsstats.TopTenantsMemoryUtilization"
              stat="Average"
              timePeriodName={selectedTimePeriod}
              metric="MemoryUtilization"
              nameSpace="AWS/ECS"
              name="Memory Utilization - Top Tenants"
              tenants={activeTenants}
            />
          </>
        )}
        {selectedTenant !== null && (
          <TenantGraphContainer
            id="ecsstats.tenantMemoryUtilization"
            stat="Average"
            timePeriodName={selectedTimePeriod}
            metric="MemoryUtilization"
            nameSpace="AWS/ECS"
            name="Memory Utilization"
            statsMap={false}
            tenant={selectedTenant}
            tenants={activeTenants}
          />
        )}
      </Row>
    </div>
  )
}
