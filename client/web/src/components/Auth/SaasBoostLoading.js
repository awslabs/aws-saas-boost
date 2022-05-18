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

import React, { Fragment } from 'react'
import { Loading } from 'aws-amplify-react'
import { RingLoader } from 'react-spinners'

export default class SaasBoostLoading extends Loading {
  showComponent() {
    return (
      <Fragment>
        <div className="container min-vh-100 d-flex flex-column justify-content-center align-items-center">
          <div>
            <h1 className="text-warning">
              <RingLoader size={120} />
            </h1>
          </div>
          <h1 className="text-warning">Authenticating...</h1>
        </div>
      </Fragment>
    )
  }
}
