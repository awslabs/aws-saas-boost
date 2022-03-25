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
import { Bar } from 'react-chartjs-2'
import { useDispatch, useSelector } from 'react-redux'
import {
  selectMetricResultsById,
  selectQueryState,
  queryMetrics,
} from './ducks'
import { _colors, _tenantLabels } from './common'
import { CircleLoader } from 'react-spinners'

MetricTopTenantsContainer.propTypes = {
  id: PropTypes.string,
  name: PropTypes.string,
  metric: PropTypes.string,
  timePeriodName: PropTypes.string,
  stat: PropTypes.string,
  nameSpace: PropTypes.string,
  tenants: PropTypes.array,
}

export default function MetricTopTenantsContainer(props) {
  const {
    id,
    name,
    metric,
    timePeriodName,
    stat = 'Sum',
    nameSpace = 'AWS/ApplicationELB',
    tenants,
  } = props
  const QUERY_ID = id

  let data = {
    datasets: [],
    labels: [],
  }

  let chartOpts = {
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false,
      },
    },
    scales: {
      xAxes: {
        ticks: { beginAtZero: true, suggestedMin: 0, suggestedMax: 100 },
      },
      yAxes: {},
    },
  }
  const dispatch = useDispatch()
  const albstats = useSelector((state) =>
    selectMetricResultsById(state, QUERY_ID)
  )
  const queryState = useSelector((state) => selectQueryState(state, QUERY_ID))
  if (albstats) {
    let tenantLabels = []
    let tenantVals = []

    if (stat === 'Average') {
      chartOpts.scales.yAxes.ticks = { suggestedMin: 0, suggestedMax: 100 }
    }
    albstats.metrics[0].topTenants.forEach((m, i) => {
      tenantVals.push(m.value)
      tenantLabels.push(m.id)
    })
    data.datasets.push({
      data: tenantVals,
      borderWidth: 1,
      backgroundColor: _colors,
      borderColor: _colors,
      fill: false,
    })

    data.labels = _tenantLabels(tenantLabels, tenants)
  }
  const queryRequest = {
    id: QUERY_ID,
    timeRangeName: timePeriodName,
    stat: stat,
    dimensions: [{ metricName: metric, nameSpace: nameSpace }],
    topTenants: true,
    statsMap: false,
  }
  const refreshGraph = () => {
    return dispatch(queryMetrics(queryRequest))
  }

  useEffect(() => {
    const queryResponse = refreshGraph()
    return () => {
      if (queryResponse.PromiseStatus === 'pending') {
        queryResponse.abort()
      }
    }
  }, [dispatch, QUERY_ID, metric, nameSpace, stat, timePeriodName])

  return (
    <Col sm="12" md="6" lg="6">
      <Card>
        <CardBody>
          <Row>
            <Col sm="10">
              <CardTitle className="mb-0">{name}</CardTitle>
              <div className="small text-muted">&nbsp;</div>
            </Col>
            <Col sm="2" className="d-flex justify-content-end">
              <div className="mr-3">
                <CircleLoader
                  size={20}
                  loading={queryState.loading === 'pending'}
                />
                {queryState.loading === 'idle' && (
                  <a href="#" onClick={refreshGraph}>
                    <i className="fa fa-refresh text-muted" />
                  </a>
                )}
              </div>
            </Col>
          </Row>
          <div className="chart-wrapper" style={{ height: 300 + 'px' }}>
            <Bar data={data} options={chartOpts} height={300} redraw={true} />
          </div>
        </CardBody>
      </Card>
    </Col>
  )
}
