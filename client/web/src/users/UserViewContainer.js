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
import UserViewComponent from './UserViewComponent'
import { UserFormComponent } from './UserFormComponent'
import { connect } from 'react-redux'
import { withRouter } from 'react-router'

import {
  fetchUser,
  dismissError,
  editedUser,
  deletedUser,
  activatedUser,
  deactivatedUser,
} from './ducks'

const mapDispatchToProps = {
  fetchUser: fetchUser,
  dismissError,
  editedUser,
  deactivatedUser,
  activatedUser,
  deletedUser,
}

const mapStateToProps = (state, props) => {
  const { match } = props
  const { params } = match
  const { username } = params
  const { users } = state
  return {
    detail: !!username ? users.entities[username] : undefined,
    loading: users.loading,
    error: users.error,
  }
}

class UserViewContainer extends Component {
  constructor(props) {
    super(props)
    this.state = {
      username: null,
      isEditing: false,
    }

    this.toggleEdit = this.toggleEdit.bind(this)
    this.saveUser = this.saveUser.bind(this)
    this.handleActiveToggle = this.handleActiveToggle.bind(this)
    this.handleError = this.handleError.bind(this)
    this.deleteUser = this.deleteUser.bind(this)
  }

  async componentDidMount() {
    const { match, fetchUser } = this.props
    const { params } = match
    const { username } = params

    if (username != null) {
      try {
        await fetchUser(username)
        this.setState((state) => {
          if (username === state.username) {
            return state
          }
          return {
            username: username,
          }
        })
      } catch (err) {
        console.error('Error when dispatching user fetch.')
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

  async saveUser(values, { setSubmitting }) {
    // dispatch save user call
    const { editedUser } = this.props

    try {
      await editedUser({ values })
      setSubmitting(false)
      this.toggleEdit()
    } catch (e) {
      // hanlded by editUser, only need to set submitting to false
      setSubmitting(false)
    }
  }

  async deleteUser(username, setSubmitting, toggleDeleteModal) {
    const { deletedUser, history } = this.props
    try {
      const deletedUserResponse = await deletedUser(username)
      if (!deletedUserResponse.error) {
        history.push('/users')
      } else {
        toggleDeleteModal()
        setSubmitting(false)
      }
    } catch (e) {
      console.error(e)
      setSubmitting(false)
    }
  }

  handleError() {
    this.props.dismissError()
  }

  handleActiveToggle(action, username) {
    const { activatedUser, deactivatedUser } = this.props
    switch (action) {
      case 'activate': {
        activatedUser(username)
        break
      }
      case 'deactivate': {
        deactivatedUser(username)
        break
      }
      default: {
        // do nothing
      }
    }
  }

  render() {
    const { detail, loading, error } = this.props
    const { isEditing } = this.state

    return (
      <>
        {isEditing ? (
          <UserFormComponent
            user={detail}
            error={error}
            handleCancel={this.toggleEdit}
            handleSubmit={this.saveUser}
            handleError={this.handleError}
          />
        ) : (
          <UserViewComponent
            user={detail}
            loading={loading}
            error={error}
            toggleEdit={this.toggleEdit}
            handleActiveToggle={this.handleActiveToggle}
            handleError={this.handleError}
            deleteUser={this.deleteUser}
          />
        )}
      </>
    )
  }
}

UserViewContainer.propTypes = {
  match: PropTypes.object,
  fetchUser: PropTypes.func,
  dismissError: PropTypes.func,
  error: PropTypes.string,
  editedUser: PropTypes.func,
  deletedUser: PropTypes.func,
  history: PropTypes.object,
  activatedUser: PropTypes.func,
  deactivatedUser: PropTypes.func,
  loading: PropTypes.string,
  detail: PropTypes.object,
}

export const UserViewContainerWithRouter = connect(
  mapStateToProps,
  mapDispatchToProps,
)(withRouter(UserViewContainer))

export default UserViewContainerWithRouter
