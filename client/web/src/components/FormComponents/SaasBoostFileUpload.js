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
import React, { useMemo, useCallback, useState } from 'react'
import { useDropzone } from 'react-dropzone'
import { Button, FormGroup } from 'reactstrap'
import { cilCloudUpload, cilX } from '@coreui/icons'
import CIcon from '@coreui/icons-react'

const baseStyle = {
  flex: 1,
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  padding: '20px',
  borderWidth: 2,
  borderRadius: 2,
  borderColor: '#eeeeee',
  borderStyle: 'dashed',
  backgroundColor: '#fafafa',
  color: '#bdbdbd',
  outline: 'none',
  transition: 'border .24s ease-in-out',
}

export const SaasBoostFileUpload = (props) => {
  const { label, fname, disabled, onFileSelected, fileMask } = props

  const [boostFiles, setBoostFiles] = useState([])
  const [filename, setFilename] = useState(fname)

  const first = ([x, ..._]) => x

  const onDrop = useCallback((acceptedFiles) => {
    setBoostFiles([...boostFiles, ...acceptedFiles])
    const file = first(acceptedFiles)
    onFileSelected(file)
    setFilename(file.name)
  })

  const { getRootProps, getInputProps, isDragActive, isDragAccept, isDragReject } = useDropzone({
    disabled: disabled,
    multiple: false,
    accept: fileMask || '',
    onDrop,
  })

  const style = useMemo(
    () => ({
      ...baseStyle,
    }),
    [isDragActive, isDragReject, isDragAccept],
  )

  const clearFilename = () => {
    setBoostFiles([])
    setFilename('')
  }

  const fileUi = () => {
    return (
      !!filename && (
        <div>
          <FormGroup>
            <span
              style={{
                alignSelf: 'flex-start',
                margin: '10px 3%',
                fontFamily: 'Helvetica',
              }}
            >
              {filename}
            </span>
            <Button onClick={clearFilename}>
            <CIcon icon={cilX} aria-hidden="true"></CIcon>
            </Button>
          </FormGroup>
        </div>
      )
    )
  }

  return (
    <>
      {!filename && (
        <div className="container">
          <div {...getRootProps({ style })}>
            <input {...getInputProps()} />
            <CIcon icon={cilCloudUpload} aria-hidden="true"></CIcon>
            <div id="notimage" className="hidden mb-3">
              {label}
            </div>
            <span id="file-upload-btn" className="btn btn-primary">
              Select a file
            </span>
          </div>
        </div>
      )}
      {fileUi()}
    </>
  )
}

SaasBoostFileUpload.propTypes = {
  label: PropTypes.string,
  fname: PropTypes.string,
  disabled: PropTypes.bool,
  onFileSelected: PropTypes.func,
  fileMask: PropTypes.string,
}
