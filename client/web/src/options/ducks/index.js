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

import { isEmpty } from 'lodash'
import optionsAPI from '../api'

export const fetchOptions = createAsyncThunk('options/fetch', async (...[, thunkAPI]) => {
  const { signal } = thunkAPI
  try {
    const options = await optionsAPI.fetchOptions({ signal })
    return options
  } catch (err) {
    if (optionsAPI.isCancel(err)) {
      return
    } else {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  }
})
const initialState = {
  loading: 'idle',
  error: null,
  data: {},
}
const optionsSlice = createSlice({
  name: 'options',
  initialState,
  reducers: {},
  extraReducers: {
    RESET: (state) => {
      return initialState
    },
    [fetchOptions.fulfilled]: (state, action) => {
      state.loading = 'idle'
      state.error = null
      state.data = action.payload
    },
    [fetchOptions.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
    },
    [fetchOptions.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload
    },
  },
})

export const { actions, reducer } = optionsSlice
export const selectOptions = (state) => state.options.data

export const selectDbUploadUrl = (state) => state.options?.data?.sqlUploadOptions?.url

export const selectDbOptions = (state) => state.options?.data?.dbOptions

export const selectOsOptions = (state) => state.options?.data?.osOptions

export const selectCertOptions = (state) => state.options?.data?.acmOptions

export const selectOSLabel = (state, os) => {
  return isEmpty(os) || isEmpty(state.options?.data?.osOptions)
    ? ''
    : state.options?.data?.osOptions[os]
}

export const selectDbLabel = (state, db) =>
  state.options.data?.dbOptions?.find((e) => e.name === db)?.description

export const selectIsOptionsLoading = (state) => state.options.loading !== 'idle'

export const selectOptionsError = (state) => state.options.error

export default reducer
