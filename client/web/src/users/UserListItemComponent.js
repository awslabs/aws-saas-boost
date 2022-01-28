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
import Moment from 'react-moment'

const UserStatusComponent = ({ user }) => {
  const { active, status } = user
  return (
    <>
      <span className={`text-${active ? 'success' : 'danger'}`}>
        {active ? 'Active' : 'Inactive'}
      </span>{' '}
      / {status}
    </>
  )
}

UserStatusComponent.propTypes = {
  user: PropTypes.object,
}

export const UserListItemComponent = ({ user, handleUserClick }) => {
  return (
    <tr className="pointer" key={user.username} onClick={() => handleUserClick(user.username)}>
      <th scope="row">{user.username}</th>
      <td>
        {user.firstName} {user.lastName}
      </td>
      <td>{user.email}</td>
      <td>
        <UserStatusComponent user={user} />
      </td>
      <td>
        <Moment date={user.created} format="LLL" />
      </td>
    </tr>
  )
}

UserListItemComponent.propTypes = {
  user: PropTypes.object,
  handleUserClick: PropTypes.func,
}
