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

import React from "react";
import { Row, Col, Card, CardHeader, Button } from "reactstrap";
import RequestGraphContainer from "./RequestGraphContainer";

export const DashboardComponent = ({ metrics, handleRefresh }) => {
  let barChartData = {
    labels: [],
    datasets: [],
  };

  //loop through the  top 10 lists and set into map with values for each tenant
  let topTenantMap = new Map(); //map of tenants and their values

  metrics.map((metric) => {
    //build X Axis labels
    barChartData.labels = metric.periods;
    //loop through the metrics data
    //*TODO:  Have to get the entry for the executed query
    metric.metrics.map((metricData) => {
      //loop through the  top tenants lists and set into map with values for each tenant
      //*TODO:  Have to get the metric record for the metricName and Namespace you want to graph
      metricData.topTenants.map((tenantRecord) => {
        var values = [];
        if (topTenantMap.has(tenantRecord.tenantId)) {
          values = topTenantMap.get(tenantRecord.tenantId);
        }
        values.push(tenantRecord.value);
        topTenantMap.set(tenantRecord.tenantId, values);
      });
    });
  });
  //loop through the topTenantMap and build the barChartDate object for Charts.js
  var i = 0;
  topTenantMap.forEach(mapValues);

  function mapValues(value, key, map) {
    let dataset = {
      label: key,
      data: value,
      borderWidth: 1,
      backgroundColor: "rgba(41, 99, 161, 0.5)",
      borderColor: "rgba(41, 99, 161, 0.5)",
    };
    barChartData.datasets.push(dataset);
    i = i + 1;
  }

  return (
    <div className="animated fadeIn">
      <Row>
        <RequestGraphContainer />
      </Row>
      <Row>
        <Col xs="12" sm="12" lg="12">
          <Card>
            <CardHeader>
              <i className="fa fa-align-justify"></i> Top 10 Requests
              <Button
                align="right"
                color="secondary"
                className="mr-2"
                onClick={handleRefresh}
              >
                <span>
                  <i className={"fa fa-refresh"} />
                </span>
              </Button>
            </CardHeader>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
