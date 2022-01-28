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

export const _colors = [
  '#20a8d8',
  '#6610f2',
  '#e83e8c',
  '#f86c6b',
  '#63c2de',
  '#f8cb00',
  '#ffc107',
  '#4dbd74',
  '#20c997',
  '#17a2b8',
  '#6f42c1',
]

export const _tenantLabels = (vals, tenants) => {
  // console.log(`tenants: ${JSON.stringify(tenants)}`);
  let labels
  if (typeof vals === 'object') {
    labels = []
    vals.forEach((label) => {
      tenants.some((tenant) => {
        if (label === tenant.id) {
          labels.push(tenant.name)
          return true
        }
      })
    })
  } else {
    labels = tenants[vals].name
  }
  // console.log(`labels: ${labels}`);

  return labels
}
