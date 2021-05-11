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

import React from "react";
import { useField } from "formik";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";

// Styled TextInput for Formik forms that
// works with React Bootstrap
const SBTextInput = ({ label, ...props }) => {
  const [field, meta] = useField(props);
  return (
    <FormGroup controlId={props.id || props.name}>
      <FormLabel>{label}</FormLabel>
      <FormControl
        {...field}
        {...props}
        isInvalid={meta.touched && meta.error}
      ></FormControl>
      <FormControl.Feedback type="invalid">{meta.error}</FormControl.Feedback>
    </FormGroup>
  );
};

export default SBTextInput;
