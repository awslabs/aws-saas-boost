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

import {normalize, schema} from 'normalizr'
import identityAPI from '../api'
import React from "react";

// Define normalizr entity schemas
const providerEntity = new schema.Entity('providers')
const providerListSchema = [providerEntity]
const providersAdapter = createEntityAdapter()

const getName = (type) => {
    if (type === 'AUTH0') {
        return 'Auth0';
    } else if (type === 'KEYCLOAK') {
        return 'Keycloak';
    } else {
        return 'Amazon Cognito';
    }
};

// Thunks
export const fetchProvidersThunk = createAsyncThunk('/providers/fetchAll', async (...[, thunkAPI]) => {
    const {signal} = thunkAPI
    try {
        const response = await identityAPI.fetchAll({signal});
        console.log('fetchProvidersThunk: ', response);
        let idFixed = response.map((provider) => {
            const name = getName(provider.type);
            return {
                ...provider,
                id: provider.type,
                name: name
            }
        });
        const normalized = normalize(idFixed, providerListSchema);
        return normalized.entities
    } catch (err) {
        if (identityAPI.isCancel(err)) {
            return
        } else {
            console.error(err)
            return thunkAPI.rejectWithValue(err.message)
        }
    }
})

export const createProviderThunk = createAsyncThunk('providers/create',
    async (providerData, thunkAPI) => {
        const {signal} = thunkAPI
        console.log('createProviderThunk.....');
        try {
            return await identityAPI.create(providerData, {signal})
        } catch (err) {
            console.error(err)
            return thunkAPI.rejectWithValue(err.message)
        }
    },
)

const rejectedReducer = (state, action) => {
    //Handle when thunk was aborted
    console.log('rejectedReducer: ', state, action);
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
const initialState = providersAdapter.getInitialState({
    loading: 'idle',
    error: null,
    detail: null,
})
// Slices
const providersSlice = createSlice({
    name: 'providers',
    initialState,
    reducers: {
        dismissError(state, error) {
            //console.log('fetchProvidersThunk.dismissError: ', state);
            state.error = null
            return state
        },
    },
    extraReducers: {
        RESET: (state) => {
            return initialState
        },
        [fetchProvidersThunk.fulfilled]: (state, action) => {
            //console.log('fetchProvidersThunk.fulfilled: ', state, action);
            if (action.payload === undefined) {
                state.loading = 'idle'
                state.error = null
                return state
            }
            // Add identities to state object

            providersAdapter.setAll(state, action.payload.providers ?? [])

            state.loading = 'idle'
            state.error = null
            return state
        },
        [fetchProvidersThunk.pending]: pendingReducer,
        [fetchProvidersThunk.rejected]: rejectedReducer,

        [createProviderThunk.fulfilled]: (state, action) => {
            console.log('createProviderThunk.fulfilled: ', state);
            //tiersAdapter.upsertOne(state, action.payload)
            state.loading = 'idle'
            state.error = null
            return state
        },
        [createProviderThunk.pending]: pendingReducer,
        [createProviderThunk.rejected]: rejectedReducer,

    },
})

const {actions, reducer} = providersSlice;

export const {fetchAll, dismissError} = actions
export const {selectAll: selectAllProviders, selectById: selectProviderById} =
    providersAdapter.getSelectors((state) => state.providers)

export default reducer
  