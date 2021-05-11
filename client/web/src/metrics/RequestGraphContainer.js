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

import React, { useEffect } from "react";
import { queryMetrics, selectMetricResultsById } from "./ducks";
import { useDispatch, useSelector } from "react-redux";
import { Line } from "react-chartjs-2";
import { Card, CardBody, CardHeader, Col } from "reactstrap";

const queryRequest = {
  id: "albstats",
  timeRangeName: "DAY_7",
  stat: "Sum",
  dimensions: [{ metricName: "RequestCount", nameSpace: "AWS/ApplicationELB" }],
  topTenants: true,
  statsMap: true,
};
export default function RequestGraphContainer(props) {
  const dispatch = useDispatch();
  const albstats = useSelector((state) =>
    selectMetricResultsById(state, "albstats")
  );

  console.log(`albstats: ${JSON.stringify(albstats)}`);
  useEffect(() => {
    const queryResponse = dispatch(queryMetrics(queryRequest));
    return () => {
      if (queryResponse.PromiseStatus === "pending") {
        console.log("Clean up onboarding list request");
        queryResponse.abort();
      }
      //dispatch(dismissError());
    };
  }, [dispatch]);

  const metrics = new Map();
  if (albstats) {
    albstats.metrics.forEach((metric) => {
      let data = {};
      const datasets = [];

      const metricData = {
        name: metric.dimension.metricName,
      };

      datasets.push({
        label: "P90",
        data: metric.stats.P90,
        borderWidth: 1,
        backgroundColor: "rgba(32,168,216, 0.5)",
        borderColor: "rgba(32,168,216, 0.5)",
        fill: false,
      });

      datasets.push({
        label: "P70",
        data: metric.stats.P70,
        borderWidth: 1,
        backgroundColor: "rgba(164,183,193, 0.5)",
        borderColor: "rgba(164,183,193, 0.5)",
        fill: false,
      });

      datasets.push({
        label: "P50",
        data: metric.stats.P50,
        borderWidth: 1,
        backgroundColor: "rgba(77,189,116, 0.5)",
        borderColor: "rgba(77,189,116, 0.5)",
        fill: false,
      });

      datasets.push({
        label: "Average",
        data: metric.stats.Average,
        borderWidth: 1,
        //backgroundColor: "rgba(248,108,107, 1)",
        borderColor: "rgba(248,108,107, 1)",
        fill: false,
      });

      datasets.push({
        label: "Sum",
        data: metric.stats.Sum,
        borderWidth: 1,
        backgroundColor: "rgba(255,193,7, 0.5)",
        borderColor: "rgba(255,193,7, 0.5)",
        fill: false,
      });

      //if (metric.dimension.metricName === "RequestCount") {
      // metric.topTenants.forEach((dataPoint) => {
      //   dataPoint.forEach((data) => {
      //     if (!tenantMap.has(data.tenantId)) {
      //       tenantMap.set(data.tenantId, []);
      //     }
      //     console.log(`dataPoint: ${JSON.stringify(data)}`);
      //     let existingValues = tenantMap.get(data.tenantId);
      //     existingValues.push(data.value);
      //     tenantMap.set(data.tenantId, existingValues);
      //   });
      // });
      // //}
      // tenantMap.forEach((v, k, m) => {
      //   datasets.push({
      //     label: k,
      //     data: v,
      //   });
      // });
      console.log(`dataset: ${JSON.stringify(datasets)}`);
      const distinctPeriods = [...new Set(albstats.periods)];

      data.labels = distinctPeriods;
      data.datasets = datasets;
      metricData.data = data;

      metrics.set(metricData.name, metricData);
    });
  }
  let metricsToShow = [];

  metricsToShow.push(<GraphComponent loading={true} name={"Request Count"} />);
  metricsToShow.push(
    <GraphComponent loading={true} name={"2XX Request Count"} />
  );
  metricsToShow.push(
    <GraphComponent loading={true} name={"4XX Request Count"} />
  );
  metricsToShow.push(
    <GraphComponent loading={true} name={"5XX Request Count"} />
  );
  metricsToShow.push(
    <GraphComponent loading={true} name={"3XX Request Count"} />
  );

  metrics.forEach((m, k) => {
    console.log(k);
    metricsToShow.push(
      <Col lg="6">
        <Card>
          <CardHeader>{k}</CardHeader>
          <CardBody>
            <Line
              data={m.data}
              options={{
                title: {
                  display: true,
                  text: k,
                  fontSize: 12,
                },
              }}
            />
          </CardBody>
        </Card>
      </Col>
    );
  });
  return metricsToShow;
}

const GraphComponent = (props) => {
  const { name, loading } = props;

  return (
    <Col lg="6">
      <Card>
        <CardHeader>{name}</CardHeader>
        <CardBody>
          {loading && <div>Loading ... </div>}
          {/*{!loading && (*/}
          {/*  <Line*/}
          {/*    data={m.data}*/}
          {/*    options={{*/}
          {/*      title: {*/}
          {/*        display: true,*/}
          {/*        text: k,*/}
          {/*        fontSize: 12,*/}
          {/*      },*/}
          {/*    }}*/}
          {/*  />*/}
          {/*)}*/}
        </CardBody>
      </Card>
    </Col>
  );
};
