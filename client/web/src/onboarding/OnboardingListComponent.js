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
import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Row, Col, Card, Button, Table, Spinner, Alert } from 'react-bootstrap'
import CIcon from '@coreui/icons-react'
import { cilReload, cilListRich } from '@coreui/icons'
import OnboardingListItemComponent from './OnboardingListItemComponent'

const showError = (error, dismissError) => {
  return (
    <Alert color="danger" isOpen={!!error} toggle={() => dismissError()}>
      <h4 className="alert-heading">Error</h4>
      <p>{error}</p>
    </Alert>
  )
}

export const OnboardingListComponent = (props) => {
  const {
    clickOnboardingRequest,
    dismissError,
    doRefresh,
    error,
    loading,
    onboardingRequests,
    showOnboardRequestForm,
    clickTenantDetails,
  } = props
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [timeoutId, setTimeoutId] = useState(null)

  const terminus = ['deployed', 'updated', 'failed']

  const checkRefresh = (refreshFn) => {
    if (
      !isRefreshing &&
      onboardingRequests.some((ob) => !terminus.includes(ob?.status))
    ) {
      setIsRefreshing(true)
      const id = setTimeout(() => {
        refreshFn()
      }, 30000)
      setTimeoutId(id)
    }
  }

  useEffect(() => {
    checkRefresh(doRefresh)
    return () => {
      if (isRefreshing) {
        clearTimeout(timeoutId)
        setIsRefreshing(false)
      }
    }
  })

  return (
    <div className="animated fadeIn">
      <Row>
        <Col>{!!error && showError(error, dismissError)}</Col>
      </Row>
      <Row className="mb-3">
        <Col sm={12} md={8} lg={9}>
          <Alert color="light">
            Onboarding tenants requires an application image to be uploaded for each service. 
            If you haven't done so, view the upload instructions for each service &nbsp;
            <Link to="/summary">here</Link>.
          </Alert>
        </Col>
        <Col sm={12} md={4} lg={3}>
          <div className="float-right">
            <Button variant="secondary" className="mr-2" onClick={doRefresh}>
              <span>
                <CIcon icon={cilReload} />
              </span>
            </Button>
            <Button variant="info" onClick={showOnboardRequestForm}>
              Provision Tenant
            </Button>
          </div>
        </Col>
      </Row>
      <Row>
        <Col lg={12}>
          <Card>
            <Card.Header>
              <CIcon icon={cilListRich} /> Onboarding Requests
            </Card.Header>
            <Card.Body>
              <Table>
                <thead>
                  <tr>
                    <th>Request Id</th>
                    <th>Tenant</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th>Modified</th>
                  </tr>
                </thead>
                <tbody>
                  {loading === 'idle' &&
                    onboardingRequests.map((onboarding) => {
                      return (
                        <OnboardingListItemComponent
                          onboarding={onboarding}
                          key={onboarding.id}
                          clickOnboardingRequest={clickOnboardingRequest}
                          clickTenantDetails={clickTenantDetails}
                        />
                      )
                    })}
                  {loading === 'idle' && onboardingRequests.length === 0 && (
                    <tr>
                      <td colSpan="5" className="text-center">
                        <p className="font-weight-bold">No results</p>
                        <p>There are no Onboarding Requests to display.</p>
                      </td>
                    </tr>
                  )}
                  {loading === 'pending' && (
                    <tr>
                      <td colSpan="5">
                        <Spinner animation="border" role="status"></Spinner>
                      </td>
                    </tr>
                  )}
                </tbody>
              </Table>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

OnboardingListComponent.propTypes = {
  clickOnboardingRequest: PropTypes.func,
  dismissError: PropTypes.func,
  doRefresh: PropTypes.func,
  error: PropTypes.string,
  loading: PropTypes.string,
  onboardingRequests: PropTypes.array,
  showOnboardRequestForm: PropTypes.func,
  ecrRepository: PropTypes.string,
  awsAccount: PropTypes.string,
  awsRegion: PropTypes.string,
  showEcrPushModal: PropTypes.bool,
  toggleEcrPushModal: PropTypes.func,
  clickTenantDetails: PropTypes.func,
}

export default OnboardingListComponent
