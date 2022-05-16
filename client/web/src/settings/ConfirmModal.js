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
import { Button, Modal, ModalHeader, ModalBody, ModalFooter } from 'reactstrap'

export const ConfirmModal = (props) => {
  const { headerText, confirmationText, toggle, modal, confirmSubmit } = props

  return (
    <Modal isOpen={modal} toggle={toggle}>
      <ModalHeader toggle={toggle}>{headerText}</ModalHeader>
      <ModalBody>{confirmationText}</ModalBody>
      <ModalFooter>
        <Button color="danger" onClick={confirmSubmit}>
          Confirm
        </Button>{' '}
        <Button color="primary" onClick={toggle}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  )
}

ConfirmModal.propTypes = {
  headerText: PropTypes.string,
  confirmationText: PropTypes.string,
  toggle: PropTypes.func,
  modal: PropTypes.bool,
  confirmSubmit: PropTypes.func,
}

export default ConfirmModal
