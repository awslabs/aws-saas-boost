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

import AppSettingsSubform from './AppSettingsSubform'
import BillingSubform from './BillingSubform'
import ServicesComponent from './ServicesComponent'

import { dismissConfigError, dismissConfigMessage } from './ducks'

ApplicationComponent.propTypes = {
  appConfig: PropTypes.object,
  dbOptions: PropTypes.array,
  hasTenants: PropTypes.bool,
  loading: PropTypes.string,
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
    message,
    osOptions,
    updateConfiguration,
    tiers,
  } = props

  const LINUX = 'LINUX'
  const WINDOWS = 'WINDOWS'
  const FSX = 'FSX'
  const EFS = 'EFS'

  const getParts = (dateTime) => {
    const parts = dateTime.split(':')
    const day = parts[0]
    const times = parts.slice(1)
    const timeStr = times.join(':')
    return [day, timeStr]
  }

  const updateConfig = (values) => {
    updateConfiguration(values)
    window.scrollTo(0, 0)
  }

  const getFsx = (fsx) => {
    if (!fsx) {
      return {
        storageGb: 32,
        throughputMbs: 8,
        backupRetentionDays: 7,
        dailyBackupTime: '01:00',
        weeklyMaintenanceTime: '07:01:00',
        weeklyMaintenanceDay: '1',
        windowsMountDrive: 'G:',
      }
    }
    const [day, time] = getParts(fsx.weeklyMaintenanceTime)
    return {
      ...fsx,
      weeklyMaintenanceTime: time,
      weeklyMaintenanceDay: day,
    }
  }

  const generateAppConfigOrDefaultInitialValuesForService = (serviceName) => {
    let thisService = appConfig.services[serviceName]
    const os = !!thisService?.operatingSystem
      ? appConfig.services[serviceName].operatingSystem === LINUX
        ? LINUX
        : WINDOWS
      : ''
    const fileSystemType = (os !== LINUX ? FSX : EFS)
    const windowsVersion = os === WINDOWS ? thisService.operatingSystem : ''
    let initialTierValues = {}
    for (var i = 0; i < tiers.length; i++) {
      var tierName = tiers[i].name
      // min, max, computeSize, cpu/memory/instanceType (not in form), filesystem, database
      let thisTier = thisService?.tiers[tierName] || {
        min: 1,
        max: 1,
        computeSize: '',
      }
      const filesystem = {
        ...thisTier.filesystem,
        mountPoint: thisTier.filesystem?.mountPoint || '',
        // Start off with FSX if Windows and EFS if Linux
        fileSystemType:
          thisTier.filesystem?.fileSystemType || fileSystemType,
        efs: thisTier.filesystem?.efs || {
          lifecycle: '0',
          encryptAtRest: '',
        },
        fsx: getFsx(thisTier.filesystem?.fsx),
      }
      const db = !!thisTier.database
        ? {
            ...thisTier.database,
            //This is frail, but try to see if the incoming password is base64d
            //If so, assume it's encrypted
            //Also store a copy in the encryptedPassword field
            hasEncryptedPassword: !!thisTier.database.password.match(
              /^[A-Za-z0-9=+/\s ]+$/
            ),
            encryptedPassword: thisTier.database.password,
          }
        : {
            engine: '',
            family: '',
            version: '',
            instance: '',
            username: '',
            password: '',
            hasEncryptedPassword: false,
            encryptedPassword: '',
            database: '',
            bootstrapFilename: '',
          }
      initialTierValues[tierName] = {
        ...thisTier,
        filesystem: filesystem,
        database: db,
        provisionDb: !!thisTier?.database || false,
        provisionFS: !!thisTier?.filesystem || false,
      }
    }
    return {
      ...thisService,
      name: thisService?.name || serviceName,
      path: thisService?.path || '/*',
      public: thisService?.public || false,
      healthCheckUrl: thisService?.healthCheckUrl || '/',
      containerPort: thisService?.containerPort || 0,
      containerTag: thisService?.containerTag || 'latest',
      description: thisService?.description || '',
      operatingSystem: os,
      filesystem: { fileSystemType: fileSystemType },
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
  const singleTierValidationSpec = (tombstone, operatingSystem) => {
    let filesystemSpec =
      operatingSystem === LINUX
        ? Yup.object({
            // LINUX, so EFS
            mountPoint: Yup.string()
              .matches(/^(\/[a-zA-Z._-]+)*$/, 'Invalid path. Ex: /mnt')
              .max(100, "The full path can't exceed 100 characters in length")
              .test(
                'subdirectories',
                'The path can only include up to four subdirectories',
                (val) => (val?.match(/\//g) || []).length <= 4
              )
              .required(),
            fsx: Yup.object().nullable(),
            efs: Yup.object({
              encryptAtRest: Yup.bool(),
              lifecycle: Yup.number().required('Lifecycle is required'),
              filesystemLifecycle: Yup.string(),
            }),
          })
        : Yup.object({
            // not LINUX, so FSX
            mountPoint: Yup.string()
              .matches(
                /^[a-zA-Z]:\\(((?![<>:"/\\|?*]).)+((?<![ .])\\)?)*$/,
                'Invalid path. Ex: C:\\data'
              )
              .required(),
            fsx: Yup.object({
              storageGb: Yup.number()
                .required()
                .min(32, 'Storage minimum is 32 GB')
                .max(1048, 'Storage maximum is 1048 GB'),
              throughputMbs: Yup.number()
                .required()
                .min(8, 'Throughput minimum is 8 MB/s')
                .max(2048, 'Throughput maximum is 2048 MB/s'),
              backupRetentionDays: Yup.number()
                .required()
                .min(7, 'Minimum retention time is 7 days')
                .max(35, 'Maximum retention time is 35 days'),
              dailyBackupTime: Yup.string().required(
                'Daily backup time is required'
              ),
              weeklyMaintenanceTime: Yup.string().required(
                'Weekly maintenance time is required'
              ),
              windowsMountDrive: Yup.string().required(
                'Windows mount drive is required'
              ),
            }),
            efs: Yup.object().nullable(),
          })
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
      database: Yup.object().when('provisionDb', {
        is: true,
        then: Yup.object({
          engine: Yup.string().required('Engine is required'),
          version: Yup.string().required('Version is required'),
          instance: Yup.string().required('Instance is required'),
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
      filesystem: Yup.object().when('provisionFS', {
        is: true,
        then: filesystemSpec,
        otherwise: Yup.object(),
      }),
    })
  }

  const allTiersValidationSpec = (tombstone, operatingSystem) => {
    let allTiers = {}
    for (var i = 0; i < tiers.length; i++) {
      var tierName = tiers[i].name
      allTiers[tierName] = singleTierValidationSpec(tombstone, operatingSystem)
    }
    return Yup.object(allTiers)
  }

  const requiredIfNotTombstoned = (tombstone, schema, requiredMessage) => {
    return tombstone ? schema : schema.required(requiredMessage)
  }

  // TODO public service paths cannot match
  const validationSpecs = Yup.object({
    name: Yup.string().required('Name is a required field.'),
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
        provisionFS: Yup.boolean(),
        tiers: Yup.object().when(
          ['tombstone', 'operatingSystem'],
          (tombstone, operatingSystem, schema) => {
            return allTiersValidationSpec(tombstone, operatingSystem)
          }
        ),
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
        servicesWithErrors.push(formik.errors.services)
      } else {
        formik.errors.services?.forEach((service, index) => {
          if (!!service) {
            servicesWithErrors.push(formik.values?.services[index].name)
          }
        })
      }
    }
    return servicesWithErrors.join(', ')
  }

  return (
    <LoadingOverlay
      active={isSubmitting()}
      spinner
      text="Loading configuration..."
    >
      <div className="animated fadeIn">
        {hasTenants && (
          <Alert color="primary">
            <span>
              <i className="fa fa-info-circle" /> Note: some settings cannot be
              modified once you have deployed tenants.
            </span>
          </Alert>
        )}
        {/* {loading !== "idle" && <div>Loading...</div>} */}
        {!!error && (
          <Alert color="danger" isOpen={!!error} toggle={dismissError}>
            {error}
          </Alert>
        )}
        {!!message && (
          <Alert color="info" isOpen={!!message} toggle={dismissMessage}>
            {message}
          </Alert>
        )}
        <Formik
          initialValues={initialValues}
          validationSchema={validationSpecs}
          validateOnChange={false}
          onSubmit={updateConfig}
          enableReinitialize={true}
        >
          {(formik) => {
            return (
              <>
                {!!formik.errors && Object.keys(formik.errors).length > 0 ? (
                  <Alert color="danger">
                    Errors in {findServicesWithErrors(formik)}
                  </Alert>
                ) : null}
                <Form>
                  <AppSettingsSubform
                    isLocked={hasTenants}
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
