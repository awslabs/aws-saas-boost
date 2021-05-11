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

export default {
  items: [
    {
      name: "Summary",
      url: "/summary",
      icon: "icon-home",
    },
    {
      name: "Dashboard",
      icon: "icon-speedometer",
      children: [
        {
          name: "Requests",
          url: "/dashboard/alb",
          icon: "",
        },
        {
          name: "Compute",
          url: "/dashboard/ecs",
          icon: "",
        },
        {
          name: "Usage",
          url: "/dashboard/accesslogging",
          icon: "",
        },
      ],
    },
    {
      name: "Application",
      url: "/application",
      icon: "icon-power",
      badge: { variant: "danger", text: "SETUP" },
    },
    {
      name: "Onboarding",
      url: "/onboarding",
      icon: "icon-power",
      badge: {},
      attributes: { disabled: true },
    },
    {
      name: "Tenants",
      url: "/tenants",
      icon: "icon-layers",
      badge: {},
      attributes: { disabled: true },
    },
    { name: "Users", url: "/users", icon: "icon-people", badge: {} },
  ],
};
