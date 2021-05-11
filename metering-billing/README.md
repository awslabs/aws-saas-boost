# Reference Billing/Metering Service

This project contains a reference implementation for a serverless billing/metering service.

**THIS IS NOT A PRODUCTION READY APPLICATION**

## Requirements

* Java, version 11 or higher
* Maven
* AWS SAM (https://aws.amazon.com/serverless/sam/)

## Optional Requirements

These requirements are necessary if you use the included Python script to add tenants to the DynamoDB table:
* Python 3+
* boto3

## Building

First, build the CommonBillingMetering package.

```shell script
$ cd CommonBillingMetering
$ mvn clean install
$ cd ..
```

Then, build the SAM application.

```shell script
$ sam build
```

You can ignore the warnings about the JVM using a major version higher than 11 if you are
using a JVM version higher than 11. The pom files tell the compiler to build for a version
of Java that is compatible with Lambda.

## Deploying

In order to deploy the application, run the following command:

```shell script
$ sam deploy --guided
```

## Initial Configuration

### Tenant Configuration

The configuration for each tenant is stored within DynamoDB. In order to add a tenant, running the following
command:

```shell script
$ scripts/create_tenant_configuration.py \
--table ${TABLE_NAME} \
--tenant-id ${TENANT_ID} \
--internal-product-code ${INTERNAL_PRODUCT_CODE} \
--stripe-subscription-item ${STRIPE_SUBSCRIPTION_ITEM}
```

Each of the variables should be filled in with the appropriate values. The internal product code maps directly onto
the subscription item that a customer in Stripe has. This will be unique for each tenant.

### Stripe API Key

The Stripe API key is stored within Secrets Manager. The Cloudformation stack creates an empty secret.
Access the secret through the Secrets Manager console and paste in a **test Stripe API key** only. Do not
paste it in JSON; the application expects to find a single string with the API key. **Do not use a production
API key.**

## Usage and Function

Place billing events onto the EventBridge created by the CloudFormation stack. The event is in the
following format:

```json
{ 
  "TenantID": "sub|1234",
  "ProductCode": "example-billing-event",
  "Quantity": 5,
  "Timestamp": 1590074184000
}
```

This is a description of what is expected in the detail type section of an event. Please refer to the 
relevant SDK you're using for the other parameters necessary to put an event onto the EventBridge.

A description of each field:
* TenantID: the ID of the tenant that owns the billing event
* ProductCode: the internal name for your billing tier; this maps to a Subscription Item in Stripe
* Quantity: the number of billing events that occurred
* Timestamp: the epoch timestamp the event occurred at, in milliseconds

After placing the event onto the EventBridge, a Lambda function processes the event and places it into
DynamoDB. 

At the top of every hour, a Cloudwatch Scheduled Event runs a Step Function State Machine that aggregates the events
in the DynamoDB table and publishes them to Stripe.

## Known Areas of Improvement

### Production readiness

There are likely several areas where exceptions, errors, etc. are not handled in a manner suitable for production usage.
**This application is not intended for production usage.**

### Timezone setting in Stripe

There is a timezone setting in Stripe. Make sure that the events you put on to the EventBridge line up with the timezone
you are using for your events in DynamoDB.