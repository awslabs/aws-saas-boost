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

import React, { useEffect } from 'react'
import { Switch, Route } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'

import {
  fetchSettings,
  updateSettings as apiUpddateSettings,
  selectAllSettings,
  dismissError,
  selectLoading,
} from './ducks'

import { SettingsFormComponent } from './SettingsFormComponent'

export function SettingsContainer(props) {
  const dispatch = useDispatch()
  const settings = useSelector(selectAllSettings)
  const loading = useSelector(selectLoading)

  useEffect(() => {
    const settingsResponse = dispatch(fetchSettings())
    return () => {
      if (settingsResponse.PromiseStatus === 'pending') {
        settingsResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])

  const updateSettings = async (values, formikBag) => {
    const { resetForm } = formikBag
    await dispatch(apiUpddateSettings(values))
    resetForm({ values })
    return
  }

  return (
    <Switch>
      <Route
        path="/settings"
        exact={true}
        name="Settings"
        render={(props) => (
          <SettingsFormComponent
            {...props}
            settings={settings}
            updateSettings={updateSettings}
            loading={loading}
          />
        )}
      />
    </Switch>
  )
}

export default SettingsContainer
