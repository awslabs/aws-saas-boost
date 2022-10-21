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

import React, { Fragment, Component } from "react";

import { Nav, Container, Row, Col } from "react-bootstrap";
import { NavLink } from "react-router-dom";

class Sidebar extends Component {
  render() {
    return (
      <Fragment>
        <Container fluid>
          <Row>
            <Col className="pt-3 pl-4">
              <h5>AWS SaaS Boost</h5>
            </Col>
          </Row>

          <Nav className="pt-3 flex-column font-weight-bold">
            <Nav.Link
              to="/tenants"
              as={NavLink}
              key="tenants"
              className="text-dark"
            >
              Tenants
            </Nav.Link>
            <Nav.Link
              to="/users"
              as={NavLink}
              key="users"
              className="text-dark"
            >
              System Users
            </Nav.Link>
            <Nav.Link disabled key="settings">
              Settings
            </Nav.Link>
          </Nav>
        </Container>
      </Fragment>
    );
  }
}

export default Sidebar;
