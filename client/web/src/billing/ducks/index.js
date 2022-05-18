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

import { createAsyncThunk, createSlice, createEntityAdapter } from '@reduxjs/toolkit'

import { normalize, schema } from 'normalizr'

import billingAPI from '../api'

const planSchema = new schema.Entity('billingPlans', {}, { idAttribute: 'planId' })
const planListSchema = [planSchema]

const planAdapter = createEntityAdapter({
  selectId: (entity) => entity.planId,
})

export const fetchPlans = createAsyncThunk('billingPlans/fetchAll', async (...[, thunkAPI]) => {
  const { signal } = thunkAPI
  try {
    const response = await billingAPI.fetchPlans({ signal })
    const normalized = normalize(response, planListSchema)
    return normalized.entities
  } catch (err) {
    if (billingAPI.isCancel(err)) {
      return
    } else {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  }
})

const initialState = planAdapter.getInitialState({
  loading: 'idle',
  error: undefined,
})
const PLAN_SLICE_NAME = 'billingPlans'
const planSlice = createSlice({
  name: PLAN_SLICE_NAME,
  initialState,
  reducers: {},
  extraReducers: {
    RESET: (state) => {
      return initialState
    },
    [fetchPlans.fulfilled]: (state, action) => {
      if (action.payload !== undefined) {
        const { billingPlans } = action.payload
        planAdapter.setAll(state, billingPlans ?? [])
      }
      state.loading = 'idle'
      state.error = null
      return state
    },
    [fetchPlans.pending]: (state) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [fetchPlans.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload

      return state
    },
  },
})

export const selectPlanLoading = (state) => state[PLAN_SLICE_NAME].loading
export const selectPlanError = (state) => state[PLAN_SLICE_NAME].error

export const billingPlans = planSlice.reducer

export const { selectAll: selectAllPlans, selectById: selectPlanById } = planAdapter.getSelectors(
  (state) => state[PLAN_SLICE_NAME],
)
