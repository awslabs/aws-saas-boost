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
import React, { useEffect } from 'react'
import { Col, Card, CardBody, Row, CardTitle } from 'reactstrap'
import { Line } from 'react-chartjs-2'
import { useDispatch, useSelector } from 'react-redux'
import { selectMetricResultsById, selectQueryState, queryMetrics } from './ducks'
import { _colors } from './common'

TenantGraphContainer.propTypes = {
  id: PropTypes.string,
  name: PropTypes.string,
  metric: PropTypes.object,
  timePeriodName: PropTypes.string,
  stat: PropTypes.string,
  nameSpace: PropTypes.string,
  statsMap: PropTypes.bool,
  topTenants: PropTypes.bool,
  tenant: PropTypes.object,
}

export default function TenantGraphContainer(props) {
  const {
    id,
    name,
    metric,
    timePeriodName,
    stat = 'Sum',
    nameSpace = 'AWS/ApplicationELB',
    statsMap = false,
    topTenants = false,
    tenant,
  } = props
  const QUERY_ID = id
  const queryRequest = {
    id: QUERY_ID,
    timeRangeName: timePeriodName,
    stat: stat,
    dimensions: [{ metricName: metric, nameSpace: nameSpace }],
    topTenants: topTenants,
    statsMap: statsMap,

    tenants: [tenant],
    singleTenant: true,
  }
  let data = {
    datasets: [],
    labels: [],
  }

  let chartOpts = {
    maintainAspectRatio: false,
    legend: {
      display: true,
    },
    scales: {
      xAxes: {
        ticks: { beginAtZero: true },
      },
      yAxes: {
        ticks: { suggestedMin: 0, suggestedMax: 100 },
      },
    },
  }
  const dispatch = useDispatch()
  const stats = useSelector((state) => selectMetricResultsById(state, QUERY_ID))
  const queryState = useSelector((state) => selectQueryState(state, QUERY_ID))
  console.log(`queryState: ${JSON.stringify(queryState)}`)

  if (stats) {
    data.datasets.push({
      label: name,
      data: stats.metrics[0].stats.Values,
      borderWidth: 1,
      backgroundColor: _colors[0],
      borderColor: _colors[0],
      fill: false,
      order: 1,
    })

    data.labels = [...new Set(stats.periods)]
  }

  useEffect(() => {
    const queryResponse = dispatch(queryMetrics(queryRequest))
    return () => {
      if (queryResponse.PromiseStatus === 'pending') {
        console.log('Clean up onboarding list request')
        queryResponse.abort()
      }
    }
  }, [dispatch, timePeriodName, tenant])

  return (
    <Col sm={12} md={12} lg={12}>
      <Card>
        <CardBody>
          <Row>
            <Col sm="5">
              <CardTitle className="mb-0">{name}</CardTitle>
              <div className="small text-muted">&nbsp;</div>
            </Col>
            <Col sm="7" className="d-none d-sm-inline-block"></Col>
          </Row>

          <div className="chart-wrapper" style={{ height: 400 + 'px' }}>
            <Line data={data} options={chartOpts} height={400} redraw={true} />
          </div>
        </CardBody>
      </Card>
    </Col>
  )
}
