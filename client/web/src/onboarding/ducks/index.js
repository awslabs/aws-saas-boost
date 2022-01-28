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
import onboardingAPI from '../api'
import MissingECRImageError from '../MissingECRImageError'
import ExceedLimitsError from '../ExceedLimitsError'

// Define normalizr entity schemas
const onboardingSchema = new schema.Entity('onboardings')
const onboardingListSchema = [onboardingSchema]

const onboardingAdapter = createEntityAdapter()

// Thunks
export const fetchOnboardings = createAsyncThunk(
  'onboardings/fetchAll',
  async (...[, thunkAPI]) => {
    const { signal } = thunkAPI
    try {
      const response = await onboardingAPI.fetchAll({ signal })
      const datesFixed = response.map((onboarding) => {
        return {
          ...onboarding,
          created: `${onboarding.created}Z`,
          modified: `${onboarding.modified}Z`,
        }
      })
      const normalized = normalize(datesFixed, onboardingListSchema)
      return normalized.entities
    } catch (err) {
      if (onboardingAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  },
)

export const fetchOnboarding = createAsyncThunk('onboardings/fetch', async (id, thunkAPI) => {
  const { signal } = thunkAPI
  try {
    const onboarding = await onboardingAPI.fetch(id, { signal })
    return {
      ...onboarding,
      created: `${onboarding.created}Z`,
      modified: `${onboarding.modified}Z`,
    }
  } catch (err) {
    if (onboardingAPI.isCancel(err)) {
      return
    } else {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  }
})

export const createOnboarding = createAsyncThunk(
  'onboardings/create',
  async (onboardingRequest, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const onboardingResponse = await onboardingAPI.create(onboardingRequest, {
        signal,
      })
      return onboardingResponse
    } catch (err) {
      console.error(err)
      if (err instanceof MissingECRImageError) {
        throw err
      }
      if (err instanceof ExceedLimitsError) {
        throw err
      }
      throw Error(err.message)
    }
  },
)
const initialState = onboardingAdapter.getInitialState({
  loading: 'idle',
  error: undefined,
  errorName: undefined,
})
const onboardingSlice = createSlice({
  name: 'onboarding',
  initialState,
  reducers: {
    dismissError(state, error) {
      state.error = undefined
      return state
    },
  },
  extraReducers: {
    RESET: (state) => {
      return initialState
    },
    [fetchOnboardings.fulfilled]: (state, action) => {
      if (action.payload !== undefined) {
        const { onboardings } = action.payload
        onboardingAdapter.setAll(state, onboardings ?? [])
      }

      state.loading = 'idle'
      state.error = null
      return state
    },
    [fetchOnboardings.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [fetchOnboardings.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload

      return state
    },
    [fetchOnboarding.fulfilled]: (state, action) => {
      console.log(action)
      if (action.payload !== undefined) {
        const onboarding = action.payload

        onboardingAdapter.upsertOne(state, onboarding)
      }
      state.loading = 'idle'
      state.error = null
      return state
    },
    [fetchOnboarding.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload
      return state
    },
    [createOnboarding.rejected]: (state, action) => {
      console.log(`createOnboarding.rejected ${JSON.stringify(action)}`)
      state.loading = 'idle'
      state.error = action.error.message
      state.errorName = action.error.name
      return state
    },
    [createOnboarding.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      state.errorName = null
      return state
    },
    default: (state, action) => {
      return state
    },
  },
})

export const selectLoading = (state) => state.onboardings.loading
export const selectError = (state) => state.onboardings.error
export const selectErrorName = (state) => state.onboardings.errorName

export const { actions, reducer } = onboardingSlice
export const { dismissError } = actions
export const { selectAll: selectAllOnboarding, selectById: selectOnboardingRequestById } =
  onboardingAdapter.getSelectors((state) => state.onboardings)

export default reducer
