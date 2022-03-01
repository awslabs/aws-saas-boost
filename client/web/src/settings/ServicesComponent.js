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
import { Row, Col, Card, CardBody, CardHeader, FormGroup, Label, FormFeedback } from 'reactstrap'
import { CAccordion, CAccordionHeader, CAccordionItem, CAccordionBody, CButton, CRow, CCol } from '@coreui/react'
import CIcon from '@coreui/icons-react'
import { cilX } from '@coreui/icons'
import { Field } from 'formik'
import { SaasBoostSelect, SaasBoostInput } from '../components/FormComponents'
import { PropTypes } from 'prop-types'
import TierServiceSettingsSubform from './TierServiceSettingsSubform'
import ServiceSettingsSubform from './ServiceSettingsSubform'

const ServicesComponent = (props) => {
  const {
    formik,
    formikErrors,
    hasTenants,
    osOptions,
    dbOptions,
    onFileSelected,
    tiers,
    initService,
  } = props
  const [services, setServices] = useState(formik.values.services)

  const addService = (serviceName) => {
    console.log('addService ' + serviceName)
    let newService = initService(serviceName)
    formik.values.services.push(newService)
    setServices([
      ...formik.values.services
    ])
  }

  const deleteService = (index) => {
    // we can't just remove this service from the list because it'll mess with our indices
    formik.values.services[index].tombstone = true
    setServices(formik.values.services)
    // kick off validation so the schema recognizes the tombstone and clears any pending errors
    formik.validateForm()
  }

  const nextServiceIndex = services.length

  // TODO alwaysOpen=true in CAccordion has a bug when creating multiple services then opening
  return (
    <>
      <Card>
        <CardHeader>
          <CRow>
            <CCol className="d-flex align-items-center">Services</CCol>
            <CCol className="d-flex justify-content-end">
              <CButton type="button" onClick={addService}>New Service</CButton>
            </CCol>
          </CRow>
        </CardHeader>
        <CardBody>
          <CAccordion alwaysOpen={false}>
            {services.map((service, index) => !service.tombstone && (
              <CAccordionItem key={"service" + index} itemKey={"service" + index}>
                <CAccordionHeader>{service.name}</CAccordionHeader>
                <CAccordionBody>
                  <ServiceSettingsSubform
                    isLocked={hasTenants}
                    formikService={services[index]}
                    formikErrors={formikErrors}
                    osOptions={osOptions}
                    serviceIndex={index}
                  ></ServiceSettingsSubform>
                  <TierServiceSettingsSubform
                    tiers={tiers}
                    isLocked={hasTenants}
                    formikService={services[index]}
                    serviceValues={service}
                    dbOptions={dbOptions}
                    onFileSelected={onFileSelected}
                    formikServicePrefix={'services[' + index + ']'}
                  ></TierServiceSettingsSubform>
                  <CButton
                      size="sm"
                      color="danger"
                      type="button"
                      onClick={() => deleteService(index)}>
                    <CIcon icon={cilX} />
                  </CButton>
                </CAccordionBody>
              </CAccordionItem>
            ))}
          </CAccordion>
        </CardBody>
      </Card>
    </>
  )
}

ServicesComponent.propTypes = {
  formikServices: PropTypes.array,
  formikErrors: PropTypes.object,
}

export default React.memo(ServicesComponent)
