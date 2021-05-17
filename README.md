# AWS SaaS Boost

## Overview
AWS SaaS Boost provides organizations with ready-to-use core software elements for successfully running SaaS workloads in the cloud.  Software builders can focus on preserving core intellectual property by removing the complexities of building SaaS solutions using AWS SaaS Boost.

Get a boost with tenant management, deployment automation, analytics dashboards, billing and metering, an administrative web interface, and more. This environment reduces development and experimentation time, allowing you to get your software into the hands of customers faster.

Check out our [announcement blog post](https://aws.amazon.com/blogs/apn/transforming-your-monolith-to-saas-with-aws-saas-boost/) for a more detailed description of how you can benefit from AWS SaaS Boost.

Now, jump to the [Getting Started Guide](./docs/getting-started.md) to start working with AWS SaaS Boost today.

## Repo Rundown

| Directory | Description |
| --- | --- |
| client/web | React JS admin web app |
| docs | Documentation |
| docs/images | Images/graphics for docs |
| functions | "Helper" Lambda functions |
| functions/alb-update | Used by Tenant Service enable/disable to modify tenant's load balancer access |
| functions/ecs-deploy | Listens for changes to ECR and Onboarding Service to trigger CodePipeline for tenants |
| functions/ecs-service-update | Used by CodePipeline to make sure ECS deploys at least 1 task |
| functions/onboarding-notification | Callback from CloudFormation -> SNS to trigger post provisioning flows |
| functions/system-rest-api-client | REST client used by services to invoke API of other services |
| installer | Command line installer |
| layers | Lambda layers (i.e. shared libraries) |
| layers/apigw-helper | Used by the REST client to invoke API Gateway endpoints. Supports SigV4 request signing for the private system API |
| layers/utils | Utility functions |
| metering-billing | Optional billing and metering module |
| metering-billing/lambdas | Billing Service |
| metrics-analytics | Optional analytics module |
| metrics-analytics/deploy | Kinesis Firehose JSONPath to write to Redshift |
| metrics-analytics/metrics-generator | Example test script to create and push metrics |
| metrics-analytics/metrics-java-sdk | Sample Java library to build and push metrics payloads to SaaS Boost |
| resources | CloudFormation resources |
| resources/custom-resources | Lambda functions called by CloudFormation |
| samples | Example workloads that can be deployed as an application to SaaS Boost |
| samples/java | Example monolithic app using the Java Spring Framework |
| services | SaaS Boost micro services |
| services/metric-service | Metrics Service supporting the operational insights dashboards in the admin web app |
| services/onboarding-service | Onboarding Service creates new tenants and provisions infrastructure |
| services/quotas-service | Quotas Service checks AWS Account service quotas before onboarding new tenants |
| services/settings-service | Settings Service maintains SaaS Boost environment configuration and tenant infrastructure configuration |
| services/tenant-service | Tenant Service manages tenants |
| services/user-service | User Service manages system users (users of the admin web app, not tenant users) |

## Cost
You will be billed for the [various services](docs/services.md) leveraged by AWS SaaS Boost. Provisioned resources are tagged with "SaaS Boost" and each tenant's resources are also uniquely tagged to help you with [cost allocation](https://docs.aws.amazon.com/awsaccountbilling/latest/aboutv2/cost-alloc-tags.html).

Each tenant you onboard into an AWS SaaS Boost environment will provision more infrastructure and will increase your costs.

Note that the _optional_ Analytics and Metrics module will provision a Redshift cluster.

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.