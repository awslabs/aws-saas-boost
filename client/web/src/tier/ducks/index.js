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
  import tierAPI from '../api'
  
  // Define normalizr entity schemas
  const tierEntity = new schema.Entity('tiers')
  const tierListSchema = [tierEntity]
  const tiersAdapter = createEntityAdapter()
  
  // Thunks
  export const fetchTiersThunk = createAsyncThunk('tiers/fetchAll', async (...[, thunkAPI]) => {
    const { signal } = thunkAPI
    try {
      const response = await tierAPI.fetchAll({ signal })
      // Normalize the data so reducers can load a predicatable payload, like:
      // `action.payload = { tiers: {} }`
      const normalized = normalize(response, tierListSchema)
      return normalized.entities
    } catch (err) {
      if (tierAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  })
  
  export const fetchTierThunk = createAsyncThunk(
    'tiers/fetch',
    async (tierId, thunkAPI) => {
      const { signal } = thunkAPI

      try {
        return await tierAPI.fetchTier(tierId, { signal })
      } catch (err) {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    },
  )
  
  export const createTierThunk = createAsyncThunk(
    'tiers/create',
    async (tierData, thunkAPI) => {
      const { signal } = thunkAPI
    
      try {
        return await tierAPI.create(tierData, { signal })
      } catch (err) {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    },
  )
  
  export const updateTierThunk = createAsyncThunk(
    'tiers/update', 
    async ({ values }, thunkAPI) => {
      const { signal } = thunkAPI
  
      try {
        return await tierAPI.update(values, { signal })
      } catch (err) {
        if (tierAPI.isCancel(err)) {
          console.log('api cancelled request')
          return
        } else {
          console.error(err)
          return thunkAPI.rejectWithValue(err.message)
        }
      }
    },
  )
  
  export const deleteTierThunk = createAsyncThunk(
    'tiers/delete', 
    async (values, thunkAPI) => {
      const { signal } = thunkAPI
  
      try {
        return await tierAPI.delete(values, { signal })
      } catch (err) {
        if (tierAPI.isCancel(err)) {
          console.log('api cancelled request')
          return
        } else {
          console.error(err)
          return thunkAPI.rejectWithValue(err.message)
        }
      }
    }
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
  const initialState = tiersAdapter.getInitialState({
    loading: 'idle',
    error: null,
    detail: null,
  })
  // Slices
  const tiersSlice = createSlice({
    name: 'tiers',
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
      [fetchTiersThunk.fulfilled]: (state, action) => {
        if (action.payload === undefined) {
          state.loading = 'idle'
          state.error = null
          return state
        }
        // Add tiers to state object
        tiersAdapter.setAll(state, action.payload.tiers ?? [])
  
        state.loading = 'idle'
        state.error = null
        return state
      },
      [fetchTiersThunk.pending]: pendingReducer,
      [fetchTiersThunk.rejected]: rejectedReducer,

      [fetchTierThunk.fulfilled]: (state, action) => {
        state.detail = action.payload
        tiersAdapter.upsertOne(state, action.payload)
        state.loading = 'idle'
        return state
      },
      [fetchTierThunk.pending]: (state, action) => {
        state.detail = null
        state.loading = 'pending'
        state.error = null
      },
      [fetchTierThunk.rejected]: (state, action) => {
        state.loading = 'idle'
        state.detail = null
        state.error = action.payload
      },

      [createTierThunk.fulfilled]: (state, action) => {
        tiersAdapter.upsertOne(state, action.payload)
        state.loading = 'idle'
        state.error = null
        return state
      },
      [createTierThunk.pending]: pendingReducer,
      [createTierThunk.rejected]: rejectedReducer,

      [updateTierThunk.fulfilled]: (state, action) => {
        tiersAdapter.upsertOne(state, action.payload)
        state.loading = 'idle'
        state.error = null
        return state
      },
      [updateTierThunk.pending]: pendingReducer,
      [updateTierThunk.rejected]: rejectedReducer,

      [deleteTierThunk.fulfilled]: (state, action) => {
        console.log(action)
        state.loading = 'idle'
        state.error = null
        return state
      },
      [deleteTierThunk.pending]: pendingReducer,
      [deleteTierThunk.rejected]: rejectedReducer,
    },
  })
  
  const { actions, reducer } = tiersSlice
  
  export const { fetchAll, dismissError } = actions
  export const { selectAll: selectAllTiers, selectById: selectTierById } =
    tiersAdapter.getSelectors((state) => state.tiers)
  
  export default reducer
  