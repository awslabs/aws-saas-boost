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

import React, { useEffect } from "react";
import { Row, Col, Card, CardBody } from "reactstrap";
import globalConfig from "../config/appConfig";
import {
  dismissError,
  fetchTenantsThunk,
  selectAllTenants,
  countTenantsByActiveFlag,
} from "../tenant/ducks";
import { useDispatch, useSelector } from "react-redux";
import {
  fetchConfig,
  selectSettingsById,
  selectConfig,
} from "../settings/ducks";
import * as SETTINGS from "../settings/common";
import { isEmpty } from "lodash";
import { selectOSLabel, selectDbLabel } from "../options/ducks";
import ECRInstructions from "../components/ECRInstructions";

const ActiveTenantsComponent = React.lazy(() =>
  import("./ActiveTenantsComponent")
);

const CurrentApplicationVersionComponent = React.lazy(() =>
  import("./CurrentApplicationVersionComponent")
);

const SaasBoostEnvNameComponent = React.lazy(() =>
  import("./SaasBoostEnvNameComponent")
);

const InstalledExtensionsComponent = React.lazy(() =>
  import("./InstalledExtensionsComponent")
);

export const DashboardComponent = (props) => {
  const dispatch = useDispatch();
  const appConfig = useSelector(selectConfig);
  const clusterOS = useSelector((state) =>
    selectSettingsById(state, SETTINGS.CLUSTER_OS)
  );
  const dbEngine = useSelector((state) =>
    selectSettingsById(state, SETTINGS.DB_ENGINE)
  );
  const version = useSelector((state) =>
    selectSettingsById(state, SETTINGS.VERSION)
  );
  let osLabel = "N/A";

  const osLabelValue = useSelector((state) =>
    selectOSLabel(state, clusterOS?.value)
  );

  const dbLabelValue = useSelector((state) =>
    selectDbLabel(state, dbEngine?.value)
  );

  if (!isEmpty(osLabelValue)) {
    osLabel = osLabelValue;
  }

  const metricsAnalyticsDeployed = useSelector((state) =>
    selectSettingsById(state, "METRICS_ANALYTICS_DEPLOYED")
  );

  const billingApiKey = useSelector((state) =>
    selectSettingsById(state, "BILLING_API_KEY")
  );

  const saasBoostEnvironment = useSelector(
    (state) => selectSettingsById(state, "SAAS_BOOST_ENVIRONMENT")?.value
  );

  const countActiveTenants = useSelector((state) => {
    return countTenantsByActiveFlag(state, true);
  });

  const countAllTenants = useSelector((state) => {
    return selectAllTenants(state).length;
  });

  const ecrRepo = useSelector((state) => selectSettingsById(state, "ECR_REPO"));
  const s3Bucket = useSelector((state) =>
    selectSettingsById(state, "SAAS_BOOST_BUCKET")
  );

  const awsAccount = globalConfig.awsAccount;
  const awsRegion = globalConfig.region;

  const fileAwsConsoleLink = `https://${awsRegion}.console.aws.amazon.com/efs/home?region=${awsRegion}#file-systems`;
  const rdsAwsConsoleLink = `https://${awsRegion}.console.aws.amazon.com/rds/home?region=${awsRegion}#databases:`;
  const ecrAwsConsoleLink = `https://${awsRegion}.console.aws.amazon.com/ecr/repositories?region=${awsRegion}`;
  const s3BucketLink = `https://s3.console.aws.amazon.com/s3/buckets/${s3Bucket?.value}`;

  useEffect(() => {
    const fetchTenants = dispatch(fetchTenantsThunk());
    return () => {
      if (fetchTenants.PromiseStatus === "pending") {
        fetchTenants.abort();
      }
      dispatch(dismissError());
    };
  }, [dispatch]); //TODO: Follow up on the use of this dispatch function.

  useEffect(() => {
    const fetchConfigResponse = dispatch(fetchConfig());
    return () => {
      if (fetchConfigResponse.PromiseStatus === "pending") {
        fetchConfigResponse.abort();
      }
      dispatch(dismissError());
    };
  }, [dispatch]);

  return (
    <div className="animated fadeIn">
      <Row>
        <Col md={12} xl={12}>
          <Card>
            <CardBody>
              <Row>
                <Col xs={12} md={6} lg={6}>
                  <Row>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className={"text-muted"}>Total Tenants</small>
                        <br />
                        <strong className="h4">{countAllTenants}</strong>
                      </div>
                    </Col>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className={"text-muted"}>Active Tenants</small>
                        <br />
                        <strong className="h4">{countActiveTenants}</strong>
                      </div>
                    </Col>
                  </Row>
                  <hr className="mt-0" />
                </Col>
                <Col xs={12} md={6} lg={6}>
                  <Row>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className={"text-muted"}>
                          AWS Saas Boost Environment
                        </small>
                        <br />
                        <strong className="h4">{saasBoostEnvironment}</strong>
                      </div>
                    </Col>
                    <Col xs={6}>
                      <div className="callout callout-info">
                        <small className={"text-muted"}>AWS Region</small>
                        <br />
                        <strong className="h4">{awsRegion}</strong>
                      </div>
                    </Col>
                  </Row>
                  <hr className="mt-0" />
                </Col>
              </Row>

              <Row>
                <Col xs={12} md={6} lg={6}>
                  <dl>
                    <dt>Application Name</dt>
                    <dd>{isEmpty(appConfig?.name) ? "N/A" : appConfig.name}</dd>
                    <dt>Operating System</dt>
                    <dd>{osLabel}</dd>
                    <dt>File System</dt>
                    <dd>
                      {isEmpty(appConfig?.filesystem) ? (
                        "Not Configured"
                      ) : (
                        <div>
                          NFS compatible{" - "}
                          <a
                            href={fileAwsConsoleLink}
                            target="new"
                            className="text-muted"
                          >
                            AWS Console <i className="fa fa-external-link"></i>
                          </a>
                        </div>
                      )}
                    </dd>
                    <dt>Database</dt>
                    <dd>
                      {!!appConfig?.database?.engine ? (
                        <div>
                          {dbLabelValue}
                          {" - "}
                          <a
                            href={rdsAwsConsoleLink}
                            target="new"
                            className="text-muted"
                          >
                            AWS Console <i className="fa fa-external-link"></i>
                          </a>
                        </div>
                      ) : (
                        "Not Configured"
                      )}
                    </dd>
                    <dt>Billing</dt>
                    <dd>
                      {!!appConfig?.billing?.apiKey
                        ? "Configured"
                        : "Not Configured"}
                    </dd>
                    <dt>Version</dt>
                    <dd>{version?.value && <div>{version.value}</div>}</dd>
                  </dl>
                </Col>
                <Col xs={12} md={6} lg={6}>
                  <dl>
                    <dt className="mb-1">Modules</dt>
                    <dd className="mb-3">
                      <div>
                        {metricsAnalyticsDeployed?.value === "true" ? (
                          <i className="icon icon-check text-success"></i>
                        ) : (
                          <i className="icon icon-close text-danger"></i>
                        )}{" "}
                        Metrics
                      </div>
                    </dd>
                    <dt>ECR Repository</dt>
                    <dd className="mb-3">
                      {ecrRepo?.value}
                      {" - "}
                      <a
                        href={ecrAwsConsoleLink}
                        target="new"
                        className="text-muted"
                      >
                        AWS Console <i className="fa fa-external-link"></i>
                      </a>
                    </dd>
                    <dt>
                      ECR Repository URL{" - "}
                      <ECRInstructions
                        awsAccount={awsAccount}
                        awsRegion={awsRegion}
                        ecrRepo={ecrRepo?.value}
                      >
                        <span className="text-muted">
                          View details <i className="fa fa-external-link"></i>
                        </span>
                      </ECRInstructions>
                    </dt>
                    <dd className="mb-3">
                      {awsAccount}.dkr.ecr.{awsRegion}.amazonaws.com{" "}
                    </dd>
                    <dt>S3 Bucket</dt>
                    <dd className="mb-3">
                      {s3Bucket?.value}
                      {" - "}
                      <a
                        href={s3BucketLink}
                        target="new"
                        className="text-muted"
                      >
                        AWS Console <i className="fa fa-external-link"></i>
                      </a>
                    </dd>
                    <dt>Public API</dt>
                    <dd>{globalConfig.apiUri}</dd>
                  </dl>
                </Col>
              </Row>
            </CardBody>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default DashboardComponent;
