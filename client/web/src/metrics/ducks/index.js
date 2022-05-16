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
import metricsAPI from '../api'

const rejectedReducer = (state, action) => {
  if (action.type.startsWith('metrics/query')) {
    const queryId = action.meta.arg.id
    if (action.error) {
      if (!state.queryStatus[queryId]) {
        state.queryStatus[queryId] = {}
      }
      state.queryStatus[queryId].error = action.payload
    }
    if (state.queryStatus[queryId].loading === 'pending') {
      state.queryStatus[queryId].loading = 'idle'
    }
  }
  return state
}

const pendingReducer = (state, action) => {
  if (action.type.startsWith('metrics/query')) {
    const queryId = action.meta.arg.id
    if (!state.queryStatus[queryId]) {
      state.queryStatus[queryId] = {
        error: null,
        loading: 'pending',
      }
    } else {
      state.queryStatus[queryId].error = null
      state.queryStatus[queryId].loading = 'pending'
    }
  }
  return state
}

// Define normalizr entity schemas

const metricResultEntity = new schema.Entity('metrics', {}, { idAttribute: 'id' })
const metricResultListSchema = [metricResultEntity]
const metricQueryAdapter = createEntityAdapter({
  selectId: (entity) => entity.id,
})

const conditionDates = (response) => {
  const formatDate = (date) => {
    const d = new Date(`${date} UTC`)
    return `${d.getMonth() + 1}-${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d
      .getMinutes()
      .toString()
      .padStart(2, '0')}`
  }
  const metrics = response[0] || {}
  const fixedDates = {
    ...metrics,
    periods: metrics.periods?.map((p) => formatDate(p)),
  }
  return [fixedDates]
}

//Thunks
export const queryMetrics = createAsyncThunk('metrics/query', async (query, thunkAPI) => {
  const { signal } = thunkAPI
  try {
    //debugger;
    const response = await metricsAPI.query(query, { signal })
    const normalized = normalize(conditionDates(response), metricResultListSchema)
    return normalized.entities
    //return response;
  } catch (err) {
    console.error(err)
    return thunkAPI.rejectWithValue(err.message)
  }
})

export const accessLogMetrics = createAsyncThunk(
  'metrics/accesslog',
  async ({ query, timePeriod }, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const response = await metricsAPI.accessLogMetrics(query, timePeriod, {
        signal,
      })
      return response
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

export const accessLogMetricsUrls = createAsyncThunk(
  'metrics/accessLogsUrls',
  async (...[, thunkAPI]) => {
    const { signal } = thunkAPI
    try {
      const response = await metricsAPI.getAccessLogsSignedUrls({ signal })
      return response
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

const initialState = metricQueryAdapter.getInitialState({
  loading: 'idle',
  error: null,
  detail: null,
  queryStatus: {},
})

//Slices
const metricQuerySlice = createSlice({
  name: 'metrics',
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
    [queryMetrics.fulfilled]: (state, action) => {
      if (action.type.startsWith('metrics/query')) {
        const queryId = action.meta.arg.id

        metricQueryAdapter.upsertMany(state, action.payload.metrics)
        state.loading = 'idle'
        state.error = null

        state.queryStatus[queryId].loading = 'idle'
        state.queryStatus[queryId].error = null
      }

      return state
    },
    [queryMetrics.pending]: pendingReducer,
    [queryMetrics.rejected]: rejectedReducer,
    [accessLogMetricsUrls.fulfilled]: (state, action) => {},
  },
})

export const selectLoading = (state) => state.metrics.loading
export const selectError = (state) => state.metrics.error
export const selectQueryState = (state, id) => {
  if (state.metrics.queryStatus && !!state.metrics.queryStatus[id]) {
    return state.metrics.queryStatus[id]
  }
  return { loading: 'idle', error: null }
}

export const { actions, reducer } = metricQuerySlice

export const { query, dismissError } = actions
export const { selectAll: selectAllMetricResults, selectById: selectMetricResultsById } =
  metricQueryAdapter.getSelectors((state) => state.metrics)

export default reducer
