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
import { MetricListComponent } from "./MetricListComponent";
import { DashboardComponent } from "./DashboardComponent";
import { useDispatch, useSelector } from "react-redux";
import {
  queryMetrics,
  dismissError,
  selectError,
  selectLoading,
  selectAllMetricResults,
} from "./ducks";
//import { useHistory } from "react-router-dom";

export default function MetricListContainer() {
  const dispatch = useDispatch();
  //  const history = useHistory();

  const metrics = useSelector(selectAllMetricResults);
  const loading = useSelector(selectLoading); // => state.metrics.loading);
  const error = useSelector(selectError); // => state.metrics.error);
  const query = {
    id: "albstats",
    timeRangeName: "DAY_7",
    stat: "Sum",
    dimensions: [
      { metricName: "RequestCount", nameSpace: "AWS/ApplicationELB" },
      {
        metricName: "HTTPCode_Target_4XX_Count",
        nameSpace: "AWS/ApplicationELB",
      },
      {
        metricName: "HTTPCode_Target_2XX_Count",
        nameSpace: "AWS/ApplicationELB",
      },
      {
        metricName: "HTTPCode_Target_3XX_Count",
        nameSpace: "AWS/ApplicationELB",
      },
      {
        metricName: "HTTPCode_Target_5XX_Count",
        nameSpace: "AWS/ApplicationELB",
      },
    ],
    topTenants: true,
    statsMap: true,
  };

  const handleRefresh = () => {
    //dispatch(queryMetrics(query));
  };

  const handleError = () => {
    dispatch(dismissError());
  };

  // useEffect(() => {
  //   const fetchMetricsPromise = dispatch(queryMetrics(query));
  //   return () => {
  //     if (fetchMetricsPromise.PromiseStatus === "pending") {
  //       fetchMetricsPromise.abort();
  //     }
  //     dispatch(dismissError());
  //   };
  // }, [dispatch]);

  return (
    <DashboardComponent
      metrics={metrics}
      loading={loading}
      error={error}
      handleRefresh={handleRefresh}
      handleError={handleError}
    />
  );

  // return (
  //   <MetricListComponent
  //     metrics={metrics}
  //     loading={loading}
  //     error={error}
  //     handleRefresh={handleRefresh}
  //     handleError={handleError}
  //   />
  // );
}
