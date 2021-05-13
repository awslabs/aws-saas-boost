# AWS SaaS Boost Sample Application

This is a sample monolithic Java web application to exercise the various features of AWS SaaS Boost. The included build shell script may also give you some ideas on how to integrate your existing build/deploy process with SaaS Boost.

## Getting Started
You must configure the **Application Settings** in your SaaS Boost environment to support this workload.
- **1** for minimum instance count
- **2** for maximum instance count
- **Small** for the compute size
- Container OS is **Linux**
- Set the Container Port to **8080**
- Set the Health Check URL to **/index.html**
- **Enable** the Provision a File System for the application checkbox
- Set the Mount point to **/mnt**
- **Enable** the Provision a database for the application checkbox
- Select any of the available databases (MariaDB with a db.t3.micro instance class will provision the fastest)
- Enter a **Database Name**, **Username**, and **Password**. You _do not_ need to provide a SQL file for database initialization.

## Provision to AWS SaaS Boost
Once you've configured your SaaS Boost environment to support this sample app, run the `build.sh` shell script to build and deploy it. The script will use the default AWS CLI profile available in the terminal session (use the same one you did to run the SaaS Boost installer).

## See it in Action
After you have pushed the app to your SaaS Boost environment, you're ready to onboard some tenants! Once a tenant is fully onboarded, they will appear on the Tenants screen of the SaaS Boost admin web UI. Select the tenant and you will see a URL to access this sample app from that tenant's environment.

Provision at least 2 tenants to see how isolation works.

Make a change to the project and re-run the `build.sh` script to see your changes automatically deployed to each of your onboarded tenants.

## How to run locally with Docker
If you'd like to test the app locally before you deploy to your SaaS Boost environment, you can run the Docker image locally as long as it can connect to a compatible database.
1. Create an empty Postgres/MySQL/MariaDB/SQL Server database
2. Build the application using Maven `mvn clean package`
3. Build a Docker image of the compiled application `docker image build -t helloworld -f Dockerfile .`
4. Run the Docker image as a container setting the various environment variables to their proper values.\
`docker run -p 8888:8080 -v hello-world:/mnt -e AWS_REGION=us-east-1 -e DB_HOST=localhost -e DB_NAME=dbname -e DB_PORT=3306 -e DB_MASTER_USERNAME=dbuser -e DB_MASTER_PASSWORD=dbpass helloworld`

## (Experimental - Using Oracle JDBC)
If you would like to experiment with Oracle, you must download the JDBC driver from your Oracle account and install it in your Maven cache.\
`mvn install:install-file -Dfile=./ojdbc7.jar -DgroupId=com.oracle -DartifactId=ojdbc7 -Dversion=12.1.0.1 -Dpackaging=jar`

Once you have the Oracle JDBC driver available to Maven, add the following dependency to the project's POM file.
```xml
<dependency>
    <groupId>com.oracle</groupId>
    <artifactId>ojdbc7</artifactId>
    <version>12.1.0.2</version>
</dependency>
```
