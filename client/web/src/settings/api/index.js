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
  baseURL: `${apiUri}/settings`,
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
    source.cancel()
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

const settingsAPI = {
  fetchAll: async (ops) => {
    const { signal } = ops

    try {
      const response = await apiServer.get('/', { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to fetch settings')
      }
    }
  },
  updateSettings: async (settings, ops) => {
    const keys = Object.keys(settings)
    let calls = []
    keys.forEach((name, index) => {
      const settingToUpdate = {
        name: name,
        value: settings[name],
      }
      calls.push(settingsAPI.updateSetting(settingToUpdate, ops))
    })

    const updatesResponse = await axios.all(calls)
    return updatesResponse
  },
  updateSetting: async (setting, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.put(`/${setting.name}`, setting, {
        signal,
      })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to update setting: ${setting.name}`)
      }
    }
  },
  fetchConfig: async (ops) => {
    const { signal } = ops

    try {
      const response = await apiServer.get('/config', { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to fetch application configuration')
      }
    }
  },
  updateConfig: async (config, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.put('/config', config, { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to update application configuration')
      }
    }
  },
  createConfig: async (config, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.post('/config', config, { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to update application configuration')
      }
    }
  },
  fetchDbOptions: async (ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.get('/options', {
        signal,
      })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to get DB Options')
      }
    }
  },
  putToPresignedBucket: async (url, file) => {
    const s3 = axios.create({
      baseURL: url,
    })
    const response = await s3.put('', file)
    return response
  },
  isCancel: (err) => {
    if (err.aborted && err.aborted === true) {
      return true
    }
    return false
  },
}

export default settingsAPI
