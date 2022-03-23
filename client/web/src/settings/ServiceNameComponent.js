import { Modal, ModalBody, ModalFooter, ModalHeader, Button } from 'reactstrap'
import { Form, Formik } from 'formik'
import * as Yup from 'yup'
import { SaasBoostInput } from '../components/FormComponents'

export const ServiceNameComponent = (props) => {
  const { showModal, toggleModal, addService } = props
  return (
    <Formik
      initialValues={{ serviceName: '' }}
      validationSchema={Yup.object({
        serviceName: Yup.string().required('Service name is required'),
      })}
      onSubmit={async (values) => {
        const { serviceName } = values
        addService(serviceName)
        toggleModal()
      }}
    >
      {(props) => (
        <Modal isOpen={showModal}>
          <Form>
            <ModalHeader>Enter service name</ModalHeader>
            <ModalBody>
              <SaasBoostInput
                placeholder="Service Name"
                name="serviceName"
                type="text"
              ></SaasBoostInput>
            </ModalBody>
            <ModalFooter>
              <Button className="btn-danger" onClick={toggleModal}>
                Close
              </Button>
              <Button type="submit" className="btn-info">
                Save changes
              </Button>
            </ModalFooter>
          </Form>
        </Modal>
      )}
    </Formik>
  )
}
