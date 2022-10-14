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
import React from 'react'
import ReactDOM from 'react-dom'
import AppWithAuth from './AppWithAuth'
import appConfig from './config/appConfig'
import { AuthProvider } from 'react-oidc-context'
import * as serviceWorker from './serviceWorker'


const oidcConfig = {
    authority: appConfig.issuer,
    client_id: appConfig.clientId,
    redirect_uri: window.location.origin,
  }

ReactDOM.render(
  <AuthProvider {...oidcConfig}>
    <AppWithAuth />
  </AuthProvider>,
  document.getElementById('root')
)

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: http://bit.ly/CRA-PWA
serviceWorker.unregister()
