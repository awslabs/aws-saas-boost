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
        serviceName: Yup.string()
                        .matches(/^[\.\-_a-zA-Z0-9]+$/, 'Name must only contain alphanumeric characters or .-_')
                        .required('Service name is required'),
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
                Create Service
              </Button>
            </ModalFooter>
          </Form>
        </Modal>
      )}
    </Formik>
  )
}
