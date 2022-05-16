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
import { cilExternalLink } from '@coreui/icons'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Dropdown,
  Form,
  FormGroup,
  Modal,
  NavLink,
  Row,
} from 'react-bootstrap'
import Moment from 'react-moment'
import Display from '../components/Display'
import { MoonLoader } from 'react-spinners'
// import { css } from '@emotion/core';

TenantViewComponent.propTypes = {
  tenant: PropTypes.object,
  enable: PropTypes.func,
  disable: PropTypes.func,
  loading: PropTypes.string,
  error: PropTypes.string,
  handleError: PropTypes.func,
  toggleEdit: PropTypes.func,
  deleteTenant: PropTypes.func,
}

function TenantViewComponent(props) {
  const banner = {
    fontWeight: 600,
    color: '#f6f6f6',
    background: '#232f3e',
    border: '1px solid orange',
  }
  //  const override = css`
  //    display: inline-block;
  //    vertical-align: middle;
  //  `
  const override = ''

  const {
    tenant,
    loading,
    error,
    handleError,
    toggleEdit,
    enable,
    disable,
    deleteTenant,
  } = props

  // const [dropDownOpen, setDropDownOpen] = useState(false)

  // const toggleActions = () => {
  //   setDropDownOpen(!dropDownOpen)
  // }

  const confirmDeleteTenant = () => {
    toggleShowModal(true)
  }

  const deleteTenantDismissModal = () => {
    toggleShowModal(false)
    deleteTenant()
  }

  const showError = (error, handleError) => {
    return (
      <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
        <h4 className={'alert-heading'}>Error</h4>
        <p>{error}</p>
      </Alert>
    )
  }
  const [showModal, toggleShowModal] = useState(false)
  const [matches, setMatches] = useState(false)
  const [services, setServices] = useState([{}])

  useEffect(() => {
    if (!tenant) return
    const s = Object.keys(tenant?.resources)
      .filter((key) => key.startsWith('SERVICE_'))
      .map((serviceName) => {
        const service = tenant.resources[serviceName]
        return {
          name: service.name,
          url: service.consoleUrl,
          key: service.name,
        }
      })
    setServices(s)
  }, [tenant])
  const toggleModal = () => {
    toggleShowModal((s) => !s)
  }

  const checkMatches = (event) => {
    if (event.target.value === tenant.id) {
      setMatches(true)
    }
  }

  return (
    <>
      <Modal size="lg" fade={true} show={showModal}>
        <Modal.Header className="bg-primary">Confirm Delete</Modal.Header>
        <Modal.Body>
          <p>
            Delete tenant with ID <code>{tenant?.id}</code>? Please type the ID
            of the tenant to confirm.
          </p>
          <Form>
            <FormGroup>
              <Form.Control
                onChange={checkMatches}
                type="text"
                name="tenantId"
                id="tenantId"
                placeholder="Tenant ID"
              />
            </FormGroup>
          </Form>
        </Modal.Body>
        <Modal.Footer>
          <Button
            disabled={!matches}
            color="danger"
            onClick={deleteTenantDismissModal}
          >
            Yes, delete
          </Button>
          <Button color="primary" onClick={toggleModal}>
            Cancel
          </Button>
        </Modal.Footer>
      </Modal>
      <div className="animated fadeIn">
        <Row>
          <Col>{error && showError(error, handleError)}</Col>
        </Row>
        <Row className="mb-3">
          <Col className="justify-content-end">
            <Dropdown
              className="float-right"
              // toggle={toggleActions}
              // isOpen={dropDownOpen}
            >
              <Dropdown.Toggle color="primary" disabled={loading === 'pending'}>
                <MoonLoader
                  size={15}
                  className="d-inline-block"
                  css={override}
                  loading={loading === 'pending'}
                />{' '}
                <span>Actions</span>
              </Dropdown.Toggle>
              {loading === 'idle' && !!tenant && (
                <Dropdown.Menu end="true">
                  <Dropdown.Item onClick={() => toggleEdit()}>
                    Edit
                  </Dropdown.Item>
                  <Dropdown.Item disabled={tenant.active} onClick={enable}>
                    Enable
                  </Dropdown.Item>
                  <Dropdown.Item disabled={!tenant.active} onClick={disable}>
                    Disable
                  </Dropdown.Item>
                  <Dropdown.Item
                    disabled={!tenant.active}
                    onClick={confirmDeleteTenant}
                  >
                    Delete
                  </Dropdown.Item>
                </Dropdown.Menu>
              )}
            </Dropdown>
          </Col>
        </Row>
        <Row>
          <Col xs={12}>
            <Card>
              <Card.Header>
                <strong>{tenant && tenant.name}</strong> (Id:{' '}
                {tenant && tenant.id})
              </Card.Header>
              <Card.Body>
                <Row className="pt-3">
                  <Col
                    sm={4}
                    className="border border border-top-0 border-bottom-0 border-left-0"
                  >
                    <dt>Name</dt>
                    <dd>
                      <Display>{!!tenant && tenant.name}</Display>
                    </dd>
                    <dt>Subdomain</dt>
                    <dd>
                      <Display>{tenant?.subdomain ?? 'Not Configured'}</Display>
                    </dd>
                    <dt>Address</dt>
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            style={{ paddingTop: 0 }}
                            active={true}
                            target="_blank"
                            href={`http://${tenant.hostname}`}
                            className="pl-0"
                          >
                            {`http://${tenant.hostname}`}
                            <CIcon
                              icon={cilExternalLink}
                              customClassName="ml-2 icon"
                            />
                          </NavLink>
                        )}
                      </Display>
                    </dd>
                  </Col>
                  <Col
                    sm={4}
                    className="border border border-top-0 border-bottom-0 border-left-0"
                  >
                    <dt>Active</dt>
                    <dd>
                      <Display condition={!!tenant}>
                        <Badge
                          color={
                            !!tenant && tenant.active ? 'success' : 'danger'
                          }
                        >
                          {!!tenant && tenant.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </Display>
                    </dd>
                    <dt>Onboarding Status</dt>
                    <dd>
                      <Display condition={!!tenant}>
                        {!!tenant && tenant.onboardingStatus}
                      </Display>
                    </dd>
                    {tenant?.fullCustomDomainName && (
                      <>
                        <dt>Tier</dt>
                        <dd>
                          <Display condition={!!tenant}>
                            {!!tenant && tenant.tier}
                          </Display>
                        </dd>
                      </>
                    )}
                  </Col>
                  <Col sm={4}>
                    <dt>Created</dt>
                    <dd>
                      <Display condition={!!tenant}>
                        <Moment format="LLL">
                          {!!tenant && new Date(tenant.created)}
                        </Moment>
                      </Display>
                    </dd>
                    <dt>Modified</dt>
                    <dd>
                      <Display condition={!!tenant}>
                        <Moment format="LLL">
                          {!!tenant && new Date(tenant.modified)}
                        </Moment>
                      </Display>
                    </dd>
                  </Col>
                </Row>
              </Card.Body>
            </Card>
          </Col>
        </Row>
        <Row>
          <Col xs={12}>
            <Card>
              <Card.Header style={banner}>
                <div>
                  <img src="/aws.png" style={{ width: '38px' }}></img>
                  <span style={{ marginLeft: '18px' }}>AWS Console Links</span>
                </div>
              </Card.Header>
              <Card.Body>
                <Row className="pt-3">
                  <Col
                    sm={4}
                    className="border border border-top-0 border-bottom-0 border-left-0"
                  >
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            active={true}
                            target="_blank"
                            href={tenant.resources.LOAD_BALANCER.consoleUrl}
                            className="pl-0"
                          >
                            Application Load Balancer Details
                            <CIcon
                              icon={cilExternalLink}
                              customClassName="ml-2 icon"
                            />
                          </NavLink>
                        )}
                      </Display>
                    </dd>
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            active={true}
                            target="_blank"
                            href={tenant.resources.VPC.consoleUrl}
                            className="pl-0"
                          >
                            VPC Details
                            <CIcon
                              icon={cilExternalLink}
                              customClassName="ml-2 icon"
                            />
                          </NavLink>
                        )}
                      </Display>
                    </dd>
                  </Col>
                  <Col
                    sm={4}
                    className="border border border-top-0 border-bottom-0 border-left-0"
                  >
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <>
                            <div className="d-flex flex-row align-items-center">
                              <span>CodePipeline Details</span>

                              <Dropdown className="ml-2">
                                <Dropdown.Toggle
                                  variant="light"
                                  id="service-dropdown"
                                >
                                  Choose...
                                </Dropdown.Toggle>

                                <Dropdown.Menu>
                                  {services.map((service) => (
                                    <Dropdown.Item
                                      className="link-primary"
                                      href={service.url}
                                      target="_blank"
                                      key={tenant.name + '-' + service.name}
                                    >
                                      {service.name}
                                    </Dropdown.Item>
                                  ))}
                                </Dropdown.Menu>
                              </Dropdown>
                              <CIcon
                                icon={cilExternalLink}
                                customClassName="icon ml-2"
                              />
                            </div>
                          </>
                        )}
                      </Display>
                    </dd>
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            active={true}
                            target="_blank"
                            href={tenant.resources.CLOUDFORMATION.consoleUrl}
                            className="pl-0"
                          >
                            CloudFormation Details
                            <CIcon
                              icon={cilExternalLink}
                              customClassName="ml-2 icon"
                            />
                          </NavLink>
                        )}
                      </Display>
                    </dd>
                  </Col>
                  <Col sm={4}>
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            active={true}
                            target="_blank"
                            href={tenant.resources.ECS_CLUSTER.consoleUrl}
                            className="pl-0"
                          >
                            ECS Cluster Details
                            <CIcon
                              icon={cilExternalLink}
                              customClassName="ml-2 icon"
                            />
                          </NavLink>
                        )}
                      </Display>
                    </dd>
                  </Col>
                </Row>
              </Card.Body>
            </Card>
          </Col>
        </Row>
      </div>
    </>
  )
}

export default TenantViewComponent
