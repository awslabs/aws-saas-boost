# ECS Startup Services

## Overview
This function can be used to startup all application workloads running for your provisioned tenants. It does this by setting the `desiredCount` attribute of each of your ECS services for each tenant to the minium task count set for the tier that tenant is onboarded into. [Watch a detailed walkthru](https://www.twitch.tv/videos/1065389231) of building this solution during our [Office Hours](https://github.com/awslabs/aws-saas-boost/discussions/106).

## Why would you need this?
This function gives you a way to undo the [EcsShutdownServices](../ecs-shutdown-services/README.md) function. If you're using that to save costs in your non-production environments, pair it with this function to bring your services back up when you're ready to use them again.

## How do I use it?
Because you should not use this feature in production, SaaS Boost will not automatically turn it on during install. The function and supporting resources like log groups and IAM policies will be installed and ready-to-use.

The common use case will be to trigger this function on a schedule. For example, you may choose to startup your application services in the morning in a development environment after having shut them down overnight. Amazon EventBridge supports [triggering Lambda functions on a schedule](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-create-rule-schedule.html). See the sample [enable.sh](enable.sh) script for one way to configure EventBridge with the EcsStartupServices function.

You can also simply invoke the Lambda function manually with the AWS CLI `aws lambda invoke --function-name sb-${SAAS_BOOST_ENV}-ecs-startup-services response.json`.

See [ECS Shutdown Services](../ecs-shutdown-services/README.md).