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
import React from 'react'
import { Formik, Form } from 'formik'

import { Modal, ModalBody, ModalFooter, ModalHeader, Button, FormGroup } from 'reactstrap'
import * as Yup from 'yup'
import { SaasBoostInput } from '../components/FormComponents'

UserDeleteConfirmationComponent.propTypes = {
  username: PropTypes.string,
  showModal: PropTypes.bool,
  toggleModal: PropTypes.func,
  deleteUser: PropTypes.func,
  resetForm: PropTypes.func,
  isValid: PropTypes.bool,
  dirty: PropTypes.bool,
  isSubmitting: PropTypes.bool,
}

export default function UserDeleteConfirmationComponent(props) {
  const { username, showModal, toggleModal, deleteUser } = props

  const initialValues = {
    confirmText: '',
  }
  const validation = Yup.object({
    confirmText: Yup.string()
      .required('Required')
      .matches(/^delete$/, 'Please type "delete" to confirm'),
  })

  const resetAndClose = (resetForm) => {
    resetForm()
    toggleModal()
  }

  const handleSubmit = async (values, { setSubmitting }) => {
    await deleteUser(username, setSubmitting, toggleModal)
  }

  return (
    <Formik initialValues={initialValues} validationSchema={validation} onSubmit={handleSubmit}>
      {(props) => (
        <Modal isOpen={showModal} className="modal-danger">
          <Form>
            <ModalHeader>Delete User</ModalHeader>
            <ModalBody>
              <p>Are you sure you want to delete the user &apos;{username}&apos;?</p>
              <p>Type &apos;delete&apos; to confirm:</p>
              <FormGroup>
                <SaasBoostInput type="text" name="confirmText" tabIndex="1" />
              </FormGroup>
            </ModalBody>
            <ModalFooter>
              <Button
                type="button"
                className="btn-info"
                onClick={() => resetAndClose(props.resetForm)}
                tabIndex="3"
              >
                Cancel
              </Button>
              <Button
                type="Submit"
                className="btn-danger"
                disabled={!(props.isValid && props.dirty) || props.isSubmitting}
                tabIndex="2"
              >
                {props.isSubmitting ? 'Deleting...' : 'Delete'}
              </Button>
            </ModalFooter>
          </Form>
        </Modal>
      )}
    </Formik>
  )
}
