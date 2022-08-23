# AWS SaaS Boost Sample Application

This is a sample monolithic .Net Framework web application (ASP.NET MVC) to exercise the various features of AWS SaaS Boost. The included build shell script may also give you some ideas on how to integrate your existing build/deploy process with SaaS Boost. This sample was created specifically to mimic the [Java sample application](../java/README.md).

## Getting Started
First, you must answer **yes** when the SaaS Boost installer asks if you want to provision an AWS Directory Service instance to support FSx for Windows File Server.

Next, you must configure the **Application Settings** in your SaaS Boost environment to support this workload.
- **1** for minimum instance count
- **2** for maximum instance count
- **Medium** for the compute size
- Container OS is **Windows**
- Select **Windows Server 2019 Core** or **Windows Server 2019 Full**
- Set the Container Port to **80**
- Set the Health Check URL to **/**
- **Enable** the Provision a File System for the application checkbox
- Select either **FSx ONTAP** or **FSx Windows** for File System Type
- Set the Mount point to **C:\Images**
- **Enable** the Provision a database for the application checkbox
- Select **SQL Server Express Edition**
- Select the latest **SQL Server 2019** version
- Enter a **Database Name**, **Username**, and **Password**
- Upload the [bootstrap-mssql.sql](bootstrap-mssql.sql) database initialization file

## Provision to AWS SaaS Boost
Once you've configured your SaaS Boost environment to support this sample app, edit the [build.ps1](build.ps1) PowerShell script to set the SaaS Boost environment name and your AWS CLI settings. You can then execute the `build.ps1` script, or build the VisualStudio solution using the Release configuration and then tag and push the resulting docker image to the ECR repositiory SaaS Boost provisioned for your environment.

## See it in Action
After you have pushed the app to your SaaS Boost environment, you're ready to onboard some tenants! Once a tenant is fully onboarded, they will appear on the Tenants screen of the SaaS Boost admin web UI. Select the tenant and you will see a URL to access this sample app from that tenant's environment.

Provision at least 2 tenants to see how isolation works.
