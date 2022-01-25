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
import { CircleLoader } from 'react-spinners'

StatsGraphContainer.propTypes = {
  id: PropTypes.string,
  name: PropTypes.string,
  metric: PropTypes.string,
  timePeriodName: PropTypes.string,
  stat: PropTypes.string,
  nameSpace: PropTypes.string,
  statsMap: PropTypes.bool,
  topTenants: PropTypes.bool,
}

export default function StatsGraphContainer(props) {
  const {
    id,
    name,
    metric,
    timePeriodName,
    stat = 'Sum',
    nameSpace = 'AWS/ApplicationELB',
    statsMap = true,
    topTenants = false,
  } = props
  const QUERY_ID = id

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

  if (stats) {
    data.datasets.push({
      label: 'P90',
      data: stats.metrics[0].stats.P90,
      borderWidth: 1,
      backgroundColor: _colors[0],
      borderColor: _colors[0],
      fill: false,
      order: 1,
    })
    data.datasets.push({
      label: 'P70',
      data: stats.metrics[0].stats.P70,
      borderWidth: 1,
      backgroundColor: _colors[1],
      borderColor: _colors[1],
      fill: false,
      order: 2,
    })
    data.datasets.push({
      label: 'P50',
      data: stats.metrics[0].stats.P50,
      borderWidth: 1,
      backgroundColor: _colors[2],
      borderColor: _colors[2],
      fill: false,
      order: 3,
    })

    data.labels = [...new Set(stats.periods)]
  }

  const queryRequest = {
    id: QUERY_ID,
    timeRangeName: timePeriodName,
    stat: stat,
    dimensions: [{ metricName: metric, nameSpace: nameSpace }],
    topTenants: topTenants,
    statsMap: statsMap,
  }
  const refreshGraph = () => {
    return dispatch(queryMetrics(queryRequest))
  }

  useEffect(() => {
    const queryResponse = refreshGraph()
    return () => {
      if (queryResponse.PromiseStatus === 'pending') {
        console.log('Clean up onboarding list request')
        queryResponse.abort()
      }
    }
  }, [dispatch, QUERY_ID, timePeriodName, stat, metric, nameSpace, topTenants, statsMap])

  return (
    <Col sm={12} md={6} lg={6}>
      <Card>
        <CardBody>
          <Row>
            <Col sm="10">
              <CardTitle className="mb-0">{name}</CardTitle>
              <div className="small text-muted">&nbsp;</div>
            </Col>
            <Col sm="2" className="d-flex justify-content-end">
              <div className="mr-3">
                <CircleLoader size={20} loading={queryState.loading === 'pending'} />
                {queryState.loading === 'idle' && (
                  <a href="#" onClick={refreshGraph}>
                    <i className="fa fa-refresh text-muted"></i>
                  </a>
                )}
              </div>
            </Col>
          </Row>

          <div className="chart-wrapper" style={{ height: 300 + 'px' }}>
            <Line data={data} options={chartOpts} height={300} redraw={true} />
          </div>
        </CardBody>
      </Card>
    </Col>
  )
}
