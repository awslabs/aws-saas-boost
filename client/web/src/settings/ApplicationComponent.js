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

import React from 'react'
import { useDispatch } from 'react-redux'

import { Formik, Form } from 'formik'
import { PropTypes } from 'prop-types'
import * as Yup from 'yup'
import { Button, Row, Col, Card, Alert } from 'react-bootstrap'
import LoadingOverlay from '@ronchalant/react-loading-overlay'
import globalConfig from '../config/appConfig'
import AppSettingsSubform from './AppSettingsSubform'
import BillingSubform from './BillingSubform'
import ServicesComponent from './ServicesComponent'
import { FILESYSTEM_DEFAULTS, FILESYSTEM_TYPES, OS_TO_FS_TYPES } from './components/filesystem'

import { dismissConfigError, dismissConfigMessage } from './ducks'

ApplicationComponent.propTypes = {
  appConfig: PropTypes.object,
  dbOptions: PropTypes.array,
  hasTenants: PropTypes.bool,
  loading: PropTypes.string,
  certOptions: PropTypes.array,
  error: PropTypes.bool,
  message: PropTypes.string,
  osOptions: PropTypes.object,
  updateConfiguration: PropTypes.func,
  onFileSelected: PropTypes.func,
  tiers: PropTypes.array,
}

export function ApplicationComponent(props) {
  const dispatch = useDispatch()
  const {
    appConfig,
    dbOptions,
    hasTenants,
    loading,
    error,
    certOptions,
    message,
    osOptions,
    updateConfiguration,
    tiers,
  } = props

  const LINUX = 'LINUX'
  const WINDOWS = 'WINDOWS'
  const awsRegion = globalConfig.region
  const acmConsoleLink = `https://${awsRegion}.console.aws.amazon.com/acm/home?region=${awsRegion}#/certificates/list`

  const updateConfig = (values) => {
    updateConfiguration(values)
    window.scrollTo(0, 0)
  }

  const generateAppConfigOrDefaultInitialValuesForTier = (tierValues, defaultValues, os) => {
    let filesystem = {
      ...FILESYSTEM_DEFAULTS,
      ...defaultValues.filesystem,
      ...splitWeeklyMaintenanceTime(tierValues.filesystem)
    }
    let defaults = Object.assign({
      min: 0,
      max: 0,
      computeSize: '',
    }, defaultValues, tierValues)
    let uncleanedInitialTierValues = {
      ...defaults,
      filesystem: filesystem,
    }

    let filesystemType = OS_TO_FS_TYPES[os]?.filter(type => type.configId === tierValues.filesystem?.type)[0]?.id || ''

    return {
      ...uncleanedInitialTierValues,
      provisionDb: !!tierValues.database,
      provisionFS: !!tierValues.filesystem,
      filesystemType: filesystemType,
      filesystem: filesystem,
    }
  }

  const splitWeeklyMaintenanceTime = (fsx) => {
    if (!!fsx && !!fsx.weeklyMaintenanceTime) {
      const getParts = (dateTime) => {
        const parts = dateTime.split(':')
        const day = parts[0]
        const times = parts.slice(1)
        const timeStr = times.join(':')
        return [day, timeStr]
      }
      const [day, time] = getParts(fsx.weeklyMaintenanceTime)
      return {
        ...fsx,
        weeklyMaintenanceTime: time,
        weeklyMaintenanceDay: day,
      }
    }
    return fsx
  }

  const generateAppConfigOrDefaultInitialValuesForService = (serviceName) => {
    let thisService = appConfig.services[serviceName]
    const os = !!thisService?.operatingSystem
      ? appConfig.services[serviceName].operatingSystem === LINUX
        ? LINUX
        : WINDOWS
      : ''
    const db = !!thisService?.database
        ? {
            ...thisService.database,
            //This is frail, but try to see if the incoming password is base64d
            //If so, assume it's encrypted
            //Also store a copy in the encryptedPassword field
            hasEncryptedPassword: !!thisService.database.password.match(
              /^[A-Za-z0-9=+/\s ]+$/
            ),
            encryptedPassword: thisService.database.password,
          }
        : {
            engine: '',
            family: '',
            version: '',
            username: '',
            password: '',
            hasEncryptedPassword: false,
            encryptedPassword: '',
            database: '',
            bootstrapFilename: '',
            tiers: {},
          }
    const windowsVersion = os === WINDOWS ? thisService.operatingSystem : ''
    let defaultTierName = tiers.filter(t => t.defaultTier)[0].name
    let defaultTierValues = generateAppConfigOrDefaultInitialValuesForTier(Object.assign({}, thisService?.tiers[defaultTierName]), {}, os)
    let initialTierValues = {}
    for (var i = 0; i < tiers.length; i++) {
      var tierName = tiers[i].name
      initialTierValues[tierName] = generateAppConfigOrDefaultInitialValuesForTier(Object.assign({}, thisService?.tiers[tierName]), defaultTierValues, os)
    }
    return {
      ...thisService,
      name: thisService?.name || serviceName,
      path: thisService?.path || '/*',
      public: thisService ? thisService.public : true,
      healthCheckUrl: thisService?.healthCheckUrl || '/',
      containerPort: thisService?.containerPort || 0,
      containerTag: thisService?.containerTag || 'latest',
      description: thisService?.description || '',
      operatingSystem: os,
      database: db,
      provisionDb: !!thisService?.database,
      windowsVersion: windowsVersion,
      tiers: initialTierValues,
      tombstone: false,
    }
  }

  const parseServicesFromAppConfig = () => {
    let initialServiceValues = []
    if (!!appConfig?.services) {
      for (const serviceName of Object.keys(appConfig?.services).sort()) {
        initialServiceValues.push(
          generateAppConfigOrDefaultInitialValuesForService(serviceName)
        )
      }
    }
    return initialServiceValues
  }

  const initialValues = {
    name: appConfig.name || '',
    domainName: appConfig.domainName || '',
    sslCertificate: appConfig.sslCertificate || '',
    services: parseServicesFromAppConfig(),
    billing: appConfig.billing || {
      apiKey: '',
    },
    provisionBilling: !!appConfig.billing || false,
  }

  // min, max, computeSize, cpu/memory/instanceType (not in form), filesystem, database
  const singleTierValidationSpec = (tombstone) => {
    return Yup.object({
      min: requiredIfNotTombstoned(
        tombstone,
        Yup.number()
          .integer('Minimum count must be an integer value')
          .min(1, 'Minimum count must be at least ${min}'),
        'Minimum count is a required field.'
      ),
      max: Yup.number()
        .required('Maximum count is a required field.')
        .integer('Maximum count must be an integer value')
        .max(10, 'Maximum count can be no larger than ${max}')
        .test('match', 'Max cannot be smaller than min', function (max) {
          return max >= this.parent.min
        }),
      computeSize: requiredIfNotTombstoned(
        tombstone,
        Yup.string(),
        'Compute size is a required field.'
      ),
      filesystem: Yup.object().when(['provisionFS', 'filesystemType'], (provisionFS, filesystemType) => {
        if (provisionFS) {
          return FILESYSTEM_TYPES[filesystemType]?.validationSchema || Yup.object()
        }
        return Yup.object()
      }),
    })
  }

  const allTiersValidationSpec = (tombstone) => {
    let allTiers = {}
    for (var i = 0; i < tiers.length; i++) {
      var tierName = tiers[i].name
      allTiers[tierName] = singleTierValidationSpec(tombstone)
    }
    return Yup.object(allTiers)
  }

  const allTiersDatabaseValidationSpec = (tombstone) => {
    let allTiers = {}
    for (var i = 0; i < tiers.length; i++) {
      var tierName = tiers[i].name
      allTiers[tierName] = Yup.object({
        instance: requiredIfNotTombstoned(
          tombstone,
          Yup.string(),
          'Database instance is required.'
        )
      })
    }
    return Yup.object(allTiers)
  }

  const requiredIfNotTombstoned = (tombstone, schema, requiredMessage) => {
    return tombstone ? schema : schema.required(requiredMessage)
  }

  // TODO public service paths cannot match
  const validationSpecs = Yup.object({
    name: Yup.string().required('Name is a required field.'),
    domainName: Yup.string().matches(
      /^$|(?=^.{4,253}$)(^((?!-)[a-zA-Z0-9-]{0,62}[a-zA-Z0-9]\.)+[a-zA-Z]{2,63}$)/,
      'Domain Name is not in valid format.'
    ),
    services: Yup.array(
      Yup.object({
        public: Yup.boolean().required(),
        name: Yup.string()
          .when('tombstone', (tombstone, schema) => {
            return requiredIfNotTombstoned(
              tombstone,
              schema,
              'Service Name is a required field.'
            )
          })
          .matches(
            /^[\.\-_a-zA-Z0-9]+$/,
            'Name must only contain alphanumeric characters or .-_'
          ),
        description: Yup.string(),
        path: Yup.string()
          .when(['public', 'tombstone'], (isPublic, tombstone, schema) => {
            if (isPublic) {
              return requiredIfNotTombstoned(
                tombstone,
                schema,
                'Path is required for public services'
              )
            }
            return schema
          })
          .matches(
            /^\/.+$/,
            'Path must start with / and be followed by at least one character.'
          ),
        containerPort: Yup.number()
          .integer('Container port must be an integer value.')
          .required('Container port is a required field.'),
        containerTag: Yup.string().required(
          'Container Tag is a required field.'
        ),
        healthCheckUrl: Yup.string()
          .required('Health Check URL is a required field')
          .matches(/^\//, 'Health Check must start with forward slash (/)'),
        operatingSystem: Yup.string().when('tombstone', (tombstone, schema) => {
          return requiredIfNotTombstoned(
            tombstone,
            schema,
            'Container OS is a required field.'
          )
        }),
        windowsVersion: Yup.string().when('operatingSystem', {
          is: (containerOs) => containerOs && containerOs === WINDOWS,
          then: Yup.string().required('Windows version is a required field'),
          otherwise: Yup.string().nullable(),
        }),
        provisionDb: Yup.boolean(),
        database: Yup.object().when('provisionDb', {
          is: true,
          then: Yup.object({
            engine: Yup.string().required('Engine is required'),
            version: Yup.string().required('Version is required'),
            tiers: Yup.object().when('tombstone', (tombstone, schema) => {
              return allTiersDatabaseValidationSpec(tombstone)
            }),
            username: Yup.string()
              .matches('^[a-zA-Z]+[a-zA-Z0-9_$]*$', 'Username is not valid')
              .required('Username is required'),
            password: Yup.string()
              .when('hasEncryptedPassword', {
                is: false,
                then: Yup.string().matches(
                  '^[a-zA-Z0-9/@"\' ]{8,}$',
                  'Password must be longer than 8 characters and can only contain alphanumberic characters or / @ " \' and spaces'
                ),
              })
              .required('Password is required'),
            database: Yup.string(),
          }),
          otherwise: Yup.object(),
        }),
        tiers: Yup.object().when(['tombstone'], (tombstone) => {
          return allTiersValidationSpec(tombstone)
        }),
        tombstone: Yup.boolean(),
      })
    ).min(1, 'Application must have at least ${min} service(s).'),
    provisionBilling: Yup.boolean(),
  })

  const onFileSelected = (serviceName, file) => {
    props.onFileSelected({ serviceName, file })
  }

  const dismissError = () => {
    dispatch(dismissConfigError())
  }

  const dismissMessage = () => {
    dispatch(dismissConfigMessage())
  }

  const isSubmitting = () => {
    return loading !== 'idle'
  }

  const findServicesWithErrors = (formik) => {
    let servicesWithErrors = []
    if (!!formik?.errors?.services) {
      if (typeof formik.errors.services === 'string') {
        // TODO when is formik.errors.services not an array?
        servicesWithErrors.push(formik.errors.services)
      } else {
        formik.errors.services?.forEach((service, index) => {
          if (!!service) {
            let serviceName = formik.values?.services[index].name
            let serviceMessage = "Service " + serviceName
            if (!!service.tiers) {
              serviceMessage = serviceMessage.concat(" Tiers ", Object.keys(service.tiers).toString())
            }
            servicesWithErrors.push(serviceMessage)
          }
        })
      }
    }
    return servicesWithErrors.join(" ; ")
  }

  return (
    <LoadingOverlay
      active={isSubmitting()}
      spinner
      text="Loading configuration..."
    >
      <div className="animated fadeIn">
        {hasTenants && (
          <Alert variant="primary">
            <span>
              <i className="fa fa-info-circle" /> Note: some settings cannot be
              modified once you have deployed tenants.
            </span>
          </Alert>
        )}
        {/* {loading !== "idle" && <div>Loading...</div>} */}
        {!!error && (
          <Alert variant="danger" isOpen={!!error} toggle={dismissError}>
            {error}
          </Alert>
        )}
        {!!message && (
          <Alert variant="info" isOpen={!!message} toggle={dismissMessage}>
            {message}
          </Alert>
        )}
        <Formik
          initialValues={initialValues}
          validationSchema={validationSpecs}
          validateOnChange={true}
          onSubmit={updateConfig}
          enableReinitialize={true}
        >
          {(formik) => {
            return (
              <>
                {!!formik.errors && Object.keys(formik.errors).length > 0 ? (
                  <Alert variant="danger">
                    Errors in form: {findServicesWithErrors(formik)}
                  </Alert>
                ) : null}
                <Form>
                  <AppSettingsSubform
                    isLocked={hasTenants}
                    certOptions={certOptions}
                    acmConsoleLink={acmConsoleLink}
                  ></AppSettingsSubform>
                  <ServicesComponent
                    formik={formik}
                    formikErrors={formik.errors}
                    hasTenants={hasTenants}
                    osOptions={osOptions}
                    dbOptions={dbOptions}
                    onFileSelected={onFileSelected}
                    tiers={tiers}
                    initService={
                      generateAppConfigOrDefaultInitialValuesForService
                    }
                    setFieldValue={(k, v) => formik.setFieldValue(k, v)}
                  ></ServicesComponent>
                  <BillingSubform
                    provisionBilling={formik.values.provisionBilling}
                    values={formik.values?.billing}
                  ></BillingSubform>
                  <Row>
                    <Col xs={12}>
                      <Card>
                        <Card.Body>
                          <Button
                            type="Submit"
                            variant="info"
                            disabled={isSubmitting()}
                            onClick={() => { window.scrollTo(0,0) }}
                          >
                            {isSubmitting() ? 'Saving...' : 'Submit'}
                          </Button>
                        </Card.Body>
                      </Card>
                    </Col>
                  </Row>
                </Form>
              </>
            )
          }}
        </Formik>
      </div>
    </LoadingOverlay>
  )
}
