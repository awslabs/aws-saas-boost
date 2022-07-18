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
import {
  Row,
  Col,
  Card,
  Button,
  CardHeader,
  CardBody,
  CardFooter,
  Alert,
} from 'reactstrap'
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
  const {
    error,
    errorName,
    submit,
    cancel,
    config,
    billingPlans,
    tiers,
  } = props
  const { domainName, tier, billing } = config
  const hasBilling = !!billing
  const hasDomain = !!domainName

  const initialValues = {
    name: '',
    tier: tiers?.filter((tier) => tier.defaultTier)[0].name || '',
    subdomain: '',
    billingPlan: '',
    hasBilling: hasBilling,
    hasDomain: hasDomain,
  }

  const getTiers = (tiers, selectedTier) => {
    const defaultTier = tiers.filter((tier) => tier.defaultTier)[0].name
    if (!selectedTier) {
      selectedTier = defaultTier
    }
    const options = tiers.map((tier) => {
      return (
        <option value={tier.name} key={tier.id}>
          {tier.name}
        </option>
      )
    })
    return (
      <SaasBoostSelect type="select" name="tier" label="Select Tier" value={selectedTier}>
        <option value='' key=''>
          Select a Tier..
        </option>
        {options}
      </SaasBoostSelect>
    )
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
            <SaasBoostSelect
              type="select"
              name="billingPlan"
              id="billingPlan"
              label="Billing Plan"
            >
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
          <SaasBoostInput
            name="subdomain"
            label="Subdomain"
            type="text"
            maxLength={25}
          />
        </Col>
        <Col sm={4}>
          <div></div>
          <p
            className="text-muted"
            style={{ marginLeft: '-20px', marginTop: '42px' }}
          >
            .{domainName}
          </p>
        </Col>
      </Row>
    ) : null
  }

  let validationSchema = Yup.object({
    name: Yup.string()
      .max(100, 'Must be 100 characters or less.')
      .required('Required'),
    tier: Yup.string().optional(),
    subdomain: Yup.string()
      .when('hasDomain', {
        is: true,
        then: Yup.string()
          .required(
            'Required because a domain name was specified during application setup'
          )
          .matches('^[a-zA-Z0-9][a-zA-Z0-9.-]+[a-zA-Z0-9]$'),
        otherwise: Yup.string(),
      })
      .max(25, 'Must be 25 characters or less.'),
    billingPlan: Yup.string().when('hasBilling', {
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
                    <SaasBoostInput
                      name="name"
                      label="Tenant Name"
                      type="text"
                      maxLength={100}
                    />
                    {getTiers(tiers, formik.values.tier)}
                    {getDomainUi(domainName, hasDomain)}
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
