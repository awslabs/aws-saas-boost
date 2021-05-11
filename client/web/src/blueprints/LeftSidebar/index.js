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

import PropTypes from "prop-types";
import { Navbar, Nav, Container } from "react-bootstrap";

import Sidebar from "../../components/Layout/Sidebar";
import CurrentUserContainer from "../../components/Auth/CurrentUserContainer";

const LeftSidebar = (props) => {
  const { children } = props;

  return (
    <div className="d-flex flex-column" id="wrapper">
      <Navbar
        bg="dark"
        expand="md"
        variant="dark"
        className="navbar-h"
        sticky="top"
      >
        <Navbar.Brand>
          <img
            src="/APN_logos_Sass-Factory_white.png"
            alt="APN SaaS Factory"
            className="d-inline-block align-top"
            height="30"
          />
        </Navbar.Brand>

        <Navbar.Collapse className="justify-content-end">
          <Navbar.Text>
            <CurrentUserContainer />
          </Navbar.Text>
        </Navbar.Collapse>
      </Navbar>

      <div id="page-content-wrapper" className="d-flex">
        <Nav className="bg-white border-right" id="sidebar-wrapper">
          <Sidebar />
        </Nav>
        <Container
          fluid
          id="page-content"
          children={children}
          className="bg-light vh-100 p-4"
        />
      </div>
    </div>
  );
};

LeftSidebar.propTypes = {
  children: PropTypes.node,
};

export default LeftSidebar;
