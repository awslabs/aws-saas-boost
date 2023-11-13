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
import {PropTypes} from 'prop-types'
import React from 'react'
import {Formik, Form} from 'formik'
import * as Yup from 'yup'
import {Row, Col, Card, Button, Alert} from 'react-bootstrap'
import {SaasBoostInput} from '../components/FormComponents'

const initialProvider = {
    id: null,
    name: '',
    metadata: {
        assumedRole: '',
        userPoolId: '',
    }
}

const ProviderForm = (props) => {
    const {
        handleSubmit,
        handleCancel,
        provider = initialProvider,
        error,
        dismissError,
    } = props

    const showError = (error, dismissError) => {
        if (!!error) {
            return (
                <Row>
                    <Col md={6}>
                        <Alert
                            color="danger"
                            isOpen={!!error}
                            toggle={() => dismissError()}
                        >
                            <h4 className="alert-heading">Error</h4>
                            <p>{error}</p>
                        </Alert>
                    </Col>
                </Row>
            )
        }
        return undefined
    }

    return (
        <Formik
            initialValues={provider}
            enableReinitialize={true}
            validationSchema={
                Yup.object({
                    metadata: Yup.object().shape({
                        userPoolId: Yup.string()
                            .max(128, 'User Pool Id cannot be longer than 128 characters.')
                            .required('Required'),
                        assumedRole: Yup.string()
                            .min(20, 'ARNs must be at least 20 characters.')
                            .max(2048, 'ARNs cannot be longer than 2048 characters.')
                            .required('Required')
                    })
                })}
            onSubmit={handleSubmit}
        >
            {(formik) => (
                <Form>
                    {provider.id && (
                        <input
                            type="hidden"
                            name="id"
                            id="id"
                            value={provider.id}
                        />
                    )}
                    <div className="animated fadeIn">
                        {showError(error, dismissError)}
                        <Row>
                            <Col lg={6}>
                                <Card>
                                    <Card.Header>Configure Identity Provider</Card.Header>
                                    <Card.Body>
                                        <SaasBoostInput label="User Pool Id" name="metadata.userPoolId" type="text"/>
                                        <SaasBoostInput label="IAM Access Role ARN" name="metadata.assumedRole"
                                                        type="text"/>
                                    </Card.Body>
                                    <Card.Footer>
                                        <Button variant="danger" onClick={handleCancel}>
                                            Cancel
                                        </Button>
                                        <Button
                                            className="ml-2"
                                            variant="primary"
                                            type="Submit"
                                            disabled={formik.isSubmitting}
                                        >
                                            {formik.isSubmitting ? 'Saving...' : 'Submit'}
                                        </Button>
                                    </Card.Footer>
                                </Card>
                            </Col>
                        </Row>
                    </div>
                </Form>
            )}
        </Formik>
    )
}

ProviderForm.propTypes = {
    handleSubmit: PropTypes.func,
    handleCancel: PropTypes.func,
    dismissError: PropTypes.func,
    provider: PropTypes.object,
    error: PropTypes.string,
    config: PropTypes.object,
}

export default ProviderForm
