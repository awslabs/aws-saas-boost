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

import { getApiServer, Aborted, isCancel } from '../../api/common'
import MissingECRImageError from '../MissingECRImageError'
import ExceedLimitsError from '../ExceedLimitsError'

const apiServer = getApiServer('onboarding')

const onboardingAPI = {
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
        throw Error('Unable to fetch onboarding requests')
      }
    }
  },
  fetch: async (id, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.get(`/${id}`, {
        signal,
      })

      return response.data
    } catch (err) {
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        console.error(err)
        throw Error(`Unable to fetch onboarding request:${id}`)
      }
    }
  },
  create: async (onboardingData, ops) => {
    const { signal } = ops
    try {
      const response = await apiServer.post(`/`, JSON.stringify(onboardingData), {
        signal,
      })
      return response.data
    } catch (err) {
      console.info(`error: ${JSON.stringify(err)}`)
      console.info(`error.response: ${JSON.stringify(err.response)}`)
      if (axios.isCancel(err)) {
        throw new Aborted('Call aborted', err)
      } else {
        let errorMessage = ''
        if (err.response?.data) {
          errorMessage = err.response.data?.message ?? err.response.data
          if (errorMessage.indexOf('ECR') >= 0) {
            throw new MissingECRImageError(errorMessage)
          } else if (errorMessage.indexOf('exceed limits') >= 0) {
            throw new ExceedLimitsError(errorMessage)
          } else {
            errorMessage = err.message
          }
          console.error(err.message)
          throw Error(errorMessage)
        }
      }
    }
  },
  update: async (onboarding) => {
    return onboarding
  },
  /**
   * Determines if err is from a Cancelled or Aborted request
   * @param err
   */
  isCancel: isCancel,
}

export default onboardingAPI
