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
import { FILESYSTEM_DEFAULTS, FILESYSTEM_TIER_DEFAULTS, FILESYSTEM_TYPES, OS_TO_FS_TYPES } from './components/filesystem'
import { LINUX, WINDOWS, isEC2AutoScalingRequired } from './components/compute'
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
    hostedZoneOptions,
    message,
    osOptions,
    updateConfiguration,
    tiers,
  } = props

  const awsRegion = globalConfig.region
  const consoleUrlSuffix = awsRegion.startsWith("cn-")? "amazonaws.cn": "aws.amazon.com"

  const acmConsoleLink = `https://${awsRegion}.console.${consoleUrlSuffix}/acm/home?region=${awsRegion}#/certificates/list`
  const route53ConsoleLink = `https://${awsRegion}.console.${consoleUrlSuffix}/route53/v2/hostedzones`
  const showProvisionBilling = awsRegion.startsWith("cn-")? false : true

  const updateConfig = (values) => {
    updateConfiguration(values)
    window.scrollTo(0, 0)
  }

  const splitWeeklyMaintenanceTime = (fsxTier) => {
    if (!!fsxTier && !!fsxTier.weeklyMaintenanceTime) {
      const getParts = (dateTime) => {
        const parts = dateTime.split(':')
        const day = parts[0]
        const times = parts.slice(1)
        const timeStr = times.join(':')
        return [day, timeStr]
      }
      const [day, time] = getParts(fsxTier.weeklyMaintenanceTime)
      return {
        ...fsxTier,
        weeklyMaintenanceTime: time,
        weeklyMaintenanceDay: day,
      }
    }
    return fsxTier
  }

  const generateAppConfigOrDefaultInitialValuesForService = (serviceName) => {
    let thisService = appConfig.services[serviceName]
    const compute = !!thisService?.compute 
      ? {
        ...thisService.compute,
        windowsVersion: thisService.compute.operatingSystem === LINUX ? '' : thisService.compute.operatingSystem,
        operatingSystem: thisService.compute.operatingSystem === LINUX ? LINUX : WINDOWS
      }
      : {
        type: 'ECS',
        operatingSystem: '',
        ecsLaunchType: '',
        ecsExecEnabled: false,
        healthCheckUrl: '/',
        containerPort: 0,
        containerTag: 'latest',
        containerRepo: '',
        windowsVersion: '',
        tiers: tiers.reduce((acc, tier) => ({...acc, [tier.name]: {
            min: 1,
            max: 1,
            computeSize: '',
            ec2min: 1,
            ec2max: 1
        }}), {})
      }
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
            tiers: tiers.reduce((acc, tier) => ({...acc, [tier.name]: {instance: ''}}), {}),
          }
    const cleanedFs = splitWeeklyMaintenanceTime(thisService?.filesystem)
    const fs = !!thisService?.filesystem
        ? {
          ...cleanedFs,
          tiers: tiers.reduce((acc, tier) => ({ ...acc, [tier.name]: {...FILESYSTEM_TIER_DEFAULTS, ...cleanedFs["tiers"][tier.name]}}), {})
        }
        : {
          ...FILESYSTEM_DEFAULTS,
          tiers: tiers.reduce((acc, tier) => ({ ...acc, [tier.name]: FILESYSTEM_TIER_DEFAULTS}), {})
        }
    let filesystemType = OS_TO_FS_TYPES[compute?.operatingSystem]?.filter(type => type.configId === thisService.filesystem?.type)[0]?.id || ''
    return {
      ...thisService,
      name: thisService?.name || serviceName,
      path: thisService?.path || '/*',
      public: thisService ? thisService.public : true,
      description: thisService?.description || '',
      database: db,
      provisionDb: !!thisService?.database,
      filesystem: fs,
      provisionFS: !!thisService?.filesystem,
      filesystemType: filesystemType,
      provisionObjectStorage: !!thisService?.s3,
      compute: compute,
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
    hostedZone: appConfig.hostedZone || '',
    sslCertificate: appConfig.sslCertificate || '',
    services: parseServicesFromAppConfig(),
    billing: appConfig.billing || {
      apiKey: '',
    },
    provisionBilling: !!appConfig.billing || false,
  }

  const allTiersDatabaseValidationSpec = () => {
    let allTiers = {}
    for (var i = 0; i < tiers.length; i++) {
      var tierName = tiers[i].name
      allTiers[tierName] = Yup.object({
        instance: Yup.string().required('Database instance is required.')
      })
    }
    return Yup.object(allTiers)
  }

  const allTiersFilesystemValidationSpec = (filesystemType) => {
    if (!!FILESYSTEM_TYPES[filesystemType]?.tierValidationSchema) {
      let allTiers = {}
      for (var i = 0; i < tiers.length; i++) {
        var tierName = tiers[i].name
        allTiers[tierName] = Yup.object(FILESYSTEM_TYPES[filesystemType].tierValidationSchema)
      }
      return Yup.object(allTiers)
    }
    return Yup.object()
  }

  const allTiersComputeValidationSpec = (operatingSystem, ecsLaunchType) => {
    let addRequiredForEc2AsgIfNecessary = (schema, requiredMessage) => {
      if (isEC2AutoScalingRequired(operatingSystem, ecsLaunchType)) {
        return schema.required(requiredMessage)
      }
      return schema
    }

    // TODO should this be more like the filesystem changes to support future different compute types?
    let allTiers = {}
    for (var i = 0; i < tiers.length; i++) {
      var tierName = tiers[i].name
      allTiers[tierName] = Yup.object({
        min: Yup.number()
          .integer('Minimum count must be an integer value')
          .min(1, 'Minimum count must be at least ${min}')
          .required('Minimum count is a required field.'),
        max: Yup.number()
          .required('Maximum count is a required field.')
          .integer('Maximum count must be an integer value')
          .test('match', 'Max cannot be smaller than min', function (max) {
            return max >= this.parent.min
          }),
        computeSize: Yup.string().required('Compute size is a required field.'),
        ec2min: addRequiredForEc2AsgIfNecessary(Yup.number()
          .integer('Minimum EC2 instance count must be an integer value')
          .min(1, 'Minimum EC2 instance count must be at least ${min}'), 'Minimum EC2 instance count is a required field.'),
        ec2max: addRequiredForEc2AsgIfNecessary(Yup.number()
          .integer('Maximum EC2 instance count must be an integer value')
          .test('match', 'Max EC2 instance count cannot be smaller than min EC2 instance count ', function (ec2max) {
            return ec2max >= this.parent.ec2min
          }), 'Maximum EC2 instance count is a required field.'),
      })
    }
    return Yup.object(allTiers)
  }

  // TODO public service paths cannot match
  const validationSpecs = Yup.object({
    name: Yup.string().required('Name is a required field.'),
    domainName: Yup.string().matches(
      /^$|(?=^.{4,253}$)(^((?!-)[a-zA-Z0-9-]{0,62}[a-zA-Z0-9]\.)+[a-zA-Z]{2,63}$)/,
      'Domain Name is not in valid format.'
    ),
    hostedZone: Yup.string().when(['domainName'], (domainName, schema) => {
      if (!!domainName) {
        return schema.required('HostedZone is required when DomainName is configured.')
      }
      return schema
    }),
    services: Yup.array(
      Yup.object({
        public: Yup.boolean().required(),
        name: Yup.string()
          .required('Service Name is a required field.')
          .matches(
            /^[\.\-_a-zA-Z0-9]+$/,
            'Name must only contain alphanumeric characters or .-_'
          ),
        description: Yup.string(),
        path: Yup.string()
          .when(['public'], (isPublic, schema) => {
            if (isPublic) {
              return schema.required('Path is required for public services')
            }
            return schema
          })
          .matches(
            /^\/.+$/,
            'Path must start with / and be followed by at least one character.'
          ),
        provisionDb: Yup.boolean(),
        database: Yup.object().when('provisionDb', {
          is: true,
          then: Yup.object({
            engine: Yup.string().required('Engine is required'),
            version: Yup.string().required('Version is required'),
            tiers: allTiersDatabaseValidationSpec(),
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
        filesystem: Yup.object().when(['provisionFS', 'filesystemType'], (provisionFS, filesystemType) => {
          if (provisionFS && FILESYSTEM_TYPES[filesystemType]?.validationSchema) {
            return Yup.object({
              ...FILESYSTEM_TYPES[filesystemType]?.validationSchema,
              tiers: allTiersFilesystemValidationSpec(filesystemType),
            })
          }
          return Yup.object()
        }),
        filesystemType: Yup.string(),
        provisionFS: Yup.boolean(),
        provisionObjectStorage: Yup.boolean(),
        compute: Yup.object({
          type: Yup.string().required(),
          containerPort: Yup.number()
            .integer('Container port must be an integer value.')
            .required('Container port is a required field.'),
          ecsExecEnabled: Yup.boolean().when('type', {
            is: (type) => type && type === 'ECS',
            then: Yup.boolean().required(),
            otherwise: Yup.boolean().nullable()
          }),
          ecsLaunchType: Yup.string().required('ECS Launch Type is required.'),
          containerTag: Yup.string().required('Container Tag is a required field.'),
          healthCheckUrl: Yup.string()
            .required('Health Check URL is a required field')
            .matches(/^\//, 'Health Check must start with forward slash (/)'),
          operatingSystem: Yup.string().required('Container OS is a required field.'),
          windowsVersion: Yup.string().when('operatingSystem', {
            is: (containerOs) => containerOs && containerOs === WINDOWS,
            then: Yup.string().required('Windows version is a required field'),
            otherwise: Yup.string().nullable(),
          }),
          tiers: Yup.object().when(['operatingSystem', 'ecsLaunchType'], (operatingSystem, ecsLaunchType) => {
            return allTiersComputeValidationSpec(operatingSystem, ecsLaunchType)
          })
        }),
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
            let extensionsWithErrors = Object.keys(service).filter((extension) => typeof service[extension] === 'object')
            if (extensionsWithErrors.length > 0) {
              serviceMessage = serviceMessage.concat(" Configurations ")
              extensionsWithErrors.forEach((extension) => {
                serviceMessage = serviceMessage.concat(extension + ' ')
                if (!!service[extension].tiers) {
                  serviceMessage = serviceMessage.concat("Tiers [" + Object.keys(service[extension].tiers).join(",") + "] ")
                }
              })
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
          validateOnBlur={false}
          onSubmit={updateConfig}
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
                    hostedZoneOptions={hostedZoneOptions.filter(option => option.name.startsWith(formik.values.domainName))}
                    route53ConsoleLink={route53ConsoleLink}
                  ></AppSettingsSubform>
                  <ServicesComponent
                    formik={formik}
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
                  {showProvisionBilling && <BillingSubform
                      provisionBilling={formik.values.provisionBilling}
                      values={formik.values?.billing}
                  ></BillingSubform>
                  }
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
