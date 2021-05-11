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
import { TenantRoutes } from "../tenant";
import { UserRoutes } from "../users";
import { OnboardingRoutes } from "../onboarding";
import { SettingsRoutes } from "../settings";
import { MetricsRoutes } from "../metrics";

const Dashboard = React.lazy(() => import("../dashboard"));
const routes = [
  { path: "/", exact: true, name: "Home" },
  { path: "/summary", exact: true, name: "Summary", component: Dashboard },
].concat(
  TenantRoutes,
  UserRoutes,
  OnboardingRoutes,
  SettingsRoutes,
  MetricsRoutes
);

export default routes;
