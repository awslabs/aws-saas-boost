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
} from '@reduxjs/toolkit'

import { normalize, schema } from 'normalizr'
import settingsAPI from '../api'

// Define normalizr entity schemas
const settingSchema = new schema.Entity('settings', {}, { idAttribute: 'name' })
const settingsListSchema = [settingSchema]

const settingAdapter = createEntityAdapter({
  selectId: (entity) => entity.name,
})

export const fetchSettings = createAsyncThunk(
  'settings/fetchAll',
  async (...[, thunkAPI]) => {
    const { signal } = thunkAPI
    try {
      const response = await settingsAPI.fetchAll({ signal })
      const normalized = normalize(response, settingsListSchema)
      return normalized.entities
    } catch (err) {
      if (settingsAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  }
)

export const updateSettings = createAsyncThunk(
  'settings/update',
  async (settings, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      await settingsAPI.updateSettings(settings, { signal })
      return
    } catch (err) {
      if (settingsAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  }
)

export const fetchConfig = createAsyncThunk(
  'settings/fetchConfig',
  async (config, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const response = await settingsAPI.fetchConfig({ signal })
      return response
    } catch (err) {
      if (settingsAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  }
)

export const updateConfig = createAsyncThunk(
  'settings/updateConfig',
  async (config, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const updateConfigResponse = await settingsAPI.updateConfig(config, {
        signal,
      })
      return updateConfigResponse
    } catch (err) {
      if (settingsAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  }
)

export const createConfig = createAsyncThunk(
  'settings/createConfig',
  async (config, thunkAPI) => {
    const { signal } = thunkAPI
    try {
      const createConfigResponse = await settingsAPI.createConfig(config, {
        signal,
      })
      return createConfigResponse
    } catch (err) {
      if (settingsAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  }
)

export const fetchDbOptions = createAsyncThunk(
  'settings/options',
  async (...[, thunkAPI]) => {
    const { signal } = thunkAPI
    try {
      const dbOptions = await settingsAPI.fetchDbOptions({ signal })
      return dbOptions
    } catch (err) {
      if (settingsAPI.isCancel(err)) {
        return
      } else {
        console.error(err)
        return thunkAPI.rejectWithValue(err.message)
      }
    }
  }
)

export const saveToPresignedBucket = createAsyncThunk(
  'settings/dbFile',
  async ({ dbFile, url }, thunkAPI) => {
    try {
      const putBucketResponse = await settingsAPI.putToPresignedBucket(
        url,
        dbFile
      )
      return putBucketResponse
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  }
)

const initialState = settingAdapter.getInitialState({
  loading: 'idle',
  error: undefined,
  config: {
    data: {},
    error: undefined,
    message: undefined,
    loading: 'idle',
  },
  settings: [],
  setup: true,
})

const settingSlice = createSlice({
  name: 'setting',
  initialState,
  reducers: {
    dismissError(state, error) {
      state.error = null
      return state
    },
    dismissConfigMessage(state, message) {
      state.config.message = null
      return state
    },
    dismissConfigError(state, error) {
      state.config.error = null
      return state
    },
  },
  extraReducers: {
    RESET: (state) => {
      return initialState
    },
    [fetchSettings.fulfilled]: (state, action) => {
      if (action.payload !== undefined) {
        const { settings } = action.payload
        if (!!settings) {
          settingAdapter.setAll(state, settings)
        } else {
          settingAdapter.removeAll()
        }
      }

      state.loading = 'idle'
      state.error = null
      return state
    },

    [fetchSettings.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [fetchSettings.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload

      return state
    },
    [fetchConfig.fulfilled]: (state, action) => {
      const appConfig = action.payload
      state.config.data = appConfig
      if (appConfig.name && appConfig.name !== '') {
        state.setup = false
      } else {
        state.setup = true
      }
      return state
    },
    [fetchSettings.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [fetchSettings.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload

      return state
    },
    [createConfig.fulfilled]: (state, action) => {
      const appConfig = action.payload
      state.config.loading = 'idle'
      state.config.error = null
      state.config.message = 'Config created successfully'
      state.config.data = appConfig
      if (appConfig.name && appConfig.name !== '') {
        state.setup = false
      } else {
        state.setup = true
      }
      return state
    },
    [createConfig.pending]: (state, action) => {
      state.config.loading = 'pending'
      state.config.error = null
      return state
    },
    [createConfig.rejected]: (state, action) => {
      state.config.loading = 'idle'
      state.config.error = action.payload
      state.config.message = null
      return state
    },
    [updateConfig.fulfilled]: (state, action) => {
      state.config.loading = 'idle'
      state.config.error = null
      const appConfig = action.payload
      state.config.data = appConfig
      if (appConfig.name && appConfig.name !== '') {
        state.setup = false
      } else {
        state.setup = true
      }

      return state
    },
    [updateConfig.pending]: (state, action) => {
      state.config.loading = 'pending'
      state.config.error = null
      state.config.message = null
      return state
    },
    [updateConfig.rejected]: (state, action) => {
      state.config.loading = 'idle'
      state.config.error = action.payload
      state.config.message = null
      return state
    },
  },
})

export const selectLoading = (state) => state.settings.loading
export const selectError = (state) => state.settings.error
export const selectConfigLoading = (state) => state.settings.config.loading
export const selectConfigError = (state) => state.settings.config.error
export const selectConfigMessage = (state) => state.settings.config.message
export const selectConfig = (state) => state.settings.config.data

export const selectServiceToS3BucketMap = (state) => {
  const appConfig = state.settings.config.data
  if (!!appConfig?.services) {
    const keys = Object.keys(appConfig?.services)
    return keys.reduce((prev, curr) => {
      const map = {
        ...prev,
        [curr]:
          appConfig?.services[curr].database?.bootstrapFilename,
      }
      return map
    }, {})
  }
  return {}
}

export const { actions, reducer } = settingSlice
export const {
  dismissError,
  dismissConfigError,
  dismissConfigMessage,
} = actions
export const {
  selectAll: selectAllSettings,
  selectById: selectSettingsById,
} = settingAdapter.getSelectors((state) => state.settings)

export default reducer
