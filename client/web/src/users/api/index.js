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
import { fetchAccessToken } from '../../api'
import appConfig from '../../config/appConfig'

const { apiUri } = appConfig
const apiServer = axios.create({
  baseURL: `${apiUri}/sysusers`,
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
  r.signal.onabort = () => {
    console.log('Aborting API Call')
    source.cancel()
    console.log('Call aborted')
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

const usersAPI = {
  fetchAll: async (ops) => {
    const { signal } = ops

    try {
      const response = await apiServer.get('', { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to fetch users')
      }
    }
  },
  fetch: async (userId, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.get(`/${userId}`, {
        signal,
      })

      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to fetch user with Id: ${userId}`)
      }
    }
  },
  create: async (user, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.post(`/`, JSON.stringify(user), {
        signal,
      })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to create user: ${JSON.stringify(user)}`)
      }
    }
  },
  update: async (user, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.put(`/${user.username}`, user, {
        signal,
      })
      console.log(response)
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to update user: ${user.username}`)
      }
    }
  },
  activate: async (username, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.patch(
        `/${username}/enable`,
        { username },
        {
          signal,
        },
      )
      console.log(response)
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to update user: ${username}`)
      }
    }
  },
  deactivate: async (username, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.patch(
        `/${username}/disable`,
        { username },
        {
          signal,
        },
      )
      console.log(response)
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to update user: ${username}`)
      }
    }
  },
  delete: async (username, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.delete(`/${username}`, {
        signal,
        data: { username },
      })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to delete user: ${username}`)
      }
    }
  },
  isCancel: (err) => {
    if (err.aborted && err.aborted === true) {
      return true
    }
    return false
  },
}

export default usersAPI
