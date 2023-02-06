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

import React, { useEffect } from 'react'
import { UserListComponent } from './UserListComponent'
import { useDispatch, useSelector } from 'react-redux'
import { fetchUsers, selectAllUsers, dismissError } from './ducks'
import { useHistory } from 'react-router-dom'
import { BaseUserRoute } from '.'

export default function UserListContainer() {
  const dispatch = useDispatch()
  const history = useHistory()

  const users = useSelector(selectAllUsers)
  const loading = useSelector((state) => state.users.loading)
  const error = useSelector((state) => state.users.error)

  const handleUserClick = (username) => {
    history.push(`${BaseUserRoute}/${username}`)
  }

  const handleRefresh = () => {
    dispatch(fetchUsers())
  }

  const handleCreateUser = () => {
    history.push(`${BaseUserRoute}/create`)
  }

  const handleError = () => {
    dispatch(dismissError())
  }

  useEffect(() => {
    const fetchUsersPromise = dispatch(fetchUsers())
    return () => {
      if (fetchUsersPromise.PromiseStatus === 'pending') {
        fetchUsersPromise.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])
  return (
    <UserListComponent
      users={users}
      loading={loading}
      error={error}
      handleUserClick={handleUserClick}
      handleRefresh={handleRefresh}
      handleCreateUser={handleCreateUser}
      handleError={handleError}
    />
  )
}
