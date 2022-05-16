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
import CIcon from '@coreui/icons-react'
import { cilReload, cilListRich } from '@coreui/icons'
import {
  Card,
  CardBody,
  CardHeader,
  Table,
  Button,
  Row,
  Col,
  Spinner,
  Alert,
  Badge,
  NavLink,
} from 'reactstrap'

TenantListItem.propTypes = {
  tenant: PropTypes.object,
  handleTenantClick: PropTypes.func,
}

function TenantListItem({ tenant, handleTenantClick }) {
  return (
    <tr
      key={tenant.id}
      onClick={() => {
        handleTenantClick(tenant.id)
      }}
      className="pointer"
    >
      <td>
        <NavLink
          href="#"
          onClick={() => {
            handleTenantClick(tenant.id)
          }}
          color="link"
          className="pl-0 pt-0"
        >
          {tenant.id}
        </NavLink>
      </td>
      <td>{tenant.name}</td>
      <td>
        <Badge color={!!tenant && tenant.active ? 'success' : 'danger'}>
          {!!tenant && tenant.active ? 'Active' : 'Inactive'}
        </Badge>
      </td>
      <td>{tenant.subdomain}</td>
      <td>
        <Moment format="LLL">{tenant.created}</Moment>
      </td>
    </tr>
  )
}

function showError(error, handleError) {
  return (
    <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
      <h4 className="alert-heading">Error</h4>
      <p>{error}</p>
    </Alert>
  )
}

TenantList.propTypes = {
  tenants: PropTypes.array,
  loading: PropTypes.string,
  error: PropTypes.string,
  handleProvisionTenant: PropTypes.func,
  handleTenantClick: PropTypes.func,
  handleRefresh: PropTypes.func,
  handleError: PropTypes.func,
}

function TenantList({
  tenants,
  loading,
  error,
  handleProvisionTenant,
  handleTenantClick,
  handleRefresh,
  handleError,
}) {
  const toRender = (
    <Table responsive striped>
      <thead>
        <tr>
          <th width="30%">Id</th>
          <th width="20%">Name</th>
          <th width="10%">Status</th>
          <th width="20%">Subdomain</th>
          <th width="20%">Created On</th>
        </tr>
      </thead>
      <tbody>
        {loading === 'idle' &&
          tenants.length !== 0 &&
          tenants.map((tenant) => (
            <TenantListItem tenant={tenant} key={tenant.id} handleTenantClick={handleTenantClick} />
          ))}
        {tenants.length === 0 && loading === 'idle' && (
          <tr>
            <td colSpan="5" className="text-center">
              <p className="font-weight-bold">No results</p>
              <p>There are no Tenants to display.</p>
            </td>
          </tr>
        )}
        {loading === 'pending' && (
          <tr>
            <td colSpan="5" className="text-center">
              <Spinner animation="border" role="status">
                Loading...
              </Spinner>
            </td>
          </tr>
        )}
      </tbody>
    </Table>
  )

  return (
    <div className="animated fadeIn">
      <Row>
        <Col>{error && showError(error, handleError)}</Col>
      </Row>
      <Row className="mb-3">
        <Col>
          <div className="float-right">
            <Button color="secondary" className="mr-2" onClick={handleRefresh}>
              <span>
                <CIcon icon={cilReload} />
              </span>
            </Button>
          </div>
        </Col>
      </Row>
      <Row>
        <Col xs="12" lg="12">
          <Card>
            <CardHeader>
              <CIcon icon={cilListRich} /> Tenants
            </CardHeader>
            <CardBody>{toRender}</CardBody>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default TenantList
