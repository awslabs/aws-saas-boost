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

import {
  createAsyncThunk,
  createSlice,
  createEntityAdapter,
  createSelector,
} from '@reduxjs/toolkit'

import { normalize, schema } from 'normalizr'
import tenantAPI from '../api'

// Define normalizr entity schemas
const tenantEntity = new schema.Entity('tenants')
const tenantListSchema = [tenantEntity]
const tenantsAdapter = createEntityAdapter()

// Thunks
export const fetchTenantsThunk = createAsyncThunk('tenants/fetchAll', async (...[, thunkAPI]) => {
  const { signal } = thunkAPI
  //since no value is passed to this async fn()
  try {
    // const response = await tenantAPI.fetchAll();
    const response = await tenantAPI.fetchAllAxios({ signal })
    const datesFixed = response.map((tenant) => {
      return {
        ...tenant,
        created: `${tenant.created}Z`,
        modified: `${tenant.modified}Z`,
      }
    })
    // Normalize the data so reducers can load a predicatable payload, like:
    // `action.payload = { tenants: {} }`
    const normalized = normalize(datesFixed, tenantListSchema)
    return normalized.entities
  } catch (err) {
    if (tenantAPI.isCancel(err)) {
      return
    } else {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  }
})

export const fetchTenantThunk = createAsyncThunk(
  'tenants/fetchTenant',
  async (tenantId, thunkAPI) => {
    //since no value is passed to this async fn()
    try {
      const response = await tenantAPI.fetchTenant(tenantId)
      return {
        ...response,
        created: `${response.created}Z`,
        modified: `${response.modified}Z`,
      }
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

export const provisionTenantThunk = createAsyncThunk(
  'tenants/provision',
  async ({ values, history }, thunkAPI) => {
    try {
      const response = await tenantAPI.provisionTenant(values)
      return response
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

export const enableTenant = createAsyncThunk('tenants/enabled', async (tenantId, thunkAPI) => {
  const { signal } = thunkAPI

  try {
    const tenant = await tenantAPI.enableTenant(tenantId, { signal })
    return tenant
  } catch (err) {
    if (tenantAPI.isCancel(err)) {
      console.log('api cancelled request')
      return
    } else {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  }
})

export const disableTenant = createAsyncThunk('tenants/disabled', async (tenantId, thunkAPI) => {
  const { signal } = thunkAPI

  try {
    const tenant = await tenantAPI.disableTenant(tenantId, { signal })
    return tenant
  } catch (err) {
    if (tenantAPI.isCancel(err)) {
      console.log('api cancelled request')
      return
    } else {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  }
})

export const editedTenant = createAsyncThunk(
  'tenants/edited',
  async ({ values, history }, thunkAPI) => {
    try {
      const response = await tenantAPI.editTenant(values)
      return response
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

export const deleteTenant = createAsyncThunk(
  'tenants/deleted',
  async ({ tenantId, history }, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const tenant = await tenantAPI.deleteTenant(tenantId, { signal })
      history.push('/tenants')
      return tenant
    } catch (err) {
      if (tenantAPI.isCancel(err)) {
        console.log('api cancelled request')
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  },
)

const rejectedReducer = (state, action) => {
  //Handle when thunk was aborted

  if (action.meta.aborted) {
    state.error = {}
  }
  if (action.error) {
    state.error = action.payload
  }
  if (state.loading === 'pending') {
    state.loading = 'idle'
  }
  return state
}

const pendingReducer = (state, action) => {
  state.error = null
  if (state.loading === 'idle') {
    state.loading = 'pending'
  }
  return state
}
const initialState = tenantsAdapter.getInitialState({
  loading: 'idle',
  error: null,
  detail: null,
})
// Slices
const tenantsSlice = createSlice({
  name: 'tenants',
  initialState,
  reducers: {
    dismissError(state, error) {
      state.error = null
      return state
    },
  },
  extraReducers: {
    RESET: (state) => {
      return initialState
    },
    [fetchTenantsThunk.fulfilled]: (state, action) => {
      if (action.payload === undefined) {
        state.loading = 'idle'
        state.error = null
        return state
      }
      // Add tenants to state object
      tenantsAdapter.setAll(state, action.payload.tenants ?? [])

      state.loading = 'idle'
      state.error = null
      return state
    },
    [fetchTenantsThunk.pending]: pendingReducer,
    [fetchTenantsThunk.rejected]: rejectedReducer,
    [fetchTenantThunk.fulfilled]: (state, action) => {
      state.detail = action.payload
      tenantsAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      return state
    },

    [fetchTenantThunk.pending]: (state, action) => {
      state.detail = null
      state.loading = 'pending'
      state.error = null
    },
    [fetchTenantThunk.rejected]: (state, action) => {
      state.loading = 'idle'
      state.detail = null
      state.error = action.payload
    },
    [provisionTenantThunk.fulfilled]: (state, action) => {
      tenantsAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [provisionTenantThunk.pending]: pendingReducer,
    [provisionTenantThunk.rejected]: rejectedReducer,
    [editedTenant.fulfilled]: (state, action) => {
      tenantsAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [editedTenant.pending]: pendingReducer,
    [editedTenant.rejected]: rejectedReducer,
    [enableTenant.fulfilled]: (state, action) => {
      console.log(action)
      tenantsAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [enableTenant.pending]: pendingReducer,
    [enableTenant.rejected]: rejectedReducer,
    [disableTenant.fulfilled]: (state, action) => {
      console.log(action)
      tenantsAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [disableTenant.pending]: pendingReducer,
    [disableTenant.rejected]: rejectedReducer,
    [deleteTenant.fulfilled]: (state, action) => {
      console.log(action)
      // tenantsAdapter.(state, action.payload);
      state.loading = 'idle'
      state.error = null
      return state
    },
    [deleteTenant.pending]: pendingReducer,
    [deleteTenant.rejected]: rejectedReducer,
  },
})

const { actions, reducer } = tenantsSlice

export const { fetchAll, dismissError } = actions
export const { selectAll: selectAllTenants, selectById: selectTenantById } =
  tenantsAdapter.getSelectors((state) => state.tenants)

export const countTenantsByActiveFlag = createSelector(
  selectAllTenants,
  (_, isActive) => isActive,
  (tenants, isActive) => {
    return tenants.filter((t) => isActive === t.active).length
  },
)

export default reducer
