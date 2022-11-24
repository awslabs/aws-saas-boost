# Provision AWS SaaS Boost in GCR

## Contents
[Introduction](#introduction)\
[Feature Availability and Implementation Differences](#feature-availability-and-implementation-differences)\
[Provision AWS SaaS Boost into your AWS GCR Account](#provision-aws-saas-boost-into-your-aws-gcr-account)\
[References](#references)

## Introduction
Amazon Web Services China (Beijing) Region and Amazon Web Services China (Ningxia) Region are the two Amazon Web Services Regions located within China. To provide the best experience for customers in China and to comply with China’s legal and regulatory requirements, Amazon Web Services has collaborated with China local partners with proper telecom licenses for delivering cloud services. The service operator and provider for Amazon Web Services China (Beijing) Region based out of Beijing and adjacent areas is Beijing Sinnet Technology Co., Ltd. (Sinnet), and the service operator and provider for Amazon Web Services (Ningxia) Region based out of Ningxia is Ningxia Western Cloud Data Technology Co., Ltd. (NWCD).

As Amazon Web Services China operates seperately from Amazon Web Services Global regions, due to feature availability and regulatory requirements, the SaaS Boost provision experience in GCR is unique. This document provides guidance of provisioning AWS SaaS Boost in GCR.

## Feature Availability and Implementation Differences
1. System User Service is different in the following ways:
    - Amazon Cognito User Pools are not currently available in the GCR Regions.
    - Keycloak(Open Source Identity and Access Management) is provided as another identity provider.
2. Admin Web UI is different in the following ways:
    - You can’t use the default CloudFront domain, `*.cloudfront.cn`, to serve content. You must add an alternate domain name, also known as a CNAME, to your CloudFront distributions, and then use that domain name in the URLs for your content. You also must have [an ICP registration](https://www.amazonaws.cn/en/about-aws/china/#ICP_in_China). In addition, just as with the global CloudFront service, to serve content over HTTPS, you must use an SSL/TLS certificate with your alternate domain name.
    - Amazon CloudFront in the China Regions currently does not support Amazon Certificate Manager. You must get an SSL/TLS certificate from a different third-party certificate authority (CA) and then upload it to the IAM certificate store.
3. Amazon SES is unique in the following ways:
    - Amazon SES is not availible in GCR regions.
4. Amazon QuickSight is unique in the following ways:
    - Amazon QuickSight is not availible in GCR regions.

## Provision AWS SaaS Boost into your AWS GCR Account
To prepare the installation process, perform the following steps as needed:
1. You have a domain and your domain is [ICP registered](https://www.amazonaws.cn/en/about-aws/china/#ICP_in_China).
2. You need a root domain name and individual domain names for your keycloak installation and the admin web UI. As part of this you need one certificate in ACM for keycloak domain, and one certificate in IAM that covers admin web UI domain. Either wildcard certificates (e.g. *.example.com) or specific certificates (e.g. keycloak.example.com). 
    - You need to create a public hosted zone in AWS Route53 for your domain.
    - Request or upload [certificate to ACM](https://docs.aws.amazon.com/acm/latest/userguide/gs-acm-request-public.html)
    - Upload [certificate to IAM](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cnames-and-https-procedures.html). 

To start the installation process, perform the following steps:
1. From the terminal window, navigate to the directory where you've downloaded AWS SaaS Boost (aws-saas-boost).
2. Invoke the install command. 
      - If you're running on Linux or OSX, run: `sh ./install.sh`
      - If you're running on Windows, run: `powershell .\install.ps1`
3. Select the option for a new installation.
4. Enter the full path to your AWS SaaS Boost directory (hit enter for the current directory): /\<mydir\>/aws-saas-boost.
5. Enter the name for this SaaS Boost environment (dev, QA, test, sandbox, etc.).
6. Enter the email address of the AWS SaaS Boost administrator who will receive the initial temporary password. In AWS GCR region, the base password for your admin installation will be stored in SecretsManager in the secret 'sb-{env}-admin'
7. Enter `Keycloak` as identity provider to use for system users.
8. Enter domain name for control plane identity provider. 
9. Select Route53 hosted zone and ACM certificate for Keycloak domain.
10. Enter domain name for SaaS Boost admin web console.
11. Select Route53 hosted zone and IAM certificate for SaaS Boost Admin UI domain.
12. Indicate whether you would like the metrics and analytics features of AWS SaaS Boost to be installed. This is ***optional*** and will provision a [Redshift](https://aws.amazon.com/redshift) cluster.
      - You can enter **N**, QuickSight is not availible in GCR currently.
13. If your application is Windows based and needs a shared file system, a [Managed Active Directory](https://aws.amazon.com/directoryservice/) must be deployed to support [Amazon FSx for Windows File Server](https://aws.amazon.com/fsx/windows/) or [Amazon FSx for NetApp ONTAP](https://aws.amazon.com/fsx/netapp-ontap/). Select y or n as needed.
14. Review the settings for your installation. Double check the AWS account number and AWS Region you're about to install AWS SaaS Boost into. Enter **y** to proceed or **n** to re-enter or adjust the values.

The installation process will take 30-45 minutes to configure and provision all the resources (this will vary based on the options you've selected). Detailed logs from the installation process are stored in **saas-boost-install.log**. 

## References
- [Amazon Web Services in China](https://www.amazonaws.cn/en/about-aws/china/)
- [Amazon Cognito](https://docs.amazonaws.cn/en_us/aws/latest/userguide/cognito.html)
- [Amazon Cloudfront](https://docs.amazonaws.cn/en_us/aws/latest/userguide/cloudfront.html)