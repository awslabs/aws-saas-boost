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
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import { Row, Col, Card, Button, CardHeader, CardBody, CardFooter, Alert } from 'reactstrap'
import { SaasBoostInput, SaasBoostSelect, SaasBoostCheckbox } from '../components/FormComponents'

const initialTenant = {
  tenantId: null,
  name: '',
  description: '',
  subdomain: '',
  planId: '',
}

const TenantForm = (props) => {
  const {
    handleSubmit,
    handleCancel,
    tenant = initialTenant,
    error,
    dismissError,
    config,
    plans,
  } = props
  const { domainName, minCount, maxCount, computeSize, billing } = config

  const hasDomain = () => {
    return !!domainName
  }

  const initialValues = {
    ...tenant,
    overrideDefaults: !!tenant.computeSize,
    computeSize: tenant.computeSize || computeSize,
    hasBilling: !!billing,
    minCount: tenant.minCount || minCount,
    maxCount: tenant.maxCount || maxCount,
    planId: tenant.planId || billing?.planId || 'product_none',
  }

  const showError = (error, dismissError) => {
    if (!!error) {
      return (
        <Row>
          <Col md={6}>
            <Alert color="danger" isOpen={!!error} toggle={() => dismissError()}>
              <h4 className="alert-heading">Error</h4>
              <p>{error}</p>
            </Alert>
          </Col>
        </Row>
      )
    }
    return undefined
  }

  const getBillingUi = (plans, hasBilling) => {
    const options = plans.map((plan) => {
      return (
        <option value={plan.planId} key={plan.planId}>
          {plan.planName}
        </option>
      )
    })
    return (
      hasBilling && (
        <Row>
          <Col>
            <SaasBoostSelect type="select" name="planId" id="planId" label="Billing Plan">
              {options}
            </SaasBoostSelect>
          </Col>
        </Row>
      )
    )
  }

  const getDomainUi = (domainName) => {
    return hasDomain() ? (
      <Row>
        <Col sm={8}>
          <SaasBoostInput name="subdomain" label="Subdomain" type="text" maxLength={25} />
        </Col>
        <Col sm={4}>
          <div></div>
          <p className="text-muted" style={{ marginLeft: '-20px', marginTop: '42px' }}>
            .{domainName}
          </p>
        </Col>
      </Row>
    ) : null
  }

  return (
    <Formik
      initialValues={initialValues}
      enableReinitialize={true}
      validationSchema={Yup.object({
        name: Yup.string().max(100, 'Must be 100 characters or less.').required('Required'),
        description: Yup.string().max(100, 'Must be 100 characters or less.'),
        subdomain: Yup.string()
          .when('fullCustomDomainName', {
            is: (fullCustomDomainName) => !!fullCustomDomainName,
            then: Yup.string()
              .required('Required because a domain name was specified during application setup')
              .matches('^[a-zA-Z0-9][a-zA-Z0-9.-]*[a-zA-Z0-9]$'),
            otherwise: Yup.string(),
          })
          .max(25, 'Must be 25 characters or less.')
          .nullable(),
        computeSize: Yup.string().when('overrideDefaults', {
          is: true,
          then: Yup.string().required('Instance size is a required field.'),
          otherwise: Yup.string(),
        }),
        minCount: Yup.number().when('overrideDefaults', {
          is: true,
          then: Yup.number()
            .required('Minimum count is a required field.')
            .integer('Minimum count must be an integer value')
            .min(1, 'Minimum count must be at least ${min}'),
          otherwise: Yup.number(),
        }),
        maxCount: Yup.number().when('overrideDefaults', {
          is: true,
          then: Yup.number()
            .required('Maximum count is a required field.')
            .integer('Maximum count must be an integer value')
            .max(10, 'Maximum count can be no larger than ${max}')
            .test(
              'match',
              'Maximum count cannot be smaller than minimum count',
              function (maxCount) {
                return maxCount >= this.parent.minCount
              },
            ),
          otherwise: Yup.number(),
        }),
        planId: Yup.string().when('hasBilling', {
          is: true,
          then: Yup.string().required('Billing plan is a required field'),
          otherwise: Yup.string(),
        }),
      })}
      onSubmit={handleSubmit}
    >
      {(formik) => (
        <Form>
          {tenant.tenantId && (
            <input type="hidden" name="tenantID" id="tenantId" value={tenant.tenantId} />
          )}
          <div className="animated fadeIn">
            {showError(error, dismissError)}
            <Row>
              <Col lg={6}>
                <Card>
                  <CardHeader>Tenant Details</CardHeader>
                  <CardBody>
                    <SaasBoostInput label="Name" name="name" type="text" />
                    {getDomainUi(domainName)}
                    <SaasBoostCheckbox
                      name="overrideDefaults"
                      id="overrideDefaults"
                      label="Override Application Defaults"
                      value={formik.values?.overrideDefaults}
                    ></SaasBoostCheckbox>
                    <SaasBoostSelect
                      disabled={!formik.values.overrideDefaults}
                      type="select"
                      name="computeSize"
                      id="computeSize"
                      label="Compute Size"
                    >
                      <option value="S">Small</option>
                      <option value="M">Medium</option>
                      <option value="L">Large</option>
                      <option value="XL">X-Large</option>
                    </SaasBoostSelect>
                    <Row>
                      <Col>
                        <SaasBoostInput
                          disabled={!formik.values.overrideDefaults}
                          key="minCount"
                          label="Minimum Instance Count"
                          name="minCount"
                          type="number"
                        />
                      </Col>
                      <Col>
                        <SaasBoostInput
                          disabled={!formik.values.overrideDefaults}
                          key="maxCount"
                          label="Maximum Instance Count"
                          name="maxCount"
                          type="number"
                        />
                      </Col>
                    </Row>
                    {getBillingUi(plans, !!billing)}
                  </CardBody>
                  <CardFooter>
                    <Button color="danger" onClick={handleCancel}>
                      Cancel
                    </Button>
                    <Button
                      className="ml-2"
                      color="primary"
                      type="Submit"
                      disabled={formik.isSubmitting}
                    >
                      {formik.isSubmitting ? 'Saving...' : 'Submit'}
                    </Button>
                  </CardFooter>
                </Card>
              </Col>
            </Row>
          </div>
        </Form>
      )}
    </Formik>
  )
}

TenantForm.propTypes = {
  handleSubmit: PropTypes.func,
  handleCancel: PropTypes.func,
  dismissError: PropTypes.func,
  tenant: PropTypes.object,
  error: PropTypes.string,
  config: PropTypes.object,
  plans: PropTypes.array,
}

export default TenantForm
