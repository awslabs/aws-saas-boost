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

import React, { Component, lazy } from 'react'
import { Switch, Route } from 'react-router-dom'

const OnboardingListContainer = lazy(() => import('./OnboardingListContainer'))
const OnboardingCreateContainer = lazy(() => import('./OnboardingCreateContainer'))
const OnboardingDetailContainer = lazy(() => import('./OnboardingDetailContainer'))

export class OnboardingContainer extends Component {
  render() {
    return (
      <Switch>
        <Route
          path="/onboarding"
          exact={true}
          name="Onboarding"
          component={OnboardingListContainer}
        />
        <Route
          path="/onboarding/request"
          exact={true}
          name="Request"
          component={OnboardingCreateContainer}
        />
        <Route
          exact={true}
          name="Detail"
          path="/onboarding/:id"
          component={OnboardingDetailContainer}
        />
      </Switch>
    )
  }
}

export default OnboardingContainer
