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

import { Auth } from 'aws-amplify'
import axios from 'axios'
import appConfig from '../config/appConfig'
const { apiUri } = appConfig

export const handleErrorResponse = async (response) => {
  if (!response.ok) {
    console.error('Returned error code: ' + response.status)
    console.error('Returned error status:' + response.statusText)
    let errorMessage = 'Error processing request.'

    try {
      const errorResponse = await response.json()
      if (errorResponse.message) {
        errorMessage = errorResponse.message
      }
    } catch (e) {
      // do nothing
    } finally {
      throw Error(errorMessage)
    }
  }
  return response.json()
}

export const handleErrorNoResponse = (response) => {
  if (!response.ok) {
    console.error('Returned error code: ' + response.status)
    console.error('Returned error status:' + response.statusText)
    throw Error('Error processing request.')
  }
}

export async function fetchAccessToken() {
  try {
    const authSession = await Auth.currentSession()
    const accessToken = authSession.getAccessToken()
    return accessToken.getJwtToken()
  } catch (e) {
    console.error(e)
    console.error('User session expired, need to log in.')
  }
}

//API Aborted class
export class Aborted extends Error {
  constructor(message, cause) {
    super(message)
    this.aborted = true
    this.cause = cause
  }
}

export const isCancel = (err) => {
  if (err.aborted && err.aborted === true) {
    return true
  }
  return false
}

export function getApiServer(endpoint) {
  const apiServer = axios.create({
    baseURL: `${apiUri}/${endpoint}`,
    headers: {
      common: {
        'Content-Type': 'application/json',
      },
    },
  })
  const CancelToken = axios.CancelToken
  const source = CancelToken.source()

  apiServer.interceptors.request.use(async (r) => {
    //Obtain and pass along Authorization token
    const authorizationToken = await fetchAccessToken()
    r.headers.Authorization = authorizationToken

    //Configure the AbortSignal
    r.signal.onabort = () => {
      console.log('Aborting API Call')
      source.cancel()
      console.log('Call aborted')
    }
    r.cancelToken = source.token

    return r
  })

  return apiServer
}
