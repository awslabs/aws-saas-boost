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

import React, {useEffect, Fragment} from 'react'
import {useDispatch, useSelector} from 'react-redux'

import {fetchProvidersThunk, selectAllProviders, dismissError} from './ducks'
import ProviderListComponent from "./ProviderListComponent";
import {useHistory} from 'react-router-dom'
import LoadingOverlay from '@ronchalant/react-loading-overlay'


export default function ProviderListContainer() {
    const dispatch = useDispatch()
    const history = useHistory()
    const providers = useSelector(selectAllProviders)
    const loading = useSelector((state) => state.tiers.loading)
    const error = useSelector((state) => state.tiers.error)
    let selectedProvider = null;
    const handleProviderClick = (id) => {
        selectedProvider = id;
        //console.log('selectedProvider: ', selectedProvider);
    }
    const handleCreateProvider = () => {
        history.push(`/providers/${selectedProvider}`);
    }

    const handleRefresh = () => {
        dispatch(fetchProvidersThunk())
    }

    const handleError = () => {
        dispatch(dismissError())
    }

    useEffect(() => {
        const fetchProviders = dispatch(fetchProvidersThunk());
        //console.log('fetchProviders: ', fetchProviders);
        return () => {
            if (fetchProviders.PromiseStatus === 'pending') {
                console.log('pending....');
                fetchProviders.abort()
            }
            dispatch(dismissError())
        }
    }, [dispatch]) //TODO: Follow up on the use of this dispatch function.

    return (
        <Fragment>
            <LoadingOverlay
                active={loading === 'pending'}
                spinner
                text="Loading providers..."
            >
                <ProviderListComponent
                    providers={providers}
                    loading={loading}
                    error={error}
                    handleProviderClick={handleProviderClick}
                    handleCreateProvider={handleCreateProvider}
                    handleRefresh={handleRefresh}
                    handleError={handleError}
                />
            </LoadingOverlay>
        </Fragment>
    )
}
