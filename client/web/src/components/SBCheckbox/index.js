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
import { FormGroup, FormLabel, FormControl, FormCheck } from "react-bootstrap";

// Styled TextInput for Formik forms that
// works with React Bootstrap
const SBCheckbox = ({ label, children, ...props }) => {
  return (
    <FormGroup controlId={props.id || props.name} label={label}>
      <FormLabel>{label}</FormLabel>
      <FormGroup>{children}</FormGroup>
    </FormGroup>
  );
};

export const SBCheckboxItem = ({ value, checked, label, ...props }) => {
  const [field, meta] = useField(props);
  return (
    <FormCheck
      {...field}
      {...props}
      type="checkbox"
      label={label}
      id={`${field.name}-radio-${value}`}
      isInvalid={meta.touched && meta.error}
    ></FormCheck>
  );
};

export default SBCheckbox;
