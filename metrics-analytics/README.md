# Steps to Deploy the Metrics & Analytics Stack


1. The resources for the Metrics and Analytics solutions are deployed with the SaaS Boost install by specifying option of Y for DEPLOYMETRICS.  This will deploy a delivery stream inside Amazon Kinesis Firehose, an S3 bucket and a Amazon Redshift Cluster.

2. Register for Quicksight inside your AWS account. You can select Standard. Choose the region where you deployed SaaS Boost if it is available. Check the option for Enable autodiscovery of data and users in your Amazon Redshift, Amazon RDS, and AWS IAM services. Once your account is created, You will want to select the option to include Redshift as a datasource.

3. The Java installer will setup the datasource and dataset in Quicksight if the option was selected at time of installation. If not, you can run setup-s3-quicksight.py to create datasource and dataset, in order to connect Quicksight to the metrics table inside Redshift cluster. You will need boto3 library installed for Python and your AWS access key.
```
python3 setup-s3-quicksight.py
```

4. OPTIONAL - Run application-metrics-generator.py inside "metrics-generator" folder to generate some sample data to the Redshift cluster. Run the following command and follow the prompts.
```
python3 application-metrics-generator.py
```
The script has
- few workloads defined which represent different microservices in the over all application, e.g. AuthApplication
- few contexts for each workload which represent various end points or services within each microservices e.g. PasswordReset
- few tenant in different tiers such as free, basic, standard and premium
- few outlier tenants to represent uneven load
- few user ids per tenant are sent in the meta-data, additional info can be sent depending on the use case
- captures metrics on DataTransfer (MB), Storage (MB) and ExecutionTime (MilliSeconds) for various AWS services
- can spread the generated events evenly within the configured time window
- will send data to the firehose stream, default stream name 'MetricsStream'

5. In Quicksight, in Manage Data, you can use the dataset beginning with name MetricsDataSet to visualize the data from the metrics table in Redshift.  Here is a sample of a dashboard:  **TODO

6. Use the SDK inside metrics-java-sdk folder, in order to integrate your Java application and send metric events. This SDK uses maven. For building the artifacts use the following commands. 
```
cd metrics-java-sdk
mvn clean install
```
This will install the artifact into your local maven repository.

In your Java application use the following snippet to import the artifact as a dependency
```
<dependency>
            <groupId>com.amazonaws.saas</groupId>
            <artifactId>metrics-java-sdk</artifactId>
            <version>1.0-SNAPSHOT</version>
</dependency>
```
Sample Java code 
```
 	MetricEventLogger logger = MetricEventLogger.getLoggerFor("STREAM_NAME", Region.US_EAST_1);
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
