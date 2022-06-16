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
import React, { useState } from 'react'
import CIcon from '@coreui/icons-react'
import { cilCheckCircle, cilXCircle } from '@coreui/icons'
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
import Display from '../components/Display'
import Moment from 'react-moment'
import { MoonLoader } from 'react-spinners'

TierViewComponent.propTypes = {
  tier: PropTypes.object,
  loading: PropTypes.string,
  error: PropTypes.string,
  handleError: PropTypes.func,
  toggleEdit: PropTypes.func,
  deleteTier: PropTypes.func,
}

function TierViewComponent(props) {
  const banner = {
    fontWeight: 600,
    color: '#f6f6f6',
    background: '#232f3e',
    border: '1px solid orange',
  }

  const {
    tier,
    loading,
    error,
    handleError,
    toggleEdit,
    deleteTier,
  } = props

  const confirmDeleteTier = () => {
    toggleShowModal(true)
  }

  const deleteTierDismissModal = () => {
    toggleShowModal(false)
    deleteTier()
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

  const toggleModal = () => {
    toggleShowModal((s) => !s)
  }

  const checkMatches = (event) => {
    if (event.target.value === tier.id) {
      setMatches(true)
    }
  }

  return (
    <>
      <Modal size="lg" fade={"true"} show={showModal}>
        <Modal.Header className="bg-primary">Confirm Delete</Modal.Header>
        <Modal.Body>
          <p>
            Delete tier <code>{tier?.name}</code> (id: <code>{tier?.id}</code>)? Please type the ID
            of the tier to confirm.
          </p>
          <Form>
            <FormGroup>
              <Form.Control
                onChange={checkMatches}
                type="text"
                name="tierId"
                id="tierId"
                placeholder="Tier ID"
              />
            </FormGroup>
          </Form>
        </Modal.Body>
        <Modal.Footer>
          <Button
            disabled={!matches}
            color="danger"
            onClick={deleteTierDismissModal}
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
            >
              <Dropdown.Toggle color="primary" disabled={loading === 'pending'}>
                <MoonLoader
                  size={15}
                  className="d-inline-block"
                  loading={loading === 'pending'}
                />{' '}
                <span>Actions</span>
              </Dropdown.Toggle>
              {loading === 'idle' && !!tier && (
                <Dropdown.Menu end="true">
                  <Dropdown.Item onClick={() => toggleEdit()}>
                    Edit
                  </Dropdown.Item>
                  <Dropdown.Item
                    onClick={confirmDeleteTier}
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
                <strong>{tier && tier.name}</strong> (Id:{' '}
                {tier && tier.id})
              </Card.Header>
              <Card.Body>
                <Row className="pt-3">
                  <Col
                    sm={4}
                    className="border border border-top-0 border-bottom-0 border-left-0"
                  >
                    <dt>Default</dt>
                    <dd>
                      <Display condition={!!tier}>
                        {!!tier && tier.defaultTier
                          ? (<CIcon icon={cilCheckCircle} className="text-success"/>)
                          : (<CIcon icon={cilXCircle} className="text-danger"/>)}
                      </Display>
                    </dd>
                    <dt>Name</dt>
                    <dd>
                      <Display condition={!!tier}>{!!tier && tier.name}</Display>
                    </dd>
                  </Col>
                  <Col
                    sm={4}
                    className="border border border-top-0 border-bottom-0 border-left-0"
                  >
                    <dt>Created</dt>
                    <dd>
                      <Display condition={!!tier}>
                        <Moment format="LLL">
                          {!!tier && new Date(tier.created)}
                        </Moment>
                      </Display>
                    </dd>
                    <dt>Modified</dt>
                    <dd>
                      <Display condition={!!tier}>
                        <Moment format="LLL">
                          {!!tier && new Date(tier.modified)}
                        </Moment>
                      </Display>
                    </dd>
                  </Col>
                  <Col
                    sm={4}
                    className="border border border-top-0 border-bottom-0 border-left-0"
                  >
                    <dt>Description</dt>
                    <dd>
                      <Display condition={!!tier}>{!!tier && tier?.description}</Display>
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

export default TierViewComponent
