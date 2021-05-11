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
import { Row, Col, Card, CardBody } from "reactstrap";

export default function ApplicationDomainComponent(props) {
  return (
    <Col xs={1} md={3} lg={3}>
      <Card className="text-white pb-3 bg-warning">
        <CardBody className="pb-0">
          <div className="text-value">{`{string}`}</div>
          <div>Application Domain</div>
        </CardBody>
      </Card>
    </Col>
  );
}
