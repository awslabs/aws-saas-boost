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
import React, { useEffect, useState } from 'react'

import { Card, CardBody, Col, Row, CardTitle } from 'reactstrap'
import { Bar } from 'react-chartjs-2'
import { _colors } from './common'
import { useDispatch, useSelector } from 'react-redux'
import {
  accessLogMetricsUrls,
  getAccessLogMetrics,
  selectAccessLogUrlById,
  selectAccessLogDatabyMetric,
} from './ducks/accessLogMetrics'

AccessLoggingGraphContainer.propTypes = {
  metric: PropTypes.string,
  selectedTimePeriod: PropTypes.string,
  title: PropTypes.string,
  label: PropTypes.string,
  colorIndex: PropTypes.number,
}

export default function AccessLoggingGraphContainer(props) {
  const dispatch = useDispatch()

  const { metric, selectedTimePeriod, title, label, colorIndex } = props
  let data = useSelector((state) => selectAccessLogDatabyMetric(state, metric)) || []

  const convertTimePeriod = (timePeriod) => {
    switch (timePeriod) {
      case 'HOUR_1':
        return '1_HOUR'
      case 'HOUR_24':
        return '24_HOUR'
      case 'DAY_7':
        return '7_DAY'
    }
  }

  let fileName = `PATH_${metric}_${convertTimePeriod(selectedTimePeriod)}_FILE`

  const fileUri = useSelector((state) => selectAccessLogUrlById(state, fileName))

  useEffect(() => {
    refreshGraph(fileUri, metric)
  }, [dispatch, fileUri, metric])

  const refreshGraph = (fileUri, metric) => {
    if (!!fileUri) {
      dispatch(getAccessLogMetrics({ metric: metric, url: fileUri }))
    }
  }

  let graphData = {
    datasets: [],
    labels: [],
  }

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

  let labels = [],
    values = []

  data.forEach((v) => {
    labels.push(v.id)
    values.push(v.value)
  })

  graphData.datasets.push({
    label: label,
    data: values,
    borderWidth: 1,
    fill: false,
    backgroundColor: _colors[colorIndex],
    borderColor: _colors[colorIndex],
  })

  graphData.labels = labels

  return (
    <Col sm="12" md="6" lg="6">
      <Card>
        <CardBody>
          <Row>
            <Col sm="5">
              <CardTitle className="mb-0">{title}</CardTitle>
              <div className="small text-muted">&nbsp;</div>
            </Col>
            <Col sm="7" className="d-inline-flex justify-content-end">
              <div>
                <a href="#" onClick={() => refreshGraph(fileUri, metric)}>
                  <i className="fa fa-refresh text-black-50"></i>
                </a>
              </div>
            </Col>
          </Row>

          <div className="chart-wrapper" style={{ height: 300 + 'px' }}>
            <Bar data={graphData} options={chartOpts} height={300} redraw={true} />
          </div>
        </CardBody>
      </Card>
    </Col>
  )
}
