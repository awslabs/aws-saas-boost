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

import React, { useState } from 'react'
import { Accordion, Card, Row, Col, Container, Button } from 'react-bootstrap'
import TierServiceSettingsSubform from './TierServiceSettingsSubform'
import ServiceSettingsSubform from './ServiceSettingsSubform'
import { ServiceNameComponent } from './ServiceNameComponent'
import { PropTypes } from 'prop-types'

const ServicesComponent = (props) => {
  const {
    formik,
    hasTenants,
    osOptions,
    dbOptions,
    onFileSelected,
    tiers,
    initService
  } = props

  const [showModal, setShowModal] = useState(false)
  const [openAccordionItem, setOpenAccordionItem] = useState()

  const toggleModal = () => {
    setShowModal((state) => !state)
  }

  const addService = (serviceName) => {
    let newService = initService(serviceName)
    setOpenAccordionItem(formik.values.services.length)
    formik.values.services.push(newService)
  }

  const deleteService = (index) => {
    // remove one entry starting from `index` from services
    formik.values.services.splice(index, 1)
    // manually remove this entry from errors if necessary, since Formik apparently doesn't do it itself
    formik.errors.services?.splice(index, 1)
    if (openAccordionItem === index) {
      setOpenAccordionItem(null)
    }
    formik.validateForm()
  }

  const defaultTier = () => {
    let filteredTiers = tiers?.filter(t => t.defaultTier)
    if (!!filteredTiers && filteredTiers.length > 0) {
      return filteredTiers[0].name
    }
  }

  return (
    <>
      <Card className="mb-3">
        <Card.Header>
          <Row>
            <Col className="d-flex align-items-center">Services</Col>
            <Col className="d-flex justify-content-end">
              <Button variant="info" type="button" onClick={toggleModal}>
                New Service
              </Button>
            </Col>
          </Row>
        </Card.Header>
        <Card.Body>
          <Accordion activeKey={openAccordionItem} onSelect={(index) => setOpenAccordionItem(index)}>
            {formik.values.services?.map(
              (service, index) => (
                  <Accordion.Item key={service.name} eventKey={index}>
                    <Accordion.Header>{service.name}</Accordion.Header>
                    <Accordion.Body>
                      <ServiceSettingsSubform
                        formik={formik}
                        isLocked={hasTenants}
                        serviceValues={formik.values.services[index]}
                        osOptions={osOptions}
                        dbOptions={dbOptions}
                        onFileSelected={onFileSelected}
                        serviceName={service.name}
                        serviceIndex={index}
                        setFieldValue={(k, v) => formik.setFieldValue(k, v)}
                      ></ServiceSettingsSubform>
                      <TierServiceSettingsSubform
                        tiers={tiers}
                        defaultTier={defaultTier()}
                        isLocked={hasTenants}
                        serviceName={service.name}
                        serviceValues={formik.values.services[index]}
                        dbOptions={dbOptions}
                        formikServicePrefix={'services[' + index + ']'}
                        setFieldValue={(k, v) => formik.setFieldValue(k, v)}
                      ></TierServiceSettingsSubform>
                      <Container className="mt-3">
                        <Row>
                          <Col className="col-md-12 text-right">
                            <Button
                              size="sm"
                              variant="outline-danger"
                              type="button"
                              onClick={() => deleteService(index)}
                            >
                              Delete
                            </Button>
                          </Col>
                        </Row>
                      </Container>
                    </Accordion.Body>
                  </Accordion.Item>
                )
            )}
          </Accordion>
        </Card.Body>
      </Card>
      <ServiceNameComponent
        showModal={showModal}
        addService={addService}
        toggleModal={toggleModal}
      />
    </>
  )
}

ServicesComponent.propTypes = {
  formikServices: PropTypes.array,
  formikErrors: PropTypes.object,
}

export default ServicesComponent
