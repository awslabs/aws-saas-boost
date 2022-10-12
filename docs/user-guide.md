# AWS SaaS Boost User Guide

## Contents
[Introduction](#introduction)\
[Overview](#overview)\
[Two Paths of Adoption](#two-paths-of-adoption)\
    [Path 1: Low-Touch Adoption](#path-1-low-touch-adoption)\
    [Path 2: Fork-and-Extend Adoption](#path-2-fork-and-extend-adoption)\
[Accessing AWS SaaS Boost](#accessing-aws-saas-boost)\
[Configuring Your Application](#configuring-your-application)\
[Uploading a Sample Application](#uploading-a-sample-application)\
[Onboarding Tenants](#onboarding-tenants)\
[Managing Tenants](#managing-tenants)\
[Managing Users](#managing-users)\
[Operational Insights](#operational-insights)\
    [Request Analytics](#request-analytics)\
    [Compute Analytics](#compute-analytics)\
    [Usage Analytics](#usage-analytics)\
[Metrics and Analytics](#metrics-and-analytics)\
    [Metrics Architecture](#metrics-architecture)\
    [Metrics Instrumentation](#metrics-instrumentation)\
    [Building Dashboards](#building-dashboards)\
[Billing and Metering](#billing-and-metering)\
    [Creating a Stripe Account](#creating-a-stripe-account)\
    [Configuring Products](#configuring-products)\
    [Creating Stripe Customers (Tenants)](#creating-stripe-customers-tenants)\
    [Metering Instrumentation](#metering-instrumentation)

## Introduction
This document introduces software-as-a-service (SaaS) developers and architects to the fundamentals of AWS SaaS Boost. It outlines the concepts associated with preparing your environment and guides you through key elements of the configuration, tenant onboarding, operations, billing, and metrics features of AWS SaaS Boost. 

While this document provides some insights into the way AWS SaaS Boost works, it is not intended to provide a deep dive into the underlying technology. A more detailed view is covered in the [Developer Guide](./developer-guide.md). 

The installation and setup of AWS SaaS Boost is covered in the [Getting Started Guide](./getting-started.md).

## Overview
AWS SaaS Boost is an environment that accelerates a single-tenant independent software vendor's (ISV's) path to a SaaS offering by providing a prescriptive, ready-to-use offering that enables them to move their existing solution to a multi-tenant model, minimizing the typical effort and investment associated with moving a solution to a SaaS delivery model. 

To better understand the mechanics of AWS SaaS Boost, look at the starting point for many ISVs. AWS SaaS Boost is focused on those organizations that are running monolithic environments (meaning the entire application can be packaged and deployed as a single unit.) In this model, each of the ISV's customers is typically running in their own environments. This might be in the cloud, in a private cloud, or on premises. Each customer is often running a distinct environment. This environment might be managed by the customer or by the ISV. 

The left side of the diagram in Figure 1 gives a conceptual view of this classic software deployment model. In this mindset, companies will often see themselves as having a series of "customers", each of which has a separately installed version of their product. Since each customer has its own environment, ISVs often allow customers to opt in to the version of the product that they want to run. In this example, each of the three customers is running a different version. It's also common for ISVs to allow one-off customization in this model, creating scenarios where the infrastructure and environment can be unique to each customer. 
 
While this variation offers flexibility, it also adds challenges for these ISVs. You'll notice in the diagram that each customer can also require specific management, operations, configuration, and so on. Supporting this level of per-customer customization often translates into higher operational costs for ISVs. As the ISV business grows, they find that this model limits their ability to scale and accept new customers without absorbing significant overhead. It also undermines their ability to innovate.

![Figure 1 - Creating a unified experience](images/ug-unified-experience.png?raw=true "Figure 1 - Creating a unified experience")
<p align="center">Figure 1 - Creating a unified experience</p>

The right side of this diagram highlights what it means to move to the AWS SaaS Boost (multi-tenant) model. Here, you have tenants instead of customers.  Each entity that signs up for a SaaS service that is viewed as a tenant of a single, unified environment that supports all of your tenants. In this model, all tenants are managed, operated, deployed, and supported via a common set of constructs. This means all tenants run the same version of the product. It also means that the deployment of new versions of the product are delivered to all tenants. Essentially, to achieve the economies of scale that align with SaaS tenets, you need to abandon support for one-off variations and require tenants to run in a single, unified experience. 

While tenants are running in this unified experience, they can still belong to different tiers that influence their experience. Basic-tier tenants, for example, may not have access to the same features or throughput that is available to premium-tier tenants. These variations are managed through configuration, eliminating the need for any one-off customization for each tenant. All of these tenants-regardless of their assigned tier-are managed and operated by a single experience. 

This, then, is the initial value proposition of AWS SaaS Boost-equip companies with an environment that can move their applications to a SaaS model that allows tenants to be onboarded, managed, and operated collectively. This allows companies to rapidly realize the business and operational advantages associated with a SaaS delivery model. 

Figure 2 highlights how this model fundamentally changes the way a SaaS provider engages its market. The idea here is to shift away from the notion of "installed" software and move to an "as-a-service" mindset where tenants can easily onboard to a service with minimal friction. This streamlines the introduction of new tenants, allowing the business to grow faster and engage new markets. The SaaS model allows you to add new business without adding significant overhead to your operational footprint.

![Figure 2 - A new onboarding mindset](images/ug-onboarding-mindset.png?raw=true "Figure 2 - A new onboarding mindset")
<p align="center">Figure 2 - A new onboarding mindset</p>

The approach taken by AWS SaaS Boost is shaped by the following tenets:
- Applications should be able to move into AWS SaaS Boost with minimal or no modifications.
- Organizations should be able to use AWS SaaS Boost as a self-contained environment, eliminating any need to modify underlying code to get their SaaS solutions up and running.
- AWS SaaS Boost provides the multi-tenant building blocks that are essential to provisioning, operating, and managing a SaaS solution on AWS.
- The move to AWS SaaS Boost may represent the first step in transforming your SaaS business, opening the door to future modernization and optimization of your SaaS application.
- While AWS SaaS Boost focuses its initial attention on technical transformation, it also includes collateral and resources to assist organizations with transforming their business to an "as-a-service" model.

It's equally important to note what AWS SaaS Boost is not. Specifically, AWS SaaS Boost is not attempting to represent itself as a platform that spans all the best practices and strategies for implementing SaaS on AWS. Instead, it covers a specific scenario that is focused on accelerating transformation of a monolith to a SaaS model. This means it implements a pattern of migration that limits the investment in refactoring and focuses more on time-to-market.

Figure 3 provides a conceptual view of the key components of AWS SaaS Boost. 

![Figure 3 - Fundamentals of AWS SaaS Boost](images/ug-boost-experience.png?raw=true "Figure 3 - Fundamentals of AWS SaaS Boost")
<p align="center">Figure 3 - Fundamentals of the AWS SaaS Boost experience</p>

1.	Acquire the solution by cloning the AWS SaaS Boost GitHub repository. 
2.	Provision AWS SaaS Boost in your AWS account,
3.	Configure the environment with the settings that are specific to your environment.
4.	Containerize and upload an image of your monolithic application or your monolithic application to AWS SaaS Boost. This would be added to your existing build process (as represented in the diagram).
5.	Provision the tenants. The AWS SaaS Boost administration application provides a built-in mechanism to provision individual tenants, or you could invoke this provisioning process from your own application's sign-up page. The key here is that AWS SaaS Boost owns the responsibility for provisioning and configuring all the pieces needed to enable and activate a tenant. You can then repeat this process to add more tenants to the system without any extra work. 
6.	Use AWS SaaS Boost to manage and operate your tenant environments.

When all these steps are done, you can focus on your application. When you introduce new features or fix a bug in your application, you can rebuild your solution and upload the new containerized image, and that image is deployed to all the tenants in your system. You essentially get all the management and deployment efficiency that you need in a SaaS environment, addressing all your tenants at once through a single unified experience.

## Two Paths of Adoption
As an open-source offering, AWS SaaS Boost presents ISV developers with two distinct models for using AWS SaaS Boost as the home for your SaaS application. The path you choose may vary (or evolve) depending on the nature of your environment and the goals of your business. The following sections highlight the two most common strategies that AWS SaaS Boost adopters might follow.

### Path 1: Low-Touch Adoption
In the low-touch model, ISVs typically align closely with the out-of-the-box experience targeted by AWS SaaS Boost. In this mode, the ISV developer is hoping to lift and shift their solution into AWS SaaS Boost without digging into the details of how it's implemented. The goal is to get to SaaS as quickly as possible, leveraging the core capabilities of self-contained AWS SaaS Boost where you simply configure and consume the experience like a product or service.

This low-touch model presumes that you can start running your SaaS solution on AWS without requiring changes to the configuration or multi-tenant strategies that are employed by the AWS SaaS Boost environment. There may be cases in the low-touch model where minor changes are made to the environment without incurring significant time or diverging from the fundamental strategies implemented by AWS SaaS Boost.

ISVs that choose the low-touch model are also likely to want to follow and apply changes that are introduced into new versions of AWS SaaS Boost. As new features are introduced, developers may want to download and take advantage of these new capabilities as they are released.

If you are aligned to the low-touch model, the next natural question to ask is this: what makes an ISV solution a good fit for the low-touch experience? The following is a list of attributes that would make a solution a good candidate for AWS SaaS Boost: 

1. You have a monolithic Linux or Windows based web application as your starting point. This is the most fundamental point to start with since this first version of AWS SaaS Boost needs to run your application on a single container. The emphasis here is more on a lift-and-shift mindset that covers applications that aren't yet modernized or decomposed into a collection of separate services. Future versions are likely to look beyond monoliths, but supporting them is not part of the v1.0 offering.
2. Your application relies on a database. AWS SaaS Boost has built-in abilities to provision and configure your database requirements. If you're a classic relational database user, you'll likely have a ready-to-use option available to you with AWS SaaS Boost. However, if you have more specialized database requirements, you may need to provision your database on its own or customize the AWS SaaS Boost provisioning experience.
3. You have dependencies on a file system. Many monolithic applications use a file system to store data that is referenced by their web applications. AWS SaaS Boost includes built-in support for file systems to make the transition easier for monolithic environments.
4. Traffic will flow into your system via HTTP. Web applications are the prime targets for AWS SaaS Boost. Generally, if you have an HTTP-based experience today, this will make for a much more seamless, low-touch transition to AWS SaaS Boost.

These are some of the basic parameters that often influence whether low-touch adoption of AWS SaaS Boost will work for you. Naturally, there are so many technology and architecture permutations that it's hard to provide a precise list of what may impact your readiness for AWS SaaS Boost. In cases where low-touch doesn't fit, consider customizing where needed by using the fork-and-extend approach described below.

### Path 2: Fork-and-Extend Adoption
The goal of AWS SaaS Boost is to reduce the amount of development, operational, and organization effort that is commonly associated with adopting SaaS model. At the same time, SaaS isn't a one-size-fits-all model. Some organizations may decide to customize the environment. Unique networking requirements, challenging third-party integrations, specific security constraints-these are among the potential custom requirements that may not be easily addressed by low-touch adoption.

For these scenarios, SaaS organization may need to fork the existing AWS SaaS Boost code base and introduce the customizations that are needed to get their environment up and running. Ideally, these customizations can be captured and merged into the core of AWS SaaS Boost, enabling other ISV developers to take advantage of the new capabilities.

If these customizations come in through an open-source community, they will extend the capabilities of AWS SaaS Boost, enabling it to evolve in a way that reduces the number of ISVs that have to select the customization path. Still, this path is valid and may, for some, represent their best opportunity to maximize the value of AWS SaaS Boost for their business and technical needs. 

## Accessing AWS SaaS Boost
After you've installed AWS SaaS Boost (using the Getting Started Guide), a message is sent to the email address provided during the install process. This message includes a link that represents the entry point to the AWS SaaS Boost administration application. The message appears as follows:

![Figure 4 - Getting your temporary password](images/ug-welcome-email.png?raw=true "Figure 4 - Getting your temporary password")
<p align="center">Figure 4 - Getting your temporary password</p>

Copy the temporary password that is shown here and select the link. It takes you to the AWS SaaS Boost administration application. The following login appears:

![Figure 5 - Setting up your password](images/ug-change-password.png?raw=true "Figure 5 - Setting up your password")
<p align="center">Figure 5 - Setting up your password</p>

Enter `admin` for the user name and enter the temporary password that was delivered in the email referenced above. Since you're logging in with a temporary password, you'll be prompted to enter a new password for your account. The screen appears as follows:

![Figure 6 - AWS SaaS Boost Login](images/ug-login.png?raw=true "Figure 6 - AWS SaaS Boost Login")
<p align="center">Figure 6 - AWS SaaS Boost Login</p>

After successfully logging into the system, you'll be placed at the Summary page. This page provides a high-level view of the state of your environment. Upon your initial entry, these settings are populated with minimal data. A sample of the screen is shown below.

As you use AWS SaaS Boost, this screen provides more context that can be helpful. Overall, though, the general role of this page is meant to serve as a way to provide a quick view into the state of the system and provide links to resources that are part of AWS SaaS Boost. 

![Figure 7 - Summary Page](images/ug-summary.png?raw=true "Figure 7 - Summary Page")
<p align="center">Figure 7 - The Summary Page</p>

## Configuring Your Application
After you've logged into the AWS SaaS Boost administration application, configure the environment to align it with the specific needs of your application. To setup the application, select **Application** from the navigation on the left side of the administration application. A page similar to the image below appears.

![Figure 8 - Configuring the application](images/ug-app-config.png?raw=true "Figure 8 - Configuring the application")
<p align="center">Figure 8 - Configuring the application</p>

> **Note**: In this example, the red **SETUP** badge next to **Application** in the navigation bar indicates that the application needs to be configured. The **Onboarding** and **Tenants** options are disabled. When you are setting up AWS SaaS Boost for the first time, you must first provide the application details before you can begin onboarding and managing tenants. After you've configured the application, the Onboarding and Tenants options are enabled.

Now, let's walk through the options that are configured on this page. The following sections provide details on each of the options that are specific to your SaaS environment.

### Application
- Name - The name you use to refer to your application (often the product name).
- Domain Name - The base domain name that customers use to access your application. AWS SaaS Boost uses a subdomain model for routing individual tenants. So, this domain is part of this routing construct where you have `tenant1.mydomain.com` and `tenant2.mydomain.com`. Then, as tenants are onboarded, provide the subdomain for individual tenants. If you do not provide a name, use specific, generated entry-point URLs that are associated with each tenant.
- SSL Certificate ARN - If you want to access the tenant applications using HTTPS, first create a certificate for your domain in [Amazon Certificate Manager](https://docs.aws.amazon.com/acm/latest/userguide/gs-acm-request-public.html). There is no cost for the certificate. Once your certificate is created, get the ARN from the Console that will look something like the following and paste into the field in SaaS Boost. `arn:aws:acm:region:account:certificate/12345678-1234-1234-1234-123456789012`

### Container Settings
- Compute Size - To simplify configuration, AWS SaaS Boost hides away some of the underlying details of infrastructure configuration. While AWS offers a fairly granular set of options for selecting compute resources, it has narrowed this to a collection of general sizes that are meant to simplify the sizing experience. This gives you options for sizing the compute your environment without requiring you to understand the specific tradeoffs of all the different compute footprints. To set your size, select **Small**, **Medium**, **Large**, or **X-Large** as your compute size. These become the default options for all tenants that are deployed in your system. However, as you onboard individual tenants, you can override these settings, allowing tenants to have separate compute sizes for each tenant tier.
- Minimum/Maximum Instance Count - Instance counts are used to configure the default scaling profile of your tenant environments. The minimum represents the fewest number of instances that will be running for a tenant and the maximum represents the instance scaling limit for tenant environment. Again, these are the defaults for all tenants, but they can be overridden at the tenant level.
- Container OS - Select the operating system model that will be used to run your application. AWS SaaS Boost allows you to run and manage Linux or Windows-based applications. Windows applications that are built with .NET Core should be deployed in the Linux model. Framework-based .NET applications should be deployed with the Windows option. When you select the **Windows** option, you need to provide an additional piece of data. Select the variation of Windows that best aligns with the requirements of your application. The options appear as follows:

![Figure 9 - Selecting a Windows server](images/ug-windows.png?raw=true "Figure 9 - Selecting a Windows server")
<p align="center">Figure 9 - Selecting a Windows server</p>

- Container Port - The port that is used to access your application. This corresponds to the `EXPOSE` line in your application's Docker file.
- HealthCheck URL - This URL is essential to the auto scaling footprint of your application. You should provide a URL that can be used to determine if your application is up and running. Generally, this is represented by some HTML entry point that can return a 200 if your application is healthy and able to process requests.

Tenant context will be provided to your application at runtime. SaaS Boost will expose the  tenant's unique identifier (a GUID) as an environment variable called `TENANT_ID`. You can use this value to add tenant context to your logs, metrics, and other configuration settings.

### File System
The file system configuration is optional for AWS SaaS Boost. If your solution relies on a file system as part of its infrastructure footprint, then you'll want to enable this option. Enabling this option creates file-system resources for each tenant that is created by your system. The file system configuration experience will vary based on the operating system you've selected,

If you've selected Linux as your operating system, selecting the **Provision a file system for the application** check box will expand the page and show the list of configuration options for the file system. The page shows the following:

![Figure 10 - Configure a file system for the Linux operating system](images/ug-filesystem-efs.png?raw=true "Figure 10 - Configure a file system for the Linux operating system")
<p align="center">Figure 10 - Configure a file system for the Linux operating system</p>

The following options can be configured for your file system:
- Mount Point - Enter the path to mount point of the files that your application uses. This corresponds to the `VOLUME` line in your application's Docker file.
- Lifecycle - Amazon Elastic File System (Amazon EFS) lifecycle management automatically manages cost-effective file storage for your file systems. When enabled, lifecycle management migrates files that have not been accessed for a set period of time to the Infrequent Access (IA) storage class. You define that period of time by using a lifecycle policy. After lifecycle management moves a file into the IA storage class, the file remains there indefinitely. Amazon EFS lifecycle management uses an internal timer to track when a file was last accessed.
- Encrypt at rest - Check this box if you want your files to be encrypted at rest. 

If you've selected Windows as your operating system, the system will display a different set of configuration options (since Windows requires a different type of file system infrastructure). Selecting the **Provision a file system for the application** check box for a Windows operating system will show a page similar to one of the following based on whether you choose "FSx ONTAP" or "FSx Windows":

![Figure 11a - Configure a Netapp ONTAP FSx file system for the Windows operating system](images/ug-filesystem-fsx-ontap.png?raw=true "Figure 11a - Configure a Netapp ONTAP file system for the Windows operating system")
<p align="center">Figure 11a - Configure a Netapp ONTAP file system for the Windows operating system</p>

![Figure 11b - Configure a Windows FSx file system for the Windows operating system](images/ug-filesystem-fsx-windows.png?raw=true "Figure 11b - Configure a Windows FSx file system for the Windows operating system")
<p align="center">Figure 11b - Configure a Windows FSx file system for the Windows operating system</p>

The following options can be configured for your Netapp ONTAP FSx file system:
- Mount Point - Enter the path to mount point of the files that your application uses. This corresponds to the `VOLUME` line in your application's Docker file.
- Storage Capacity - Enter the size of the file system you want to provision.
- Throughput - Enter the level of throughput you want to be supported by your file system.
- Daily Backup Time - Provide the time of day the file system will be backed up each day.
- Drive Letter Assignment - Select the logical drive letter that will be used for the file system.
- Weekly Maintenance Day/Time - Provide the day/time when you want maintenance to be applied to your file system.
- Backup Retention - Enter the number of days a backup will be retained.
- Volume Size - Enter the size of the ONTAP Volume in GiB that you want to create.

The following options can be configured for your Windows FSx file system:
- Mount Point - Enter the path to mount point of the files that your application uses. This corresponds to the `VOLUME` line in your application's Docker file.
- Storage - Enter the size of the file system you want to provision.
- Throughput - Enter the level of throughput you want to be supported by your file system.
- Daily Backup Time - Provide the time of day the file system will be backed up each day.
- Drive Letter Assignment - Select the logical drive letter that will be used for the file system.
- Weekly Maintenance Day/Time - Provide the day/time when you want maintenance to be applied to your file system.
- Backup Retention - Enter the number of days a backup will be retained.

### Database
In many cases, the application you're moving into AWS SaaS Boost has a database as part of its infrastructure footprint. This portion of the application configuration experience is used to configure the database options that your tenants use.

The first step in configuring the database options is to select the **Provision a database for the application**. This expands the database options portion of the page similar to what is shown below.

![Figure 12 - Configure a database](images/ug-database.png?raw=true "Figure 12 - Configure a database")
<p align="center">Figure 12 - Configure a database</p>

The goal on this page is to select the type and size of the database that your application will use. The configuration you select here applies to each tenant that is deployed in your system. The database options, at this point, are focused on relational databases. 

The following options are configured when setting up a database in AWS SaaS Boost:
- Engine - Select the flavor of AWS relational database that you want to use for your application. The choice you make here is driven by the requirements of your application and, potentially, the features of the various AWS relational database engines.
- Instance - The Amazon Elastic Container Service (Amazon ECS) instance size you choose here is guided by the sizing profile of your system's storage needs. The engine you select here also influences the list of instance types that are presented.
- Version - In many cases, the database engine you select supports multiple versions. Select the version here that aligns with the profile of your application.
- Username - The username that you will use to access your database.
- Password - The password that you will use to access your database.
- Database Name (optional) - You can have AWS SaaS Boost create and initialize the state of your database. If you provide a name, a database with the provided name is created in your instance.
- Select File (optional) - As part of initializing your database, you can provide a file that is used to initialize the state of your database. This file is executed and is typically used to create the constructs and data that is needed for each new tenant that is onboarded to your system. When pressing the **Select File** button, you select and upload a file that is executed against your database. _Note this file must be UTF-8 encoded_.

SaaS Boost will securely expose the following environment variables to your application when you configure a database for your application. Use these values to create your database connection.
- DB_NAME
- DB_HOST
- DB_PORT
- DB_MASTER_USERNAME
- DB_MASTER_PASSWORD

### Billing
AWS SaaS Boost includes integration with Stripe for billing tenants for use of your SaaS applications (more detail in the billing section below). This integration requires SaaS providers to create an account with Stripe. As part of this process, you also need to get the API key from Stripe for your account. To enable this option, select the **Configure Billing Provider** check box to show the configuration options for Stripe.

![Figure 13 - Configure billing provider](images/ug-billing.png?raw=true "Figure 13 - Configure billing provider")
<p align="center">Figure 13 - Configure billing provider</p>

Enter the Stripe API key. This key will be used as data is metered from your application and sent to Stripe to generate tenant bills. When you configure your application for billing, SaaS Boost will securely expose an environment variable called `SAAS_BOOST_EVENT_BUS`. Use this EventBridge event bus to publish metering events from your application if you'd like to take advantage of consumption-based billing.

Your use of Stripe's services is not subject to your agreement with AWS. Your use of Stripe's services is subject to Stripe's legal terms, including Stripe's terms for processing your personal data, available here: https://stripe.com/legal. Note that all use of AWS Services is billed to the applicable AWS Account, in addition to any fees you incur for use of AWS SaaS Boost. You may review your use of AWS Services for each AWS Account under the AWS Console.

## Uploading a Sample Application
Now that you've configured your application settings, the next step in this process is to upload your application. As mentioned above, AWS SaaS Boost requires an application to be containerized before it can be published to the AWS SaaS Boost application repository.

To better understand how this works, let's go through the process of uploading a [simple sample application](../samples/java/README.md). The following steps outline the process of uploading a sample application to AWS SaaS Boost.

- Give the application a **Name**
- You can leave Compute Size set to **Small**
- Minimum and Maximum Instance Count can be **1** and **2** respectively
- Container OS is **Linux**
- Set the Container Port to **8080**
- Set the Health Check URL to **/index.html**
- **Enable** the Provision a File System for the application checkbox
- Set the Mount point to **/mnt**
- **Enable** the Provision a database for the application checkbox
- Select any of the available databases (MariaDB with a db.t3.micro instance class will provision the fastest)
- Enter a **Database Name**, **Username**, and **Password**. You _do not_ need to provide a SQL file for database initialization.

![Figure 14 - Configure the sample application](images/ug-sample-app.png?raw=true "Figure 14 - Configure the sample application")
<p align="center">Figure 14 - Configure the sample application</p>

Once you have saved your Application Setup, you can build and containerize the sample application. To create a Docker image of this sample application, you will need to have Docker running on your local machine. Navigate to the **samples/java/** directory in your clone of the AWS SaaS Boost repo and execute the build script which will build, containerize, and push your containerized application to the AWS SaaS Boost ECR repository. You can review the steps in this example shell script to see how you might enhance your current build process for your application to integrate with AWS SaaS Boost.

```shell
cd aws-saas-boost/samples/java
sh build.sh
```

This script prompts you for your AWS SaaS Boost environment. Enter the environment label that you specified when you ran the AWS SaaS Boost installer. When this script finishes, you have published your application to the AWS SaaS Boost application repository. To verify this, open the AWS Console, navigate to the Amazon ECR repositories, and verify that your application was uploaded. As you make changes to the application, you can execute the build script again to update your application.

The steps needed to containerize vary depending on the nature of your application. Here are some resources that provide information on containerizing applications:
- [AWS Learning Path: Containerize the Monolith](https://aws.amazon.com/getting-started/container-microservices-tutorial/module-one/)
- [AWS App2Container](https://aws.amazon.com/app2container/)

The sample application is compatible with PostgreSQL, MySQL/MariaDB, and SQL Server database engines. When the application initializes, it connects to the database using information provided to the Docker image as environment variables. SaaS Boost will set the following environment variables when you configure a database for your application:
- DB_NAME
- DB_HOST
- DB_PORT
- DB_MASTER_USERNAME
- DB_MASTER_PASSWORD

## Onboarding Tenants
The key elements are in place now. The application is configured and you've uploaded a containerized representation of your application. You're now ready to onboard tenants.

The goal of the onboarding process is to create an entirely automated, frictionless experience that configures all the resources that are needed to deploy a new tenant in your SaaS environment. The repeatability of this process is essential to the agility of your SaaS organization. Being able to onboard new tenants with minimum friction enables your organization to accelerate your ability to onboard new customers without incurring significant additional operational overhead.

AWS SaaS Boost offers two paths for onboarding tenants: self-service and internally managed. 

- With a self-service onboarding experience, your application introduces a sign-up flow where tenants can fill out and submit a registration form. In the scenario, your application integrates with the AWS SaaS Boost API, sending this registration information into the onboarding service.
- For an internally managed onboarding flow, use the AWS SaaS Boost administration application to fill out all the data needed to provision a new tenant and trigger the onboarding process. 

To access the built-in tenant onboarding experience, select the **Onboarding** entry from the navigation pane and the following page will appear:

![Figure 15 - Tenant Onboarding](images/ug-tenant-onboarding.png?raw=true "Figure 15 - Tenant Onboarding")
<p align="center">Figure 15 - Tenant Onboarding</p>

In this example, the list of tenants is empty. This because you have not yet provisioned a tenant. At the top right of this page is a Provision Tenant button. Select this button to provision a tenant with the sample application that you uploaded before. The following form appears.

![Figure 16 - Provision a Tenant](images/ug-tenant-provision.png?raw=true "Figure 16 - Provision a Tenant")
<p align="center">Figure 16 - Provision a Tenant</p>

To complete this form, provide the following settings:
- Tenant Name - The friendly name of the tenant (often the name of the business)
- Compute Size - The compute size you specified when you configured the application. The size that was provided at the application level represents the default size for all tenants. AWS SaaS Boost allows you to override this value on a tenant-by-tenant basis, optimizing the performance and scale for individual tenants. To alter the value for the tenant being deployed, select the **Override Application Defaults** box. This allows you to override compute size (as well and min/max instance counts). 
- Minimum/Maximum Instance Count - When you select **Override Application Defaults**, you can enter new minimum or maximum instance counts for the tenant being provisioned. The values you enter here should correlate to the tiers of your application. Use this tiering strategy to identify a set of minimum and maximum values that align with this tiering model. The goal here is to avoid having custom sizing for each tenant. Introducing too many sizing variations can undermine the operational agility you're targeting for your SaaS environment.
- Billing Plan - As your tenants are onboarded, associate them with a specific billing plan. AWS SaaS Boost has a preconfigured range of billing plans. Ultimately, you'll want to better align these billing plan with the options that best align with your business model. This is achieved by configuring your products in Stripe and mapping them to the plans that are managed within AWS SaaS Boost.

### Monitoring the Onboarding Process
After you've submitted a tenant for provisioning, the system allows you to monitor the status of the onboarding progress. If you select the **Onboarding** option from the navigation, the onboarding page provides a list of tenants that are being onboarded. This is true if the tenant is provisioned through the API or the AWS SaaS Boost administration application.

The image in Figure 17 provides an example of a page where tenants have been provisioned. The list includes detail on the provisioning status for each tenant.

![Figure 17 - Monitor onboarding status](images/ug-onboarding-status.png?raw=true "Figure 17 - Monitor onboarding status")
<p align="center">Figure 17 - Monitor onboarding status</p>

This example shows five tenants that have been provisioned. The status of the first four are shown as **Deployed**. This indicates that the tenant has been successfully provisioned. The final tenant in the list has a status of **Failed**. This should be a rare condition that could be triggered by different states of your environment or configuration. 

The provisioning process goes through multiple steps, each of which is reflected as a separate phase of the provisioning lifecycle. The states include created, provisioning, provisioned, deploying, and deployed.

![Figure 18 - View onboarding detail](images/ug-onboarding-detail.png?raw=true "Figure 18 - View onboarding detail")
<p align="center">Figure 18 - View onboarding detail</p>

When a tenant is fully provisioned, you can examine details about the provisioned tenant environment by selecting an individual tenant row from the list of provisioned tenants. The detail page is shown in Figure 18.

This detail view allows you to navigate directly to the tenant that was provisioned by selecting the link under **Tenant**. You can also access the AWS CloudFormation stack associated with this tenant by selecting the link under **Stack Id**.

## Managing Tenants
The onboarding flow described above is used to initially provision a tenant. Once that step is complete, it's unlikely that you'll return to the onboarding page (unless you're looking for specific tenant provisioning details). Instead, access any additional activity for tenants through the **Tenants** navigation on the left. 

![Figure 19 - Managing tenants](images/ug-tenant-management.png?raw=true "Figure 19 - Managing tenants")
<p align="center">Figure 19 - Managing tenants</p>

Selecting **Tenants** from the navigation pane presents you with a list of the tenants for your system. Figure 19 provides a sample of the tenant list.

This page includes a list of all the tenants in the system along with key attributes associated with each tenant. If you select a single tenant, a more detailed view of the tenant appears. The tenant detail view is shown in Figure 20.

Within this detail view, you'll see two distinct sections. The top half of the page contains more information about the state of the selected tenants. The two key attributes you'll want to pay attention to here are the subdomain the Load Balancer DNS. These attributes play a role in how tenants access their environments.

If you've supplied a subdomain, tenant uses this subdomain to access your SaaS application. The subdomain is combined with the domain that was setup with your application (see [Application Configuration](#application-configuration) above) to construct the URL for each tenant. If you, for example, provide a subdomain of `abccompany` and the domain you setup in the application was `my-saas.com`, then tenant URL would be `abccompany.my-saas.com`. If you setup another tenant as `xyzcompany`, their URL would be `xyzcompany.my-saas.com`.

![Figure 20 - Tenant detail](images/ug-tenant-detail.png?raw=true "Figure 20 - Tenant detail")
<p align="center">Figure 20 - Tenant detail</p>

In the scenario where you do not setup your domain and subdomain, you'd then need to refer to the Load Balancer DNS to get the URL to access a tenant's environment. This URL is available in both scenarios. However, if you're relying on the subdomain model, you don't need to rely on this Load Balancer DNS URL. Generally, this Load Balancer DNS URL would mostly be used as a way to access the system before you've configured your domain.

The bottom half of this page includes links to AWS resources that were provisioned for this tenant. This provides you with a simple way to navigate to specific tenant resources in the AWS Console.

In the tenant detail view you also have the option to disable/enable or edit individual tenants. The detail view also has an **Actions** button at the top right of the page. When you select this button, you'll are presented with a list of options.

Selecting the **edit** option from this **Actions** list opens a form (shown in Figure 21) that allows you to edit the data for a tenant. This includes modifying the tenant's name, description, and subdomain.

The **disable/enable** action for tenants is used to change whether a tenant is allowed to access the system. If a tenant is set to a **disabled** status, they are not allowed to login to the system until they have been re-enabled. This feature allows a SaaS provider to selectively determine which tenants are active and which are not.

![Figure 21 - Edit tenant](images/ug-tenant-edit.png?raw=true "Figure 21 - Edit tenant")
<p align="center">Figure 21 - Edit tenant</p>

The **delete** action is used to delete the tenant stack. __**This action is not reversible and all data will be lost**__. All deployed resources such as the database and the file system will be deleted. Before selecting the delete action, make sure you have backups for the resources in case you want to restore a tenant's data.

## Managing Users
The AWS SaaS Boost provisioning process created an initial user for your system. However, you'll probably want to introduce additional users to AWS SaaS Boost. To manage these users, select the **Users** link from the navigation pane at the left. This displays the current list of users as shown below. 

![Figure 22 - Manage users](images/ug-user-management.png?raw=true "Figure 22 - Manage users")
<p align="center">Figure 22 - Manage users</p>

The page displays all the key information about each user. At the top right of this page is a **Create User** button. Upon choosing this button, enter the data that is associated with a new user. The image in Figure 23 provides an example of the **User Details** form.

![Figure 23 - Edit user](images/ug-user-edit.png?raw=true "Figure 23 - Edit user")
<p align="center">Figure 23 - Edit user</p>

This form collects basic user information (username, first name, last name, and email address). It also includes a check box at the bottom of the form that is used to control how the new user is verified. 
- If the box is checked, the system treats the user as verified and does not send an email for verification. 
- If the box is not checked, the system sends a verification email to the user. When the user attempts to log in, they are required to create a new password.

If you select an individual item from the list of users, you are presented with a detail page for that user (shown in Figure 24). 

![Figure 24 - User details](images/ug-user-details.png?raw=true "Figure 24 - User details")
<p align="center">Figure 24 - User details</p>

From this **User Details** page, you can edit, delete, and disable or enable individual users. These options are all invoked from the **Actions** button at the top right of the page. Choosing Edit displays same form shown in Figure 23. 

Choosing the **Enable** and **Disable** options from the **Actions** list causes the user to be activated or deactivated. This allows you to selectively disable a user's access to the system without deleting the user. 

Finally, this same menu includes a **Delete** option that, when selected, deletes the selected user from the system.

## Operational Insights
Operations are an essential aspect of any SaaS environment. With AWS SaaS Boost, the goal is to provide a built-in operations experience that can allow SaaS providers to have a single pane of glass that collects key insights about tenant activity. 

There are two basic goals for operational insights in AWS SaaS Boost. First, these metrics must be able to be surfaced without requiring any additional instrumentation. While this may narrow the scope of what data is available, it also ensures that SaaS providers are equipped with ready-to-consume insights that are aligned with the lift-and-shift model of AWS SaaS Boost.

The other goal of AWS SaaS Boost operational insights is to remain focused on offering insights that are focused on multi-tenant operational scenarios. Instead of trying to be the single dashboard for all system insights, AWS SaaS Boost creates just those visualizations that are focused on assessing tenant trends and activity.

To access the operational insights, open the Dashboard list from the navigation pane. This displays three categories of visualization (requests, compute, and usage). Each of these entries corresponds to data visualizations that can be used to analyze activity in your SaaS system. 

At the top of each data visualization page, you'll see a set of filters that are common to all of the analytics views. Figure 25 provides a snapshot of the Requests view within the dashboard page.

![Figure 25 - Graph filtering options](images/ug-dashboard-home.png?raw=true "Figure 25 - Graph filtering options")
<p align="center">Figure 25 - Graph filtering options</p>

- The dropdown on the left of this Requests page is used to filter the charts on the page by tenant. When you select an individual tenant from the dropdown, all of the graphs are scoped to that tenants. 
- The same is true for the time period at the top right of this page. It is used to adjust the window of time that is reflected in each of the graphs on the page.

### Request Analytics
The first set of graphs provide data on request activity. The general theme here is to look at the nature of the requests coming into your system and be able to assess the activity and potential error conditions that are being generated across the system or through the lens of individual tenants.

The graphs on **Requests** page are broken into two types. The left-hand side of the page is focused on identifying trends of tenants and allowing you to better understand the patterns of tenant activity over time. The right-hand side of the page is more about identity specific totals of counts by tenant. The sections that follow cover the various request-based graphs.

#### Request Count
The graph at the top left of the **Requests** page is used to generally profile the trends related to tenant activity. The goal of this chart is to categorize the request volume across a window of time. The graph in Figure 26 provides a sample of this activity.

![Figure 26 - Percentile request count](images/ug-dashboard-chart-p90.png?raw=true "Figure 26 - Percentile request count")
<p align="center">Figure 26 - Percentile request count</p>

Here we have a three-hour window of time during which we received requests. Those requests have been categorized and rendered as separate lines in this graph. These three lines correspond the P90, P70, and P50 show in the legend at the top of the graph.

These graphs enable you to find key boundaries that can classify request activity. The data shown in the graph is using percentiles to classify activity. P50 indicates that 50% of the tenants had a request profile below this curve. P70 is for 70% and P90 is for 90%. For example, P90 is calculated by sorting the data points and throwing out the bottom 90%. In essence, it means 10% of the tenants are operating above this level. This allows you to see both the common case for requests as well as the outliers, both of which are helpful in classifying and analyzing tenant load profiles. If the P90 is very low, it would mean that your tenants are not very active.

You can filter this view by selecting items in the legend. When you select one of the legend items, it toggles the visibility of a given percentile. This allows you to filter the view in the graph based one or more percentiles.

#### Requests - Top Tenant
In addition to seeing global trends across all tenants, the system also shows you a breakdown of the most active tenants. This view shows the request activity of individual tenants (or at least those that are most active). Figure 27 provides an example of the request count graph.

![Figure 27 - Tenant request count](images/ug-dashboard-chart-count.png?raw=true "Figure 27 - Tenant request count")
<p align="center">Figure 27 - Tenant request count</p>

This graph represents three tenants with relatively even distribution. However, you can imagine having the top 10 tenants here with widely varying loads. This would give you quick information about whether a particular set of tenants are putting excess load on the system.

#### Request Failures (4XX and 5XX)
Being able to see trends in error conditions is also valuable. Here, you'll see trending data (on the left) and tenant counts (on the right) that correlate to HTTP 4XX and 5XX errors events.

This follows the pattern shown in Figure 26 where the P50, P70, and P90 percentiles are used to categorize the error conditions. Meanwhile the counts give you a clearer view of which specific tenants may be seeing more of these errors.

### Compute Analytics
Compute analytics are used to give you a better sense of how tenants are exercising the compute load placed on your multi-tenant environment. With AWS SaaS Boost, you can create some specific tenant-aware visualizations that help you identify potential scaling or compute bottlenecks.

The breakdown of compute trends is focused on CPU and memory at this point. You'll see a graph on the left that illustrated the general consumption trends and a graph on the right that illustrates specific tenant consumption patterns.

#### CPU Utilization
When you select **Compute** option from the **Dashboard** menu, you will be presented with a colletion of compute related graphs. CPU utilization is represented with another P50, P70, P90 graph that shows the CPU consumption over a window of time. Each of these three lines gives you a sense of how CPU consumption is distributed across your tenants. Are most tenants pressing the CPU or are most under consumed? These are questions you'll be able to answer from the CPU utilization graph that could influence the sizing of your application and tenant environments. Figure 28 provides an example of a CPU utilization graph. 

The CPU utilization also includes a graph that illustrates the tenants that are the top consumers of CPU resources. These two graphs provide a macro level view into which tenants are placing load on the CPU in specific windows of time.

![Figure 28 - Compute utilization trend](images/ug-dashboard-trend.png?raw=true "Figure 28 - Compute utilization trend")
<p align="center">Figure 28 - Compute utilization trend</p>

#### Memory Utilization
Memory utilization mirrors the same experience that was shown above with CPU utilization. The only difference here is that you're looking at a different compute-related metric. The data from memory utilization and tenant trends can help you better assess any correlation in consumption that may be causing bottlenecks across both resource types.

### Usage Analytics
As a SaaS provider, you want to have some sense of how tenants are consuming the specific features and functions in your application. AWS SaaS Boost uses the access logs of your application to derive these usage analytics.

The general approach here is to examine the various URL paths that tenants access and summarize the consumption of these entry points to your application. This approach assumes that there is some natural correlation between the paths being accessed and the features of your application.

This data is broken out by request types, illustrating the distribution of the top paths being accessed by tenants. The graph in Figure 29 provides a view of the request counts by path.

![Figure 29 - Request count by endpoint](images/ug-dashboard-endpoints.png?raw=true "Figure 29 - Request count by endpoint")
<p align="center">Figure 29 - Request count by endpoint</p>

## Metrics and Analytics
SaaS providers generally need to collect a broad spectrum of analytics that span business and technical scenarios. The metrics and analytics tend to be specific to an application or domain.

To support a broader notion of metrics and analytics, AWS SaaS Boost includes a metrics and analytics module that is optionally installed. This module introduces an API, metric aggregation, and BI tooling that enables SaaS providers to build their own views of metrics that are essential to their business and technical teams.

This metrics module equips your team with the basic infrastructure it needs to support this analytics experience. However, with this more generalized approach, it becomes your responsibility to publish the metrics that you want to aggregate and analyze.

### Metrics Architecture
Before looking at how you'll instrument your application with metrics, let's first take a quick look at the high-level metrics architecture. The image in Figure 30 provides a conceptual view of the core elements of the metrics solution:

![Figure 30 - Metrics and analytics architecture](images/ug-analytics-arch.png?raw=true "Figure 30 - Metrics and analytics architecture")
<p align="center">Figure 30 - Metrics and analytics architecture</p>

This metrics architecture represents a commonly used analytics pattern. In this model, your application publishes a metric event to the AWS SaaS Boost Metrics API. The metrics are aggregated and stored in an Amazon Redshift database. Once the data is ingested, your business and technical teams can use Amazon QuickSight to build dashboards that surface any metrics that are essential analyzing trends in your SaaS environment.

The goal here is to publish any and all metrics that add value to this centralized repository. Then, your product managers, operations teams, architects, and business leaders can build the dashboards that best align their needs.

### Metrics Instrumentation
Now that you have a better sense for how metrics are represented, let's look at how you publish metrics from your application. The general flow here requires a developer to populate a standardized metrics object, then call an API that publishes the metric.

> **Note**: The examples covered here happen to reference a Java API that was created as the metrics API for AWS SaaS Boost. This API simplifies the metrics publishing process. However, you can still populate and publish a JSON representation of the metric events from the language of your choice.

The first step in your process is to add the metrics API to your environment. This can be achieved via the following Maven dependency. The dependency appears as follows:
```xml
<dependency>
    <groupId>com.amazonaws.saas</groupId>
    <artifactId>metrics-java-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Once you've included this dependency, you can then use the METRICS_STREAM environment variable to get the stream name that is needed to publish your metrics. This line of code (in Java) would appear as follows:
```java
private final String streamName = System.getenv("METRICS_STREAM");
```
Finally, with these pieces in place, you can then focus on how and where you'd like to add metrics to your system. In reality, it's likely that you'll use other language-specific frameworks and tooling to capture the various types of metrics you want to surface. Some metrics may be time based, others may be counts-the capture and publishing of each of these can influence how they are introduced into your code.

Now, if you look more closely that the metrics logging API, you'll see that the metrics object can include a number of pieces, including tenant context. The sample below provides some insight into the population of a sample metrics object.
```java
MetricEventLogger logger = MetricEventLogger.getLoggerFor(streamName, Region.US_EAST_1);
MetricEvent event = new MetricEventBuilder()
        .withType(MetricEvent.Type.Application)
        .withWorkload("AuthApp")
        .withContext("Login")
        .withMetric(new MetricBuilder()
                .withName("ExecutionTime")
                .withUnit("msec")
                .withValue(1000L)
                .build()
        )
        .withTenant(new TenantBuilder()
                .withId("123")
                .withName("ABC")
                .withTier("Free")
                .build())
        .addMetaData("user", "111")
        .addMetaData("resource", "s3")
        .build();
logger.log(event);
```
Here you'll notice that the AWS SaaS Boost code supplies various parameters that are used to classify the metrics. This extra data is essential to enabling more detailed analytics of your tenant activity.

While the expressiveness of the metrics API enables richer data, including calls of this size directly in your code would be invasive to the functionality and readability of your code. The expectation is that you would create wrappers/helper that would hide away most of the detail shown here. Instead, the much of the context can be inferred from the state of the code being executed. Ideally, each metric call (when wrapped) would fit on a single line in your environment.

### Building Dashboards
The data aggregated by the metrics and analytics module of AWS SaaS Boost can be surfaced through any BI tool that can sit on top of Amazon Redshift. To simplify your path to dashboards, AWS has setup Amazon QuickSight.

As you begin publishing data, you can access QuickSight from the AWS Console and begin creating the analytics views that help you analyze the business and technical trends of your SaaS environment.

## Billing and Metering
As you move into a SaaS model, you may also want to think about how or if you will adopt new pricing and billing models for your application. This often moving from traditional contract-based licensing to more subscription and/or consumption-based pricing models.

To support this transition, AWS SaaS Boost has created a billing integration with Stripe that allows SaaS providers to streamline their integration with a billing system. Stripe is amongst one of many billing providers that support SaaS billing construct. The goal was to provide an initial integration with the assumption that alternate options could be offered in the future.

### Creating a Stripe Account
Before you can use the AWS SaaS Boost billing, you must first create an account with Stripe. Access the Stripe sign-up process at the following link: https://dashboard.stripe.com/register

For running AWS SaaS Boost in production environments, you will want to create a restricted API key in Stripe and configure that key in SaaS Boost. The restricted key should allow minimum permissions. The integration with SaaS Boost needs write permissions for invoices, plans, usage records, products, subscriptions, and customers at a minimum. You will want to limit the permissions based on your integration requirements. Please refer to the Stripe documentation for more information on restricted API keys https://stripe.com/docs/keys.

For testing and development environments of AWS SaaS Boost, after creating the account in Stripe, access the API key for your Stripe integration by selecting the Secret key from the Stripe navigation pane. This provides a page similar to the image shown below.

![Figure 31 - Stripe](images/ug-stripe.png?raw=true "Figure 31 - Stripe")
<p align="center">Figure 31 - Stripe API Key</p>

When you access the page, you can view the API keys that are published for this account (shown in Figure 31). Copy the Secret or restricted key from this page and return to the AWS SaaS Boost environment to set up your billing experience.

To configure billing in AWS SaaS Boost, log in to the administration application and select **Application** from the navigation pane on the left. At the bottom of this page, you'll see the **Billing** panel. Select the **Configure Billing Provider** check box to expand this panel. Figure 32 provide a view of this billing configuration experience.

![Figure 32 - Configure Stripe API Key](images/ug-billing-config-api-key.png?raw=true "Figure 32 - Configure Stripe API Key")
<p align="center">Figure 32 - Configure Stripe API Key</p>

Paste the key from Stripe into this text box. This key is used to invoke Stripe APIs from the AWS SaaS Boost billing service.

### Configuring Products
As part of setting up any billing system, you must also determine what billing model will be used for your SaaS application. Each billing provider's approach to defining and referencing these billing plans can vary significantly.

Stripe uses the notion of "products" to describe the different models that are used to define the billing constructs of your system. Each time your application wants to record a metering event, it must include the product that is associated with that event.

To simplify this experience, AWS SaaS Boost has pre-defined a set of plans that can be used by your application. These plans have specific Stripe product configurations. If you want different plans or different product configurations, you would need to change this data in AWS SaaS Boost.

As each metering event is sent from AWS SaaS Boost to Stripe, the system uses the AWS SaaS Boost plan configuration data to determine the tenant's plan and resolve that the to a corresponding Stripe product configuration (in AWS SaaS Boost).

### Creating Stripe Customers (Tenants)
As new tenants are onboarded via AWS SaaS Boost, each new tenant must have an account created within Stripe. The creation of these customers is handled for you as part of the AWS SaaS Boost onboarding experience. 

If you return to the onboarding experience and examine the form that is used to onboard tenants, you can now see how the Stripe integration fits with the overall onboarding flow. The image in Figure 33 provides a snapshot of the form that is used to add a new tenant to the system.

![Figure 33 - Select a billing plan](images/ug-billing-plan.png?raw=true "Figure 33 - Select a billing plan")
<p align="center">Figure 33 - Select a billing plan</p>

At the bottom of this form, there is an option to select a **Billing Plan**. This list of plans is populated from the AWS SaaS Boost database that configures plans and correlates them to Stripe products. If you add new plans to this configuration, they would appear here.

This onboarding could also be triggered by an API call to AWS SaaS Boost. This API call would accept the tenant plan and provision the Stripe customer using the same underlying mechanisms that are used to provision tenants via the AWS SaaS Boost administration application.

In this model, a SaaS provider would have its own onboarding experience in its application that would collect the registration data, select a plan, and submit that data to the onboarding API The remainder of the onboarding experience would still surface in the AWS SaaS Boost administration application as described above. More specifically, the status of each onboarding tenant could be observed through the onboarding page of AWS SaaS Boost.

### Metering Instrumentation
The last piece of the billing puzzle is instrumentation. For billing to work, you'll need to introduce code into your application that publishes metering data to Stripe that is then used to calculate each tenant's bill. 

What you meter, of course, varies significantly based on the billing model of your SaaS offering. Some providers charge a flat subscription fee, some will meter based on consumption, and some will use a combination of plans. This reality means that the metering model of each application is going to be different. It is recommended that you familiarize yourself with the metering and billing options that are available with Stripe and identify an approach that best fits with the business model of your application.

While the metering approach will vary across environments, you can still look at the basics of how metering calls would get introduced into your application. Let's start by looking at a helper function that is used to publish metering events. As a general rule of thumb, you should make publishing of these metering events as non-invasive as possible. The code sample below provides an example of a `putMeteringEvent` function that streamlines the publishing of metering events.
```java
private void putMeteringEvent(String eventBridgeDetail) {
    try {
        final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
        if (SAAS_BOOST_EVENT_BUS == null || SAAS_BOOST_EVENT_BUS.isEmpty()) {
            throw new RuntimeException("Undefined SAAS_BOOST_EVENT_BUS");
        }

        EventBridgeClient eventBridgeClient = null;
        try {
            eventBridgeClient = EventBridgeClient.builder()
                    .credentialsProvider(ContainerCredentialsProvider.builder().build())
                    .build();
        } catch (Exception e) {
            eventBridgeClient = EventBridgeClient.builder()
                    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                    .build();
        }

        PutEventsRequestEntry systemApiCallEvent = PutEventsRequestEntry.builder()
                .eventBusName(SAAS_BOOST_EVENT_BUS)
                .detailType("BILLING")
                .source("saas-boost-" + tenantId)
                .detail(eventBridgeDetail)
                .build();
        PutEventsResponse eventBridgeResponse = eventBridgeClient.putEvents(r -> r
                .entries(systemApiCallEvent)
        );
        for (PutEventsResultEntry entry : eventBridgeResponse.entries()) {
            if (entry.eventId() != null && !entry.eventId().isEmpty()) {
                System.out.println("Put event success " + entry.toString() + " " + systemApiCallEvent.toString());
            } else {
                System.err.println("Put event failed " + entry.toString());
            }
        }
    } catch (SdkServiceException eventBridgeError) {
        // LOGGER.error("events::PutEvents");
        // LOGGER.error(getFullStackTrace(eventBridgeError));
        throw eventBridgeError;
    }
}
```
This function sends a metering event to Amazon EventBridge, which accepts and processes each metering events. As a helper, this function attempts to hide away all the details associated with publishing a message to EventBridge.

You'll notice that the function accepts an `eventBridgeDetail` object that is populated with the metering signature that will be published to the billing system. It then validates that AWS SaaS Boost metering is enabled before constructing `EventBridgeClient` object.

Once the client is initialized, the code creates a `PutEventsRequestEntry` object and populates it with the settings to construct a valid metering object, including the billing details payload that includes the body of a given metering event.

The last step in the process is to actually put the metering event to the EventBridge, calling the `eventBridgeClient.putEvents()` function. The remainder of the function is all about evaluating the response and processing any errors.

Now that you have this helper in place, you can look at how this helper would be called from your application to publish a metering event. The following snippet of code provides an example of an application calling the `putMeteringEvent` function.
```java
public String meterEvent(@RequestParam String count, @RequestParam String productCode, Map<String, Object> model) {
    if (tenantId == null || tenantId.isEmpty()) {
        tenantId = "Unknown";
    }

    // productCode is the internal product code. For each tenant, the DDB billing
    // table has a config item to map internal product code to the Billing system
    // subscription item.
    if (productCode == null) {
        productCode = "product_requests";
    }
    int meterVal = Integer.valueOf(count);
    long startTimeMillis = System.currentTimeMillis();

    try {
        // Create the body of the metering event
        ObjectNode systemApiRequest = MAPPER.createObjectNode();
        systemApiRequest.put(TENANT_ID, tenantId);
        systemApiRequest.put(PRODUCT_CODE, productCode);
        systemApiRequest.put(QUANTITY, meterVal);
        systemApiRequest.put(TIMESTAMP, Instant.now().toEpochMilli()); // epoch time in UTC

        // Use the metering API helper to publish this event
        putMeteringEvent(MAPPER.writeValueAsString(systemApiRequest));
        
        model.put("product", productCode);
        model.put("result", "Success");
    } catch (JsonProcessingException ioe) {
        //LOGGER.error("JSON processing failed");
        //LOGGER.error(getFullStackTrace(ioe));
        model.put("result", "Error: " + ioe.getMessage());
        throw new RuntimeException(ioe);
    }

    long totalTimeMillis = System.currentTimeMillis() - startTimeMillis;
    model.put("executionTime", (totalTimeMillis / 1000));
    return "meter";
}
```
This function acquires all the context needed to publishing the map of billing detail properties before calling the `putMeteringEvent` helper function described above.
