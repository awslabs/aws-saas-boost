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
import { CircleLoader } from 'react-spinners'

AccessLogGraphComponent.propTypes = {
  title: PropTypes.string,
  label: PropTypes.string,
  colorIndex: PropTypes.string,
  loading: PropTypes.bool,
  data: PropTypes.array,
  tenantName: PropTypes.string,
}

export default function AccessLogGraphComponent(props) {
  const { title, label, colorIndex, loading, data = [], tenantName } = props

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

  let graphData = {
    datasets: [],
    labels: [],
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
            <Col sm="10">
              <CardTitle className="mb-0">
                <>{title}</>
                <span className="font-weight-bold">{tenantName}</span>
              </CardTitle>
              <div className="small text-muted">&nbsp;</div>
            </Col>
            <Col sm="2" className="d-flex justify-content-end">
              <div className="mr-3">
                <CircleLoader size={20} loading={loading === 'pending'} />
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
