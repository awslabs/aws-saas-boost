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

import React, { useEffect, useState } from 'react'
import { useHistory } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import * as logger from 'loglevel'
import {
  dismissError,
  fetchOnboardings,
  selectAllOnboarding,
  selectError,
  selectLoading,
} from './ducks'
import globalConfig from '../config/appConfig'

import { OnboardingListComponent } from './OnboardingListComponent'

const log = logger.getLogger('onboarding')
log.setLevel(log.levels.INFO)

export default function OnboardingListContainer() {
  const [showEcrPushModal, setShowEcrPushModal] = useState(false)
  const dispatch = useDispatch()
  const history = useHistory()

  const onboardings = useSelector(selectAllOnboarding)
  const loading = useSelector(selectLoading)
  const error = useSelector(selectError)

  const showOnboardRequestForm = () => {
    history.push('/onboarding/request')
  }

  const clickOnboardingRequest = (id) => {
    history.push(`/onboarding/${id}`)
  }

  const clickTenantDetails = (id) => {
    history.push(`/tenants/${id}`)
  }

  const clearError = () => {
    dispatch(dismissError())
  }

  const refresh = () => {
    dispatch(fetchOnboardings())
  }

  const toggleEcrPushModal = () => {
    setShowEcrPushModal((state) => !state)
  }

  useEffect(() => {
    const onboardingResponse = dispatch(fetchOnboardings())
    return () => {
      if (onboardingResponse.PromiseStatus === 'pending') {
        console.log('Clean up onboarding list request')
        onboardingResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])

  return (
    <OnboardingListComponent
      clickOnboardingRequest={clickOnboardingRequest}
      dismissError={clearError}
      doRefresh={refresh}
      error={error}
      loading={loading}
      onboardingRequests={onboardings}
      showOnboardRequestForm={showOnboardRequestForm}
      awsAccount={globalConfig.awsAccount}
      awsRegion={globalConfig.region}
      showEcrPushModal={showEcrPushModal}
      toggleEcrPushModal={toggleEcrPushModal}
      clickTenantDetails={clickTenantDetails}
    />
  )
}
