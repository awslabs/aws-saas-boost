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
import CIcon from '@coreui/icons-react'
import { cilReload, cilListRich, cilCheckCircle, cilXCircle } from '@coreui/icons'
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
import Moment from 'react-moment'
import Display from '../components/Display'

TierListItem.propTypes = {
  tier: PropTypes.object,
  handleTierClick: PropTypes.func,
}

function TierListItem({ tier, handleTierClick }) {
  return (
    <tr
      key={tier.id}
      onClick={() => {
        handleTierClick(tier.id)
      }}
      className="pointer"
    >
      <td>
        {!!tier && tier.defaultTier
          ? (<CIcon icon={cilCheckCircle} className="text-success"/>)
          : (<CIcon icon={cilXCircle} className="text-danger"/>)}
      </td>
      <td>
        <NavLink
          href="#"
          onClick={() => {
            handleTierClick(tier.id)
          }}
          color="link"
          className="pl-0 pt-0"
        >
          {tier.name}
        </NavLink>
      </td>
      <td>{tier.description}</td>
      <td>
        <Display condition={!!tier}>
          <Moment format="LLL">
            {!!tier && new Date(tier.created)}
          </Moment>
        </Display>
      </td>
      <td>
        <Display condition={!!tier}>
          <Moment format="LLL">
            {!!tier && new Date(tier.modified)}
          </Moment>
        </Display>
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

TierList.propTypes = {
  tiers: PropTypes.array,
  loading: PropTypes.string,
  error: PropTypes.string,
  handleProvisionTier: PropTypes.func,
  handleTierClick: PropTypes.func,
  handleCreateTier: PropTypes.func,
  handleRefresh: PropTypes.func,
  handleError: PropTypes.func,
}

function TierList({
  tiers,
  loading,
  error,
  handleTierClick,
  handleCreateTier,
  handleRefresh,
  handleError,
}) {
  const toRender = (
    <Table responsive striped>
      <thead>
        <tr>
          <th width="5%">Default</th>
          <th width="20%">Name</th>
          <th width="40%">Description</th>
          <th width="17.5%">Created</th>
          <th width="17.5%">Modified</th>
        </tr>
      </thead>
      <tbody>
        {loading === 'idle' &&
          tiers.length !== 0 &&
          tiers.map((tier) => (
            <TierListItem tier={tier} key={tier.id} handleTierClick={handleTierClick} />
          ))}
        {tiers.length === 0 && loading === 'idle' && (
          <tr>
            <td colSpan="5" className="text-center">
              <p className="font-weight-bold">No results</p>
              <p>There are no Tiers to display.</p>
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
            <Button color="primary" onClick={handleCreateTier}>
              Create Tier
            </Button>
          </div>
        </Col>
      </Row>
      <Row>
        <Col xs="12" lg="12">
          <Card>
            <CardHeader>
              <CIcon icon={cilListRich} /> Tiers
            </CardHeader>
            <CardBody>{toRender}</CardBody>
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default TierList
