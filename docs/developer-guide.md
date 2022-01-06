# AWS SaaS Boost Developer Guide

## Contents
[Introduction](#introduction)\
[High-Level Architecture](#high-level-architecture)\
[Administration Application](#administration-application)\
[Core Services](#core-services)\
[Onboarding Service](#onboarding-service)\
[Tenant Service](#tenant-service)\
[User Service](#user-service)\
[Settings Service](#settings-service)\
[Metrics Service](#metrics-service)\
[Quota Service](#quota-service)\
[Billing Integration](#billing-integration)\
[Metrics and Analytics](#metrics-and-analytics)\
[Deploying Application Updates](#deploying-application-updates)

## Introduction
This document provides software as a service (SaaS) developers and architects with an inside view of the SaaS Boost technology. It explores the services and technologies that define the overall SaaS Boost experience.

As an open-source offering, the architecture and design of the SaaS Boost environment will be continually evolving. The goal of this document, then, is to focus more on the core building blocks of the SaaS Boost architecture and less on the signatures and contracts of each API call. It outlines the overall technical landscape of SaaS Boost architecture, providing developers with insights that help them understand the high-level architectural elements of the system.

## High-Level Architecture
The SaaS Boost architecture has four main layers. Each of these layers plays a different role in the overall architecture of the system. Figure 1 provides an overview of these core concepts.

![High-Level Architecture](images/dg-high-level-arch.png?raw=true "High-Level Architecture")
<p align="center">Figure 1 - High-level architecture</p>

The left-hand side of figure 1 shows the foundational components of the SaaS Boost environment. The administration application provides the management user interface to the environment, allowing you to configures and manage the attributes of your SaaS Boost environment. This application interacts with the core services layer, which represents all the microservices used to provision, manage, and operate your SaaS solution. This is where the majority of the environment's code lives. It is also where your customizations are likely to be introduced.

The layer below represents more operational constructs (billing, metering, metrics, and analytics services). While these services are essential to a robust SaaS environment, they appear separately in the diagram because they are used by both core services and the application that runs in each tenant's environment. These services also require developers to instrument their applications with additional code that can publish operational data and metrics to these services.

The last layer of this model is the managed tenant environments. This layer represents the resources that are provisioned for each tenant of your system. In reality, there is no code in the repository that corresponds to this layer. Instead, there is code in core services that provisions and configures all of the architecture that ends up running in the managed tenant environment layer. Nevertheless, once your system is up-and-running with tenants, you will have provisioned architecture constructs for each of the tenants that are managed by the SaaS Boost environment.

## Administration Application
The SaaS Boost [User Guide](user-guide.md) provides an in-depth look at the functionality of the administration application. Here we focus more on the technology and architecture that was used to build this application.

The administration application is built as a single page application (SPA) using the React JS framework. It conforms to the typical patterns and conventions used to construct any React application. It also uses the CoreUI administration template that runs the Bootstrap toolkit.

![Administration Application Architecture](images/dg-admin-app-arch.png?raw=true "Administration Application Architecture")
<p align="center">Figure 2 - Administration application architecture</p>

Figure 2 shows the architecture footprint of the client environment. The services depicted represent the typical AWS constructs used to host a single-page web application.

This solution uses Amazon CloudFront as the entry point of the administration application. This distribution directs incoming requests to an Amazon S3 bucket that hosts the SaaS Boost React application. Access to this application is controlled by Amazon Cognito, which manages all the users that are allowed to access the SaaS Boost administration experience.

### Running the client locally
After SaaS Boost is installed and running, you may be interested in making changes to the administration application. If you want to make changes and develop/test them locally, you can navigate to the `/client/web` folder that is part of the code you downloaded when cloning the SaaS Boost repository.

Within the same folder is a `sample.env` file that holds variables that are specific to your deployed environment. Rename this file to just be `.env` and edit its settings to allow your local environment to connect to the core services of your deployed SaaS Boost environment.

Open the newly named file in an editor of your choice. To run the client locally, there are various environment variables that must be configured. The following is an example of configured settings:
```ini
AWS_DEFAULT_REGION=us-east-1
REACT_APP_AWS_REGION=us-east-1
REACT_APP_COGNITO_USERPOOL=us-east-1_XXXXXXXXX
REACT_APP_CLIENT_ID=[YOUR CLIENT ID HERE]
REACT_APP_API_URI=[API URI HERE]
```
The values for these settings can be retrieved from the AWS Management Console. The Region is based on the physical location of your SaaS Boost deployment. You can also access the Amazon Cognito service in the console to look up the user pool and client ID settings. To get the URI entry point for backend services, use the API Gateway service in the console and copy the invoke URL for the v1 stage of the public API.

After these environment variables are correctly configured, you can edit and run the client locally from the `/client/web` folder using the following command: `yarn start`

## Core Services
The core services of SaaS Boost are responsible for much of the heavy lifting that is supported by the environment. Take note of the microservices and plumbing required to provision, manage, and operate your SaaS environment.

Before we dig into the details, let's take a high-level view of core services, as shown in figure 3.

![Core Services](images/dg-core-services.png?raw=true "Core Services")
<p align="center">Figure 3 - Core services</p>


The architecture of core services is implemented as a series of microservices that run in a serverless model with AWS Lambda. The highly variable nature of the load placed on these services typically fits well with the cost and consumption profile of the serverless model. This prevents you from paying for idle or rarely used services in your environment.

The administration application and each SaaS tenant environment access these services via the Amazon API Gateway, which exposes the public API of SaaS Boost.

Each service relies on a range of AWS services to deliver their functionality. The services conform to the general microservices best practices, encapsulating the storage and underlying constructs that are used to implement them. Each service supports an API that represents its contract.

The services here represent the baseline functionality of SaaS Boost, which covers specific use cases that are currently supported by the system. The service list will grow as SaaS Boost adds support for new SaaS models and use cases.

The sections that follow will look into the implementation of the system's microservices, providing a view into the goals and scope of each service's implementation. This will familiarize developers with the design of these services and give users a sense of where and how to customize the environment and align it with your application's needs.

## Onboarding Service
The onboarding service is one of the key components of the SaaS Boost experience. It's responsible for orchestrating the creation, configuration, and update of your SaaS environments, which includes both the introduction of new tenants as well as the deployment of application updates.

Having this all managed and deployed within SaaS Boost allows the environment to support all the strategies and policies that are part of the onboarding process. This means that the architecture, configuration, and deployment policies of your tenant environments can change over time without affecting the onboarding experience. In many respects, we want onboarding to be a black box that evolves according to the emerging needs of your system.

The onboarding service includes an API that accepts the data required to onboard a new tenant. This opens two onboarding paths. If your system supports a self-service onboarding experience, you can add an onboarding page to your application and trigger onboarding. If your system doesn't support self-service onboarding, you can onboard via the SaaS Boost admin application. Figure 4 provides a conceptual outline of the onboarding process.

The images on the left of figure 4 illustrate the two different paths for onboarding a new tenant, illustrating the self-service or admin application submission of an onboarding request. The request is submitted to the API of the onboarding service as a JSON message that holds all the data about your new tenant.

![Onboarding Flow](images/dg-onboarding-flow.png?raw=true "Onboarding Flow")
<p align="center">Figure 4 - The onboarding flow</p>

To introduce a new tenant, the onboarding service uses configuration data to create and configure the following resources:
- **Provision a tenant environment**: This process creates the virtual private network (VPC), networking constructs, Amazon ECS cluster, Route 53 Domain Name System (DNS) entries, and other infrastructure for each tenant.
- **Provision extensions**: Extensions represent the optional bits of infrastructure that are unique to a given SaaS application. These extensions (databases, file systems, etc.) are configured as part of your application configuration and are deployed/configured as part of the onboarding process.
- **Provision tenant**: Creates a signature of a tenant in the system using data that is submitted during the onboarding process.
- **Create a billing account**: If you use the built-in billing capabilities of SaaS Boost, your application must create a new customer in that billing system. The onboarding process creates the new customer as part of the billing integration.
- **Deploy the application**: Once the tenant's infrastructure is in place, you must deploy the SaaS application to that infrastructure. Onboarding deploys the application that's uploaded to SaaS Boost.

This should provide a sense of the importance of the onboarding service in the overall SaaS Boost model. Onboarding is at the hub of much of the automation that is essential to building a SaaS environment -- especially in a siloed SaaS model.

It should also be clear that the code behind this process is a likely area where you might choose to extend the existing model. Adding new extensions, for example, might be a natural place to introduce custom concepts that are not supported by the default SaaS Boost experience.

The next sections provide a detailed view of the onboarding process.

### Provisioning Tenant Infrastructure
The provisioning of tenant infrastructure is about creating the necessary infrastructure to host each tenant's environment. Because we're running in a silo model, the amount of infrastructure that gets provisioned here is more pronounced. If we used a pooled model (with shared infrastructure), there would be fewer moving parts to the onboarding experience. Figure 5 shows the key elements of these tenant environments.

![Provisioned Tenant Environments](images/dg-tenant-environments.png?raw=true "Provisioned Tenant Environments")
<p align="center">Figure 5 - Provisioned tenant environments</p>

Note that each tenant deploys in a separate Multi-AZ VPC. These VPCs run Fargate-based Amazon ECS clusters and have separate Application Load Balancers for each VPC entry point. Amazon Route 53 is also configured to route each tenant subdomain (e.g. `tenant1.abc.com`) to the corresponding VPC that hosts a tenant's infrastructure.

Some configuration options are used to modify the performance of the tenant experience within your infrastructure. The scaling profile of the compute cluster, for example, might vary based on the tier of a given tenant.

The code to provision the environment is largely driven by AWS CloudFormation. The onboarding service invokes and monitors calls to launch the AWS CloudFormation stacks. Extending and customizing tenant environments, in many cases, can be achieved by modifying the underlying CloudFormation templates.

### Provisioning Extensions
A key challenge associated with creating a complete SaaS environment is that each SaaS application may have infrastructure dependencies beyond compute. Most applications, for example, depend on a database, but which database and how it's configured can vary from one application to the next.

To support these variations, SaaS Boost uses extensions that let you configure additional resources that may be required by your application. Because SaaS Boost focuses on migrating monolithic environments to SaaS, the chosen extensions are biased toward services that are more common in a monolithic environment.

![Provisioning Extensions](images/dg-extensions.png?raw=true "Provisioning Extensions")
<p align="center">Figure 6 - Provisioning extensions</p>

Figure 6 provides a high-level view of the extension model. Each extension has a corresponding AWS CloudFormation file that stores the configuration options. As each tenant is provisioned, parameters are passed to the AWS CloudFormation stacks to convey the settings for each tenant.

The types of supported extensions are dictated by your operating system. Linux and Windows environments have specific requirements that affect the options you can enable. The emphasis at this stage is mostly on databases and file systems with the expectation that more extensions will eventually be supported.

If you want to introduce your own extensions, familiarize yourself with the code that runs the AWS CloudFormation stacks. Extensions represent a natural opportunity for developers to introduce new configuration options to support the needs of their SaaS environments.

### Create Billing Account (if enabled)
As each new tenant is onboarded to the system, it creates an associated customer within the billing system that is integrated with SaaS Boost. SaaS Boost captures this customer information during the onboarding process and references it as your SaaS application publishes billing events.

The onboarding process creates a customer by publishing an onboarding event to the billing system. This event includes all the necessary data to create a new customer in the billing system. This message is published to Amazon EventBridge, which takes the incoming request and calls a third-party billing API to create a new customer.

### Deploy Application
The last piece of the onboarding flow is to deploy an application to the new tenant environment. This deployment uses the containerized image that you uploaded to Amazon ECR and deploys it to the Amazon ECS cluster that was created during the provisioning of the tenant environment.

![Deploy Application for Tenant](images/dg-deploy.png?raw=true "Deploy Application for Tenant")
<p align="center">Figure 7 - Deploy application for new tenant</p>

Figure 7 provides an example of the deployment process. The tenant onboarding microservice uses AWS CodePipeline to orchestrate the application's deployment. It takes the container image from ECR and initiates a rolling deployment to the ECS cluster. After this step completes, the tenant can access the new environment and run the SaaS application.

### Surface and Manage Onboarding Status
The onboarding microservice tracks the status of each step of the onboarding flow. The steps are captured and surfaced to provide visibility into the progress and state of the onboarding lifecycle. The states are surfaced in the administration application.

While these states are informational, they are also monitored at stages in the flow to trigger downstream events. So, in many cases, the system may be waiting on a specific state transition before proceeding.

![Onboarding States](images/dg-onboarding-status.png?raw=true "Onboarding States")
<p align="center">Figure 8 - Onboarding states</p>

Figure 8 shows the different states of the onboarding flow. You first create a tenant using the tenant microservice. After a tenant is created, the onboarding flow moves to the "Created" state. The onboarding flow then uses AWS CloudFormation stacks to create the required infrastructure for each tenant, which includes any required extensions. When this process launches, the onboarding flow moves to the "Provisioning" state.

Note that the workflow relies on an Amazon Simple Notification Service (Amazon SNS) listener to track the provisioning state. Essentially, this listener follows the progress of AWS CloudFormation by waiting for it to complete provisioning the tenant infrastructure. When this process completes, the onboarding workflow moves to the "Provisioned" state.

At this stage, we have data about the created tenant resources that must be retained for future reference. An AWS Lambda function is introduced to capture and store the data about the resources created during the provisioning process. This function also sends a tenant onboarding event to Amazon EventBridge to provision a new tenant account in the billing system. When the Lambda function finishes, the system moves to the "Deploying" state.

The final steps in this process are managed by AWS CodePipeline, which deploys the application to your tenant's new ECS cluster. Amazon EventBridge tracks the progress of AWS CodePipeline and then moves the workflow to the "Deployed" state when it determines that the pipeline is finished.

## Tenant Service
Every SaaS system must have a centralized mechanism for tracking and managing the state of individual tenants. This is the role of the tenant microservice, which provides a collection of basic operations that can be performed on any tenant. This includes creating new tenants (as part of onboarding), updating tenant settings, and enabling/disabling tenants.

The tenant service is implemented as a collection of Lambda functions, each of which supports a different operation. The data for this service is stored in Amazon DynamoDB, where each item is accessed by a tenant identifier that's stored in a partition key of a DynamoDB table. This data includes a range of attributes that represent information about a tenant's configuration and status (enabled/disabled).

When onboarding a tenant, you have the option to specify settings for that tenant's environment. This allows you to size the tenant based on their given tier. This means the compute size, for example, could be configured differently for basic- and advanced-tier tenants.

![Tenant Configuration](images/dg-tenant-configuration.png?raw=true "Tenant Configuration")
<p align="center">Figure 9 - Tenant configuration</p>

Figure 9 provides an example of the tenant configuration experience. This represents the built-in tenant onboarding that is supported by the SaaS Boost administration application. Take note of the Override Application Defaults check box. When checked, this allows you to override the compute settings that are configured at the application level.

These configuration options are stored with each tenant and used as part of the provisioning experience when a tenant is onboarded. The values can also be individually updated and applied to a tenant.

The ability to provide separate sizing attributes at the tenant level can be used as part of your tiering strategy. However, it is recommended that you avoid having separate scaling configurations for each tenant. This could undermine the operational efficiency that you're targeting as part of your SaaS system. We recommend that you define a standard set of scaling values for each tier of your system and use these tier values when provisioning your tenants. The settings you configure at the application level represent your default tier. Tiers will eventually become a formal construct that manage sizing and scaling within SaaS Boost.

The tenant service also supports an enable/disable option where you can selectively change the tenant's status. When you ask the service to disable a tenant, all access to that tenant's application is blocked until it is re-enabled.

## User Service
The user microservice provides operations for managing users of the administration application. It supports the classic set of create, read, update, and delete (CRUD) mechanisms to manage the list of administrators. Amazon Cognito manages the data for each user, which means that each management operation makes an API call to Amazon Cognito to fetch, create, update, or delete users.

## Settings Service
The settings service provides a centralized mechanism for managing a wide range of settings used by various SaaS Boost services. In many respects, this service is implemented as a basic property/value store, housing information that helps collect key outputs from infrastructure provisioning in addition to the global configuration data.

![Settings Service](images/dg-settings-service.png?raw=true "Settings Service")
<p align="center">Figure 10 - Settings service</p>

Figure 10 shows a high-level view of the settings service. There are two primary producers of SaaS Boost settings. On the right-hand side of the diagram, the installation script that's part of SaaS Boost publishes a range of infrastructure environment parameters that are stored by the settings service. The properties and values that are stored correspond to the various infrastructure resources that are created when the SaaS Boost environment is provisioned. This allows downstream processes and scripts to resolve references to infrastructure resources.

On the left-hand side of the diagram, the administration application publishes configuration data to the settings services by sending in all the default settings that are used to configure your application. This includes settings for your application domain in addition to compute, operating system, extensions (database, file system, etc.), and billing integration information.

The settings service is also used by the onboarding process, which relies on these settings to configure each new tenant that is added to the system. The settings service stores settings using AWS Systems Manager Parameter Store.

## Metrics Service
SaaS Boost includes a built-in experience that lets organizations continually assess the activity and health of tenant environments. The data that's collected for this service comes from the metrics service, which provides a way for the administration application to access and render its operational analytics charts and graphs.

Currently, the metrics service monitors data activity from the Amazon ECS cluster that runs each tenant's compute resources. It also collects data about access patterns from the Application Load Balancer access logs. Figure 11 provides a view of the key parts of the metrics microservice.

![Inside the Metrics Service](images/dg-metrics-service.png?raw=true "Inside the Metrics Service")
<p align="center">Figure 11 - Inside the metrics service</p>

Figure 11 shows the infrastructure that's part of each tenant environment. Consumption metrics from Amazon ECS are published through Amazon CloudWatch, and access patterns data is captured and stored in an S3 bucket. As the administration application accesses the metrics microservice to render graphs, these calls build the graph dataset from the two sources described here.

The metrics service retrieves data from CloudWatch for graphs that view consumption data, while the graphs for access trends make calls to Amazon Athena to query access logs. The data is then shaped into a JSON structure that is used to return a standardized representation of the graph's data points.

It's important to note that the focus of the metrics service is on enabling access to specific tenant-aware operational insights. Tenant context is part of all the metrics data that is captured here. This context is used to create views of the data that allow SaaS providers to analyze and assess system activity through a tenant (or tier) lens. Where possible, the administration application lets you filter analytics views by tenant. This aids operations teams by focusing on the trends of individual tenants.

## Quota Service
The quota service provides a lightweight mechanism for collecting information about AWS account quotas. As new tenants are added to the system, it evaluates the state of different account limits to determine the system's readiness to provision new infrastructure.

This microservice makes a variety of calls to different AWS services to gather quota data. The data returned from these calls is used to assess whether your SaaS Boost environment exceeds any quota thresholds that could affect the system's ability to provision more tenants.

## Billing Integration
SaaS Boost gives companies the option of integrating with a billing system. This provides those who migrate to a SaaS model with a ready-to-use billing solution, enabling SaaS providers to introduce billing support with minimal effort.

The challenge of providing a billing integration is that there is no standard API or model for SaaS billing systems. While there are common themes, each provider has a unique mechanism for representing their billing experience. To help mitigate this, we introduced a layer between the third-party billing system (Stripe) and SaaS Boost.

Note that billing integration uses both SaaS Boost and the changes you introduce into your application to build a complete billing experience. SaaS Boost configures as many moving parts as it can. Once you enable billing in SaaS Boost, it is the application's responsibility to publish events that are used to generate a SaaS bill. This integration from your application relies on the billing integration layer that sits between you and the billing system.

Your use of Stripe's services is not subject to your agreement with AWS. Your use of Stripe's services are subject to Stripe's legal terms, including Stripe's terms for processing your personal data, available here: https://stripe.com/legal. Note that all use of AWS Services is billed to the applicable AWS Account, in addition to any fees you incur for use of AWS SaaS Boost. You may review your use of AWS Services for each AWS Account under the AWS Console.

![Billing Integration](images/dg-billing-integration.png?raw=true "Billing Integration")
<p align="center">Figure 12 - Billing integration</p>

Figure 12 provides a view of the core moving parts of the billing integration. There are four distinct elements of the billing architecture that are represented by different coloring schemes. On the right-hand side of the diagram are steps 1 and 2, where a SaaS provider begins by creating a Stripe account. This is also the point at which you get the required API key for your SaaS Boost integration.

After you create a Stripe account, provide the API key through the SaaS Boost administration application (steps 3–5). The system uses the settings microservice to store this API key, which enables billing for the system. Configuring this API also sets up the Stripe products that are used to determine your application's billing model.

The billing pieces are now in place to support the onboarding of new tenants. Steps 6–9 represent the onboarding flow. When your application or the administration application onboards a tenant, the application sends a message to the onboarding service. The system then sends a message to billing integration (via Amazon EventBridge), which triggers a call to the Stripe API to create an account for a new customer within Stripe. Stripe returns data about the subscription, which is stored in Amazon DynamoDB.

The last part of the onboarding flow focuses on publishing billing events from your application (steps 10–12). This is where you identify the key billing activities in your application and send them to Stripe to calculate your bill. To process these events, the system must look up the tenant's subscription information (step 11) before sending the message to Stripe.

## Metrics and Analytics
Despite SaaS Boost having built-in metrics and analytics tools, it's essential for SaaS providers to have a broader mechanism to capture and analyze metrics that are specific to their domain/environment. Metrics can span a wide range of uses cases and consumers.

Collecting these custom metrics is achieved by instrumenting your SaaS application with all the metrics data that is essential to your business. The value you extract from this data is often directly related to the quality and depth of the data your application publishes.

To support this, we create a classic data ingestion architecture that allows organizations to publish, aggregate, and analyze metric data. Figure 13 provides a high-level view of the SaaS Boost metrics infrastructure. The model follows the typical data ingestion and aggregation mechanisms that are commonly used to address data analytics challenges.

![Metrics and Analytics](images/dg-metrics-analytics.png?raw=true "Metrics and Analytics")
<p align="center">Figure 13 - Metrics ingesting, aggregation, and visualization</p>

Figure 13 shows your tenant environment, where you can add code to publish relevant metrics in your environment. This data is ingested by Amazon Kinesis Data Firehose and moved to Amazon Redshift. At this point, you can use Amazon QuickSight to build dashboards that analyze different operational, architectural, business, and tenant trends for your SaaS environment.

The data published from your application is packaged in a generic JSON event that conveys consumption information. We provide a JSON model for this event, but you may want to refine the event's structure based on the needs of your specific application and environment.

Note that the metrics architecture is an optional component of SaaS Boost. You can introduce this when it makes sense for your environment. While having these metrics is essential to a SaaS business, we acknowledge that you may use other tools to address this requirement.

## Deploying Application Updates
After your environment is running and you've onboarded a few tenants, the next area that teams will focus on is the deployment of application updates. Deployment of these updates is especially important in SaaS environments where each new release is deployed to all the tenants of your system. SaaS Boost handles this entire deployment lifecycle, publishing each new version as a rolling update to all of your tenant environments.

![Deploying Application Updates](images/dg-deploying-updates.png?raw=true "Deploying Application Updates")
<p align="center">Figure 14 - Deploying application updates</p>

Figure 14 provides a high-level view of the deployment process. Note that each application update is packaged as a containerized application and uploaded to the SaaS Boost instance of Amazon ECR.

When SaaS Boost detects a newly uploaded image, it triggers the applicable update process as part of a rolling update to each tenant's environment. This removes the heavy lifting from the DevOps pipeline and pushes the responsibility to SaaS Boost.

## Conclusion
This document explored the core elements of the SaaS Boost architecture by providing a summary of the building blocks used in the overall experience. This should have provided a foundation for exploring the code and services used to implement the SaaS Boost experience.

This version of SaaS Boost is the first step in the long-term development of a prescriptive, open-source SaaS model that can accelerate development for many SaaS organizations. While we focused on a specific migration pattern, we expect the user community and SaaS Boost team to introduce new features and capabilities. We encourage you to dig into the code and provide feedback to help us shape the evolution of this environment.
