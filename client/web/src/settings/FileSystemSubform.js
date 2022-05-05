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
import React, { Fragment } from 'react';
import {
  Row,
  Col,
  Card,
  CardBody,
  CardHeader,
  Input,
  InputGroup,
  InputGroupAddon,
  FormGroup,
  Label,
} from 'reactstrap';
import { Field } from 'formik';
import { SaasBoostSelect, SaasBoostInput, SaasBoostCheckbox } from '../components/FormComponents';
import Slider from 'rc-slider';
import 'rc-slider/assets/index.css';
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
                  id="provisionFS"
                  name="provisionFS"
                  value={this.props.provisionFs}
                  label="Provision a File System for the application."
                  tooltip="If selected, a new file system will be created and mounted to the container as a docker volume"
                />
                <EfsFilesystemOptions
                  provisionFs={this.props.provisionFs}
                  containerOs={this.props.containerOs}
                  isLocked={this.props.isLocked}
                  values={this.props.filesystem}
                ></EfsFilesystemOptions>
                <FsxFilysystemType
                  formik={this.props.formik}
                  provisionFs={this.props.provisionFs}
                  containerOs={this.props.containerOs}
                  isLocked={this.props.isLocked}
                  values={this.props.filesystem}
                ></FsxFilysystemType>
                <FsxFilesystemOptions
                  formik={this.props.formik}
                  provisionFs={this.props.provisionFs}
                  containerOs={this.props.containerOs}
                  isLocked={this.props.isLocked}
                  values={this.props.filesystem}
                ></FsxFilesystemOptions>
                <FsxFilesystemOntapOptions
                  formik={this.props.formik}
                  provisionFs={this.props.provisionFs}
                  containerOs={this.props.containerOs}
                  isLocked={this.props.isLocked}
                  values={this.props.filesystem}
                >
                </FsxFilesystemOntapOptions>
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Fragment>
    );
  }
}
const EfsFilesystemOptions = (props) => {
  return (
    props.provisionFs &&
    props.containerOs === 'LINUX' && (
      <Row>
        <Col xl={6} className="mt-2">
          <SaasBoostInput
            key="filesystem.mountPoint"
            label="Mount point"
            name="filesystem.mountPoint"
            type="text"
            disabled={props.isLocked}
          />
          <SaasBoostSelect
            id="filesystem.efs.lifecycle"
            label="Lifecycle"
            name="filesystem.efs.lifecycle"
            value={props.values?.efs.lifecycle}
          >
            <option value="0">Never</option>
            <option value="7">7 Days</option>
            <option value="14">14 Days</option>
            <option value="30">30 Days</option>
            <option value="60">60 Days</option>
            <option value="90">90 Days</option>
          </SaasBoostSelect>
          <SaasBoostCheckbox
            id="filesystem.efs.encryptAtRest"
            name="filesystem.efs.encryptAtRest"
            key="filesystem.efs.encryptAtRest"
            label="Encrypt at rest"
            value={props.values?.efs.encryptAtRest}
          />
        </Col>
        <Col xl={6}></Col>
      </Row>
    )
  );
};
const FsxFilysystemType = (props) => {
  return (
    props.provisionFs &&
    props.containerOs === 'WINDOWS' && (
      <FormGroup>
        <div className="mb-2">File System Type</div>
          <FormGroup check inline>
            <Field
              className="form-check-input"
              type="radio"
              id="inline-radio11"
              name="filesystem.fsx.useOntap"
              value="true"
              disabled={props.isLocked}
            />
            <Label className="form-check-label" check htmlFor="inline-radio11">
              <i className="fa fa-cubes"></i> FSx ONTAP
            </Label>
          </FormGroup>
          <FormGroup check inline>
            <Field
              className="form-check-input"
              type="radio"
              id="inline-radio22"
              name="filesystem.fsx.useOntap"
              value="false"
              disabled={props.isLocked}
            />
            <Label className="form-check-label" check htmlFor="inline-radio22">
              <i className="fa fa-windows"></i> FSx Windows
            </Label>
          </FormGroup>
        </FormGroup>
    )
  );
};
const FsxFilesystemOptions = (props) => {
  const fsMarks = {
    32: '32',
    1024: '1024',
  };
  const tpMarks = {
    8: '8',
    2048: '2048',
  };
  const onStorageChange = (val) => {
    props.formik.setFieldValue('filesystem.fsx.storageGb', val);
  };
  const onThroughputChange = (val) => {
    props.formik.setFieldValue('filesystem.fsx.throughputMbs', val);
  };
  const onWeeklyMaintTimeChange = (val) => {
    props.formik.setFieldValue('filesystem.fsx.weeklyMaintenanceTime', val.target.value);
  };
  const onWeeklyDayChange = (val) => {
    props.formik.setFieldValue('filesystem.fsx.weeklyMaintenanceDay', val.target.value);
  };
  return (
    props.provisionFs &&
    props.containerOs === 'WINDOWS' && props.formik.values.filesystem.fsx.useOntap === 'false' && (
      <Row>
        <Col sm={6} className="mt-2">
          <SaasBoostInput
            key="filesystem.mountPoint"
            label="Mount point"
            name="filesystem.mountPoint"
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
                key="filesystem.fsx.dailyBackupTime"
                label="Daily Backup Time (UTC)"
                name="filesystem.fsx.dailyBackupTime"
                type="time"
                disabled={props.isLocked}
              />
            </Col>
            <Col xs={6}>
              <Label>Weekly Maintenance Day/Time (UTC)</Label>
              <InputGroup className="mb-3">
                <InputGroupAddon addonType="prepend">
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
                </InputGroupAddon>
                <Input
                  key="filesystem.fsx.weeklyMaintenanceTime"
                  onChange={onWeeklyMaintTimeChange}
                  value={props.values?.fsx.weeklyMaintenanceTime}
                  name="filesystem.fsx.weeklyMaintenanceTime"
                  type="time"
                  disabled={props.isLocked}
                />
              </InputGroup>
            </Col>
          </Row>
          <Row>
            <Col xs={6}>
              <SaasBoostSelect
                id="filesystem.fsx.windowsMountDrive"
                label="Drive Letter Assignment"
                name="filesystem.fsx.windowsMountDrive"
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
                key="filesystem.fsx.backupRetentionDays"
                label="Backup Retention (Days)"
                name="filesystem.fsx.backupRetentionDays"
                type="number"
                disabled={props.isLocked}
              />
            </Col>
          </Row>
        </Col>
      </Row>
    )
  );
};
const FsxFilesystemOntapOptions = (props) => {
  const fsMarksOntap = {
    1024: '1024',
    10240: '10240',
  };
  const tpMarksOntap = {
    128: '128',
    2048: '2048',
  };
  const volMarks = {
    1: '1',
    100: '100',
  };
  const onStorageChangeOntap = (val) => {
    props.formik.setFieldValue('filesystem.fsx.storageGbOntap', val);
  };
  const onThroughputChangeOntap = (val) => {
    props.formik.setFieldValue('filesystem.fsx.throughputMbsOntap', val);
  };
  const onOntapVolumeChange = (val) => {
    props.formik.setFieldValue('filesystem.fsx.ontapVolumeSize', val);
  };
  const onWeeklyMaintTimeChange = (val) => {
    props.formik.setFieldValue('filesystem.fsx.weeklyMaintenanceTime', val.target.value);
  };
  const onWeeklyDayChange = (val) => {
    props.formik.setFieldValue('filesystem.fsx.weeklyMaintenanceDay', val.target.value);
  };
  return (
    props.provisionFs &&
    props.containerOs === 'WINDOWS' && props.formik.values.filesystem.fsx.useOntap === 'true' && (
      <Row>
        <Col sm={6} className="mt-2">
          <SaasBoostInput
            key="filesystem.mountPoint"
            label="Mount point"
            name="filesystem.mountPoint"
            type="text"
            disabled={props.isLocked}
          />
          <Row>
            <Col xs={3}>
              <FormGroup>
                <Label htmlFor="storageVal">Storage Capacity</Label>
                <Input
                  id="storageVal"
                  className="mb-4"
                  type="number"
                  value={props.values?.fsx.storageGbOntap}
                  readOnly
                ></Input>
              </FormGroup>
            </Col>
            <Col xs={9}>
              <FormGroup>
                <Label htmlFor="storage">In GB</Label>
                <Slider
                  id="storage"
                  defaultValue={props.values?.fsx.storageGbOntap}
                  onChange={onStorageChangeOntap}
                  className="mb-4"
                  marks={fsMarksOntap}
                  included={false}
                  min={1024}
                  max={10240}
                  step={256}
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
                  value={props.values?.fsx.throughputMbsOntap}
                  readOnly
                ></Input>
              </FormGroup>
            </Col>
            <Col xs={9}>
              <FormGroup>
                <Label htmlFor="throughput">In MB/s</Label>
                <Slider
                  id="throughput"
                  defaultValue={props.values?.fsx.throughputMbsOntap}
                  onChange={onThroughputChangeOntap}
                  marks={tpMarksOntap}
                  className="mb-4"
                  included={false}
                  min={128}
                  max={2048}
                  step={128}
                />
              </FormGroup>
            </Col>
          </Row>
          <Row>
            <Col xs={3}>
              <FormGroup>
                <Label htmlFor="ontapVolumeSizeval">ONTAP Volume Size</Label>
                <Input
                  id="ontapVolumeSizeval"
                  className="mb-4"
                  type="number"
                  value={props.values?.fsx.ontapVolumeSize}
                  readOnly
                ></Input>
              </FormGroup>
            </Col>
            <Col xs={9}>
              <FormGroup>
                <Label htmlFor="ontapVolumeSize">In GB</Label>
                <Slider
                  id="ontapVolumeSize"
                  defaultValue={props.values?.fsx.ontapVolumeSize}
                  onChange={onOntapVolumeChange}
                  marks={volMarks}
                  className="mb-4"
                  included={false}
                  min={1}
                  max={100}
                  step={1}
                />
              </FormGroup>
            </Col>
          </Row> 
        </Col>
        <Col sm={6} className="mt-2">
          <Row>
            <Col xs={6}>
              <SaasBoostInput
                key="filesystem.fsx.dailyBackupTime"
                label="Daily Backup Time (UTC)"
                name="filesystem.fsx.dailyBackupTime"
                type="time"
                disabled={props.isLocked}
              />
            </Col>
            <Col xs={6}>
              <Label>Weekly Maintenance Day/Time (UTC)</Label>
              <InputGroup className="mb-3">
                <InputGroupAddon addonType="prepend">
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
                </InputGroupAddon>
                <Input
                  key="filesystem.fsx.weeklyMaintenanceTime"
                  onChange={onWeeklyMaintTimeChange}
                  value={props.values?.fsx.weeklyMaintenanceTime}
                  name="filesystem.fsx.weeklyMaintenanceTime"
                  type="time"
                  disabled={props.isLocked}
                />
              </InputGroup>
            </Col>
          </Row>
          <Row>
            <Col xs={6}>
              <SaasBoostSelect
                id="filesystem.fsx.windowsMountDrive"
                label="Drive Letter Assignment"
                name="filesystem.fsx.windowsMountDrive"
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
                key="filesystem.fsx.backupRetentionDays"
                label="Backup Retention (Days)"
                name="filesystem.fsx.backupRetentionDays"
                type="number"
                disabled={props.isLocked}
              />
            </Col>
          </Row>
        </Col>
      </Row>
    )
  );
};