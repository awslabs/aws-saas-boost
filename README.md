# AWS SaaS Boost

## Overview
AWS SaaS Boost provides organizations with ready-to-use core software elements for successfully running SaaS workloads in the cloud.  Software builders can focus on preserving core intellectual property by removing the complexities of building SaaS solutions using AWS SaaS Boost.

SaaS Boost provides tenant management, deployment automation, analytics dashboards, billing and metering, an administrative web interface, and more out-of-the-box. This environment reduces development and experimentation time, allowing you to get your software into the hands of your customers faster.

Jump to the [Getting Started Guide](./docs/getting-started.md) to start working with AWS SaaS Boost today!

## Repo Rundown

| Directory | Description |
| --- | --- |
| client/web | Admin web application |
| docs | Documentation |
| docs/images | Images/graphics for docs |
| functions | "Helper" Lambda functions |
| functions/core-stack-listener | Callback from CloudFormation -> SNS to trigger a CloudFormation Macro that dynamically creates resources for your application services  |
| functions/ecs-service-update | Used by CodePipeline to make sure ECS deploys at least 1 task |
| functions/ecs-shutdown-services | Optional functionality to shutdown tenant ECS services for costs savings in non-production environments |
| functions/ecs-startup-services | Optional functionality to startup tenant ECS services that have been shutdown |
| functions/onboarding-app-stack-listener | Callback from CloudFormation -> SNS to trigger post provisioning flows for each of your application servces |
| functions/onboarding-stack-listener | Callback from CloudFormation -> SNS to trigger post tenant provisioning flows |
| functions/system-rest-api-client | REST client used by services to invoke API of other services |
| functions/workload-deploy | Listens for changes to ECR and Onboarding Service to trigger CodePipeline for tenants |
| installer | Command line installer |
| layers | Lambda layers (i.e. shared libraries) |
| layers/apigw-helper | Used by the REST client to invoke API Gateway endpoints. Supports SigV4 request signing for the private system API |
| layers/cloudformation-utils | CloudFormation Utility functions |
| layers/utils | Utility functions |
| metering-billing | Optional billing and metering module |
| metering-billing/lambdas | Billing Service |
| metrics-analytics | Optional analytics module |
| metrics-analytics/deploy | Kinesis Firehose JSONPath to write to Redshift |
| metrics-analytics/metrics-generator | Example test script to create and push metrics |
| metrics-analytics/metrics-java-sdk | Sample Java library to build and push metrics payloads to SaaS Boost |
| resources | CloudFormation resources |
| resources/custom-resources | CloudFormation custom resources |
| resources/custom-resources/app-services-ecr-macro | CloudFormation Macro to dynamically generate ECR repositories and supporting infrastructure for each defined application service |
| resources/custom-resources/cidr-dynamodb | Populates a DynamoDB table with the available CIDR blocks for tenant VPCs |
| resources/custom-resources/clear-s3-bucket | Deletes all versions of all files and delete markers from S3 buckets so CloudFormation can remove the bucket on stack delete |
| resources/custom-resources/fsx-dns-name | Retrieves the DNS entry for the hosted FSx file system |
| resources/custom-resources/rds-bootstrap | Executes your SQL file to bootstrap empty databases during tenant onboarding |
| resources/custom-resources/rds-options | Caches the available RDS engines, versions, and instance types for the current region and account |
| resources/custom-resources/redshift-table | Bootstraps the RedShift database for the optional analytics module |
| resources/custom-resources/set-instance-protection | Disables AutoScaling instance protection when we update or delete stacks |
| samples | Example workloads that can be deployed as an application to SaaS Boost |
| samples/java | Linux example monolithic app using Java Spring Framework Web MVC |
| samples/dotnet-framework | Windows OS example monolithic app using .Net Framework 4.x ASP.NET MVC (not .NET Core) 
| services | SaaS Boost micro services |
| services/metric-service | Metrics Service supports the operational insights dashboards in the admin web app |
| services/onboarding-service | Onboarding Service orchestrates tenant creation, infrastructure provisioning, workload deployment and billing setup |
| services/quotas-service | Quotas Service checks AWS Account service quotas before onboarding new tenants |
| services/settings-service | Settings Service maintains SaaS Boost environment configuration and your application configuration |
| services/tenant-service | Tenant Service manages your tenants and their unique attributes |
| services/tier-service | Tier Service manages tiers for packaging your offering. You can define application configurations per tier. |
| services/user-service | User Service manages system users (users of the admin web app, not users of your application) |

## Cost
You will be billed for the [various services](docs/services.md) leveraged by AWS SaaS Boost. Provisioned resources are tagged with "SaaS Boost" and each tenant's resources are also uniquely tagged to help you with [cost allocation](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/cost-alloc-tags.html).

Each tenant you onboard into an AWS SaaS Boost environment will provision more infrastructure and will increase your costs.

Note that the _optional_ Analytics and Metrics module will provision a Redshift cluster.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License. See the [LICENSE](LICENSE) file.