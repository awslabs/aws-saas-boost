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
import {
  SaasBoostInput,
  SaasBoostSelect,
  SaasBoostCheckbox,
  SaasBoostFileUpload,
} from '../components/FormComponents'

function showError(message, name) {
  let color = !!name && name === 'MissingECRImageError' ? 'warning' : 'danger'
  let displayMessage =
    !!name && name === 'MissingECRImageError'
      ? 'No application image uploaded to ECR. An image must be uploaded before you can onboard tenants'
      : message

  return (
    <Alert fade color={color}>
      {displayMessage}
    </Alert>
  )
}

OnboardingFormComponent.propTypes = {
  error: PropTypes.object,
  errorName: PropTypes.string,
  submit: PropTypes.func,
  cancel: PropTypes.func,
  config: PropTypes.object,
  billingPlans: PropTypes.array,
  onFileSelected: PropTypes.func,
  values: PropTypes.object,
}

export default function OnboardingFormComponent(props) {
  const { error, errorName, submit, cancel, config, billingPlans } = props
  const { domainName, minCount, maxCount, computeSize, billing } = config
  const hasBilling = !!billing
  const hasDomain = !!domainName

  const initialValues = {
    name: '',
    subdomain: '',
    planId: '',
    overrideDefaults: false,
    hasBilling: hasBilling,
    hasDomain: hasDomain,
    computeSize: computeSize ?? '',
    minCount: minCount ?? 1,
    maxCount: maxCount ?? 1,
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
              <option value="">Select One...</option>
              {options}
            </SaasBoostSelect>
          </Col>
        </Row>
      )
    )
  }

  const getDomainUi = (domainName, hasDomain) => {
    return hasDomain ? (
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

  let validationSchema
  validationSchema = Yup.object({
    name: Yup.string().max(100, 'Must be 100 characters or less.').required('Required'),
    subdomain: Yup.string()
      .when('hasDomain', {
        is: true,
        then: Yup.string()
          .required('Required because a domain name was specified during application setup')
          .matches('^[a-zA-Z0-9][a-zA-Z0-9.-]+[a-zA-Z0-9]$'),
        otherwise: Yup.string(),
      })
      .max(25, 'Must be 25 characters or less.'),
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
        .test('match', 'Maximum count cannot be smaller than minimum count', function (maxCount) {
          return maxCount >= this.parent.minCount
        }),
      otherwise: Yup.number(),
    }),
    planId: Yup.string().when('hasBilling', {
      is: true,
      then: Yup.string().required('Billing plan is a required field'),
      otherwise: Yup.string(),
    }),
  })

  return (
    <div className="animated fadeIn">
      <Formik
        enableReinitialize={true}
        initialValues={initialValues}
        onSubmit={submit}
        validationSchema={validationSchema}
      >
        {(formik) => (
          <Form>
            <Row>
              <Col lg={6}>{!!error && showError(error, errorName)}</Col>
            </Row>
            <Row>
              <Col lg={6}>
                <Card>
                  <CardHeader>Onboarding Request</CardHeader>
                  <CardBody>
                    <SaasBoostInput name="name" label="Tenant Name" type="text" maxLength={100} />
                    {getDomainUi(domainName, hasDomain)}
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
                      <option value="">Select One...</option>
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
                    {getBillingUi(billingPlans, hasBilling)}
                    <SaasBoostFileUpload
                      fileMask=".zip"
                      label="(OPTIONAL) Select or drop a .zip file that contains config files for your new tenant"
                      onFileSelected={props.onFileSelected}
                      fname={props.values?.bootstrapFilename}
                    />
                  </CardBody>
                  <CardFooter>
                    <Button color="danger" onClick={cancel} type="button">
                      Cancel
                    </Button>
                    <Button
                      type="submit"
                      color="primary"
                      className="ml-2"
                      disabled={formik.isSubmitting}
                    >
                      {formik.isSubmitting ? 'Saving...' : 'Submit'}
                    </Button>
                  </CardFooter>
                </Card>
              </Col>
            </Row>
          </Form>
        )}
      </Formik>
    </div>
  )
}
