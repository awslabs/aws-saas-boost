# AWS SaaS Boost Getting Started Guide

## Contents
[Introduction](#introduction)\
[The Target Experience](#the-target-experience)\
[Step 1 - Set Up Tooling](#step-1---set-up-tooling)\
[Step 2 - Clone the AWS SaaS Boost Repository](#step-2---clone-the-aws-saas-boost-repository)\
[Step 3 - Provision AWS SaaS Boost into your AWS Account](#step-3---provision-aws-saas-boost-into-your-aws-account)\
[Step 4 - Login to AWS SaaS Boost](#step-4---login-to-aws-saas-boost)\
[Step 5 - Configure Tiers and Application Settings](#step-5---configure-tiers-and-application-settings)\
[Step 6 - Upload Your Application Services](#step-6---upload-your-application-services)\
[Step 7 - (Optional) Deploy the Sample Application](#step-7---optional-deploy-the-sample-application)\
[Mapping Your Application to AWS SaaS Boost](#mapping-your-application-to-aws-saas-boost)

## Introduction
This document describes the basic steps to install, configure, and start running a workload in AWS SaaS Boost. Refer to the other AWS SaaS Boost documents to get a more comprehensive view of the system.

While this document provides an overview of the steps to setup AWS SaaS Boost, it is not intended to provide a deep dive into the user experience or underlying technology. These details are covered in the [User Guide](user-guide.md) and [Developer Guide](developer-guide.md). 

## The Target Experience
Before digging into the steps needed to set up AWS SaaS Boost, let's look at the basic elements of the environment to get a better sense of what AWS SaaS Boost enables. The diagram below shows the key components of the AWS SaaS Boost experience in the order of the setup flow.

![Setup Flow](images/gs-install-flow.png?raw=true "Setup Flow")

The following is a breakdown of each of the steps:
1. Set up the required tooling for the install process
2. Clone the AWS SaaS Boost repository
3. Provision AWS SaaS Boost into your AWS Account
4. Access the AWS SaaS Boost administration web application
5. Configure the tiers and application settings for your requirements
6. Upload Docker images for each of the services in your application

At this stage, all the moving parts of the environment are now in place to begin onboarding tenants. You can use the AWS SaaS Boost application to onboard new tenants, or you can make API calls from your application to trigger the onboarding of new tenants.

The last piece to consider is the update process for your application. As changes are introduced into your application, you can upload the latest version to AWS SaaS Boost. Whenever a new version is uploaded it will be automatically deployed to all the tenants in your system. AWS SaaS Boost now has you ready to operate as a SaaS business. This includes operational and management tooling that's built into the AWS SaaS Boost environment.

Now that you have a feel for the AWS SaaS Boost technology and experience, let's look at the details that are involved in setting up AWS SaaS Boost.

## Step 1 - Set Up Tooling
AWS SaaS Boost uses a handful of technologies for the installation process. Install and configure each of these prerequisites for your operating system if they aren't already installed:
- Java 11 [Amazon Corretto 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)
- [Apache Maven](https://maven.apache.org/download.cgi) (see [Installation Instructions](https://maven.apache.org/install.html))
- [AWS Command Line Interface version 2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- [Git](https://git-scm.com/downloads)
- [Node 14 (LTS)](https://nodejs.org/download/release/latest-v14.x/)
- [Yarn](https://classic.yarnpkg.com/en/docs/install)

If you are unable to or would prefer not to install all of the prerequisites on your local machine, you can use an AWS Cloud9 instance to install AWS SaaS Boost. Follow the steps in [Install using AWS Cloud9](./install-using-cloud9.md) and then continue with [Step 3](#step-3---provision-aws-saas-boost).

## Step 2 - Clone the AWS SaaS Boost Repository
With the tooling in place, you can now download the code and installation scripts for AWS SaaS Boost. Open your terminal window and navigate to the directory where you want to store the AWS SaaS Boost assets. Issue the following command to clone the AWS SaaS Boost repository:

`git clone https://github.com/awslabs/aws-saas-boost ./aws-saas-boost`

## Step 3 - Provision AWS SaaS Boost into your AWS Account 
Now that you have the code, AWS SaaS Boost needs to be installed in an AWS account that you own and manage. The installation process executes scripts that provision and configure all the resources that are needed to set up AWS SaaS Boost. The system where you run the installation should have at least 4 GB of memory and high speed Internet access.

Before running the installation, [setup your AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html):
1. Setup an IAM user in your AWS account with full admin permissions.
2. Set up your AWS CLI credentials with an AWS Access Key and default region.

To start the installation process, perform the following steps:
1. From the terminal window, navigate to the directory where you've downloaded AWS SaaS Boost (aws-saas-boost).
2. Invoke the install command. 
      - If you're running on Linux or OSX, run: `sh ./install.sh`
      - If you're running on Windows, run: `powershell .\install.ps1`
3. Select the option for a new installation.
4. Enter the full path to your AWS SaaS Boost directory (hit enter for the current directory): /\<mydir\>/aws-saas-boost.
5. Enter the name for this SaaS Boost environment (dev, QA, test, sandbox, etc.).
6. Enter the email address of the AWS SaaS Boost administrator who will receive the initial temporary password.
7. Indicate whether you would like the metrics and analytics features of AWS SaaS Boost to be installed. This is ***optional*** and will provision a [Redshift](https://aws.amazon.com/redshift) cluster.
      - If you enter **Y**, you are prompted for [QuickSight](https://aws.amazon.com/quicksight/) setup. To use Quicksight, _you must have already registered_ for a Standard or Enterprise Quicksight account in your AWS Account by following the steps at [https://docs.aws.amazon.com/quicksight/latest/user/signing-up.html](https://docs.aws.amazon.com/quicksight/latest/user/signing-up.html).
8. If your application is Windows based and needs a shared file system, a [Managed Active Directory](https://aws.amazon.com/directoryservice/) must be deployed to support [Amazon FSx for Windows File Server](https://aws.amazon.com/fsx/windows/) or [Amazon FSx for NetApp ONTAP](https://aws.amazon.com/fsx/netapp-ontap/). Select y or n as needed.
9. Review the settings for your installation. Double check the AWS account number and AWS Region you're about to install AWS SaaS Boost into. Enter **y** to proceed or **n** to re-enter or adjust the values.

The installation process will take 30-45 minutes to configure and provision all the resources (this will vary based on the options you've selected). Detailed logs from the installation process are stored in **saas-boost-install.log**. 

## Step 4 - Login to AWS SaaS Boost
As part of the installation process you will receive a message at the email address you provided during the installation process. This message includes a link with a URL to the AWS SaaS Boost administration application. The message appears as follows:

![Welcome Email](images/gs-welcome-email.png?raw=true "Welcome Email")

Copy the temporary password that is shown here. Once the install script completes, it will also print out the same URL of the AWS SaaS Boost administration application. Use your web browser to navigate to the admin application. The following login appears:

![Login Screen](images/gs-login.png?raw=true "Login Screen")

Enter `admin` for the user name and enter the temporary password that was delivered in the email referenced earlier. Since you're logging in with a temporary password, you're prompted to enter a new password for your account. The screen appears as follows:

![Change Password](images/gs-change-password.png?raw=true "Change Password")

## Step 5 - Configure Tiers and Application Settings
After you've logged in to the AWS SaaS Boost administration application, the first thing you need to do is configure the environment to align it with the needs of your application.

AWS SaaS Boost supports configuring your application settings based on _tiers_. Tiers allow you to package your SaaS offering for different segments of customers. For example, you may have a trial tier, or a premium tier in addition to your standard tier offering. AWS SaaS Boost creates a ***default*** tier for you. If you'd like to rename the default tier, or create additional tiers, select **Tiers** from the navigation on the left side of the screen. A page appears similar to the following:

![Application Setup](images/gs-tiers.png?raw=true "Tiers")

Click on the **Create Tier** button to make new tiers or click on the tier name in the table listing to edit existing tiers. For convienience, newly created tiers will initially inherit the settings of the __default__ tier. Refer to the User Guide for more details on how you can leverage tiers to optimize your SaaS application delivery.

Now that your tiers are defined, you need to configure the settings for your application and its services. Select **Application** from the navigation on the left side of the screen. A page appears similar to the following:

![Application Setup](images/gs-app-setup.png?raw=true "Application Setup")

Start by giving your application a friendly **Name**. You do not need to fill out the **Domain Name** or **SSL Certificate** entries for testing. In production, make sure you have a certificate defined in [Amazon Certificate Manager](https://aws.amazon.com/certificate-manager/) for the domain name you will host your SaaS application at. Note that [registering a domain name](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/domain-register.html) and [setting up SSL certificates](https://docs.aws.amazon.com/acm/latest/userguide/gs.html) must be done prior to configuring AWS SaaS Boost.

AWS SaaS Boost lets you configure as many "services" as necessary to support your workload. You provide a separate Docker image for each of your services. These services can be public or private and are configured independently of each other. Services do not share resources like file systems or databases, but they can communicate with each other inside the provisioned VPC network. Publically accessible services are exposed via the Application Load Balancer and reachable via DNS over the Internet. Private services are not reachable from the Internet. Refer to the Developer and User Guides for a deeper dive on how to use services to best represent your SaaS application.

Create your first service by click on the **New Service** button. A popup dialog similar to the following will appear:

![Application Setup](images/gs-new-service.png?raw=true "New Service")

In this example, I've called my service `main`. After clicking the **Create Service** button in the popup dialog, the new service will appear in the list of services similar to the following:

![Application Setup](images/gs-service-collapsed.png?raw=true "Service Config Collapsed")

Click on the service name to expand the configuration options for this service. A form similar to the following will appear:

![Application Setup](images/gs-service-config.png?raw=true "Service Config")

While this _Getting Started Guide_ doesn't document every field in the configuration screens, the options you choose are essential to getting your application working.

## Step 6 - Upload Your Application Services
Once you've configured each of your services for every tier you've defined, SaaS Boost will automatically create an [Amazon ECR](https://docs.aws.amazon.com/AmazonECR/latest/userguide/what-is-ecr.html) image repository for each service. Before you can onboard any tenants into your system, you must upload a Docker image for each of the services in your application. From the **Summary** page, click the **View details** link next to the `ECR Repository URL` listing for each service to see the proper Docker push commands to upload your image. You can also refer to the build shell scripts included with the sample apps to see one approach to automating the Docker push process.

## Step 7 - (Optional) Deploy the Sample Application
This section goes through the process of uploading a sample application to give you a feel for how this process works. We've provided a simple example application as part of the AWS SaaS Boost repo.

### Configure AWS SaaS Boost for the Sample Application
Just as you would configure AWS SaaS Boost to support the requirements of your application, we must configure the application settings properly for this sample app. This sample application relies on the following configuration in AWS SaaS Boost.
- Enter a friendly name `Name` for the application such as **Sample**
- Create a `New Service` and give it a name such as **main**
- Make sure the `Publicly accessible` box is **checked**
- Make sure the `Service Addressable Path` is **/\***
- For `Container OS`, select **Linux**
- Set the `Container Tag` to **latest**
- Set the `Container Port` to **8080**
- Set the `Health Check URL` to **/index.html**
- Under the `default` Tier settings, set `Compute Size` to **Medium**
- `Minimum Instance Count` and `Maximum Instance Count` can be **1** and **2** respectively
- **Enable** the `Provision a File System for the application` checkbox
- Set the `Mount point` to **/mnt**
- **Enable** the `Provision a database for the application` checkbox
- Select any of the available databases (MariaDB with a db.t3.micro instance class will provision the fastest)
- Enter a **Database Name**, **Username**, and **Password**. You _do not_ need to provide a SQL file for database initialization.

Your config should look similar to the following:

![Sample App Config](images/gs-sample-app-config.png?raw=true "Sample App Config")

### Build and Deploy the Sample Application
Once you have saved your Application Setup, you can build and containerize the sample application. To create a Docker image of this sample application, you will need to have Docker running on your local machine. Navigate to the **samples/java/** directory in your clone of the AWS SaaS Boost repo and execute the build script which will build, containerize, and push your containerized application to the AWS SaaS Boost ECR repository. You can review the steps in this example shell script to see how you might enhance your current build process for your application to integrate with AWS SaaS Boost.

```shell
cd aws-saas-boost/samples/java
sh build.sh
```

This script prompts you for your AWS SaaS Boost environment. Enter the environment label that you specified when you ran the AWS SaaS Boost installer. The script will then prompt you for the name of the service you're building. In this example, use `main`. Note that service names are case-sensitive. When this script finishes, you have published your application service to the AWS SaaS Boost application repository. As you make changes to the application, you can execute the build script again to update your application.

For multi service applications, you'd repeat the same steps for each service you configure.

### Onboard a Tenant
You can now onboard a new tenant which will provision all the necessary infrastructure to support your applicaiton and deploy all of the services you've configured. Navigate to the **Onboarding** page of the administration application and click the **Provision Tenant** button. Once the onboarding process completes, you'll be able to access that tenant's instance of the sample application by navigating to the **Tenants** page of the administration application, then go to the tenant detail page, and finally clicking on the **Load Balancer DNS** link.

## Mapping Your Application to AWS SaaS Boost
This guide provides a high-level view into the basic steps that are required to set up AWS SaaS Boost. As you think about moving a workload into AWS SaaS Boost, examine the broader capabilities of AWS SaaS Boost in more detail. This means looking more carefully at the configuration options for your application and how they map to the different AWS SaaS Boost application settings.

After familiarizing yourself with the overall experience, you will have a better sense about how your application fits in the AWS SaaS Boost model. A review of the AWS SaaS Boost [User Guide](user-guide.md) and [Developer Guide](developer-guide.md) will also allow you to better understand how you might configure AWS SaaS Boost to align with the needs of your solution.

The steps needed to containerize your workload vary depending on the nature of your application. You can use the Dockerfile examples in the sample apps provided with AWS SaaS Boost. Here are some additional resources that provide information on containerizing applications:
- [AWS Learning Path: Containerize the Monolith](https://aws.amazon.com/getting-started/container-microservices-tutorial/module-one/)
- [AWS App2Container](https://aws.amazon.com/app2container/)
