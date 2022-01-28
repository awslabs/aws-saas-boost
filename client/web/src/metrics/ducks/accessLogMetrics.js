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

import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'

import metricsAPI from '../api'

export const SLICE_NAME = 'accessLogMetrics'

export const accessLogMetricsUrls = createAsyncThunk(
  'accessLogMetrics/signedUrls',
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

export const getAccessLogMetrics = createAsyncThunk(
  'accessLogMetrics/data',
  async ({ url, metric }, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const response = await metricsAPI.getAccessLogFile(url, { signal })
      return response
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

export const getTenantAccessLogs = createAsyncThunk(
  'accessLogMetrics/tenants',
  async ({ metric, timeRange, tenantId }, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const response = await metricsAPI.getAccessLogsByTenant(metric, timeRange, tenantId, {
        signal,
      })
      return response
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

const accessLogMetricsSlice = createSlice({
  name: SLICE_NAME,
  initialState: {
    urls: {},
    loading: 'idle',
    error: null,
    data: {},
  },
  reducers: {},
  extraReducers: {
    [accessLogMetricsUrls.fulfilled]: (state, action) => {
      const urls = action.payload
      if (urls?.length > 0) {
        urls.forEach((url) => {
          const name = Object.keys(url).shift()
          const value = url[name]
          state.urls[name] = value
        })
      } else {
        state.urls = {}
      }
      state.loading = 'idle'
      state.error = null
      return state
    },
    [accessLogMetricsUrls.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
    },
    [accessLogMetricsUrls.rejected]: (state, action) => {
      state.loading = 'idle'
      state.urls = {}
      state.error = action.payload
    },
    [getAccessLogMetrics.fulfilled]: (state, action) => {
      const { meta } = action
      const { arg } = meta
      const { metric } = arg

      state.loading = 'idle'
      state.data[metric] = action.payload
      return state
    },
    [getAccessLogMetrics.pending]: (state, action) => {
      state.loading = 'pending'
      state.data = {}
      return state
    },
    [getTenantAccessLogs.pending]: (state, action) => {
      const { meta } = action
      const { arg } = meta
      const { metric } = arg

      state.loading = 'pending'
      state.data[metric] = null
      return state
    },
    [getTenantAccessLogs.fulfilled]: (state, action) => {
      const { meta, payload } = action
      const { arg } = meta
      const { metric } = arg

      state.loading = 'idle'
      state.data[metric] = payload
      return state
    },
  },
})

export const selectLoading = (state) => state[SLICE_NAME].loading
export const selectError = (state) => state[SLICE_NAME].error
export const selectAccessLogUrlById = (state, id) => {
  return state[SLICE_NAME].urls[id]
}
export const selectAccessLogDatabyMetric = (state, metric) => {
  return state[SLICE_NAME].data[metric]
}

export const { actions, reducer } = accessLogMetricsSlice

export default reducer
