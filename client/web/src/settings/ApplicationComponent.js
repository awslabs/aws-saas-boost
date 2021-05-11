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

import React from "react";
import { useDispatch } from 'react-redux'

import { Formik, Form } from "formik";
import * as Yup from "yup"; //validation
import { Button, Row, Col, Card, CardBody, Alert } from "reactstrap";
import LoadingOverlay from 'react-loading-overlay';

import AppSettingsSubform from "./AppSettingsSubform";
import FileSystemSubform from "./FileSystemSubform";
import DatabaseSubform from "./DatabaseSubform";
import ContainerSettingsSubform from "./ContainerSettingsSubform";
import BillingSubform from "./BillingSubform";


import {
  dismissConfigError,
  dismissConfigMessage,
} from "./ducks";

export function ApplicationComponent(props) {
  const dispatch = useDispatch();
  const {
    appConfig,
    dbOptions,
    hasTenants,
    loading,
    error,
    message,
    osOptions,
    updateConfiguration,
  } = props;

  const os = !!appConfig.operatingSystem
    ? appConfig.operatingSystem === "LINUX"
      ? "LINUX"
      : "WINDOWS"
    : "";
  const db = !!appConfig.database
    ? {
        ...appConfig.database,
        //This is frail, but try to see if the incoming password is base64d
        //If so, assume it's encryped
        //Also store a copy in the encryptedPassword field
        hasEncryptedPassword: !!appConfig.database.password.match(
          /^[A-Za-z0-9=+/\s ]+$/
        ),
        encryptedPassword: appConfig.database.password,
      }
    : {
        engine: "",
        family: "",
        version: "",
        instance: "",
        username: "",
        password: "",
        hasEncryptedPassword: false,
        encryptedPassword: "",
        database: "",
        bootstrapFilename: "",
      };

  const getParts = (dateTime) => {
    const parts = dateTime.split(":");
    const day = parts[0];
    const times = parts.slice(1);
    const timeStr = times.join(":");
    return [day, timeStr];
  };

  const updateConfig = (values) => {
    updateConfiguration(values);
    window.scrollTo(0, 0);
  }

  const getFsx = (fsx) => {
    if (!fsx) {
      return {
        storageGb: 32,
        throughputMbs: 8,
        backupRetentionDays: 7,
        dailyBackupTime: "01:00",
        weeklyMaintenanceTime: "07:01:00",
        weeklyMaintenanceDay: "1",
        windowsMountDrive: "G:",
      };
    }
    const [day, time] = getParts(fsx.weeklyMaintenanceTime);
    return {
      ...fsx,
      weeklyMaintenanceTime: time,
      weeklyMaintenanceDay: day,
    };
  };

  const filesystem = {
    ...appConfig.filesystem,
    mountPoint: appConfig.filesystem?.mountPoint || "",
    fileSystemType: appConfig.filesystem?.fileSystemType || "",
    efs: appConfig.filesystem?.efs || {
      lifecycle: "0",
      encryptAtRest: "",
    },
    fsx: getFsx(appConfig.filesystem?.fsx),
  };

  const initialValues = {
    operatingSystem: os,
    windowsVersion: os !== "LINUX" ? appConfig.operatingSystem : "",
    name: appConfig.name || "",
    domainName: appConfig.domainName || "",
    sslCertArn: appConfig.sslCertArn || "",
    computeSize: appConfig.computeSize || "",
    containerPort: appConfig.containerPort || 80,
    minCount: appConfig.minCount || 1,
    maxCount: appConfig.maxCount || 1,
    healthCheckURL: appConfig.healthCheckURL || "/index.html",

    database: {
      ...db,
      password: db.hasEncryptedPassword
        ? db.password.substring(0, 8)
        : db.password,
    },
    filesystem: filesystem,
    billing: appConfig.billing || {
      apiKey: "",
    },

    provisionDb: !!appConfig.database,
    provisionFS: !!appConfig.filesystem,
    provisionBilling: !!appConfig.billing,
  };

  const validationSpecs = Yup.object({
    operatingSystem: Yup.string().required("Container OS is a required field"),
    name: Yup.string().required("Name is a required field."),
    computeSize: Yup.string().required("Compute size is a required field."),
    database: Yup.object().when("provisionDb", {
      is: true,
      then: Yup.object({
        engine: Yup.string().required("Engine is required"),
        version: Yup.string().required("Version is required"),
        instance: Yup.string().required("Instance is required"),
        username: Yup.string()
          .matches("^[a-zA-Z]+[a-zA-Z0-9_$]*$", "Username is not valid")
          .required("Username is required"),
        password: Yup.string()
          .matches("^[a-zA-Z0-9/@\"' ]{8,}$", "Password is not valid")
          .required("Password is required"),
        database: Yup.string(),
      }),
      otherwise: Yup.object(),
    }),
    filesystem: Yup.object().when("provisionFS", {
      is: true,
      then: Yup.object({
        mountPoint: Yup.string().when("fileSystemType", {
          is: "EFS",
          then: Yup.string()
            .matches(/^\/[a-zA-Z/]+$/, "Invalid path. Ex: /mnt")
            .required(),
          otherwise: Yup.string()
            .matches(
              /^[a-zA-Z]:\\(((?![<>:"/\\|?*]).)+((?<![ .])\\)?)*$/,
              "Invalid path. Ex: C:\\data"
            )
            .required(),
        }),
        fsx: Yup.object().when("fileSystemType", {
          is: "FSX",
          then: Yup.object({
            storageGb: Yup.number()
              .required()
              .min(32, "Storage minimum is 32 GB")
              .max(1048, "Storage maximum is 1048 GB"),
            throughputMbs: Yup.number()
              .required()
              .min(8, "Throughput minimum is 8 MB/s")
              .max(2048, "Throughput maximum is 2048 MB/s"),
            backupRetentionDays: Yup.number()
              .required()
              .min(7, "Minimum retention time is 7 days")
              .max(35, "Maximum retention time is 35 days"),
            dailyBackupTime: Yup.string().required(
              "Daily backup time is required"
            ),
            weeklyMaintenanceTime: Yup.string().required(
              "Weekly maintenance time is required"
            ),
            windowsMountDrive: Yup.string().required(
              "Windows mount drive is required"
            ),
          }),
          otherwise: Yup.object().nullable(),
        }),
        efs: Yup.object().when("fileSystemType", {
          is: "EFS",
          then: Yup.object({
            encryptAtRest: Yup.bool(),
            lifecycle: Yup.number().required("Lifecycle is required"),
            filesystemLifecycle: Yup.string(),
          }),
          otherwise: Yup.object().nullable(),
        }),
      }),
      otherwise: Yup.object(),
    }),
    containerPort: Yup.number()
      .integer("Container port must be an integer value.")
      .required("Container port is a required field."),
    minCount: Yup.number()
      .required("Minimum count is a required field.")
      .integer("Minimum count must be an integer value")
      .min(1, "Minimum count must be at least ${min}"),
    maxCount: Yup.number()
      .required("Maximum count is a required field.")
      .integer("Maximum count must be an integer value")
      .max(10, "Maximum count can be no larger than ${max}")
      .test(
        "match",
        "Maximum count cannot be smaller than minimum count",
        function (maxCount) {
          return maxCount >= this.parent.minCount;
        }
      ),
    windowsVersion: Yup.string().when("operatingSystem", {
      is: (containerOs) => containerOs && containerOs === "WINDOWS",
      then: Yup.string().required("Windows version is a required field"),
      otherwise: Yup.string().nullable(),
    }),

    healthCheckURL: Yup.string()
      .required("Health Check URL is a required field")
      .matches(/^\//, "Health Check must start with forward slash (/)"),
    provisionDb: Yup.boolean(),
    provisionFS: Yup.boolean(),
    provisionBilling: Yup.boolean(),
  });

  const onFileSelected = (formik, file) => {
    formik.setFieldValue("database.bootstrapFilename", file.name);
    props.onFileSelected(file);
  };

  const dismissError = () => {
    dispatch(dismissConfigError());
  }

  const dismissMessage = () => {
    dispatch(dismissConfigMessage());
  }

  const isSubmitting = () => {
    return loading !== 'idle';
  }

  return (
    <LoadingOverlay
      active={isSubmitting()}
      spinner
      text='Loading configuration...'
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
      {!!error && <Alert color="danger" isOpen={!!error} toggle={dismissError}>{error}</Alert>}
      {!!message && <Alert color="info" isOpen={!!message} toggle={dismissMessage}>{message}</Alert>}
      <Formik
        initialValues={initialValues}
        validationSchema={validationSpecs}
        onSubmit={updateConfig}
        enableReinitialize={true}
      >
        {(formik) => {
          return (
            <Form>
              <AppSettingsSubform isLocked={hasTenants}></AppSettingsSubform>
              <ContainerSettingsSubform
                isLocked={hasTenants}
                formik={formik}
                osOptions={osOptions}
              ></ContainerSettingsSubform>
              <FileSystemSubform
                isLocked={hasTenants}
                formik={formik}
                filesystem={formik.values.filesystem}
                provisionFs={formik.values.provisionFS}
                containerOs={formik.values.operatingSystem}
              ></FileSystemSubform>
              <DatabaseSubform
                isLocked={hasTenants}
                formik={formik}
                dbOptions={dbOptions}
                provisionDb={formik.values.provisionDb}
                values={formik.values?.database}
                onFileSelected={(file) => onFileSelected(formik, file)}
              ></DatabaseSubform>
              <BillingSubform
                provisionBilling={formik.values.provisionBilling}
                values={formik.values?.billing}
              ></BillingSubform>
              <Row>
                <Col xs={12}>
                  <Card>
                    <CardBody>
                      <Button
                        type="Submit"
                        color="primary"
                        disabled={isSubmitting()}
                      >
                        {isSubmitting() ? "Saving..." : "Submit"}
                      </Button>
                    </CardBody>
                  </Card>
                </Col>
              </Row>
            </Form>
          );
        }}
      </Formik>
    </div>
    </LoadingOverlay>
  );
}
