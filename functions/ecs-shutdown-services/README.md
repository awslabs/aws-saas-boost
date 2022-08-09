# ECS Shutdown Services

## Overview
This function can be used to gracefully shutdown all application workloads running for your provisioned tenants. It does this by setting the `desiredCount` attribute of each of your ECS services for each tenant to zero (0). [Watch a detailed walkthru](https://www.twitch.tv/videos/1065389231) of building this solution during our [Office Hours](https://github.com/awslabs/aws-saas-boost/discussions/106).

## Why would you turn off your SaaS application?
Excellent question! You should **not** use this function for your production environments. Your SaaS customers expect your service to be available at all times. However, for development and other non-production environments, it may be useful to temporarily shutdown your application tasks as a way to save operational costs. ECS tasks that run on Fargate are only billed when they are running. If you are running your tasks on EC2, the ECS capacity provider will shutdown the EC2 instances in your ECS cluster.

## How do I use it?
Because you should not use this feature in production, SaaS Boost will not automatically turn it on during install. The function and supporting resources like log groups and IAM policies will be installed and ready-to-use.

The common use case will be to trigger this function on a schedule. For example, you may choose to shutdown your application services overnight in a development environment. Amazon EventBridge supports [triggering Lambda functions on a schedule](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-create-rule-schedule.html). See the sample [enable.sh](enable.sh) script for one way to configure EventBridge with the EcsShutdownServices function.

You can also simply invoke the Lambda function manually with the AWS CLI `aws lambda invoke --function-name sb-${SAAS_BOOST_ENV}-ecs-shutdown-services response.json`.

Once you've shutdown your application tasks to save money, you'll probably want to turn them back on. See [ECS Startup Services](../ecs-startup-services/README.md).