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
} from 'reactstrap'
import 'rc-slider/assets/index.css'
import { FILESYSTEM_TYPES } from './index.js'

class EmptyComponent extends React.Component {
  render() {
    return null
  }
}

export default class FileSystemTierSubform extends React.Component {
  render() {
    var FsComponent = (!!this.props.filesystemType && FILESYSTEM_TYPES[this.props.filesystemType]) 
        ? FILESYSTEM_TYPES[this.props.filesystemType].component
        : EmptyComponent
    return this.props.provisionFs && this.props.filesystemType ? (
      <Fragment>
        <Row className="mt-3">
          <Col xs={12}>
            <Card>
              <CardHeader>File System</CardHeader>
              <CardBody>
                  <FsComponent {...this.props} forTier={true}></FsComponent>
              </CardBody>
            </Card>
          </Col>
        </Row>
      </Fragment>
    ) : (<></>)
  }
}

FileSystemTierSubform.propTypes = {
  provisionFs: PropTypes.bool,
  filesystemType: PropTypes.string,
  containerOs: PropTypes.string,
  containerLaunchType: PropTypes.string,
  isLocked: PropTypes.bool,
  filesystem: PropTypes.object,
  formik: PropTypes.object,
}