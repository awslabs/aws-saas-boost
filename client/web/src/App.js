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
import { BrowserRouter, Route, Switch } from 'react-router-dom'
import { FetchSettings } from './settings'
import { PropTypes } from 'prop-types'
import './scss/style.scss'
import { Provider } from 'react-redux'
import store from './store/index'
import ScrollToTop from './utils/ScrollToTop'

// Containers
const DefaultLayout = React.lazy(() => import('./layout/DefaultLayout'))

App.propTypes = {
  authState: PropTypes.string,
}

function App(props) {
  const oidcAuth = props.oidcAuth
  if (props.authState === 'signedIn') {
    return (
      <Provider store={store}>
        <BrowserRouter>
          <ScrollToTop>
            <FetchSettings>
              <Switch>
              <Route path="/" name="Home" render={(props) => <DefaultLayout {...props} oidcAuth={oidcAuth} />} />
              </Switch>
            </FetchSettings>
          </ScrollToTop>
        </BrowserRouter>
      </Provider>
    )
  } else {
    return null
  }
}

export default App
