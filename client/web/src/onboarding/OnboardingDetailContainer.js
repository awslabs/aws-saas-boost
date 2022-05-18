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
import React, { useEffect } from 'react'

import { useDispatch, useSelector } from 'react-redux'
import {
  fetchOnboarding,
  selectError,
  selectOnboardingRequestById,
  selectLoading,
  dismissError,
} from './ducks'

import { OnboardingDetailComponent } from './OnboardingDetailComponent'
import { useHistory } from 'react-router-dom'

OnboardingDetailContainer.propTypes = {
  match: PropTypes.object,
}

export default function OnboardingDetailContainer(props) {
  const dispatch = useDispatch()
  const history = useHistory()

  const { match } = props
  const { params } = match
  const { id } = params

  const onboarding = useSelector((state) => selectOnboardingRequestById(state, id))
  const loading = useSelector(selectLoading)
  const error = useSelector(selectError)

  const clearError = () => {
    dispatch(dismissError())
  }

  const refresh = () => {
    dispatch(fetchOnboarding(id))
  }

  const showTenant = (id) => {
    history.push(`/tenants/${id}`)
  }

  useEffect(() => {
    const fetchResponse = dispatch(fetchOnboarding(id))
    return () => {
      if (fetchResponse.PromiseStatus === 'pending') {
        console.log('Clean up onboarding list request')
        fetchResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [id, dispatch])

  return (
    <OnboardingDetailComponent
      onboarding={onboarding}
      loading={loading}
      error={error}
      clearError={clearError}
      refresh={refresh}
      showTenant={showTenant}
    />
  )
}
