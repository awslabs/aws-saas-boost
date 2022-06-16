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

import React, { useEffect, Fragment } from 'react'
import { useDispatch, useSelector } from 'react-redux'

import { fetchTiersThunk, selectAllTiers, dismissError } from './ducks'
import TierListComponent from './TierListComponent'
import { useHistory } from 'react-router-dom'
import LoadingOverlay from '@ronchalant/react-loading-overlay'

export default function TierListContainer() {
  const dispatch = useDispatch()
  const history = useHistory()
  const tiers = useSelector(selectAllTiers)
  const loading = useSelector((state) => state.tiers.loading)
  const error = useSelector((state) => state.tiers.error)

  const handleTierClick = (tierId) => {
    history.push(`/tiers/${tierId}`)
  }

  const handleCreateTier = () => {
    history.push(`/tiers/create`)
  }

  const handleRefresh = () => {
    dispatch(fetchTiersThunk())
  }

  const handleError = () => {
    dispatch(dismissError())
  }

  useEffect(() => {
    const fetchTiers = dispatch(fetchTiersThunk())
    return () => {
      if (fetchTiers.PromiseStatus === 'pending') {
        fetchTiers.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch]) //TODO: Follow up on the use of this dispatch function.

  return (
    <Fragment>
      <LoadingOverlay
          active={loading === 'pending'}
          spinner
          text="Loading tiers..."
        >
        <TierListComponent
          tiers={tiers}
          loading={loading}
          error={error}
          handleTierClick={handleTierClick}
          handleCreateTier={handleCreateTier}
          handleRefresh={handleRefresh}
          handleError={handleError}
        />
      </LoadingOverlay>
    </Fragment>
  )
}
