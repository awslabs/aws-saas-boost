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

import { fetchTenantsThunk, selectAllTenants, dismissError } from './ducks'
import TenantListComponent from './TenantListComponent'
import { useHistory } from 'react-router-dom'

export default function TenantListContainer() {
  const dispatch = useDispatch()
  const history = useHistory()
  const tenants = useSelector(selectAllTenants)
  const loading = useSelector((state) => state.tenants.loading) //TODO: move to tenants duck file.
  const error = useSelector((state) => state.tenants.error)

  const handleProvisionTenant = () => {
    history.push('/tenants/provision')
  }

  const handleTenantClick = (tenantId) => {
    history.push(`/tenants/${tenantId}`)
  }

  const handleRefresh = () => {
    dispatch(fetchTenantsThunk())
  }

  const handleError = () => {
    dispatch(dismissError())
  }

  useEffect(() => {
    const fetchTenants = dispatch(fetchTenantsThunk())
    return () => {
      if (fetchTenants.PromiseStatus === 'pending') {
        fetchTenants.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch]) //TODO: Follow up on the use of this dispatch function.

  return (
    <Fragment>
      <TenantListComponent
        tenants={tenants}
        loading={loading}
        error={error}
        handleProvisionTenant={handleProvisionTenant}
        handleTenantClick={handleTenantClick}
        handleRefresh={handleRefresh}
        handleError={handleError}
      />
    </Fragment>
  )
}
