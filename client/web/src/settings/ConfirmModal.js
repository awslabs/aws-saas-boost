import React from "react";
import { Button, Modal, ModalHeader, ModalBody, ModalFooter } from "reactstrap";

export const ConfirmModal = (props) => {
  const { headerText, confirmationText, toggle, modal, confirmSubmit } = props;

  return (
    <Modal isOpen={modal} toggle={toggle}>
      <ModalHeader toggle={toggle}>{headerText}</ModalHeader>
      <ModalBody>{confirmationText}</ModalBody>
      <ModalFooter>
        <Button color="danger" onClick={confirmSubmit}>
          Confirm
        </Button>{" "}
        <Button color="primary" onClick={toggle}>
          Cancel
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default ConfirmModal;
