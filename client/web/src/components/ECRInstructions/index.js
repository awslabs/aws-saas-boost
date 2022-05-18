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
import React, { useState } from 'react'
import { Modal, ModalBody, ModalHeader } from 'reactstrap'

ECRInstructions.propTypes = {
  awsAccount: PropTypes.string,
  awsRegion: PropTypes.string,
  ecrRepo: PropTypes.string,
  children: PropTypes.object,
}

export default function ECRInstructions(props) {
  const { awsAccount, awsRegion, ecrRepo, children } = props

  const [showModal, toggleShowModal] = useState(false)

  function toggleModal() {
    toggleShowModal((s) => !s)
  }

  return (
    <>
      <a href="#" onClick={toggleShowModal}>
        {children}
      </a>
      <Modal size="lg" fade={true} isOpen={!!showModal}>
        <ModalHeader toggle={toggleModal} className="bg-secondary">
          Push commands for ECR repository
        </ModalHeader>
        <ModalBody>
          <p>
            Make sure that you have the latest version of the AWS CLI and Docker
            installed. For more information, see Getting Started with Amazon
            ECR.
          </p>
          <p>
            Use the following steps to authenticate and push an image to your
            repository. For additional registry authentication methods,
            including the Amazon ECR credential helper, see Registry
            Authentication.
          </p>
          <div>
            <ol>
              <li>
                Retrieve an authentication token and authenticate your Docker
                client to your registry. Use the AWS CLI:
                <div className="text-monospace bg-gray-200 mt-3 mb-3 p-2">
                  aws ecr get-login-password --region {awsRegion} | docker login
                  --username AWS --password-stdin {awsAccount}.dkr.ecr.
                  {awsRegion}.amazonaws.com
                </div>
                <div className="text-muted">
                  Note: If you receive an error using the AWS CLI, make sure
                  that you have the latest version of the AWS CLI and Docker
                  installed.
                </div>
              </li>
              <li>
                Build your Docker image using the following command. For
                information on building a Docker file from scratch see the
                instructions here . You can skip this step if your image is
                already built:{' '}
                <div className="text-monospace bg-gray-200 mt-3 mb-3 p-2">
                  docker build -t saas-boost .
                </div>
              </li>
              <li>
                After the build completes, tag your image so you can push the
                image to this repository:{' '}
                <div className="text-monospace bg-gray-200 mt-3 mb-3 p-2">
                  docker tag saas-boost:latest {awsAccount}.dkr.ecr.{awsRegion}
                  .amazonaws.com/
                  {ecrRepo}:latest
                </div>
              </li>
              <li>
                Run the following command to push this image to your newly
                created AWS repository:{' '}
                <div className="text-monospace bg-gray-200 mt-3 mb-3 p-2">
                  docker push {awsAccount}.dkr.ecr.{awsRegion}.amazonaws.com/
                  {ecrRepo}:latest
                </div>
              </li>
            </ol>
          </div>
        </ModalBody>
      </Modal>
    </>
  )
}
