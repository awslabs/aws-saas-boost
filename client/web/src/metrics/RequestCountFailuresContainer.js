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
import styles from './Progress.module.css'
import { Col, Card, CardBody, Row, CardTitle, Progress } from 'reactstrap'
import { Line } from 'react-chartjs-2'
import { useDispatch, useSelector } from 'react-redux'
import { selectMetricResultsById, selectQueryState, queryMetrics } from './ducks'
import { _colors } from './common'
import { CircleLoader } from 'react-spinners'

const QUERY_ID = 'albstats.requestCountFailures4XX'
const queryRequest = {
  id: QUERY_ID,
  timeRangeName: 'DAY_7',
  stat: 'Sum',
  dimensions: [
    {
      metricName: 'HTTPCode_Target_4XX_Count',
      nameSpace: 'AWS/ApplicationELB',
    },
  ],
  topTenants: false,
  statsMap: true,
}

RequestCountFailuresContainer.propTypes = {
  selectedTimePeriod: PropTypes.string,
}

export default function RequestCountFailuresContainer(props) {
  const { selectedTimePeriod } = props
  queryRequest.timeRangeName = selectedTimePeriod

  let data = {
    datasets: [],
    labels: [],
  }
  let legends = []

  let chartOpts = {
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
  const dispatch = useDispatch()
  const albstats = useSelector((state) => selectMetricResultsById(state, QUERY_ID))
  const queryState = useSelector((state) => selectQueryState(state, QUERY_ID))
  if (albstats) {
    const metric = albstats.metrics[0]

    data.datasets.push({
      label: 'P90',
      data: metric.stats.P90,
      borderWidth: 1,
      backgroundColor: _colors[0],
      borderColor: _colors[0],
      fill: false,
      order: 2,
    })

    data.datasets.push({
      label: 'P70',
      data: metric.stats.P70,
      borderWidth: 1,
      backgroundColor: _colors[1],
      borderColor: _colors[1],
      fill: false,
      order: 3,
    })

    data.datasets.push({
      label: 'P50',
      data: metric.stats.P50,
      borderWidth: 1,
      backgroundColor: _colors[2],
      borderColor: _colors[2],
      fill: false,
      order: 4,
    })
    ;['P90', 'P70', 'P50'].forEach((value, i) => {
      legends.push(
        <Col sm={4} md className="mb-sm-2 mb-0" key={i}>
          <div className="text-muted">{value}</div>
          <strong>&nbsp;</strong>
          <Progress className="progress-xs mt-2" barClassName={styles['index' + i]} value="100" />
        </Col>,
      )
    })
    data.labels = [...new Set(albstats.periods)]
  }

  const refreshGraph = () => {
    return dispatch(queryMetrics(queryRequest))
  }

  useEffect(() => {
    const queryResponse = refreshGraph()
    return () => {
      if (queryResponse.loading === 'pending') {
        queryResponse.abort()
      }
    }
  }, [selectedTimePeriod, dispatch])

  return (
    <Col sm="12" md="6" lg="6">
      <Card>
        <CardBody>
          <Row>
            <Col sm="10">
              <CardTitle className="mb-0">Request Failures - 4XX</CardTitle>
              <div className="small text-muted">&nbsp;</div>
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
            <Line data={data} options={chartOpts} height={300} redraw={true} />
          </div>
        </CardBody>
      </Card>
    </Col>
  )
}
