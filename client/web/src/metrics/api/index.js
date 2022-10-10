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
import moment from 'moment'
import { fetchAccessToken } from '../../api'
import appConfig from '../../config/appConfig'

const { apiUri } = appConfig
const apiServer = axios.create({
  baseURL: `${apiUri}/metrics`,
  headers: {
    common: {
      'Content-Type': 'application/json',
    },
  },
})

const datasetServer = axios.create({
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
  r.headers['Content-Type'] = 'application/json'

  //Configure the AbortSignal
  r.signal.onabort = () => {
    console.log('Aborting API Call')
    source.cancel()
    console.log('Call aborted')
  }
  r.cancelToken = source.token

  return r
})

datasetServer.interceptors.request.use(async (r) => {
  r.headers['Content-Type'] = 'application/json'

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

const metricsAPI = {
  /*fetchAll: async (ops) => {
    const { signal } = ops;

    try {
      const response = await apiServer.get("", { signal });
      return response.data;
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted("Call aborted", err);
      } else {
        console.error(err);
        throw Error("Unable to fetch metrics");
      }
    }
  }, */

  query: async (metricQuery, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.post(`/query`, JSON.stringify(metricQuery), {
        signal,
      })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to query metrics: ${JSON.stringify(metricQuery)}`)
      }
    }
  },

  accessLogMetrics: async (metric = 'Count', timePeriod, ops) => {
    const { signal } = ops

    let period = '07Day'
    switch (timePeriod) {
      case 'DAY_07':
        period = '07Day'
        break
      case 'HOUR_24':
        period = '24Hour'
        break
      case 'HOUR_1':
        period = '01Hour'
        break
    }

    const fileName = `path${metric}${period}.js`
    try {
      const response = await datasetServer.get(`/${fileName}`, { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to query metrics: ${JSON.stringify(timePeriod)}`)
      }
    }
  },
  getAccessLogsByTenant: async (metric, timerange, id, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.get(`/alb/${metric}/${timerange}/${id}`, { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call Aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to obtain Tenant Access Logs')
      }
    }
  },
  getAccessLogsSignedUrls: async (ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.get(`/datasets`, { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call Aborted', err)
      } else {
        console.error(err)
        throw Error('Unable to obtain signed URLs for Access Logs')
      }
    }
  },

  getAccessLogFile: async (url, ops) => {
    const { signal } = ops
    try {
      const response = await datasetServer.get(url, { signal })
      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to obtain data file from:  ${url}`)
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

export default metricsAPI
