# Provision AWS SaaS Boost in GCR

## Contents
[Introduction](#introduction)\
[Feature Availability and Implementation Differences](#feature-availability-and-implementation-differences)\
[Provision AWS SaaS Boost into your AWS GCR Account](#provision-aws-saas-boost-into-your-aws-gcr-account)\
[References](#references)

## Introduction
Amazon Web Services China (Beijing) Region and Amazon Web Services China (Ningxia) Region are the two Amazon Web Services Regions located within China. To provide the best experience for customers in China and to comply with China’s legal and regulatory requirements, Amazon Web Services has collaborated with China local partners with proper telecom licenses for delivering cloud services. The service operator and provider for Amazon Web Services China (Beijing) Region based out of Beijing and adjacent areas is Beijing Sinnet Technology Co., Ltd. (Sinnet), and the service operator and provider for Amazon Web Services (Ningxia) Region based out of Ningxia is Ningxia Western Cloud Data Technology Co., Ltd. (NWCD).

As Amazon Web Services China operates seperately from Amazon Web Services Global regions, due to feature availability and regulatory requirements, the SaaS Boost provision experience in GCR is unique. This document provides guidence of provisioning AWS SaaS Boost in GCR.

## Feature Availability and Implementation Differences
1. Amazon Cognito is unique in the following ways:
    - Amazon Cognito is available in Beijing regions in China.
    - Amazon Cognito User Pools are not currently available in the Beijing Region.
    - Keycloak(Open Source Identity and Access Management) is provided as another identity provider.
2. Amazon CloudFront is unique in the following ways:
    - You can’t use the default CloudFront domain, `*.cloudfront.cn`, to serve content. You must add an alternate domain name, also known as a CNAME, to your CloudFront distributions, and then use that domain name in the URLs for your content. You also must have [an ICP registration](https://www.amazonaws.cn/en/about-aws/china/#ICP_). In addition, just as with the global CloudFront service, to serve content over HTTPS, you must use an SSL/TLS certificate with your alternate domain name.
    - Amazon CloudFront in the China Regions currently does not support Amazon Certificate Manager. You must get an SSL/TLS certificate from a different third-party certificate authority (CA) and then upload it to the IAM certificate store.
3. Amazon QuickSight is unique in the following ways:
    - Amazon QuickSight is not availible in GCR regions.

## Provision AWS SaaS Boost into your AWS GCR Account
To prepare the installation process, perform the following steps as needed:
1. You have a domain and your domain is [ICP registrated](https://www.amazonaws.cn/en/about-aws/china/#ICP_).
2. You need a root domain name and individual domain names for your keycloak installation and the admin web UI. As part of this you need one certificate in ACM for keycloak domain, and one certificate in IAM that covers admin web UI domain. Either wildcard certificates (e.g. *.example.com) or specific certificates (e.g. keycloak.example.com). 
    - You need to create a public hosted zone in AWS Route53 for your domain.
    - Request or upload [certificate to ACM](https://docs.aws.amazon.com/acm/latest/userguide/gs-acm-request-public.html)
    - Upload [certificate to IAM](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cnames-and-https-procedures.html). 
    - If you don't have a certificate availible, [letsencrypt.org](https://letsencrypt.org/) provides free TLS certificates.
        ```
        brew install certbot
        ```
        ```
        sudo certbot certonly --manual --preferred-challenges dns -d "*.example.com"
        ```
        It will prompt you like below 
        ```
        Please deploy a DNS TXT record under the name
        *_acme-challenge.example.com* with the following value:
        F1GrseCyEboH_PzuHH9V_oyifx8BPcKk********
        ```
        Add `*_acme-challenge.example.com*` ** Route 53 TXT type entry and set the value to `F1GrseCyEboH_PzuHH9V_oyifx8BPcKk******`.\
        Press enter to continue and you will get the signed cert.\
        You can upload through the CloudFront User Interface or using the following command.
        ```
        aws iam list-server-certificates --region cn-north-1
        ```
        ```
        $ sudo aws iam upload-server-certificate \
        --path '/cloudfront/' \
        --server-certificate-name '+.example.com' \
        --certificate-body file:///etc/letsencrypt/live/example.com/cert.pem \
        --private-key file:///etc/letsencrypt/live/example.com/privkey.pem \
        --certificate-chain file:///etc/letsencrypt/live/example.com/chain.pem \
        --region cn-north-1
        ```

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
8. Select Route53 hosted zone and ACM certificate for Keycloak domain.
9. Enter domain name for SaaS Boost admin web console.
10. Select Route53 hosted zone and IAM certificate for SaaS Boost Admin UI domain.
11. Indicate whether you would like the metrics and analytics features of AWS SaaS Boost to be installed. This is ***optional*** and will provision a [Redshift](https://aws.amazon.com/redshift) cluster.
      - You can enter **N**, QuickSight is not availible in GCR currently.
12. If your application is Windows based and needs a shared file system, a [Managed Active Directory](https://aws.amazon.com/directoryservice/) must be deployed to support [Amazon FSx for Windows File Server](https://aws.amazon.com/fsx/windows/) or [Amazon FSx for NetApp ONTAP](https://aws.amazon.com/fsx/netapp-ontap/). Select y or n as needed.
13. Review the settings for your installation. Double check the AWS account number and AWS Region you're about to install AWS SaaS Boost into. Enter **y** to proceed or **n** to re-enter or adjust the values.

The installation process will take 30-45 minutes to configure and provision all the resources (this will vary based on the options you've selected). Detailed logs from the installation process are stored in **saas-boost-install.log**. 

## References
- [Amazon Web Services in China](https://www.amazonaws.cn/en/about-aws/china/)
- [Amazon Cognito](https://docs.amazonaws.cn/en_us/aws/latest/userguide/cognito.html)
- [Amazon Cloudfront](https://docs.amazonaws.cn/en_us/aws/latest/userguide/cloudfront.html)