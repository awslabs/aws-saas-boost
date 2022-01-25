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

import { useEffect } from 'react'
import { withRouter } from 'react-router-dom'

const ScrollToTop = ({ children, location: { pathname } }) => {
  useEffect(() => {
    window.scrollTo({
      top: 0,
      left: 0,
      behavior: 'smooth',
    })
  }, [pathname])

  return children || null
}

export default withRouter(ScrollToTop)
