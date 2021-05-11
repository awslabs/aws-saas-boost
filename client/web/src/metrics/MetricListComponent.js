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
import {
  Table,
  Row,
  Col,
  Spinner,
  Button,
  Card,
  CardHeader,
  CardBody,
  Alert,
} from "reactstrap";

import { MetricListItemComponent } from "./MetricListItemComponent";

function showError(error, handleError) {
  return (
    <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
      <h4 className={"alert-heading"}>Error</h4>
      <p>{error}</p>
    </Alert>
  );
}

export const MetricListComponent = ({
  metrics,
  loading,
  error,
  handleRefresh,
  handleError,
}) => {
  const table = (
    <Table responsive hover>
      <thead>
        <tr>
          <th>Time</th>
        </tr>
      </thead>
      <tbody>
        {loading === "idle" &&
          metrics.map((metric) => (
            <MetricListItemComponent metric={metric} key={metric.time} />
          ))}
        {loading !== "idle" ? (
          <tr>
            <td colSpan="5" className="text-center">
              <Spinner animation="border" role="status">
                Loading...
              </Spinner>
            </td>
          </tr>
        ) : null}
      </tbody>
    </Table>
  );
  return (
    <div className="animated fadeIn">
      <Row>
        <Col>{error && showError(error, handleError)}</Col>
      </Row>
      <Row>
        <Col>
          <div className="mb-3 float-right">
            <Button color="secondary" className="mr-2" onClick={handleRefresh}>
              <span>
                <i className={"fa fa-refresh"} />
              </span>
            </Button>
          </div>
        </Col>
      </Row>
      <Row>
        <Col xl={12}>
          <Card>
            <CardHeader>
              <i className="fa fa-align-justify"></i> Metrics
            </CardHeader>
            <CardBody>{table}</CardBody>
          </Card>
        </Col>
      </Row>
    </div>
  );
};
