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
import { PropTypes } from 'prop-types'
import React, { Component } from 'react'
import { connect } from 'react-redux'
import { unwrapResult } from '@reduxjs/toolkit'
import { withRouter } from 'react-router'
import LoadingOverlay from '@ronchalant/react-loading-overlay'

import TierForm from './TierFormComponent'
import {
  fetchTiersThunk,
  fetchTierThunk,
  deleteTierThunk,
  updateTierThunk,
  dismissError,
} from './ducks'
import TierViewComponent from './TierViewComponent'

const mapDispatchToProps = {
  fetchTiersThunk,
  fetchTierThunk,
  dismissError,
  updateTierThunk,
  deleteTierThunk,
}

const mapStateToProps = (state, props) => {
  const { tierId } = props.match.params
  const { tiers } = state
  const tier = !!tierId ? tiers.entities[tierId] : undefined

  return {
    tier: tier,
    loading: tiers.loading,
    error: tiers.error,
  }
}

class TierContainer extends Component {
  constructor(props) {
    super(props)
    this.state = {
      tierId: null,
      isEditing: false,
    }
    this.toggleEdit = this.toggleEdit.bind(this)
    this.saveTier = this.saveTier.bind(this)
    this.handleError = this.handleError.bind(this)
    this.deleteTier = this.deleteTier.bind(this)
  }

  async componentDidMount() {
    const { tierId } = this.props.match.params

    if (tierId != null) {
      try {
        this.setState((state, props) => {
          if (tierId === state.tierId) {
            return state
          }
          return { tierId: tierId }
        })
      } catch (err) {
        console.error('error when dispatching tier thunk.', err)
      }
    }
  }

  toggleEdit() {
    const { error } = this.props
    if (error) {
      const { dismissError } = this.props
      dismissError()
    }
    this.setState((state) => ({ isEditing: !state.isEditing }))
  }

  saveTier(values, { setSubmitting }) {
    const dispatchResponse = this.props.updateTierThunk({ values: values })
    dispatchResponse
      .then(unwrapResult)
      .then(() => {
        setSubmitting(false)
        this.toggleEdit()
      })
      .catch((e) => {
        setSubmitting(false)
      })
  }

  deleteTier() {
    const valToSend = {
      tierId: this.state.tierId,
      history: this.props.history,
    }
    this.props.deleteTierThunk(valToSend)
  }

  handleError() {
    this.props.dismissError()
  }

  render() {
    const { tier, error, dismissError, loading } = this.props
    const { tierId, isEditing } = this.state
    return (
      <div>
        <LoadingOverlay
          active={(loading === 'pending')}
          spinner
        >
          {!isEditing && (
            <TierViewComponent
              tier={tier}
              tierId={tierId}
              loading={loading}
              error={error}
              handleError={this.handleError}
              toggleEdit={this.toggleEdit}
              deleteTier={this.deleteTier}
            />
          )}
          {isEditing && (
            <TierForm
              tier={tier}
              error={error}
              handleCancel={this.toggleEdit}
              handleSubmit={this.saveTier}
              dismissError={dismissError}
            />
          )}
        </LoadingOverlay>
      </div>
    )
  }
}

TierContainer.propTypes = {
  fetchTiersThunk: PropTypes.func,
  fetchTierThunk: PropTypes.func,
  dismissError: PropTypes.func,
  updateTierThunk: PropTypes.func,
  deleteTierThunk: PropTypes.func,
}

export const TierContainerWithRouter = connect(
  mapStateToProps,
  mapDispatchToProps
)(withRouter(TierContainer))

export default TierContainerWithRouter
