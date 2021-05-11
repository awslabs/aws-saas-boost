# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License").
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import sys
import os
import json
import random
import datetime
import boto3

start_time = datetime.datetime.now()
no_of_days = 30

workload_contexts_distribution = [2, 15, 20, 18, 30, 15]
workload_contexts = {
    "OnBoardingApplication": ['TenantCreation', 'UserCreation'],
    "AuthApplication": ['Login', 'Logout', 'PasswordReset'],
    "PhotoApplication": ['PhotoUpload', 'PhotoEdit', 'PhotoDelete'],
    "MessagingApplication": ['SendMessage', 'SendBulkMessages', 'DeleteMessages', 'ArchiveMessages'],
    "ProductApplication": ['ViewProduct', 'ViewProductDetails', 'AddNewProduct', 'DeleteProduct', 'UpdateProduct'],
    "BatchWorkload": ['ActiveProductsReport', 'DailyTransactionReport', 'DailyInventoryReport', 'DailySalesReport']
    }

tenant_distribution = [5, 10, 20, 15, 2, 3, 10, 5, 25, 5]
regular_tenants = [
      {"id": "tenant-id-1", "name":"tenant-name-a", "tier":"standard"},
      {"id": "tenant-id-2", "name":"tenant-name-b", "tier":"premium"},
      {"id": "tenant-id-3", "name":"tenant-name-c", "tier":"basic"},
      {"id": "tenant-id-4", "name":"tenant-name-d", "tier":"basic"},
      {"id": "tenant-id-5", "name":"tenant-name-e", "tier":"standard"},
      {"id": "tenant-id-6", "name":"tenant-name-f", "tier":"standard"},
      {"id": "tenant-id-7", "name":"tenant-name-g", "tier":"free"},
      {"id": "tenant-id-8", "name":"tenant-name-h", "tier":"free"},
      {"id": "tenant-id-9", "name":"tenant-name-i", "tier":"basic"},
      {"id": "tenant-id-0", "name":"tenant-name-j", "tier":"free"}
      ]

user_distribution = [10, 40, 20, 30]
users = ('user-1', 'user-2', 'user-3', 'user-4')

resource_metrics = {
    "s3":  ["Storage", "DataTransfer"],
    "load-balancer":  ["ExecutionTime"],
    "lambda":  ["ExecutionTime"],
    "dynamo-db":  ["Storage", "ExecutionTime", "DataTransfer"],
    "rds": ["Storage", "ExecutionTime", "DataTransfer"]
}

def generate_random_metric_value(metric_name):
    metric = {}
    if metric_name == "Storage":
        metric = {'name' : 'Storage', 'unit' : 'MB', 'value' : random.randrange(50, 5000, 100)}
    elif metric_name == "ExecutionTime":
        metric = {'name' : 'ExecutionTime', 'unit' : 'MilliSeconds', 'value' : random.randrange(100, 5000, 200)}
    elif metric_name == "DataTransfer":
        metric = {'name' : 'DataTransfer', 'unit' : 'MB', 'value' : random.randrange(10, 3000, 200)}

    return metric

def event_time():
    random_days = random.randint(1, no_of_days)
    prev_days  = start_time + datetime.timedelta(days=random_days)
    random_minute = random.randint(0, 59)
    random_hour = random.randint(0, 23)
    time =  prev_days + datetime.timedelta(hours=random_hour) + datetime.timedelta(minutes=random_minute)
    return int(time.timestamp())

def input_with_default(msg, default):
    value = input(msg + " [" + default + "] : ")
    if value == "":
        value = default
    return value

def generate_metric_for(workload, tenants):
    selected_context = random.choice(workload_contexts[workload])
    selected_resource = random.choice(list(resource_metrics.keys()))
    selected_metric_name = random.choice(resource_metrics[selected_resource])
    random_metric_value = generate_random_metric_value(selected_metric_name)

    application_metric = {
        'type' : 'Application',
        'workload': workload,
        'context': selected_context,
        'tenant' : random.choices(tenants, tenant_distribution)[0],
        'metric': random_metric_value,
        'timestamp': event_time(),
        'metadata' : {'user' : random.choices(users, user_distribution)[0], 'resource': selected_resource}
    }
    #print(application_metric)
    return application_metric

def generate_metrics_for(tenants, no_of_metrics, stream_name, batch_size):
    metrics_batch = []

    for m in range(no_of_metrics):
        selected_workload = random.choices(list(workload_contexts.keys()), workload_contexts_distribution)[0]

        if len(metrics_batch) < batch_size:
            print("Generating Metric for: " + selected_workload)
            metric = generate_metric_for(selected_workload, tenants)
            metrics_batch.append({'Data' : json.dumps(metric)})
        else:
            write_data_to_firehose(stream_name, metrics_batch)
            display("Processed batch of " + str(batch_size))
            metrics_batch = []

    if len(metrics_batch) > 0:
        write_data_to_firehose(stream_name, metrics_batch)
        display("Processed batch of " + str(len(metrics_batch)))

def display(msg):
    print("___________________________________")
    print(msg + "...")
    print("___________________________________")

def write_data_to_firehose(stream_name, metrics_batch):
    response = firehose_client.put_record_batch(
        DeliveryStreamName = stream_name,
        Records = metrics_batch
    )

if __name__ == "__main__":
    try:
        sb_env = input_with_default("SaaS Boost Environment? ", "")
        number_of_metrics_to_generate = int(input_with_default("How many metrics? ", "10000"))
        start_at = input_with_default("Enter start date for metrics? ", "2020-01-01")
        start_time = datetime.datetime.strptime(start_at, "%Y-%m-%d")
        no_of_days = int(input_with_default("Number of days? ", "30"))
        batch_size = int(input_with_default("Batch size for Kinesis? ", "25"))

        current_session =  boto3.session.Session()
        current_credentials = current_session.get_credentials().get_frozen_credentials()

        region = input_with_default("Enter region for the deployed metrics stack", current_session.region_name)
        access_key = input_with_default("Enter AWS Access Key for the deployed metrics stack", current_credentials.access_key)
        secret_key = input_with_default("Enter AWS Secret Key for the deployed metrics stack", current_credentials.secret_key)

        firehose_client = boto3.client('firehose', region_name=region, aws_access_key_id=access_key, aws_secret_access_key=secret_key)
        streams = firehose_client.list_delivery_streams()

# get the stream name from the SSM Parameter store
        path = "/saas-boost/{}/METRICS_STREAM".format(sb_env)
        #print("Path = " + path)
        ssm_client = boto3.client('ssm', region_name=region, aws_access_key_id=access_key, aws_secret_access_key=secret_key)
        stream_name_param = ssm_client.get_parameter(Name = path)
        stream_name = stream_name_param["Parameter"]["Value"]
        #stream_name = input_with_default("Enter Kinesis stream name: ", ssm_stream_param)
        #[stream for stream in streams['DeliveryStreamNames'] if 'sb-metrics-dev5-us-west-2' in stream][0])

        display("Generating Event Metrics: " + str(number_of_metrics_to_generate))
        generate_metrics_for(regular_tenants, number_of_metrics_to_generate, stream_name, batch_size)

    except Exception as e:
        print("error occured")
        print(e)
        raise
