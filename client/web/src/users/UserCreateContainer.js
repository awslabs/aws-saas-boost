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
import { UserFormComponent } from './UserFormComponent'
import { connect } from 'react-redux'
import { withRouter } from 'react-router'

import { dismissError, createdUser } from './ducks'

const mapDispatchToProps = {
  createdUser,
  dismissError,
}

const mapStateToProps = (state) => {
  return { users: state.users }
}

class UserCreateContainer extends Component {
  constructor(props) {
    super(props)
    this.state = {}

    this.saveUser = this.saveUser.bind(this)
    this.handleError = this.handleError.bind(this)
  }

  async saveUser(values, { setSubmitting, resetForm }) {
    const { createdUser, history } = this.props
    const user = values
    const { username } = user

    try {
      const createdResponse = await createdUser(user)
      if (!createdResponse.error) {
        history.push(`/users/${username}`)
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
    const { error } = this.props.users
    return (
      <UserFormComponent
        handleCancel={this.handleCancel}
        handleSubmit={this.saveUser}
        error={error}
        handleError={this.handleError}
      />
    )
  }
}

UserCreateContainer.propTypes = {
  createdUser: PropTypes.func,
  history: PropTypes.object,
  users: PropTypes.object,
  dismissError: PropTypes.func,
}

export const UserCreateContainerWithRouter = connect(
  mapStateToProps,
  mapDispatchToProps,
)(withRouter(UserCreateContainer))

export default UserCreateContainerWithRouter
