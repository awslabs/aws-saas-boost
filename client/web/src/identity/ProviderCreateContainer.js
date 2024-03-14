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
import {PropTypes} from 'prop-types'
import React, {Component} from 'react'
import ProviderForm from './ProviderFormComponent'
import {connect} from 'react-redux'
import {withRouter} from 'react-router'
import identityAPI from './api'
import {dismissError, createProviderThunk} from './ducks'

const mapDispatchToProps = {
    createProviderThunk,
    dismissError,
}

const mapStateToProps = (state, props) => {
    const {providerId} = props.match.params;
    const {providers} = state;
    const provider = !!providerId ? providers.entities[providerId] : undefined;
    //console.log('mapStateToProps: ', provider);

    return {
        providers: providers,
        provider: provider
    }
}

class ProviderCreateContainer extends Component {
    constructor(props) {
        super(props)
        this.state = {}

        this.saveProvider = this.saveProvider.bind(this)
        this.handleError = this.handleError.bind(this)
    }

    async saveProvider(values, {setSubmitting, resetForm}) {
        const provider = this.props.provider;
        const saveProvider = {
            type: provider.type,
            metadata: values.metadata
        }
        console.log('saveProvider: ', saveProvider);
        try {
            //const createdResponse = await createProviderThunk(saveProvider);
            const createdResponse = await identityAPI.create(saveProvider);
            if (!createdResponse.error) {
                const {history} = this.props
                history.goBack()
                //history.push(`/providers/${createdResponse.payload.id}`)
            } else {
                setSubmitting(false)
                resetForm({values})
            }
        } catch (e) {
            setSubmitting(false)
            resetForm({values})
        }
    }

    handleCancel = () => {
        const {history} = this.props
        history.goBack()
    }

    handleError = () => {
        const {dismissError} = this.props
        dismissError()
    }

    render() {
        const {error} = this.props.providers
        return (
            <ProviderForm
                handleCancel={this.handleCancel}
                handleSubmit={this.saveProvider}
                error={error}
                handleError={this.handleError}
            />
        )
    }
}

ProviderCreateContainer.propTypes = {
    createProviderThunk: PropTypes.func,
    history: PropTypes.object,
    providers: PropTypes.object,
    dismissError: PropTypes.func,
}

export const ProviderCreateContainerWithRouter = connect(
    mapStateToProps,
    mapDispatchToProps,
)(withRouter(ProviderCreateContainer))

export default ProviderCreateContainerWithRouter
