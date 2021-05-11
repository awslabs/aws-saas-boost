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

import React, { lazy, useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { _colors } from "./common";
import {
  selectAccessLogDatabyMetric,
  getTenantAccessLogs,
  selectLoading,
} from "./ducks/accessLogMetrics";
import { dismissError, selectTenantById } from "../tenant/ducks";

const AccessLogGraphComponent = lazy(() => import("./AccessLogGraphComponent"));

export default function AccessLogsMetricsContainer(props) {
  const {
    selectedTimePeriod,
    metric,
    title,
    label,
    colorIndex,
    selectedTenant,
  } = props;

  const dispatch = useDispatch();

  let data =
    useSelector((state) => selectAccessLogDatabyMetric(state, metric)) || [];
  let loading = useSelector((state) => selectLoading(state));

  let tenant = useSelector((state) => selectTenantById(state, selectedTenant));

  useEffect(() => {
    let response;
    try {
      response = dispatch(
        getTenantAccessLogs({
          metric,
          timeRange: selectedTimePeriod,
          tenantId: selectedTenant,
        })
      );
    } catch (err) {
      if (response.PromiseStatus === "pending") {
        response.abort();
      }
    }
  }, [dispatch, metric, selectedTimePeriod, selectedTenant]);

  return (
    <AccessLogGraphComponent
      title={title}
      label={label}
      colorIndex={colorIndex}
      data={data}
      loading={loading}
      tenantName={tenant.name}
    />
  );
}
