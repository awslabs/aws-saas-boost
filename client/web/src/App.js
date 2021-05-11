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

import React, { Component } from "react";
import { BrowserRouter } from "react-router-dom";
import configureAppStore from "./store";
import { Provider } from "react-redux";
import { FetchSettings } from "./settings";
import { Hub } from "aws-amplify";
import "./App.scss";
import Routes from "./Routes";
import ScrollToTop from "./utils/ScrollToTop";

const store = configureAppStore();

/**
 * Hub.listen(*) handlers moved out of constructor and into main file
 * since we're moving function into a stateless function
 */
Hub.listen("auth", (data) => {
  const { payload } = data;
  const { event } = payload;

  switch (event) {
    case "signOut":
      console.log("dispatching reset");
      store.dispatch({ type: "RESET" });
      break;
    case "configured":
      break;
    default:
      console.log("Hub::catchAll - " + JSON.stringify(payload.event));
  }
});

/**
 * Hub.listen(*) handlers
 *
 * == END
 */

function App(props) {
  if (props.authState === "signedIn") {
    return (
      <Provider store={store}>
        <BrowserRouter basename="/">
          <ScrollToTop>
            <FetchSettings>
              <Routes />
            </FetchSettings>
          </ScrollToTop>
        </BrowserRouter>
      </Provider>
    );
  } else {
    return null;
  }
}

export default App;
//TODO: Clear out redux store when user is signed out.
