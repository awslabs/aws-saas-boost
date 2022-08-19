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
  FormGroup,
  Label,
} from 'reactstrap'
import { Field } from 'formik'
import {
  SaasBoostCheckbox,
} from '../../../components/FormComponents'
import CIcon from '@coreui/icons-react'
import 'rc-slider/assets/index.css'
import { FILESYSTEM_TYPES, OS_TO_FS_TYPES } from './index.js'

// TODO there should be a tooltip for the checkbox to provision:
// TODO tooltip="If selected, a new file system will be created and mounted to the container as a docker volume"

const FilesystemType = (props) => {
  let filesystemOptions = OS_TO_FS_TYPES[props.containerOs]

  if (!filesystemOptions || filesystemOptions.length === 0) {
    console.error("No filesystem type found for containerOs", props.containerOs)
    return null
  }

  if (filesystemOptions.length === 1) {
    if (props.filesystemType !== filesystemOptions[0].id) {
      props.setFieldValue(props.formikTierPrefix + ".filesystemType", filesystemOptions[0].id)
    }
    return null
  }

  return (
    <FormGroup>
      <div className="mb-2">Choose a Filesystem Type:</div>
      {filesystemOptions.map((fs) => (
        <FormGroup check inline>
        <Field
          className="form-check-input"
          type="radio"
          id={"fs-" + fs.id + "-" + props.formikTierPrefix}
          name={props.formikTierPrefix + ".filesystemType"}
          value={fs.id}
          disabled={!fs.enabled(props.containerOs, props.containerLaunchType)}
        />
        <Label className="form-check-label" check htmlFor={"fs-" + fs.id + "-" + props.formikTierPrefix}>
          <CIcon icon={fs.icon} /> {fs.name}
        </Label>
      </FormGroup>
      ))}
    </FormGroup>)
}

class EmptyComponent extends React.Component {
  render() {
    return null
  }
}

export default class FileSystemSubform extends React.Component {
  render() {
    var FsComponent = (!!this.props.filesystemType && FILESYSTEM_TYPES[this.props.filesystemType]) 
        ? FILESYSTEM_TYPES[this.props.filesystemType].component
        : EmptyComponent
    return (
      <Fragment>
        <Row className="mt-3">
          <Col xs={12}>
            <Card>
              <CardHeader>File System</CardHeader>
              <CardBody>
                <SaasBoostCheckbox
                  id={this.props.formikTierPrefix + '.provisionFS'}
                  name={this.props.formikTierPrefix + '.provisionFS'}
                  label="Provision a File System for the application."
                />
                {this.props.provisionFs && (
                  <FilesystemType {...this.props}></FilesystemType>
                )}
                {this.props.provisionFs && this.props.filesystemType && (
                  <FsComponent {...this.props}></FsComponent>
                )}
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
  filesystemType: PropTypes.string,
  containerOs: PropTypes.string,
  containerLaunchType: PropTypes.string,
  isLocked: PropTypes.bool,
  filesystem: PropTypes.object,
  formik: PropTypes.object,
}