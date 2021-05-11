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

import React, { Fragment } from "react";
import TenantFormComponent from "./TenantFormComponent";
import { unwrapResult } from "@reduxjs/toolkit";
import { useDispatch, useSelector } from "react-redux";
import { useHistory } from "react-router-dom";
import { provisionTenantThunk, dismissError } from "./ducks";

const TenantProvisionContainer = () => {
  const dispatch = useDispatch();
  const history = useHistory(); //pass to thunk to redirect after successful provisioning
  const handleCancel = () => {
    history.goBack();
  };
  const handleSubmit = (values, { setSubmitting }) => {
    dispatch(provisionTenantThunk({ values, history }))
      .then(unwrapResult)
      .then((tenant) => {
        setSubmitting(false);
        history.push(`/tenants/${tenant.tenantId}`);
      })
      .catch((e) => {
        // handled by component, setting submitting to false
        setSubmitting(false);
      });
  };

  const error = useSelector((state) => state.tenants.error);

  return (
    <Fragment>
      <TenantFormComponent
        title="Provision Tenant"
        handleSubmit={handleSubmit}
        handleCancel={handleCancel}
        error={error}
        dismissError={dismissError}
      />
    </Fragment>
  );
};

export default TenantProvisionContainer;
