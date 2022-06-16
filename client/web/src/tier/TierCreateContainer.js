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
import TierForm from './TierFormComponent'
import { connect } from 'react-redux'
import { withRouter } from 'react-router'

import { dismissError, createTierThunk } from './ducks'

const mapDispatchToProps = {
  createTierThunk,
  dismissError,
}

const mapStateToProps = (state) => {
  return { tiers: state.tiers }
}

class TierCreateContainer extends Component {
  constructor(props) {
    super(props)
    this.state = {}

    this.saveTier = this.saveTier.bind(this)
    this.handleError = this.handleError.bind(this)
  }

  async saveTier(values, { setSubmitting, resetForm }) {
    const { createTierThunk, history } = this.props
    const tier = values
    const { id } = tier

    try {
      const createdResponse = await createTierThunk(tier)
      if (!createdResponse.error) {
        history.push(`/tiers/${createdResponse.payload.id}`)
      } else {
        setSubmitting(false)
        resetForm({ values })
      }
    } catch (e) {
      setSubmitting(false)
      resetForm({ values })
    }
  }

  handleCancel = () => {
    const { history } = this.props
    history.goBack()
  }

  handleError = () => {
    const { dismissError } = this.props
    dismissError()
  }

  render() {
    const { error } = this.props.tiers
    return (
      <TierForm
        handleCancel={this.handleCancel}
        handleSubmit={this.saveTier}
        error={error}
        handleError={this.handleError}
      />
    )
  }
}

TierCreateContainer.propTypes = {
  createTierThunk: PropTypes.func,
  history: PropTypes.object,
  tiers: PropTypes.object,
  dismissError: PropTypes.func,
}

export const TierCreateContainerWithRouter = connect(
  mapStateToProps,
  mapDispatchToProps,
)(withRouter(TierCreateContainer))

export default TierCreateContainerWithRouter
