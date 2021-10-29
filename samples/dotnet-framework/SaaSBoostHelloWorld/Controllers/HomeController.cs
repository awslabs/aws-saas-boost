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
using log4net;
using System;
using System.Web.Mvc;

namespace SaaSBoostHelloWorld.Controllers
{
    public class HomeController : Controller
    {
        private static readonly ILog LOGGER = LogManager.GetLogger(typeof(HomeController));

        // GET: Home
        public ActionResult Index()
        {
            LOGGER.Info("HomeController::Index");
            string tenantId = Environment.GetEnvironmentVariable("TENANT_ID");
            if (String.IsNullOrEmpty(tenantId))  {
                tenantId = "Unknown";
            }
            LOGGER.Info($"Setting Tenant ID to {tenantId}");
            ViewData["TenantId"] = tenantId;
            return View();
        }
    }
}