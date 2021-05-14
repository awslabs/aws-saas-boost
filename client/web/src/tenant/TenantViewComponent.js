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

import React, { useState } from 'react';

import {
  Alert,
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  Col,
  Dropdown,
  DropdownItem,
  DropdownMenu,
  DropdownToggle,
  Form,
  FormGroup,
  Input,
  Label,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  NavLink,
  Row,
} from 'reactstrap';
import Moment from 'react-moment';
import Display from '../components/Display';
import { MoonLoader } from 'react-spinners';
import { css } from '@emotion/core';

function TenantViewComponent(props) {
  const banner = {
    fontWeight: 600,
    color: '#f6f6f6',
    background: '#232f3e',
    border: '1px solid orange',
  };
  const override = css`
    display: inline-block;
    vertical-align: middle;
  `;

  const { tenant, loading, error, handleError, toggleEdit, enable, disable, deleteTenant } = props;

  const [dropDownOpen, setDropDownOpen] = useState(false);

  const toggleActions = () => {
    setDropDownOpen(!dropDownOpen);
  };

  const confirmDeleteTenant = () => {
    toggleShowModal(true);
  };

  const deleteTenantDismissModal = () => {
    toggleShowModal(false);
    deleteTenant();
  };

  const showError = (error, handleError) => {
    return (
      <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
        <h4 className={'alert-heading'}>Error</h4>
        <p>{error}</p>
      </Alert>
    );
  };
  const [showModal, toggleShowModal] = useState(false);
  const [matches, setMatches] = useState(false);

  const toggleModal = () => {
    toggleShowModal((s) => !s);
  };

  const checkMatches = (event) => {
    if (event.target.value === tenant.id) {
      setMatches(true);
    }
  }

  return (
    <>
      <Modal size="lg" fade={true} isOpen={showModal}>
        <ModalHeader className="bg-primary">Confirm Delete</ModalHeader>
        <ModalBody>
          <p>Delete tenant with ID <code>{tenant?.id}</code>? Please type the ID of the tenant to confirm.</p>
          <Form>
            <FormGroup>
              <Input onChange={checkMatches} type="text" name="tenantId" id="tenantId" placeholder="Tenant ID" />
            </FormGroup>
          </Form>
        </ModalBody>
        <ModalFooter>
          <Button disabled={!matches} color="danger" onClick={deleteTenantDismissModal}>
            Yes, delete
          </Button>
          <Button color="primary" onClick={toggleModal}>
            Cancel
          </Button>
        </ModalFooter>
      </Modal>
      <div className="animated fadeIn">
        <Row>
          <Col>{error && showError(error, handleError)}</Col>
        </Row>
        <Row className={'mb-3'}>
          <Col className="justify-content-end ">
            <Dropdown className="float-right" toggle={toggleActions} isOpen={dropDownOpen}>
              <DropdownToggle caret color="primary" disabled={loading === 'pending'}>
                <MoonLoader
                  size={15}
                  className="d-inline-block"
                  css={override}
                  loading={loading === 'pending'}
                />{' '}
                <span>Actions</span>
              </DropdownToggle>
              {loading === 'idle' && !!tenant && (
                <DropdownMenu right>
                  <DropdownItem onClick={() => toggleEdit()}>Edit</DropdownItem>
                  <DropdownItem disabled={tenant.active} onClick={enable}>
                    Enable
                  </DropdownItem>
                  <DropdownItem disabled={!tenant.active} onClick={disable}>
                    Disable
                  </DropdownItem>
                  <DropdownItem disabled={!tenant.active} onClick={confirmDeleteTenant}>
                    Delete
                  </DropdownItem>
                </DropdownMenu>
              )}
            </Dropdown>
          </Col>
        </Row>
        <Row>
          <Col xs={12}>
            <Card>
              <CardHeader>
                <i className="fa fa-info" />
                <strong>{tenant && tenant.name}</strong> (Id: {tenant && tenant.id})
              </CardHeader>
              <CardBody>
                <Row className="pt-3">
                  <Col sm={4} className="border border border-top-0 border-bottom-0 border-left-0">
                    <dt>Name</dt>
                    <dd>
                      <Display>{!!tenant && tenant.name}</Display>
                    </dd>
                    <dt>Subdomain</dt>
                    <dd>
                      <Display>{tenant?.subdomain ?? 'Not Configured'}</Display>
                    </dd>
                    <dt>Load Balancer DNS</dt>
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            style={{ paddingTop: 0 }}
                            active={true}
                            target="_blank"
                            href={`http://${tenant.resources.LOAD_BALANCER_DNSNAME}`}
                            className="pl-0"
                          >
                            {`http://${tenant.resources.LOAD_BALANCER_DNSNAME}`}
                            <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
                          </NavLink>
                        )}
                      </Display>
                    </dd>
                  </Col>
                  <Col sm={4} className="border border border-top-0 border-bottom-0 border-left-0">
                    <dt>Active</dt>
                    <dd>
                      <Display condition={!!tenant}>
                        <Badge color={!!tenant && tenant.active ? 'success' : 'danger'}>
                          {!!tenant && tenant.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </Display>
                    </dd>
                    <dt>Onboarding Status</dt>
                    <dd>
                      <Display condition={!!tenant}>{!!tenant && tenant.onboardingStatus}</Display>
                    </dd>
                    {tenant?.fullCustomDomainName && (
                      <>
                        <dt>Custom domain URL</dt>
                        <dd>
                          <Display>
                            {!!tenant && !!tenant.fullCustomDomainName && (
                              <NavLink
                                style={{ paddingTop: 0 }}
                                active={true}
                                target="_blank"
                                href={tenant?.fullCustomDomainName}
                                className="pl-0"
                              >
                                {tenant?.fullCustomDomainName}
                                <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
                              </NavLink>
                            )}
                          </Display>
                        </dd>
                      </>
                    )}
                  </Col>
                  <Col sm={4}>
                    <dt>Created</dt>
                    <dd>
                      <Display condition={!!tenant}>
                        <Moment format="LLL">{!!tenant && new Date(tenant.created)}</Moment>
                      </Display>
                    </dd>
                    <dt>Modified</dt>
                    <dd>
                      <Display condition={!!tenant}>
                        <Moment format="LLL">{!!tenant && new Date(tenant.modified)}</Moment>
                      </Display>
                    </dd>
                  </Col>
                </Row>
              </CardBody>
            </Card>
          </Col>
        </Row>
        <Row>
          <Col xs={12}>
            <Card>
              <CardHeader style={banner}>
                <div>
                  <img src="/aws.png" style={{ width: '38px' }}></img>
                  <span style={{ marginLeft: '18px' }}>AWS Console Links</span>
                </div>
              </CardHeader>
              <CardBody>
                <Row className="pt-3">
                  <Col sm={4} className="border border border-top-0 border-bottom-0 border-left-0">
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            active={true}
                            target="_blank"
                            href={tenant.resources.ALB}
                            className="pl-0"
                          >
                            Application Load Balancer Details
                            <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
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
                            href={tenant.resources.VPC}
                            className="pl-0"
                          >
                            VPC Details
                            <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
                          </NavLink>
                        )}
                      </Display>
                    </dd>
                  </Col>
                  <Col sm={4} className="border border border-top-0 border-bottom-0 border-left-0">
                    <dd>
                      <Display>
                        {!!tenant && !!tenant.resources && (
                          <NavLink
                            active={true}
                            target="_blank"
                            href={tenant.resources.CODE_PIPELINE}
                            className="pl-0"
                          >
                            CodePipeline Details
                            <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
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
                            href={tenant.resources.CLOUDFORMATION}
                            className="pl-0"
                          >
                            CloudFormation Details
                            <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
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
                            href={tenant.resources.ECS_CLUSTER_LOG_GROUP}
                            className="pl-0"
                          >
                            ECS Cluster CloudWatch Log
                            <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
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
                            href={tenant.resources.ECS_CLUSTER}
                            className="pl-0"
                          >
                            ECS Cluster Details
                            <i className="fa fa-external-link ml-2" aria-hidden="true"></i>
                          </NavLink>
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
    </>
  );
}

export default TenantViewComponent;
