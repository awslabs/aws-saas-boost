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
import React, { Component, Fragment } from 'react'
import {
  Alert,
  Spinner,
  Row,
  Col,
  Dropdown,
  DropdownToggle,
  DropdownMenu,
  DropdownItem,
  Card,
  CardHeader,
  CardBody,
} from 'reactstrap'
import { SBMoment } from '../components/SBMoment'
import Display from '../components/Display'
import UserDeleteConfirmationComponent from './UserDeleteConfirmationComponent'

function showError(error, handleError) {
  return (
    <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
      <h4 className={'alert-heading'}>Error</h4>
      <p>{error}</p>
    </Alert>
  )
}

export class UserViewComponent extends Component {
  constructor(props) {
    super(props)

    this.showError = this.showError.bind(this)
    this.toggleActions = this.toggleActions.bind(this)
    this.toggleDeleteConfirm = this.toggleDeleteConfirm.bind(this)

    this.state = {
      dropdownOpen: false,
      confirmDeleteModal: false,
    }
  }

  toggleActions() {
    this.setState((state) => {
      return {
        dropdownOpen: state.dropdownOpen ? false : true,
      }
    })
  }

  toggleDeleteConfirm() {
    this.setState((state) => {
      return {
        confirmDeleteModal: state.confirmDeleteModal ? false : true,
      }
    })
  }

  showError(error, handleError) {
    return (
      <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
        <h4 className={'alert-heading'}>Error</h4>
        <p>{error}</p>
      </Alert>
    )
  }

  viewLoading() {
    return (
      <Spinner animation="border" role="status">
        Loading...
      </Spinner>
    )
  }

  render() {
    const { user, loading, error, toggleEdit, handleActiveToggle, handleError, deleteUser } =
      this.props

    const { dropdownOpen } = this.state

    return (
      <Fragment>
        <div className="animated fadeIn">
          <Row>
            <Col>{error && showError(error, handleError)}</Col>
          </Row>
          <Row>
            <Col>{loading === 'pending' && this.viewLoading()}</Col>
            <Col className="justify-content-end">
              <Dropdown className="float-right" toggle={this.toggleActions} isOpen={dropdownOpen}>
                <DropdownToggle caret color={'primary'} disabled={loading === 'pending' || !!error}>
                  Actions
                </DropdownToggle>
                {loading === 'idle' && !!user && (
                  <DropdownMenu end="true">
                    <DropdownItem onClick={() => toggleEdit()}>Edit</DropdownItem>
                    <DropdownItem
                      disabled={user.active}
                      onClick={() => handleActiveToggle('activate', user.username)}
                    >
                      Enable
                    </DropdownItem>
                    <DropdownItem
                      disabled={!user.active}
                      onClick={() => handleActiveToggle('deactivate', user.username)}
                    >
                      Disable
                    </DropdownItem>
                    <DropdownItem onClick={this.toggleDeleteConfirm}>Delete</DropdownItem>
                  </DropdownMenu>
                )}
              </Dropdown>
            </Col>
          </Row>

          <Row>
            <Col md={12} className="pt-2">
              <Card>
                <CardHeader>
                  <i className="fa fa-info" />
                  User Details
                </CardHeader>
                <CardBody>
                  <Row>
                    <Col className="border border-top-0 border-left-0 border-bottom-0">
                      <dt>Username</dt>
                      <dd>
                        <Display condition={!!user}>{user && user.username}</Display>
                      </dd>

                      <dt>Name</dt>
                      <dd>
                        <Display condition={!!user}>
                          {!!user && user.firstName} {!!user && user.lastName}
                        </Display>
                      </dd>
                    </Col>
                    <Col className="border border-top-0 border-left-0 border-bottom-0">
                      <dt>Email</dt>
                      <dd>
                        <Display>{!!user && user.email}</Display>
                      </dd>
                      <dt>Status</dt>
                      <dd>
                        <Display>
                          {!!user ? (
                            <>
                              {user.active ? (
                                <span className="text-success">Active</span>
                              ) : (
                                <span className="text-danger">Inactive</span>
                              )}
                              {' / '}
                              {user.status}
                            </>
                          ) : undefined}
                        </Display>
                      </dd>
                    </Col>
                    <Col>
                      <dt>Created On</dt>
                      <dd>
                        <Display condition={!!user}>
                          <SBMoment instant="{!!user && user.created}" />
                        </Display>
                      </dd>
                    </Col>
                  </Row>
                  <Row></Row>
                  <Row>
                    <Col></Col>
                    <Col>
                      <dt></dt>
                      <dd></dd>
                    </Col>
                    <Col>
                      <dt></dt>
                      <dd></dd>
                    </Col>
                  </Row>
                </CardBody>
              </Card>
            </Col>
          </Row>
        </div>
        {!!user && (
          <UserDeleteConfirmationComponent
            showModal={this.state.confirmDeleteModal}
            username={user.username}
            toggleModal={this.toggleDeleteConfirm}
            deleteUser={deleteUser}
          />
        )}
      </Fragment>
    )
  }
}

UserViewComponent.propTypes = {
  user: PropTypes.object,
  loading: PropTypes.string,
  error: PropTypes.string,
  toggleEdit: PropTypes.func,
  handleActiveToggle: PropTypes.func,
  handleError: PropTypes.func,
  deleteUser: PropTypes.func,
}

export default UserViewComponent
