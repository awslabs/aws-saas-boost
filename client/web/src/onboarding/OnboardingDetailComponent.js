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
import CIcon from '@coreui/icons-react'
import { cilReload, cilExternalLink } from '@coreui/icons'
import {
  Card,
  CardHeader,
  CardBody,
  Row,
  Col,
  Alert,
  Button,
  NavLink,
} from 'reactstrap'
import Display from '../components/Display'
import Moment from 'react-moment'
import { OnboardingStatus } from './OnboardingStatus'
import { OnboardingTenantLink } from './OnboardingTenantLink'

const showError = (error, clearError) => {
  return (
    !!error && (
      <Row>
        <Col sm={12}>
          <Alert color="danger" isOpen={!!error} toggle={() => clearError()}>
            <h4 className="alert-heading">Error</h4>
            <p>{error}</p>
          </Alert>
        </Col>
      </Row>
    )
  )
}

export const OnboardingDetailComponent = (props) => {
  const { onboarding, error, clearError, refresh, showTenant } = props
  const terminus = ['deployed', 'deleted', 'updated', 'failed']

  const [isRefreshing, setIsRefreshing] = useState(null)
  const [timeoutId, setTimeoutId] = useState(null)

  const refreshStatus = (status, refreshFn) => {
    if (!isRefreshing && !terminus.includes(status)) {
      setIsRefreshing(true)
      const id = setTimeout(() => {
        refreshFn()
      }, 30000)
      setTimeoutId(id)
    }
  }

  useEffect(() => {
    refreshStatus(onboarding?.status, refresh)
    return () => {
      if (isRefreshing) {
        clearTimeout(timeoutId)
        setIsRefreshing(false)
      }
    }
  })

  const rootStack = onboarding?.stacks?.find((s) => s.baseStack === true)
  const rootStackUrl = rootStack?.cloudFormationUrl

  return (
    <div className="animated fadeIn">
      {showError(error, clearError)}
      <Row className="mb-3">
        <Col className="d-flex justify-content-end">
          <div>
            <Button color="secondary" className="mr-2" onClick={refresh}>
              <span>
                <CIcon icon={cilReload} />
              </span>
            </Button>
          </div>
        </Col>
      </Row>
      <Row>
        <Col xs={12}>
          <Card>
            <CardHeader>
              <i className="fa fa-info" />
              Onboarding Request Detail
            </CardHeader>
            <CardBody>
              <Row className="pt-3">
                <Col
                  sm={4}
                  className="border border border-top-0 border-bottom-0 border-right-0"
                >
                  <dt>Id</dt>
                  <dd>
                    <Display>{onboarding && onboarding.id}</Display>
                  </dd>
                  <dt>Status</dt>
                  <dd>
                    <Display>
                      <OnboardingStatus status={onboarding?.status} />
                    </Display>
                  </dd>
                </Col>
                <Col
                  sm={4}
                  className="border border border-top-0 border-bottom-0 border-left-0 border-right-0"
                >
                  <dt>Tenant</dt>
                  <dd>
                    <Display>
                      {onboarding && onboarding.tenantId && (
                        <OnboardingTenantLink
                          tenantId={onboarding.tenantId}
                          tenantName={onboarding.request?.name}
                          clickTenantDetails={showTenant}
                        />
                      )}
                    </Display>
                  </dd>
                  <dt>Root stack</dt>
                  <dd>
                    <Display>
                      {onboarding && rootStackUrl && (
                        <NavLink
                          active={true}
                          target="_blank"
                          href={rootStackUrl}
                          className="pl-0"
                        >
                          {rootStack?.name} <CIcon icon={cilExternalLink} />
                        </NavLink>
                      )}
                    </Display>
                  </dd>
                </Col>
                <Col
                  sm={4}
                  className="border border border-top-0 border-bottom-0 border-left-0"
                >
                  <dt>Created On</dt>
                  <dd>
                    <Display>
                      {onboarding && (
                        <Moment format="LLLL" date={onboarding.created} />
                      )}
                    </Display>
                  </dd>
                  <dt>Modified On</dt>
                  <dd>
                    <Display>
                      {onboarding && (
                        <Moment
                          format="dddd, MMMM Do YYYY, h:mm:ss a"
                          date={onboarding.modified}
                        />
                      )}
                    </Display>
                  </dd>
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

OnboardingDetailComponent.propTypes = {
  onboarding: PropTypes.object,
  error: PropTypes.string,
  refresh: PropTypes.func,
  showTenant: PropTypes.func,
  clearError: PropTypes.func,
}

export default OnboardingDetailComponent
