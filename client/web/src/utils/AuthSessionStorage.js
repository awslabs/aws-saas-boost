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

const SESSION_KEY = '@SaasBoost:'
let dataMemory = {}

export class AuthSessionStorage {
  static syncPromise = null

  static sync() {
    if (!AuthSessionStorage.syncPromise) {
      AuthSessionStorage.syncPromise = new Promise((res, rej) => {
        const numKeys = sessionStorage.length
        let key = ''
        // get all keys
        for (let i = 0; i < numKeys; i++) {
          key = sessionStorage.key(i)
          if (key.startsWith(SESSION_KEY)) {
            let value = sessionStorage.getItem(key)
            let shortKey = key.replace(SESSION_KEY, '')
            dataMemory[shortKey] = value
          }
        }
        res()
      })
    }
    return AuthSessionStorage.syncPromise
  }

  static setItem(key, value) {
    sessionStorage.setItem(SESSION_KEY + key, value)
    dataMemory[key] = value
    return dataMemory[key]
  }

  static getItem(key) {
    return Object.prototype.hasOwnProperty.call(dataMemory, key) ? dataMemory[key] : undefined
  }

  static removeItem(key) {
    sessionStorage.removeItem(SESSION_KEY + key)
    return delete dataMemory[key]
  }

  static clear() {
    dataMemory = {}
    sessionStorage.clear()
  }
}
