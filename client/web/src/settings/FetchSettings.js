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
import { PropTypes } from 'prop-types'
import React, { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import {
  dismissError,
  fetchConfig,
  fetchSettings,
  selectAllSettings,
  selectConfig,
  selectLoading,
} from './ducks'
import { fetchOptions, selectOptions } from '../options/ducks'
import { isEmpty, size } from 'lodash'
import SBLoading from '../components/SBLoading'
import { fetchTiersThunk, selectAllTiers } from '../tier/ducks'

FetchSettings.propTypes = {
  children: PropTypes.object,
}

function FetchSettings(props) {
  const dispatch = useDispatch()
  const settings = useSelector(selectAllSettings)
  const loading = useSelector(selectLoading)
  const appConfig = useSelector(selectConfig)
  const options = useSelector(selectOptions)
  const tiers = useSelector(selectAllTiers)
  const [isAppConfigLoaded, setAppConfigLoaded] = useState(false)
  const [isTiersLoaded, setTiersLoaded] = useState(false)
  const [isSettingsLoaded, setSettingsLoaded] = useState(false)
  const [isOptionsLoaded, setOptionsLoaded] = useState(false)

  /**
   * If appConfig object is not empty
   *  AND the state value isn't true already. This is to avoid
   *  re-rendering the component if the redux state is updated.
   */
  if (!isEmpty(appConfig) && !isAppConfigLoaded) {
    setAppConfigLoaded(true)
  }

  if (!isEmpty(tiers) && !isTiersLoaded) {
    setTiersLoaded(true)
  }

  /**
   * If the configuration isn't loaded, fetch it now
   */
  useEffect(() => {
    let fetchConfigResponse
    if (!isAppConfigLoaded) {
      fetchConfigResponse = dispatch(fetchConfig())
    }
  }, [dispatch])

  useEffect(() => {
    let fetchTiersResponse
    if (!isTiersLoaded) {
      fetchTiersResponse = dispatch(fetchTiersThunk())
    }
  }, [dispatch])

  /**
   * If settings object is not empty
   *  AND the state value isn't true already. This is to avoid
   *  re-rendering the component if the redux state is updated.
   */
  if (size(settings) !== 0 && !isSettingsLoaded) {
    setSettingsLoaded(true)
  }
  /**
   * If the settings aren't loaded, fetch them now
   */
  useEffect(() => {
    let settingsResponse
    if (!isSettingsLoaded) {
      settingsResponse = dispatch(fetchSettings())
    }
    return () => {
      if (settingsResponse?.PromiseStatus === 'pending') {
        settingsResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])

  if (!isEmpty(options) && !isOptionsLoaded) {
    setOptionsLoaded(true)
  }

  useEffect(() => {
    let optionsResponse
    if (!isOptionsLoaded) {
      optionsResponse = dispatch(fetchOptions())
    }
    return () => {
      if (optionsResponse?.PromiseStatus === 'pending') {
        optionsResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])

  return isSettingsLoaded && isOptionsLoaded && isTiersLoaded && isAppConfigLoaded ? props.children : <SBLoading />
}

export default FetchSettings
