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

import { configureStore, combineReducers } from "@reduxjs/toolkit";
import thunk from "redux-thunk";
import persistState from "redux-sessionstorage";

import reducers from "./reducers";

const sessionStorageConfig = {
  key: "saas-boost-session",
};

const appReducers = combineReducers(reducers);

const rootReducer = (state, action) => {
  return appReducers(state, action);
};

export default function configureAppStore() {
  const store = configureStore({
    reducer: rootReducer,
    middleware: [thunk],
    enhancers: [persistState("session", sessionStorageConfig)],
  });
  return store;
}
