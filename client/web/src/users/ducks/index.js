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
import usersAPI from '../api'

const rejectedReducer = (state, action) => {
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

// Define normalizr entity schemas
const userEntity = new schema.Entity('users', {}, { idAttribute: 'username' })
const userListSchema = [userEntity]

const usersAdapter = createEntityAdapter({
  selectId: (user) => user.username,
})

//Thunks
export const fetchUsers = createAsyncThunk('users/fetchAll', async (...[, thunkAPI]) => {
  const { signal } = thunkAPI
  try {
    const response = await usersAPI.fetchAll({ signal })
    const normalized = normalize(response, userListSchema)
    return normalized.entities
  } catch (err) {
    console.error(err)
    return thunkAPI.rejectWithValue(err.message)
  }
})

export const fetchUser = createAsyncThunk('users/fetchUser', async (username, thunkAPI) => {
  const { signal } = thunkAPI
  try {
    const response = await usersAPI.fetch(username, { signal })
    return response
  } catch (err) {
    console.error(err)
    return thunkAPI.rejectWithValue(err.message)
  }
})

export const createdUser = createAsyncThunk('users/created', async (values, thunkAPI) => {
  try {
    const response = await usersAPI.create(values, thunkAPI)
    return response
  } catch (err) {
    console.error(err)
    return thunkAPI.rejectWithValue(err.message)
  }
})

export const editedUser = createAsyncThunk(
  'users/edited',
  async ({ values, history }, thunkAPI) => {
    try {
      const response = await usersAPI.update(values, thunkAPI)
      return response
    } catch (err) {
      console.error(err)
      return thunkAPI.rejectWithValue(err.message)
    }
  },
)

export const activatedUser = createAsyncThunk('users/activated', async (username, thunkAPI) => {
  try {
    const response = await usersAPI.activate(username, thunkAPI)
    return response
  } catch (err) {
    console.error(err)
    return thunkAPI.rejectWithValue(err.message)
  }
})

export const deactivatedUser = createAsyncThunk('users/deactivated', async (username, thunkAPI) => {
  try {
    const response = await usersAPI.deactivate(username, thunkAPI)
    return response
  } catch (err) {
    console.error(err)
    return thunkAPI.rejectWithValue(err.message)
  }
})

export const deletedUser = createAsyncThunk('users/deleted', async (username, thunkAPI) => {
  try {
    const response = await usersAPI.delete(username, thunkAPI)
    return response
  } catch (err) {
    console.error(err)
    return thunkAPI.rejectWithValue(err.message)
  }
})

const initialState = usersAdapter.getInitialState({
  loading: 'idle',
  error: null,
  detail: null,
})

//Slices
const usersSlice = createSlice({
  name: 'users',
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
    [fetchUsers.fulfilled]: (state, action) => {
      usersAdapter.upsertMany(state, action.payload.users)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [fetchUsers.pending]: pendingReducer,
    [fetchUsers.rejected]: rejectedReducer,
    [fetchUser.fulfilled]: (state, action) => {
      state.detail = action.payload
      usersAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      return state
    },
    [fetchUser.pending]: (state, action) => {
      state.detail = null
      state.loading = 'pending'
      state.error = null
      return state
    },
    [fetchUser.rejected]: (state, action) => {
      state.loading = 'idle'
      state.detail = null
      state.error = action.payload
      return state
    },
    [createdUser.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [createdUser.fulfilled]: (state, action) => {
      usersAdapter.upsertOne(state, action.payload)
      state.detail = null
      state.loading = 'idle'
      state.error = null
      return state
    },
    [createdUser.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload
      return state
    },
    [editedUser.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [editedUser.fulfilled]: (state, action) => {
      usersAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [editedUser.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.error
      return state
    },
    [activatedUser.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [activatedUser.fulfilled]: (state, action) => {
      usersAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [activatedUser.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload
      return state
    },
    [deactivatedUser.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [deactivatedUser.fulfilled]: (state, action) => {
      usersAdapter.upsertOne(state, action.payload)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [deactivatedUser.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload
      return state
    },
    [deletedUser.pending]: (state, action) => {
      state.loading = 'pending'
      state.error = null
      return state
    },
    [deletedUser.fulfilled]: (state, action) => {
      usersAdapter.removeOne(state, action.meta.arg)
      state.loading = 'idle'
      state.error = null
      return state
    },
    [deletedUser.rejected]: (state, action) => {
      state.loading = 'idle'
      state.error = action.payload
      return state
    },
    default: (state, action) => {
      return state
    },
  },
})

const { actions, reducer } = usersSlice

export const { fetchAll, dismissError } = actions
export const { selectAll: selectAllUsers, selectById: selectUserById } = usersAdapter.getSelectors(
  (state) => state.users,
)

export default reducer
