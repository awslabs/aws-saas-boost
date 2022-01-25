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
import { PropTypes } from 'prop-types'
import React from 'react'
import ContentLoader from 'react-content-loader'

const MyLoader = () => (
  <ContentLoader
    speed={2}
    width={240}
    height={20}
    viewBox="0 0 240 20"
    backgroundColor="#f3f3f3"
    foregroundColor="#ecebeb"
  >
    <rect x="3" y="3" rx="3" ry="3" width="200" height="8" />
  </ContentLoader>
)

export const Display = ({ children, condition = true }) => {
  return !!children && condition ? <>{children}</> : <MyLoader />
}

Display.propTypes = {
  children: PropTypes.node,
  condition: PropTypes.bool,
}

export default Display
