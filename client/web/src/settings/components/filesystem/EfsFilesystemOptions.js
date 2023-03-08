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
import {Row, Col } from 'reactstrap'
import {
    SaasBoostSelect,
    SaasBoostInput,
    SaasBoostCheckbox,
} from '../../../components/FormComponents'

const EfsFilesystemOptions = (props) => {
    if (!!props.forTier) {
        return (
            <Row>
                <Col xl={6} className="mt-2">
                    <SaasBoostSelect
                        id={props.formikFilesystemTierPrefix + '.lifecycle'}
                        label="Lifecycle"
                        name={props.formikFilesystemTierPrefix + '.lifecycle'}
                        value={props.filesystem?.lifecycle}
                    >
                        <option value="NEVER">Never</option>
                        <option value="AFTER_7_DAYS">7 Days</option>
                        <option value="AFTER_14_DAYS">14 Days</option>
                        <option value="AFTER_30_DAYS">30 Days</option>
                        <option value="AFTER_60_DAYS">60 Days</option>
                        <option value="AFTER_90_DAYS">90 Days</option>
                    </SaasBoostSelect>
                    <SaasBoostCheckbox
                        id={props.formikFilesystemTierPrefix + '.encrypt'}
                        name={props.formikFilesystemTierPrefix + '.encrypt'}
                        key={props.formikFilesystemTierPrefix + '.encrypt'}
                        label="Encrypt at rest"
                        value={props.filesystem?.encrypt === 'true'}
                    />
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
                        value={props.filesystem?.mountPoint}
                    />
                </Col>
            </Row>
        )
    }
}

EfsFilesystemOptions.propTypes = {
    provisionFs: PropTypes.bool,
    containerOs: PropTypes.string,
    isLocked: PropTypes.bool,
    filesystem: PropTypes.object,
}

export default EfsFilesystemOptions