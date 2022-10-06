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
import { Row, Col, Card, CardBody, CardHeader } from 'reactstrap'
import globalConfig from '../config/appConfig'
import CIcon from '@coreui/icons-react'
import { cilCheckCircle, cilXCircle, cilExternalLink } from '@coreui/icons'
import {
  dismissError,
  fetchTenantsThunk,
  selectAllTenants,
  countTenantsByActiveFlag,
} from '../tenant/ducks'
import { useDispatch, useSelector } from 'react-redux'
import {
  fetchConfig,
  selectSettingsById,
  selectConfig,
} from '../settings/ducks'
import * as SETTINGS from '../settings/common'
import { isEmpty } from 'lodash'
import { selectOSLabel, selectDbLabel } from '../options/ducks'
import ECRInstructions from '../components/ECRInstructions'
import { selectAllTiers } from '../tier/ducks'

const ActiveTenantsComponent = React.lazy(() =>
  import('./ActiveTenantsComponent')
)

const CurrentApplicationVersionComponent = React.lazy(() =>
  import('./CurrentApplicationVersionComponent')
)

const SaasBoostEnvNameComponent = React.lazy(() =>
  import('./SaasBoostEnvNameComponent')
)

const InstalledExtensionsComponent = React.lazy(() =>
  import('./InstalledExtensionsComponent')
)

export const DashboardComponent = (props) => {
  const dispatch = useDispatch()
  const appConfig = useSelector(selectConfig)
  const tiers = useSelector(selectAllTiers)
  const clusterOS = useSelector((state) =>
    selectSettingsById(state, SETTINGS.CLUSTER_OS)
  )
  const dbEngine = useSelector((state) =>
    selectSettingsById(state, SETTINGS.DB_ENGINE)
  )
  const version = useSelector((state) =>
    selectSettingsById(state, SETTINGS.VERSION)
  )
  let osLabel = 'N/A'

  const osLabelValue = useSelector((state) =>
    selectOSLabel(state, clusterOS?.value)
  )
  const dbLabelValue = useSelector((state) =>
    selectDbLabel(state, dbEngine?.value)
  )
  if (!isEmpty(osLabelValue)) {
    osLabel = osLabelValue
  }

  const metricsAnalyticsDeployed = useSelector((state) =>
    selectSettingsById(state, 'METRICS_ANALYTICS_DEPLOYED')
  )

  const billingApiKey = useSelector((state) =>
    selectSettingsById(state, 'BILLING_API_KEY')
  )

  const saasBoostEnvironment = useSelector(
    (state) => selectSettingsById(state, 'SAAS_BOOST_ENVIRONMENT')?.value
  )

  const countActiveTenants = useSelector((state) => {
    return countTenantsByActiveFlag(state, true)
  })

  const countAllTenants = useSelector((state) => {
    return selectAllTenants(state).length
  })

  const ecrRepo = useSelector((state) => selectSettingsById(state, 'ECR_REPO'))
  const s3Bucket = useSelector((state) =>
    selectSettingsById(state, 'SAAS_BOOST_BUCKET')
  )

  const awsAccount = globalConfig.awsAccount
  const awsRegion = globalConfig.region

  const fileAwsConsoleLink = `https://${awsRegion}.console.aws.amazon.com/efs/home?region=${awsRegion}#file-systems`
  const rdsAwsConsoleLink = `https://${awsRegion}.console.aws.amazon.com/rds/home?region=${awsRegion}#databases:`
  const ecrAwsConsoleLink = `https://${awsRegion}.console.aws.amazon.com/ecr/repositories`
  const s3BucketLink = `https://s3.console.aws.amazon.com/s3/buckets/${s3Bucket?.value}`

  useEffect(() => {
    const fetchTenants = dispatch(fetchTenantsThunk())
    return () => {
      if (fetchTenants.PromiseStatus === 'pending') {
        fetchTenants.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch]) //TODO: Follow up on the use of this dispatch function.

  useEffect(() => {
    const fetchConfigResponse = dispatch(fetchConfig())
    return () => {
      if (fetchConfigResponse.PromiseStatus === 'pending') {
        fetchConfigResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])

  return (
    <div className="animated fadeIn">
      <Row>
        <Col md={12} xl={12}>
          <Card>
            <CardBody>
              <Row>
                <Col xs={12} md={6} lg={6}>
                  <Row>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className="text-muted">Boost Environment</small>
                        <br />
                        <strong className="h4">{saasBoostEnvironment}</strong>
                      </div>
                    </Col>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className="text-muted">Active Tenants</small>
                        <br />
                        <strong className="h4">{countActiveTenants}</strong>
                      </div>
                    </Col>
                  </Row>
                  <hr className="mt-0" />
                </Col>
                <Col xs={12} md={6} lg={6}>
                  <Row>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className="text-muted">Onboarded Tenants</small>
                        <br />
                        <strong className="h4">{countAllTenants}</strong>
                      </div>
                    </Col>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className="text-muted">AWS Region</small>
                        <br />
                        <strong className="h4">{awsRegion}</strong>
                      </div>
                    </Col>
                  </Row>
                  <hr className="mt-0" />
                </Col>
              </Row>

              <Row>
                <Col xs={12} md={6} lg={6}>
                  <strong className="h4 mb-1">Application</strong>
                  <dl>
                    <dt className="mb-1">Application Name</dt>
                    <dd className="mb-3">
                      {isEmpty(appConfig?.name) ? 'N/A' : appConfig.name}
                    </dd>
                    <dt className="mb-1">Application Domain Name</dt>
                    <dd className="mb-3">
                      {isEmpty(appConfig?.domainName)
                        ? 'Not Configured'
                        : appConfig.domainName}
                    </dd>
                    <dt className="mb-1">Public API Endpoint</dt>
                    <dd className="mb-3">{globalConfig.apiUri}</dd>
                  </dl>
                </Col>
                <Col xs={12} md={6} lg={6}>
                  <strong className="h4 mb-1">Tiers</strong>
                  {tiers.map((tier) => (
                    <dl key={tier.id}>
                      <dt className="mb-1">{tier.name}</dt>
                      <dd className="mb-3">
                        {isEmpty(tier.description)
                          ? 'No Description'
                          : tier.description}
                      </dd>
                    </dl>
                  ))}
                </Col>
              </Row>
              <Row>
                <strong className="h4 mb-1">Services</strong>
                <Row>
                  {isEmpty(appConfig?.services) ? (
                    <Col xs={12} md={6} xl={4}>
                      No Services
                    </Col>
                  ) : (
                    Object.values(appConfig.services).map((service) => (
                      <Col xs={12} md={6} xl={4} key={service.name}>
                        <Card className="mb-2">
                          <CardHeader>
                            <strong>
                              {service.name} - {service.path}
                            </strong>
                          </CardHeader>
                          <CardBody>
                            <dl>
                              <dt>ECR Repository</dt>
                              <dd>
                                {isEmpty(service.containerRepo)
                                  ? 'Creating...'
                                  : service.containerRepo}{' '}
                                {' - '}
                                <a
                                  href={
                                    ecrAwsConsoleLink +
                                    (service.containerRepo
                                      ? `/private/${awsAccount}/` +
                                        service.containerRepo
                                      : '')
                                  }
                                  target="new"
                                  className="text-muted"
                                >
                                  AWS Console Link{' '}
                                  <CIcon icon={cilExternalLink} />
                                </a>
                              </dd>
                              <dt>
                                ECR Repository URL{' - '}
                                <ECRInstructions
                                  awsAccount={awsAccount}
                                  awsRegion={awsRegion}
                                  ecrRepo={service.containerRepo}
                                >
                                  <span className="text-muted">
                                    View docker image upload instructions{' '}
                                    <CIcon icon={cilExternalLink} />
                                  </span>
                                </ECRInstructions>
                              </dt>
                              <dd className="mb-3">
                                {awsAccount}.dkr.ecr.{awsRegion}.amazonaws.com
                                {service.containerRepo
                                  ? `/${service.containerRepo}`
                                  : ''}
                              </dd>
                              <dt>Description</dt>
                              <dd>{service.description}</dd>
                            </dl>
                          </CardBody>
                        </Card>
                      </Col>
                    ))
                  )}
                </Row>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default DashboardComponent
