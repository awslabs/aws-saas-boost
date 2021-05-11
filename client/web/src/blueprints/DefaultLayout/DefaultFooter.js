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

import React, { Component } from "react";
import PropTypes from "prop-types";
import { useSelector } from "react-redux";
import { selectSettingsById } from "../../settings/ducks";
import * as SETTINGS from "../../settings/common";

const propTypes = {
  children: PropTypes.node,
};

const DefaultFooter = (props) => {
  // eslint-disable-next-line
  const { children, ...attributes } = props;
  const version = useSelector((state) =>
    selectSettingsById(state, SETTINGS.VERSION)
  );
  const saasBoostEnvironment = useSelector((state) =>
    selectSettingsById(state, "SAAS_BOOST_ENVIRONMENT")
  );

  return (
    <React.Fragment>
      <span>
        <a rel="noopener noreferrer" href="http://aws.amazon.com">AWS</a> &copy; Amazon.com, Inc.
      </span>
      <span className="ml-auto">
        Version {version?.value} - {saasBoostEnvironment?.value}
      </span>
    </React.Fragment>
  );
};

export default DefaultFooter;
