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
import { fetchAccessToken, handleErrorResponse, handleErrorNoResponse } from '../../api'
import appConfig from '../../config/appConfig'
const { apiUri } = appConfig

const apiServer = axios.create({
  baseURL: `${apiUri}/identity`,
  headers: {
    common: {
      'Content-Type': 'application/json',
    },
  },
  mode: 'cors',
})
const CancelToken = axios.CancelToken
const source = CancelToken.source()

apiServer.interceptors.request.use(async (r) => {
  //Obtain and pass along Authorization token
  const authorizationToken = await fetchAccessToken()
  r.headers.Authorization = "Bearer " + authorizationToken

  //Configure the AbortSignal
  if (r.signal) {
    r.signal.onabort = () => {
      source.cancel()
    }
  }
  r.cancelToken = source.token

  return r
})

//API Aborted class
class Aborted extends Error {
  constructor(message, cause) {
    super(message)
    this.aborted = true
    this.cause = cause
  }
}
const  identityAPI = {
  fetchAll: async (ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.get('providers', { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        console.log('API call cancelled')
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to fetch providers')
      }
    }
  },
  create: async (providerData) => {
    //const { signal } = ops
    try {
      const authorizationToken = await fetchAccessToken();
      const response = await fetch(`${apiUri}/identity/`, {
        method: 'POST',
        mode: 'cors',
        body: JSON.stringify({
          ...providerData,
        }),
        headers: {
          'Content-Type': 'application/json',
          Authorization: "Bearer " + authorizationToken,
        },
      })
      return Promise.resolve(response);
      //return await handleErrorResponse(response)
    } catch (err) {
      console.error(err)
      throw Error('Unable to create provider')
    }
  },
  update: async (providerData, ops) => {
    const { signal } = ops

    try {
      const authorizationToken = await fetchAccessToken()
      const response = await fetch(`${apiUri}/providers/${providerData.id}`, {
        method: 'PUT',
        mode: 'cors',
        body: JSON.stringify({
          ...providerData,
        }),
        headers: {
          'Content-Type': 'application/json',
          Authorization: "Bearer " + authorizationToken,
        },
      })
      console.log('provider api update response', response)
      return await handleErrorResponse(response)
    } catch (err) {
      console.error(err)
      throw Error('Unable to edit provider.')
    }
  },
  fetchProvider: async (providerId, ops) => {
    const { signal } = ops

    try {
      const authorizationToken = await fetchAccessToken()
      const response = await fetch(`${apiUri}/providers/${providerId}`, {
        headers: {
          'Content-Type': 'application/json',
          Authorization: "Bearer " + authorizationToken,
        },
      })
      return await handleErrorResponse(response)
    } catch (err) {
      console.error(err)
      throw Error(`Unable to fetch provider: ${providerId}`)
    }
  },
  delete: async (values, ops) => {
    const { signal } = ops
    const { providerId, history } = values

    try {
      const response = await apiServer.delete(`/${providerId}`, { signal })
      history.push('/providers')
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to delete provider ${providerId}`)
      }
    }
  },
  /**
   * Determines if err is from a Cancelled or Aborted request
   * @param err
   */
  isCancel: (err) => {
    if (err.aborted && err.aborted === true) {
      return true
    }
    return false
  },
}

export default identityAPI
