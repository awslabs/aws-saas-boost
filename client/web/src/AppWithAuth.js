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

import React, { Component, lazy, Suspense } from 'react'

import { Authenticator, Loading } from 'aws-amplify-react'
import { SaasBoostLoading, SaasBoostVerifyContact } from './components/Auth'
import App from './App'
import IdleTimer from 'react-idle-timer'
import { Auth, Hub } from 'aws-amplify'
import { AuthSessionStorage } from './utils/AuthSessionStorage'
import store from './store/index'
const SaasBoostSignIn = lazy(() => import('./components/Auth/SaasBoostSignIn'))
const SaasBoostForgotPassword = lazy(() => import('./components/Auth/SaasBoostForgotPassword'))
const SaasBoostResetPassword = lazy(() => import('./components/Auth/SaasBoostResetPassword'))
const SaasBoostRequireNewPassword = lazy(() =>
  import('./components/Auth/SaasBoostRequireNewPassword'),
)

const amplifyConfig = {
  Auth: {
    region: process.env.REACT_APP_AWS_REGION,
    userPoolId: process.env.REACT_APP_COGNITO_USERPOOL,
    userPoolWebClientId: process.env.REACT_APP_CLIENT_ID,
    authenticationFlowType: 'USER_SRP_AUTH',
  },
  storage: AuthSessionStorage,
}

const timeout = Number(process.env.REACT_APP_TIMEOUT) || 600000
const minutes = timeout / (60 * 1000)

Hub.listen('auth', (data) => {
  const { payload } = data
  const { event } = payload

  switch (event) {
    case 'signOut':
      console.log('dispatching reset')
      store.dispatch({ type: 'RESET' })
      break
    case 'configured':
      break
    default:
      console.log('Hub::catchAll - ' + JSON.stringify(payload.event))
  }
})

class AppWithAuth extends Component {
  constructor(props) {
    super(props)

    this.state = {
      signOutReason: undefined,
    }

    this.onIdle = this.onIdle.bind(this)
    this.dismissSignOutReason = this.dismissSignOutReason.bind(this)
  }

  loading = () => (
    <div className="pt-3 text-center">
      <div className="sk-spinner sk-spinner-pulse">Loading...</div>
    </div>
  )

  async onIdle() {
    try {
      const session = await Auth.currentSession()
      if (session.isValid()) {
        this.setState({
          signOutReason: `Session closed due to ${minutes} minutes of inactivity.`,
        })
        return Auth.signOut()
      }
    } catch (e) {
      // do nothing
    }
  }

  dismissSignOutReason() {
    this.setState({ signOutReason: undefined })
  }

  render() {
    const { signOutReason } = this.state
    return (
      <Suspense fallback={this.loading()}>
        <Authenticator hideDefault={true} amplifyConfig={amplifyConfig}>
          <SaasBoostSignIn
            signOutReason={signOutReason}
            dismissSignOutReason={this.dismissSignOutReason}
          />
          <SaasBoostForgotPassword />
          <SaasBoostResetPassword />
          <SaasBoostRequireNewPassword />
          <App />
          <SaasBoostVerifyContact />
          <SaasBoostLoading override={Loading}></SaasBoostLoading>
        </Authenticator>
        <IdleTimer onIdle={this.onIdle} debounce={250} timeout={timeout} />
      </Suspense>
    )
  }
}

export default AppWithAuth
