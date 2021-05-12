# AWS SaaS Boost Getting Started Guide

## Contents
[Introduction](#introduction)\
[The Target Experience](#the-target-experience)\
[Step 1 - Set Up Tooling](#step-1---set-up-tooling)\
[Step 2 - Clone the AWS SaaS Boost Repository](#step-2---clone-the-aws-saas-boost-repository)\
[Step 3 - Provision AWS SaaS Boost](#step-3---provision-aws-saas-boost)\
[Step 4 - Login to AWS SaaS Boost](#step-4---login-to-aws-saas-boost)\
[Step 5 - Configure Application Settings](#step-5---configure-application-settings)\
[Step 6 - Upload Your Application](#step-6---upload-your-application)\
[Step 7 - (Optional) Deploy the Sample Application](#step-7---optional-deploy-the-sample-application)\
[Mapping Your Application to AWS SaaS Boost](#mapping-your-application-to-aws-saas-boost)

## Introduction
This document describes how to create and configure AWS SaaS Boost. Use this document alongside the other AWS SaaS Boost documents to get a more comprehensive view of the system.

While this document provides a review of the steps to setup AWS SaaS Boost, it is not intended to provide a deep dive into the user experience or underlying technology. These details are covered in the User Guide and Developer Guide. 

## The Target Experience
Before digging into the steps needed to set up AWS SaaS Boost, let's look at the basic elements of the environment to get a better sense of what AWS SaaS Boost enables. The diagram in below shows the key components of the AWS SaaS Boost experience in the order of the setup flow. This flow presumes that you have a monolithic application -- one where your entire application (other than the database) can be packaged and deployed as a single unit.

![Setup Flow](images/gs-install-flow.png?raw=true "Setup Flow")

The following is a breakdown of each of the steps:
1. Set up the tooling for the install process
2. Clone the AWS SaaS Boost repository
3. Provision AWS SaaS Boost
4. Access the AWS SaaS Boost administration web application
5. Configure the application settings for your requirements
6. Upload a containerized Docker image of your application to AWS SaaS Boost

At this stage, all the moving parts of the environment are now in place to begin onboarding tenants. You can use the AWS SaaS Boost application to configure and onboard new tenants, or you can make API calls from your application to trigger the onboarding of a new tenants.

The last piece to consider is the update process for your application. As changes are introduced into your application, you can upload the latest version to AWS SaaS Boost. Whenever a new version is uploaded it will be automatically deployed to all the tenants in your system. AWS SaaS Boost now has you ready to operate as a SaaS business. This includes operational and management tooling that's built into the AWS SaaS Boost environment.

Now that you have a feel for the AWS SaaS Boost technology and experience, let's look at the details that are involved in setting up AWS SaaS Boost.

## Step 1 - Set Up Tooling
AWS SaaS Boost uses a handful of technologies to orchestrate the installation process. Install and configure each of these prerequisites for your operating system if they aren't already installed:
- Java 11 [Amazon Corretto 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html) or 
- [Apache Maven](https://maven.apache.org/download.cgi) (see [Installation Instructions](https://maven.apache.org/install.html))
- [AWS Command Line Interface version 2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- [Git](https://git-scm.com/downloads)
- [Node 14.15 (LTS)](https://nodejs.org/download/release/v14.15.1/)
- [Yarn](https://classic.yarnpkg.com/en/docs/install)

## Step 2 - Clone the AWS SaaS Boost Repository
With the tooling in place, you can now download the code and installation scripts for AWS SaaS Boost. Open your terminal window and navigate to the directory where you want to store the AWS SaaS Boost assets. Issue the following command to clone the AWS SaaS Boost repository:

`git clone https://github.com/awslabs/aws-saas-boost ./aws-saas-boost`

## Step 3 - Provision AWS SaaS Boost 
Now that you have the code, AWS SaaS Boost needs to be installed in an AWS account that you own and manage. The installation process executes scripts that provision and configure all the resources that are needed to set up AWS SaaS Boost. The system where you run the installation should have at least 4 GB of memory.

Before running the installation, setup your AWS user and CLI:
1. Setup an IAM user in your AWS account with full admin permissions.
2. Set up your AWS CLI credentials with an AWS Access Key and default region.

To initiate this process, perform the following steps:
1. From the terminal window, navigate to the directory where you've downloaded AWS SaaS Boost (aws-saas-boost).
2. Invoke the install command. 
      - If you're running on Linux or OSX, run: `sh install.sh`
      - If you're running on Windows, run: `.\install.ps1`
3. Select the option for a new installation.
4. Enter the full path to your AWS SaaS Boost directory: /\<mydir\>/aws-saas-boost.
5. Enter the name of the SaaS Boost environment (dev, QA, test, sandbox, etc.).
6. Enter the email address of the AWS SaaS Boost administrator who will receive the initial temporary password.
7. Indicate whether you want a domain to be setup for AWS SaaS Boost (Y/N).
      - If you enter **Y**, you are prompted for a domain name. For example, if your main domain is **example.com**, you can enter **app.example.com** to have that sub-domain setup by the installer. After the installation completes, you need to update the DNS for example.com to add an entry for app.example.com pointing to the nameservers for the Route53 hosted zone created for app.example.com.
      - This step can also be done after the install process.
8. Indicate whether you would like the metrics and analytics features of AWS SaaS Boost to be installed (Y/N). This is **optional** and will provision a [Redshift](https://aws.amazon.com/redshift) cluster.
      - If you enter **Y**, you are prompted for [QuickSight](https://aws.amazon.com/quicksight/) setup. To select Y for Quicksight setup, _you must have already registered_ for at least a Standard account of Quicksight in your AWS Account by following the steps at [https://docs.aws.amazon.com/quicksight/latest/user/signing-up.html](https://docs.aws.amazon.com/quicksight/latest/user/signing-up.html).
9. If your application is Windows based and needs a shared file system, a [Managed Active Directory](https://aws.amazon.com/directoryservice/) must be deployed to support [Amazon FSx for Windows File Server](https://aws.amazon.com/fsx/windows/). Select y or n as needed.
10. Review the settings for your installation. Enter **y** to proceed or **n** to re-enter or adjust the values.

The execution of this process will take 30-45 minutes to provision and configure all the resources (this will vary based on the options you've selected). Detailed logs from the installation process are stored in **saas-boost-install.log**. 

## Step 4 - Login to AWS SaaS Boost
After the installation completes, you will receive a message at the email address you provided during the installation process. This message includes a link with a URL to the AWS SaaS Boost administration application. The message appears as follows:

![Welcome Email](images/gs-welcome-email.png?raw=true "Welcome Email")

Copy the temporary password that is shown here. Select the link to the AWS SaaS Boost administration application. The following login appears:

![Login Screen](images/gs-login.png?raw=true "Login Screen")

Enter `admin` for the user name and enter the temporary password that was delivered in the email referenced earlier. Since you're logging in with a temporary password, you're prompted to enter a new password for your account. The screen appears as follows:

![Change Password](images/gs-change-password.png?raw=true "Change Password")

## Step 5 - Configure Application Settings
After you've logged in to the AWS SaaS Boost administration application, the first thing you need to do is configure the environment to align it with the needs of your application. To set up the application, select **Application** from the navigation on the left side of the screen. A page appears similar to the following:

![Application Setup](images/gs-app-setup.png?raw=true "Application Setup")

While this _Getting Started Guide_ doesn't document each of the fields on the application settings page, the options you configure are essential to getting your application working. Here you define the footprint of how your application is configured for your tenants as they onboard.

 You also set up key attributes that correlate directly to details that are part of your application's Docker configuration (container port, mount point, database settings, file system settings, and so on). Review the options for these settings before deploying your application.

## Step 6 - Upload Your Application
Next, upload your application. As mentioned earlier, AWS SaaS Boost requires an application to be containerized as a Docker image before it can be published to the AWS SaaS Boost application repository. From the **Summary** page, click the **View details** link next to the ECR Repository URL listing to see the proper Docker push commands to upload your image.

## Step 7 - (Optional) Deploy the Sample Application
This section goes through the process of uploading a sample application to give you a feel for how this process works. We've provided a simple example application as part of the AWS SaaS Boost repo.

### Configure AWS SaaS Boost for the Sample Application
Just as you would configure AWS SaaS Boost to support the requirements of your application, we must configure the application settings properly for this sample app. This sample application relies on the following configuration in AWS SaaS Boost.
- Give the application a **Name**
- You can leave Compute Size set to **Small**
- Minimum and Maximum Instance Count can be **1** and **2** respectively
- Container OS is **Linux**
- Set the Container Port to **8080**
- Set the Health Check URL to **/index.html**
- **Enable** the Provision a File System for the application checkbox
- Set the Mount point to **/mnt**
- **Enable** the Provision a database for the application checkbox
- Select any of the available databases (MariaDB with a db.t2.micro instance class will provision the fastest)
- Enter a **Database Name**, **Username**, and **Password**. You _do not_ need to provide a SQL file for database initialization.

![Application Config](images/gs-app-config.png?raw=true "Application Config")

### Build and Deploy the Sample Application
Once you have saved your Application Setup, you can build and containerize the sample application. To create a Docker image of this sample application, you will need to have Docker running on your local machine. Navigate to the **samples/java/** directory in your clone of the AWS SaaS Boost repo and execute the build script which will build, containerize, and push your containerized application to the AWS SaaS Boost ECR repository. You can review the steps in this example shell script to see how you might enhance your current build process for your application to integrate with AWS SaaS Boost.

```
cd aws-saas-boost/samples/java
bash build.sh
```

This script prompts you for your AWS SaaS Boost environment. Enter the environment label that you specified when you ran the AWS SaaS Boost installer. When this script finishes, you have published your application to the AWS SaaS Boost application repository. To verify this, open the AWS Console, navigate to the Amazon ECR repositories, and verify that your application was uploaded. As you make changes to the application, you can execute the build script again to update your application.

### Onboard a Tenant
You can now onboard a new tenant to deploy your containerized application. Navigate to the **Onboarding** page of the administration application and click the **Provision Tenant** button. Once the onboarding process completes, you'll be able to access that tenant's instance of the sample application by navigating to the **Tenants** page of the administration application, then go to the tenant detail page, and finally clicking on the **Load Balancer DNS** link.

## Mapping Your Application to AWS SaaS Boost
This guide provides a high-level view into the basic steps that are required to set up AWS SaaS Boost. As you think about moving a workload into AWS SaaS Boost, examine the broader capabilities of AWS SaaS Boost in more detail. This means looking more carefully at the configuration options for your application and how they map to the different AWS SaaS Boost application settings.

After familiarizing yourself with the overall experience, you will have a better sense about how your application fits in the AWS SaaS Boost model. A review of the AWS SaaS Boost _User Guide_ and _Developer Guide_ will also allow you to better understand how you might configure AWS SaaS Boost to align with the needs of your solution.

The steps needed to containerize vary depending on the nature of your application. Here are some resources that provide information on containerizing applications:
- [AWS Learning Path: Containerize the Monolith](https://aws.amazon.com/getting-started/container-microservices-tutorial/module-one/)
- [AWS App2Container](https://aws.amazon.com/app2container/)
