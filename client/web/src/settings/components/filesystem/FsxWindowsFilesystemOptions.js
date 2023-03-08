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
import { PropTypes } from 'prop-types'
import {
    Row,
    Col,
    Input,
    InputGroup,
    FormGroup,
    Label, } from 'reactstrap'
import {
    SaasBoostSelect,
    SaasBoostInput,
} from '../../../components/FormComponents'
import Slider from 'rc-slider'

export default class FsxWindowsFilesystemOptions extends React.Component {
    render() {
        let props = this.props
        const fsMarks = {
            32: '32',
            1024: '1024',
          }

          const tpMarks = {
            3: '8',
            11: '2048',
          }

          const onStorageChange = (val) => {
            props.setFieldValue(
              props.formikFilesystemTierPrefix + '.storageGb',
              val
            )
          }

          const onThroughputChange = (val) => {
            props.setFieldValue(
              props.formikFilesystemTierPrefix + '.throughputMbs',
              Math.pow(2, val)
            )
          }

          const onWeeklyMaintTimeChange = (val) => {
            props.setFieldValue(
              props.formikFilesystemTierPrefix + '.weeklyMaintenanceTime',
              val.target.value
            )
          }

          const onWeeklyDayChange = (val) => {
            props.setFieldValue(
              props.formikFilesystemTierPrefix + '.weeklyMaintenanceDay',
              val.target.value
            )
          }

          if (props.forTier) {
            return (
              <Row>
                <Col sm={6} className="mt-2">
                  <Row>
                    <Col xs={3}>
                      <FormGroup>
                        <Label htmlFor="storageVal">Storage</Label>
                        <Input
                          id={'storageVal' + props.formikFilesystemTierPrefix}
                          className="mb-4"
                          type="number"
                          value={props.filesystem?.storageGb}
                          readOnly
                        ></Input>
                      </FormGroup>
                    </Col>
                    <Col xs={9}>
                      <FormGroup>
                        <Label htmlFor="storage">In GB</Label>
                        <Slider
                          id={'storage' + props.formikFilesystemTierPrefix}
                          defaultValue={props.filesystem?.storageGb}
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
                          id={'throughputVal' + props.formikFilesystemTierPrefix}
                          className="mb-4"
                          type="number"
                          value={props.filesystem?.throughputMbs}
                          readOnly
                        ></Input>
                      </FormGroup>
                    </Col>
                    <Col xs={9}>
                      <FormGroup>
                        <Label htmlFor="throughput">In MB/s</Label>
                        <Slider
                          id={'throughput' + props.formikFilesystemTierPrefix}
                          defaultValue={Math.sqrt(props.filesystem?.throughputMbs)}
                          onChange={onThroughputChange}
                          marks={tpMarks}
                          className="mb-4"
                          included={false}
                          min={3}
                          max={11}
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
                        key={props.formikFilesystemTierPrefix + '.dailyBackupTime'}
                        label="Daily Backup Time (UTC)"
                        name={props.formikFilesystemTierPrefix + '.dailyBackupTime'}
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
                          value={props.filesystem?.weeklyMaintenanceDay}
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
                          key={props.formikFilesystemTierPrefix + '.weeklyMaintenanceTime'}
                          onChange={onWeeklyMaintTimeChange}
                          value={props.filesystem?.weeklyMaintenanceTime}
                          name={props.formikFilesystemTierPrefix + '.weeklyMaintenanceTime'}
                          type="time"
                          disabled={props.isLocked}
                        />
                      </InputGroup>
                    </Col>
                  </Row>
                  <Row>
                    <Col xs={6}>
                      <SaasBoostSelect
                        id={props.formikFilesystemTierPrefix + '.windowsMountDrive'}
                        label="Drive Letter Assignment"
                        name={props.formikFilesystemTierPrefix + '.windowsMountDrive'}
                        value={props.filesystem?.windowsMountDrive}
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
                        key={props.formikFilesystemTierPrefix + '.backupRetentionDays'}
                        label="Backup Retention (Days)"
                        name={props.formikFilesystemTierPrefix + '.backupRetentionDays'}
                        type="number"
                        disabled={props.isLocked}
                      />
                    </Col>
                  </Row>
                </Col>
              </Row>
            )
          } else {
            return (
                <Row>
                  <Col xl={6} className="mt-2">
                    <SaasBoostInput
                      key={props.formikServicePrefix + '.filesystem.mountPoint'}
                      label="Mount point"
                      name={props.formikServicePrefix + '.filesystem.mountPoint'}
                      type="text"
                      disabled={props.isLocked}
                    />
                  </Col>
                </Row>
              )
          }
    }
  }

FsxWindowsFilesystemOptions.propTypes = {
    setFieldValue: PropTypes.func,
    provisionFs: PropTypes.bool,
    containerOs: PropTypes.string,
    isLocked: PropTypes.bool,
    filesystem: PropTypes.object,
}