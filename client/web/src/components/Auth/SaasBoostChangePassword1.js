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
import PropTypes from 'prop-types'
import React, { Component } from 'react'
import { Formik, Form } from 'formik'
import * as Yup from 'yup'
import { Auth } from 'aws-amplify'

import { Modal, ModalHeader, ModalBody, ModalFooter, Button, Alert, Row, Col } from 'reactstrap'
import { SaasBoostInput } from '../FormComponents/SaasBoostInput'

class SaasBoostChangePassword extends Component {
  constructor(props) {
    super(props)
    this.state = {
      isOpen: false,
      error: undefined,
      success: false,
    }
    this.handleCancel = this.handleCancel.bind(this)
    this.handleSubmit = this.handleSubmit.bind(this)
    this.showError = this.showError.bind(this)
    this.handleError = this.handleError.bind(this)
  }

  toggle() {}

  showError(error, handleError) {
    return (
      <Alert color="danger" isOpen={!!error} toggle={() => handleError()}>
        <h4 className="alert-heading">Error</h4>
        <p>{error}</p>
      </Alert>
    )
  }

  handleCancel() {
    const { handleClose } = this.props
    // do clean up.
    handleClose()
  }

  handleError() {
    this.setState({ error: undefined })
  }

  async handleSubmit(values, { resetForm, setSubmitting }) {
    const { newPassword, oldPassword } = values
    try {
      const user = await Auth.currentAuthenticatedUser()

      await Auth.changePassword(user, oldPassword, newPassword)
      setSubmitting(false)
      this.setState((state, props) => {
        return { success: true }
      })
    } catch (err) {
      this.setState({ error: err.message })
      setSubmitting(false)
      resetForm({ values })
    }
  }

  render() {
    const { show } = this.props
    const { error, success } = this.state

    const initialValue = {
      oldPassword: '',
      newPassword: '',
      confirmNewPassword: '',
    }

    const validationSchema = Yup.object({
      oldPassword: Yup.string().required('Required'),
      newPassword: Yup.string()
        .min(6, 'Password must have a minimum of 6 characters')
        .required('Required'),
      confirmNewPassword: Yup.string()
        .min(6, 'Password must have a minimum of 6 characters')
        .equals([Yup.ref('newPassword'), null], 'Password does not match')
        .required('Required'),
    })

    return (
      <>
        <Modal isOpen={show && !success} className="modal-primary">
          <Formik
            initialValues={initialValue}
            validationSchema={validationSchema}
            onSubmit={this.handleSubmit}
          >
            {(props) => (
              <Form>
                <ModalHeader>Change Password</ModalHeader>
                <ModalBody>
                  <Row>
                    <Col>{error && this.showError(error, this.handleError)}</Col>
                  </Row>
                  <Row>
                    <Col>
                      <SaasBoostInput
                        placeholder="Current Password"
                        label="Current Password"
                        name="oldPassword"
                        type="password"
                      />
                      <SaasBoostInput
                        placeholder="New Password"
                        label="New Password"
                        name="newPassword"
                        type="password"
                      />
                      <SaasBoostInput
                        placeholder="Confirm New Password"
                        label="Confirm New Password"
                        name="confirmNewPassword"
                        type="password"
                      />
                    </Col>
                  </Row>
                </ModalBody>
                <ModalFooter>
                  <Button color="primary" className="ml-2" disabled={props.isSubmitting}>
                    {props.isSubmitting ? 'Saving...' : 'Change Password'}
                  </Button>
                  <Button color="secondary" onClick={this.handleCancel}>
                    Cancel
                  </Button>
                </ModalFooter>
              </Form>
            )}
          </Formik>
        </Modal>
        <Modal isOpen={success && show} className="modal-success">
          <ModalHeader>Success</ModalHeader>
          <ModalBody>
            <p>Your password has been changed successfully.</p>
          </ModalBody>
          <ModalFooter>
            <Button color="secondary" onClick={this.handleCancel}>
              Close
            </Button>
          </ModalFooter>
        </Modal>
      </>
    )
  }
}

SaasBoostChangePassword.propTypes = {
  show: PropTypes.bool,
  handleClose: PropTypes.func,
}

export default SaasBoostChangePassword
