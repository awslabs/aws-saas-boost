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
import { fetchAccessToken, handleErrorResponse } from '../../api'
import appConfig from '../../config/appConfig'
const { apiUri } = appConfig

const apiServer = axios.create({
  baseURL: `${apiUri}/tenants`,
  headers: {
    common: {
      'Content-Type': 'application/json',
    },
  },
})
const CancelToken = axios.CancelToken
const source = CancelToken.source()

apiServer.interceptors.request.use(async (r) => {
  console.log(r)
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
const tenantAPI = {
  fetchAll: async () => {
    try {
      const authorizationToken = await fetchAccessToken()
      const response = await fetch(`${apiUri}/tenants/provisioned`, {
        method: 'GET',
        mode: 'cors',
        headers: {
          'Content-Type': 'application/json',
          Authorization: "Bearer " + authorizationToken,
        },
      })

      const responseJSON = await handleErrorResponse(response)
      return responseJSON
    } catch (err) {
      console.error(err)
      throw Error('Unable to fetch tenants')
    }
  },
  fetchAllAxios: async (ops) => {
    const { signal } = ops

    try {
      const response = await apiServer.get('?status=onboarded', { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        console.log('API call cancelled')
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to fetch tenants')
      }
    }
  },
  fetchTenant: async (tenantId) => {
    try {
      const authorizationToken = await fetchAccessToken()
      const response = await fetch(`${apiUri}/tenants/${tenantId}`, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          Authorization: "Bearer " + authorizationToken,
        },
      })
      const responseJSON = await handleErrorResponse(response)
      return responseJSON
    } catch (err) {
      console.error(err)
      throw Error(`Unable to fetch tenant: ${tenantId}`)
    }
  },
  provisionTenant: async (values) => {
    throw Error('Unsupported operation')
  },
  editTenant: async (values) => {
    try {
      const authorizationToken = await fetchAccessToken()
      const response = await fetch(`${apiUri}/tenants/${values.id}`, {
        method: 'PUT',
        mode: 'cors',
        body: JSON.stringify({
          ...values,
        }),
        headers: {
          'Content-Type': 'application/json',
          Authorization: "Bearer " + authorizationToken,
        },
      })
      const responseJSON = await handleErrorResponse(response)
      return responseJSON
    } catch (err) {
      console.error(err)
      throw Error('Unable to edit tenant.')
    }
  },
  enableTenant: async (tenantId, ops) => {
    const { signal } = ops

    try {
      const response = await apiServer.patch(
        `/${tenantId}/enable`,
        { id: tenantId },
        { signal }
      )
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to enable tenant ${tenantId}`)
      }
    }
  },
  disableTenant: async (tenantId, ops) => {
    const { signal } = ops

    try {
      const response = await apiServer.patch(
        `/${tenantId}/disable`,
        { id: tenantId },
        { signal }
      )
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to disable tenant ${tenantId}`)
      }
    }
  },
  deleteTenant: async (tenantId, ops) => {
    const { signal } = ops

    try {
      const response = await apiServer.delete(
        `/${tenantId}`,
        {
          data: { id: tenantId },
        },
        { signal }
      )
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to delete tenant ${tenantId}`)
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

export default tenantAPI
