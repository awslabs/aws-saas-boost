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
import { queryMetrics, selectMetricResultsById, selectQueryState } from './ducks'
import { useDispatch, useSelector } from 'react-redux'
import { Line } from 'react-chartjs-2'
import { Card, CardBody, Col, Row, CardTitle } from 'reactstrap'
import { _colors } from './common'
import { CircleLoader } from 'react-spinners'

const QUERY_ID = 'albstats.requestCount'
const queryRequest = {
  id: QUERY_ID,
  timeRangeName: 'DAY_7',
  stat: 'Sum',
  dimensions: [{ metricName: 'RequestCount', nameSpace: 'AWS/ApplicationELB' }],
  topTenants: false,
  statsMap: true,
}

export default function RequestGraphContainer(props) {
  const { selectedTimePeriod } = props

  queryRequest.timeRangeName = selectedTimePeriod
  let data = {
    datasets: [],
    labels: [],
  }
  const dispatch = useDispatch()
  const albstats = useSelector((state) => selectMetricResultsById(state, QUERY_ID))
  const queryState = useSelector((state) => selectQueryState(state, QUERY_ID))

  const refreshGraph = () => {
    return dispatch(queryMetrics(queryRequest))
  }

  useEffect(() => {
    const queryResponse = refreshGraph()
    return () => {
      if (queryResponse.PromiseStatus === 'pending') {
        // console.log("Clean up onboarding list request");
        queryResponse.abort()
      }
    }
  }, [selectedTimePeriod, dispatch])

  if (albstats) {
    const metric = albstats.metrics[0]

    const datasets = []

    datasets.push({
      label: 'P90',
      data: metric.stats.P90,
      borderWidth: 1,
      backgroundColor: _colors[0],
      borderColor: _colors[0],
      fill: false,
      order: 2,
    })

    datasets.push({
      label: 'P70',
      data: metric.stats.P70,
      borderWidth: 1,
      backgroundColor: _colors[1],
      borderColor: _colors[1],
      fill: false,
      order: 3,
    })

    datasets.push({
      label: 'P50',
      data: metric.stats.P50,
      borderWidth: 1,
      backgroundColor: _colors[2],
      borderColor: _colors[2],
      fill: false,
      order: 4,
    })

    const distinctPeriods = [...new Set(albstats.periods)]

    data.labels = distinctPeriods
    data.datasets = datasets
  }

  return (
    <GraphComponent
      queryState={queryState}
      name="Request Count"
      data={data}
      refreshGraph={refreshGraph}
    />
  )
}

RequestGraphContainer.propTypes = {
  selectedTimePeriod: PropTypes.string,
}

const GraphComponent = (props) => {
  const { queryState, data, refreshGraph } = props

  const dataToGraph = { ...data }

  const chartOpts = {
    tooltips: {
      enabled: true,
      intersect: true,
      mode: 'index',
      position: 'nearest',
    },
    maintainAspectRatio: false,
    legend: {
      display: true,
    },
    scales: {
      xAxes: {
        gridLines: {
          drawOnChartArea: false,
        },
      },
      yAxes: {
        ticks: {
          beginAtZero: true,
          //maxTicksLimit: 5,
          //stepSize: Math.ceil(250 / 5),
          //max: 250,
        },
      },
    },
    elements: {
      point: {
        radius: 0,
        hitRadius: 10,
        hoverRadius: 4,
        hoverBorderWidth: 3,
      },
    },
  }

  GraphComponent.propTypes = {
    queryState: PropTypes.object,
    data: PropTypes.object,
    refreshGraph: PropTypes.func,
  }

  return (
    <Col sm="12" md="6" lg="6">
      <Card>
        <CardBody>
          <Row>
            <Col sm="10">
              <CardTitle className="mb-0">Request Count</CardTitle>
              <div className="small text-muted">
                Total requests passing through the Application Load Balancers
              </div>
            </Col>
            <Col sm="2" className="d-flex justify-content-end">
              <div className="mr-3">
                <CircleLoader size={20} loading={queryState.loading === 'pending'} />
                {queryState.loading === 'idle' && (
                  <a href="#" onClick={refreshGraph}>
                    <i className="fa fa-refresh text-muted" />
                  </a>
                )}
              </div>
            </Col>
          </Row>

          <div className="chart-wrapper" style={{ height: 300 + 'px' }}>
            <Line data={dataToGraph} options={chartOpts} height={300} redraw={true} />
          </div>
        </CardBody>
      </Card>
    </Col>
  )
}
