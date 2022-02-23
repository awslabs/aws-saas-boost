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
import React, { Fragment } from 'react'
import {
  Row,
  Col,
  Card,
  CardBody,
  CardHeader,
  Input,
  InputGroup,
  FormGroup,
  Label,
} from 'reactstrap'
import { SaasBoostSelect, SaasBoostInput, SaasBoostCheckbox } from '../components/FormComponents'
import Slider from 'rc-slider'
import 'rc-slider/assets/index.css'

// TODO there should be a tooltip for the checkbox to provision:
// TODO tooltip="If selected, a new file system will be created and mounted to the container as a docker volume"

export default class FileSystemSubform extends React.Component {
  render() {
    return (
      <Fragment>
        <Row>
          <Col xs={12}>
            <Card>
              <CardHeader>File System</CardHeader>
              <CardBody>
                <SaasBoostCheckbox
                  id={this.props.formikServicePrefix + ".provisionFS"}
                  name={this.props.formikServicePrefix + ".provisionFS"}
                  value={this.props.provisionFs}
                  label="Provision a File System for the application."
                />
                <EfsFilesystemOptions
                  provisionFs={this.props.provisionFs}
                  containerOs={this.props.containerOs}
                  isLocked={this.props.isLocked}
                  values={this.props.filesystem}
                  formikTierPrefix={this.props.formikTierPrefix}
                ></EfsFilesystemOptions>
                <FsxFilesystemOptions
                  formik={this.props.formik}
                  provisionFs={this.props.provisionFs}
                  containerOs={this.props.containerOs}
                  isLocked={this.props.isLocked}
                  values={this.props.filesystem}
                  formikTierPrefix={this.props.formikTierPrefix}
                ></FsxFilesystemOptions>
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Fragment>
    )
  }
}

FileSystemSubform.propTypes = {
  provisionFs: PropTypes.bool,
  containerOs: PropTypes.string,
  isLocked: PropTypes.bool,
  filesystem: PropTypes.object,
  formik: PropTypes.object,
  values: PropTypes.object,
  serviceIndex: PropTypes.number,
}

const EfsFilesystemOptions = (props) => {
  return (
    props.provisionFs &&
    props.containerOs === 'LINUX' && (
      <Row>
        <Col xl={6} className="mt-2">
          <SaasBoostInput
            key={props.formikTierPrefix + ".filesystem.mountPoint"}
            label="Mount point"
            name={props.formikTierPrefix + ".filesystem.mountPoint"}
            type="text"
            disabled={props.isLocked}
            value={props.values?.mountPoint}
          />
          <SaasBoostSelect
            id={props.formikTierPrefix + ".filesystem.efs.lifecycle"}
            label="Lifecycle"
            name={props.formikTierPrefix + ".filesystem.efs.lifecycle"}
            value={props.values?.efs?.lifecycle}
          >
            <option value="0">Never</option>
            <option value="7">7 Days</option>
            <option value="14">14 Days</option>
            <option value="30">30 Days</option>
            <option value="60">60 Days</option>
            <option value="90">90 Days</option>
          </SaasBoostSelect>
          <SaasBoostCheckbox
            id={props.formikTierPrefix + ".filesystem.efs.encryptAtRest"}
            name={props.formikTierPrefix + ".filesystem.efs.encryptAtRest"}
            key={props.formikTierPrefix + ".filesystem.efs.encryptAtRest"}
            label="Encrypt at rest"
            value={props.values?.efs.encryptAtRest === 'true'}
          />
        </Col>
        <Col xl={6}></Col>
      </Row>
    )
  ) || null
}

EfsFilesystemOptions.propTypes = {
  provisionFs: PropTypes.bool,
  containerOs: PropTypes.string,
  isLocked: PropTypes.bool,
  values: PropTypes.object,
}

const FsxFilesystemOptions = (props) => {
  const fsMarks = {
    32: '32',
    1024: '1024',
  }

  const tpMarks = {
    8: '8',
    2048: '2048',
  }

  const onStorageChange = (val) => {
    props.formik.setFieldValue(this.props.formikTierPrefix + '.filesystem.fsx.storageGb', val)
  }

  const onThroughputChange = (val) => {
    props.formik.setFieldValue(this.props.formikTierPrefix + '.filesystem.fsx.throughputMbs', val)
  }

  const onWeeklyMaintTimeChange = (val) => {
    props.formik.setFieldValue(this.props.formikTierPrefix + '.filesystem.fsx.weeklyMaintenanceTime', val.target.value)
  }

  const onWeeklyDayChange = (val) => {
    props.formik.setFieldValue(this.props.formikTierPrefix + '.filesystem.fsx.weeklyMaintenanceDay', val.target.value)
  }

  return (
    props.provisionFs &&
    props.containerOs === 'WINDOWS' && (
      <Row>
        <Col sm={6} className="mt-2">
          <SaasBoostInput
            key={this.props.formikTierPrefix + ".filesystem.mountPoint"}
            label="Mount point"
            name={this.props.formikTierPrefix + ".filesystem.mountPoint"}
            type="text"
            disabled={props.isLocked}
          />
          <Row>
            <Col xs={3}>
              <FormGroup>
                <Label htmlFor="storageVal">Storage</Label>
                <Input
                  id="storageVal"
                  className="mb-4"
                  type="number"
                  value={props.values?.fsx.storageGb}
                  readOnly
                ></Input>
              </FormGroup>
            </Col>
            <Col xs={9}>
              <FormGroup>
                <Label htmlFor="storage">In GB</Label>
                <Slider
                  id="storage"
                  defaultValue={props.values?.fsx.storageGb}
                  onChange={onStorageChange}
                  className="mb-4"
                  marks={fsMarks}
                  included={false}
                  min={32}
                  max={1024}
                  step={32}
                />
              </FormGroup>
            </Col>
          </Row>
          <Row>
            <Col xs={3}>
              <FormGroup>
                <Label htmlFor="throughputVal">Throughput</Label>
                <Input
                  id="throughputVal"
                  className="mb-4"
                  type="number"
                  value={props.values?.fsx.throughputMbs}
                  readOnly
                ></Input>
              </FormGroup>
            </Col>
            <Col xs={9}>
              <FormGroup>
                <Label htmlFor="throughput">In MB/s</Label>
                <Slider
                  id="throughput"
                  defaultValue={props.values?.fsx.throughputMbs}
                  onChange={onThroughputChange}
                  marks={tpMarks}
                  className="mb-4"
                  included={false}
                  min={8}
                  max={2048}
                  step={120}
                />
              </FormGroup>
            </Col>
          </Row>
        </Col>
        <Col sm={6} className="mt-2">
          <Row>
            <Col xs={6}>
              <SaasBoostInput
                key={this.props.formikTierPrefix + ".filesystem.fsx.dailyBackupTime"}
                label="Daily Backup Time (UTC)"
                name={this.props.formikTierPrefix + ".filesystem.fsx.dailyBackupTime"}
                type="time"
                disabled={props.isLocked}
              />
            </Col>
            <Col xs={6}>
              <Label>Weekly Maintenance Day/Time (UTC)</Label>
              <InputGroup className="mb-3">
                <Input
                  type="select"
                  onChange={onWeeklyDayChange}
                  value={props.values?.fsx.weeklyMaintenanceDay}
                >
                  <option value="1">Sun</option>
                  <option value="2">Mon</option>
                  <option value="3">Tue</option>
                  <option value="4">Wed</option>
                  <option value="5">Thu</option>
                  <option value="6">Fri</option>
                  <option value="7">Sat</option>
                </Input>
                <Input
                  key={this.props.formikTierPrefix + ".filesystem.fsx.weeklyMaintenanceTime"}
                  onChange={onWeeklyMaintTimeChange}
                  value={props.values?.fsx.weeklyMaintenanceTime}
                  name={this.props.formikTierPrefix + ".filesystem.fsx.weeklyMaintenanceTime"}
                  type="time"
                  disabled={props.isLocked}
                />
              </InputGroup>
            </Col>
          </Row>
          <Row>
            <Col xs={6}>
              <SaasBoostSelect
                id={this.props.formikTierPrefix + ".filesystem.fsx.windowsMountDrive"}
                label="Drive Letter Assignment"
                name={this.props.formikTierPrefix + ".filesystem.fsx.windowsMountDrive"}
                value={props.values?.fsx.windowsMountDrive}
              >
                <option value="G:">G:</option>
                <option value="H:">H:</option>
                <option value="I:">I:</option>
                <option value="J:">J:</option>
                <option value="K:">K:</option>
                <option value="L:">L:</option>
                <option value="M:">M:</option>
                <option value="N:">N:</option>
                <option value="O:">O:</option>
                <option value="P:">P:</option>
                <option value="Q:">Q:</option>
                <option value="R:">R:</option>
                <option value="S:">S:</option>
                <option value="T:">T:</option>
                <option value="U:">U:</option>
                <option value="V:">V:</option>
                <option value="X:">X:</option>
                <option value="Y:">Y:</option>
                <option value="Z:">Z:</option>
              </SaasBoostSelect>
            </Col>
            <Col xs={6}>
              <SaasBoostInput
                key={this.props.formikTierPrefix + ".filesystem.fsx.backupRetentionDays"}
                label="Backup Retention (Days)"
                name={this.props.formikTierPrefix + ".filesystem.fsx.backupRetentionDays"}
                type="number"
                disabled={props.isLocked}
              />
            </Col>
          </Row>
        </Col>
      </Row>
    )
  ) || null
}

FsxFilesystemOptions.propTypes = {
  formik: PropTypes.object,
  provisionFs: PropTypes.bool,
  containerOs: PropTypes.string,
  isLocked: PropTypes.bool,
  values: PropTypes.object,
}
