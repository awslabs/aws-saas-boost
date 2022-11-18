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

import LoadingOverlay from '@ronchalant/react-loading-overlay'
import OnboardingFormComponent from './OnboardingFormComponent'
import {
  createOnboarding,
  selectLoading,
  selectError,
  selectErrorName,
} from './ducks'
import { useDispatch, useSelector } from 'react-redux'
import { selectConfig } from '../settings/ducks'
import { selectAllPlans } from '../billing/ducks'
import { saveToPresignedBucket } from '../settings/ducks'
import { selectAllTiers } from '../tier/ducks'

export default function OnboardingCreateContainer() {
  const dispatch = useDispatch()
  const history = useHistory()
  const config = useSelector(selectConfig)
  const error = useSelector(selectError)
  const errorName = useSelector(selectErrorName)
  const loading = useSelector(selectLoading)
  const plans = useSelector(selectAllPlans)
  const tiers = useSelector(selectAllTiers)

  const [file, setFile] = useState({})

  const nullBlankProps = (obj) => {
    const ret = { ...obj }
    Object.keys(ret).forEach((key) => {
      const val = ret[key]
      if (val !== null && !val) {
        ret[key] = null
      }
    })
    return ret
  }

  const submitOnboardingRequestForm = async (
    values,
    { resetForm, setSubmitting }
  ) => {
    const { hasDomain, hasBilling, ...rest } = values
    const valsToSend = nullBlankProps(rest)
    let onboardingResponse
    try {
      onboardingResponse = await dispatch(createOnboarding(valsToSend))
      const presignedS3url = onboardingResponse.payload.zipFile
      if (presignedS3url && !!file && file.name) {
        await dispatch(
          saveToPresignedBucket({ dbFile: file, url: presignedS3url })
        )
      }
      history.push(`/onboarding/${onboardingResponse.payload.id}`)
    } catch (err) {
      resetForm({ values })
    } finally {
      setSubmitting(false)
    }

    if (!onboardingResponse.error) {
      history.push(`/onboarding/${onboardingResponse.payload.id}`)
    }
  }

  const cancel = () => {
    history.push('/onboarding')
  }

  const handleFileSelected = (file) => {
    console.log(file)
    setFile(file)
  }

  return (
    <LoadingOverlay active={!loading} spinner text="Loading...">
      <OnboardingFormComponent
        billingPlans={plans}
        cancel={cancel}
        config={config}
        error={error}
        errorName={errorName}
        loading={loading}
        onFileSelected={handleFileSelected}
        submit={submitOnboardingRequestForm}
        tiers={tiers}
      />
    </LoadingOverlay>
  )
}
