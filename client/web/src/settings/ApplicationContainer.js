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

import React, { useEffect, useState } from 'react'
import { Switch, Route } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import {
  saveToPresignedBucket,
  selectServiceToS3BucketMap,
} from '../settings/ducks'
import {
  fetchSettings,
  fetchConfig,
  updateConfig,
  selectAllSettings,
  dismissError,
  selectLoading,
  selectConfig,
  selectConfigLoading,
  selectConfigError,
  selectConfigMessage,
} from './ducks'

import { ApplicationComponent } from './ApplicationComponent'
import { ConfirmModal } from './ConfirmModal'
import { selectDbOptions, selectOsOptions, selectCertOptions, selectHostedZoneOptions } from '../options/ducks'
import { fetchTenantsThunk, selectAllTenants } from '../tenant/ducks'
import { fetchTiersThunk, selectAllTiers } from '../tier/ducks'
import { FILESYSTEM_TYPES } from './components/filesystem'

export function ApplicationContainer(props) {
  const LINUX = 'LINUX'

  const dispatch = useDispatch()
  const appConfig = useSelector(selectConfig)
  const configError = useSelector(selectConfigError)
  const configLoading = useSelector(selectConfigLoading)
  const configMessage = useSelector(selectConfigMessage)
  const dbOptions = useSelector(selectDbOptions)
  const loading = useSelector(selectLoading)
  const osOptions = useSelector(selectOsOptions)
  const certOptions = useSelector(selectCertOptions)
  const hostedZoneOptions = useSelector(selectHostedZoneOptions)
  const serviceToS3BucketMap = useSelector(selectServiceToS3BucketMap)
  const settings = useSelector(selectAllSettings)

  const hasTenants = useSelector((state) => {
    return selectAllTenants(state)?.length > 0
  })
  const tiers = useSelector(selectAllTiers)

  const [modal, setModal] = useState(false)
  const [file, setFile] = useState({})
  const [formValues, setFormValues] = useState(null)

  const toggleModal = () => setModal(!modal)

  let settingsObj = {}

  if (!!settings) {
    // covert array to object to reference values easier
    settings.forEach((setting) => {
      settingsObj[setting.name] = setting
    })
  }

  useEffect(() => {
    const settingsResponse = dispatch(fetchSettings())
    return () => {
      if (settingsResponse.PromiseStatus === 'pending') {
        settingsResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])

  useEffect(() => {
    const fetchConfigResponse = dispatch(fetchConfig())
    return () => {
      if (fetchConfigResponse.PromiseStatus === 'pending') {
        fetchConfigResponse.abort()
      }
      dispatch(dismissError())
    }
  }, [dispatch])

  useEffect(() => {
    const fetchTenantsResponse = dispatch(fetchTenantsThunk())
    return () => {
      if (fetchTenantsResponse.PromiseStatus === 'pending') {
        fetchTenantsResponse.abort()
      }
      dismissError(dismissError())
    }
  }, [dispatch])

  useEffect(() => {
    const fetchTiersResponse = dispatch(fetchTiersThunk())
    return () => {
      if (fetchTiersResponse.PromiseStatus === 'pending') {
        fetchTiersResponse.abort()
      }
      dismissError(dismissError())
    }
  }, [dispatch])

  useEffect(() => {
    Object.keys(file).forEach((fn) => {
      const dbFile = file[fn]
      const url = serviceToS3BucketMap[fn]
      if (dbFile && url) {
        dispatch(
          saveToPresignedBucket({
            dbFile,
            url,
          })
        )
      }
    })
  }, [serviceToS3BucketMap, appConfig, dispatch, file])

  const presubmitCheck = (values) => {
    setFormValues(values)
    if (hasTenants) {
      setModal(true)
    } else {
      setModal(false)
      updateConfiguration(values)
    }
  }

  const confirmSubmit = () => {
    setModal(false)
    updateConfiguration(formValues)
  }

  const removeUnwantedKeys = (objectToClean, wantedKeys) => {
    Object.keys(objectToClean).forEach(k => {
      if (!wantedKeys.includes(k)) {
        delete objectToClean[k]
      }
    })
  }

  const cleanFilesystemForSubmittal = (provisionFS, filesystemType, filesystem) => {
    if (provisionFS) {
      let cleanedFs = {
        ...filesystem,
        type: FILESYSTEM_TYPES[filesystemType].configId
      }
      // clean tiers if present
      if (!!filesystem?.tiers) {
        let cleanedTiers = {}
        for (let tierName in filesystem.tiers) {
          let {
            weeklyMaintenanceDay,
            weeklyMaintenanceTime,
            ...cleanedFsTier
          } = filesystem.tiers[tierName]
          if (weeklyMaintenanceDay && weeklyMaintenanceTime) {
            cleanedFsTier.weeklyMaintenanceTime = `${weeklyMaintenanceDay}:${weeklyMaintenanceTime}`
          }
          // get rid of keys that aren't needed for this tier for this filesystem type
          removeUnwantedKeys(cleanedFsTier, Object.keys(FILESYSTEM_TYPES[filesystemType].tierDefaults))
          cleanedTiers[tierName] = cleanedFsTier
        }
        cleanedFs.tiers = cleanedTiers
      }
      // get rid of keys that aren't needed for this filesystem type
      removeUnwantedKeys(cleanedFs, [...Object.keys(FILESYSTEM_TYPES[filesystemType].defaults), 'type', 'tiers'])
      return cleanedFs
    } else {
      return null
    }
  }

  const cleanDatabaseForSubmittal = (provisionDb, database, serviceName) => {
    const isMatch = (pw, encryptedPw) => {
      return encryptedPw.substring(0, 8) === pw
    }

    if (provisionDb) {
      const {
        port,
        hasEncryptedPassword,
        encryptedPassword,
        bootstrapFilename,
        ...restDb
      } = database
      const cleanedDb = {
        ...restDb,
        // if new file was dropped into the AppConfig page it will be in the file object for this service
        // that means a new bootstrap file was chosen, so pass null for the bootstrap filename so the settings
        // service will return a presigned url for us. otherwise, return the bootstrap filename already in
        // the database
        bootstrapFilename: !!file[serviceName] ? null : bootstrapFilename,
        // If we detected an encrypted password coming in, and it looks like they haven't changed it
        // then send the encrypted password back to the server. Otherwise send what they changed.
        password:
          hasEncryptedPassword &&
          isMatch(restDb.password, encryptedPassword)
            ? encryptedPassword
            : restDb.password,
      }
      return cleanedDb
    }
    return null
  }

  const updateConfiguration = async (values) => {
    try {
      const { services, billing, provisionBilling, ...rest } = values
      let cleanedServicesMap = {}
      for (var serviceIndex in services) {
        let thisService = services[serviceIndex]
        // update the service config
        const {
          name,
          provisionDb,
          provisionObjectStorage,
          database,
          filesystem,
          provisionFS,
          filesystemType,
          compute,
          ...rest
        } = thisService
        const {
          operatingSystem,
          ecsLaunchType,
          windowsVersion,
          ...restCompute
        } = compute
        // if operatingSystem is not linux (assumed to mean Windows) then launch type must be EC2. otherwise use the launchType if non-null, defaulting to Fargate
        const cleanedEcsLaunchType = (!!operatingSystem && operatingSystem !== LINUX) ? "EC2" : (!!ecsLaunchType ? ecsLaunchType : "FARGATE")
        const cleanedCompute = {
          operatingSystem: operatingSystem === LINUX ? LINUX : windowsVersion,
          ecsLaunchType: cleanedEcsLaunchType,
          ...restCompute
        }
        cleanedServicesMap[name] = {
          ...rest,
          name,
          database: cleanDatabaseForSubmittal(provisionDb, database, name),
          filesystem: cleanFilesystemForSubmittal(provisionFS, filesystemType, filesystem),
          s3: provisionObjectStorage ? {} : null,
          compute: cleanedCompute,
        }
      }

      // compile the complete appConfig
      const configToSend = {
        ...rest,
        billing: provisionBilling ? billing : null,
        services: cleanedServicesMap,
      }
      dispatch(updateConfig(configToSend))
    } catch (e) {
      console.error(e)
    }
  }

  const handleFileSelected = (newFile) => {
    file[newFile.serviceName] = newFile.file
    setFile(file)
  }

  return (
    <Switch>
      <Route
        path="/application"
        exact={true}
        name="Application"
        render={(props) => (
          <>
            <ConfirmModal
              headerText="WARNING"
              confirmationText="Warning: The changes you've made will be applied to all existing tenants that are configured with the default settings (as well as future tenants)."
              toggle={toggleModal}
              modal={modal}
              confirmSubmit={confirmSubmit}
            ></ConfirmModal>
            <ApplicationComponent
              appConfig={appConfig}
              dbOptions={dbOptions}
              hasTenants={hasTenants}
              onFileSelected={handleFileSelected}
              osOptions={osOptions}
              certOptions={certOptions}
              hostedZoneOptions={hostedZoneOptions}
              settings={settings}
              settingsObj={settingsObj}
              error={configError}
              message={configMessage}
              loading={
                loading === 'idle' && configLoading === 'idle'
                  ? 'idle'
                  : 'pending'
              }
              updateConfiguration={presubmitCheck}
              tiers={tiers}
              {...props}
            />
          </>
        )}
      ></Route>
    </Switch>
  )
}

export default ApplicationContainer
