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

import React, { Suspense, lazy } from "react";
import { Switch, Route } from "react-router-dom";

// Containers
const DefaultLayout = lazy(() => import("./blueprints/DefaultLayout"));

const Routes = () => {
  const loading = () => (
    <div className="animated fadeIn pt-3 text-center">Loading...</div>
  );

  return (
    <Suspense fallback={loading()}>
      <Switch>
        <Route
          path="/"
          name="Home"
          render={(props) => <DefaultLayout {...props} />}
        />
      </Switch>
    </Suspense>
  );
};

export default Routes;
