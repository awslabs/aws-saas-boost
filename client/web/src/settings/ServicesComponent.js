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
    appConfig,
    formik,
    hasTenants,
    osOptions,
    dbOptions,
    onFileSelected,
    tiers
  } = props

  // we should default this to the list of services defined in the appConfig, or just default otherwise
  // let's say services here is just a list of names
  // const [services, setServices] = useState([])
  // but only after appConfig exists -- the below line sets default, but causes failures on signout bc
  //   appConfig is undefined (or bc appConfig.services is undefined?)
  const [services, setServices] = useState((!!appConfig && !!appConfig.services) ? Object.keys(appConfig?.services) : [])

  let serviceIndex = services.length
//  console.log(appConfig.services)
//  console.log(services)

  /*
    <Row><Col xs={12}><Card><Row><Col>???
  */

  const addService = () => {
    console.log('addService!')
    let newServiceName = 'service' + serviceIndex
    const extendedServices = [
      newServiceName,
      ...services
    ]
    setServices(extendedServices)
    serviceIndex++
  }

  const deleteService = (serviceName) => {
    console.log('deleting ' + serviceName)
    const index = services.indexOf(serviceName)
    if (index > -1) {
      services.splice(index, 1)
    }
    setServices(services)
    // TODO currently delete doesn't actually delete the accordionItem if expanded
  }

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
            {services.map((serviceName, index) => (
              <CAccordionItem key={"service" + index} itemKey={"service" + index}>
                <CAccordionHeader>{serviceName}</CAccordionHeader>
                <CAccordionBody>
                  <ServiceSettingsSubform
                    isLocked={hasTenants}
                    formik={formik}
                    osOptions={osOptions}
                    serviceIndex={index}
                  ></ServiceSettingsSubform>
                  <TierServiceSettingsSubform
                    tiers={tiers}
                    isLocked={hasTenants}
                    formik={formik}
                    serviceValues={formik.values.services[index]}
                    dbOptions={dbOptions}
                    onFileSelected={(file) => onFileSelected(formik, file)}
                    formikServicePrefix={'services[' + index + ']'}
                  ></TierServiceSettingsSubform>
                  <CButton
                      size="sm"
                      color="danger"
                      type="button"
                      onClick={() => deleteService(serviceName)}>
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
  appConfig: PropTypes.object,
  formik: PropTypes.object,
}

export default ServicesComponent
