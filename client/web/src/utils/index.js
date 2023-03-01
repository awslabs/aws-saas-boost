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

const sortByModified = function (a, b) {
    if (!!!a?.modified || !!!b?.modified) {
      // if a or b is falsy or modified does not exist, we cannot act
      // so just retain the original order
      return 0
    }
    const aDate = new Date(a.modified)
    const bDate = new Date(b.modified)
    if (!!!aDate || !!!bDate) {
      // similarly if either of the modified objects could not be easily
      // converted into date objects, we cannot act, so retain original order
      return 0
    }
    // flip the order for most recent first
    return -1 * (aDate - bDate)
}

export { sortByModified }