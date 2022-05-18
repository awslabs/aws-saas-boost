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
import { SBInput } from './SaasBoostSignIn'
import { Alert } from 'reactstrap'
import { Auth } from '@aws-amplify/auth'
import { isEmpty } from '@aws-amplify/core'
import { cilEnvelopeClosed, cilUser } from '@coreui/icons'

const UsernameAttributes = {
  EMAIL: 'email',
  PHONE_NUMBER: 'phone_number',
  USERNAME: 'username',
}

export class SaasBoostAuthComponent extends Component {
  _validAuthStates = []
  constructor(props) {
    super(props)
    this.state = {}

    this._validAuthStates = []
    this.inputs = {}
    this.changeState = this.changeState.bind(this)
    this.error = this.error.bind(this)
    this.handleInputChange = this.handleInputChange.bind(this)
    this.renderUsernameField = this.renderUsernameField.bind(this)
    this.getUsernameFromInput = this.getUsernameFromInput.bind(this)
    this.dismiss = this.dismiss.bind(this)
    this.checkContact = this.checkContact.bind(this)
  }

  handleInputChange(e) {
    const { name, value, type, checked } = e.target
    const check_type = ['radio', 'checkbox'].includes(type)
    this.inputs[name] = check_type ? checked : value
    this.inputs['checkedValue'] = check_type ? value : null
  }

  triggerAuthEvent(event) {
    const state = this.props.authState
    if (this.props.onAuthEvent) {
      this.props.onAuthEvent(state, event, false)
    }
  }

  changeState(state, data) {
    if (this.props.onStateChange) {
      this.props.onStateChange(state, data)
    }

    this.triggerAuthEvent({
      type: 'stateChange',
      data: state,
    })
  }
  errorMessage(err) {
    if (typeof err === 'string') {
      return err
    }
    return err.message ? err.message : JSON.stringify(err)
  }

  error(err) {
    this.triggerAuthEvent({
      type: 'error',
      data: this.errorMessage(err),
    })
  }

  getUsernameFromInput() {
    const { usernameAttributes = 'username' } = this.props
    switch (usernameAttributes) {
      case UsernameAttributes.EMAIL:
        return this.inputs.email
      case UsernameAttributes.PHONE_NUMBER:
        return this.phone_number
      default:
        return this.inputs.username || this.state.username
    }
  }
  renderUsernameField() {
    const { usernameAttributes = [] } = this.props
    if (usernameAttributes === UsernameAttributes.EMAIL) {
      return (
        <SBInput
          autoFocus
          name="email"
          type="email"
          placeholder="Enter your email"
          icon={cilEnvelopeClosed}
          key="email"
        />
      )
    } else if (usernameAttributes === UsernameAttributes.PHONE_NUMBER) {
      return <div>Phone Field</div>
    } else {
      return (
        <SBInput
          name="username"
          placeholder="Enter your username"
          icon={cilUser}
          key="username"
          id="username"
        />
      )
    }
  }

  showError() {
    const { error } = this.state
    return (
      !!error && (
        <Alert color="danger" isOpen={!!error} toggle={this.dismiss}>
          {error && error.message}
        </Alert>
      )
    )
  }

  checkContact(user) {
    if (!Auth || typeof Auth.verifiedContact !== 'function') {
      throw new Error('No Auth module found, please ensure @aws-amplify/auth is imported')
    }
    Auth.verifiedContact(user).then((data) => {
      console.log(data)
      if (!isEmpty(data.verified)) {
        this.changeState('signedIn', user)
      } else {
        user = Object.assign(user, data)
        this.changeState('verifyContact', user)
      }
    })
  }

  dismiss() {
    this.setState({ error: null })
  }

  render() {
    if (!this._validAuthStates.includes(this.props.authState)) {
      return null
    }
    return this.showComponent()
  }
}

SaasBoostAuthComponent.propTypes = {
  authState: PropTypes.string,
  onAuthEvent: PropTypes.func,
  onStateChange: PropTypes.func,
  usernameAttributes: PropTypes.object,
}

export default SaasBoostAuthComponent
