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

import axios from 'axios'
import appConfig from '../config/appConfig'
import { AuthSessionStorage } from '../utils/AuthSessionStorage'
const { apiUri } = appConfig

const OIDC_STORAGE_USER_KEY = "aws-saas-boost-userinfo"

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
  const userInfo = AuthSessionStorage.getItem(OIDC_STORAGE_USER_KEY)
  return JSON.parse(userInfo).access_token
}

export const saveUserInfo = (user) => {
  AuthSessionStorage.setItem(OIDC_STORAGE_USER_KEY, JSON.stringify(user))
}

export const removeUserInfo = () => {
  AuthSessionStorage.removeItem(OIDC_STORAGE_USER_KEY)
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
    r.headers.Authorization = "Bearer " + authorizationToken

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
