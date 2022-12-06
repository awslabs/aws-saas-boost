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

import React, { Fragment, Suspense, useState } from 'react'
import { useAuth } from 'react-oidc-context'
import App from './App'
import IdleTimer from 'react-idle-timer'
import { OidcSignIn } from './components/Auth'
import appConfig from './config/appConfig'
import { removeUserInfo, saveUserInfo } from './api/common'

const AppWithAuth = () => {
  const [signOutReason, setSignOutReason] = useState()
  const auth = useAuth()
  const timeout = Number(process.env.REACT_APP_TIMEOUT) || 600000
  const minutes = timeout / (60 * 1000)

  const loading = () => (
    <div className="animated fadeIn pt-1 text-center">Loading...</div>
  )

  const onIdle = async () => {
    const signOutReason = `Session closed due to ${minutes} minutes of inactivity.`
    signout(signOutReason)
  }

  const signout = async (signOutReason) => {
    setSignOutReason(signOutReason)
    try {
      if (appConfig.idp.toUpperCase() === 'COGNITO') {
        // since Cognito doesn't support the end_session_endpoint in openid-configuration,
        // we need to send a manual redirect to log out
        const path = `${appConfig.idpDomain}/logout?client_id=${appConfig.clientId}&logout_uri=${auth.settings.post_logout_redirect_uri}`
        window.location.href = path
      } else {
        // assume we're using an IDP that supports end_session_endpoint
        await auth.signoutRedirect()
      }
      await auth.removeUser()
      return removeUserInfo()
    } catch (e) {
      // do nothing
    }
  }

  if (auth.isLoading) {
    return <div>{loading()}</div>
  }
  if (auth.error) {
    return <div>Oops... {auth.error.message}</div>
  }

  if (auth.isAuthenticated) {
    saveUserInfo(auth.user)
    return (
      <Fragment>
        <Suspense fallback={loading()}>
          <App authState={'signedIn'} oidcAuth={auth} signout={() => signout('Successfully signed out.')} />
        </Suspense>
        <IdleTimer onIdle={onIdle} debounce={250} timeout={timeout} />
      </Fragment>
    )
  } else {
    removeUserInfo()
    return <OidcSignIn signOutReason={signOutReason} auth={auth} />
  }
}

export default AppWithAuth