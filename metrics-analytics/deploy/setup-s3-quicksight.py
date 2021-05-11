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
import time
import boto3
import random
import string

def SetupS3(client, s3_bucket_name):
    client.upload_file(Bucket=s3_bucket_name,Key='metrics_redshift_jsonpath.json', Filename='./artifacts/metrics_redshift_jsonpath.json')

def SetupQuicksight(client, account_id, quicksight_user_arn, random_string, redshift_host, redshift_port, redshift_db_name, redshift_cluster_id, redshift_user_name, redshift_password):
    datasource_response = client.create_data_source(
        AwsAccountId=account_id,
        DataSourceId='MetricsDataSource-' + random_string,
        Name='MetricsDataSource-' + random_string,
        Type='REDSHIFT',
        DataSourceParameters={
            'RedshiftParameters': {
                'Host': redshift_host_name,
                'Port': redshift_port,
                'Database': redshift_db_name,
                'ClusterId': redshift_cluster_id
            }
        },
        Credentials={
            'CredentialPair': {
                'Username': redshift_user_name,
                'Password': redshift_password
            }
        },
        Permissions=[
            {
                'Principal': quicksight_user_arn,
                'Actions': ["quicksight:DescribeDataSource","quicksight:DescribeDataSourcePermissions","quicksight:PassDataSource","quicksight:UpdateDataSource","quicksight:DeleteDataSource","quicksight:UpdateDataSourcePermissions"]
            },
        ],
        
        SslProperties={
            'DisableSsl': False
        },
        Tags=[
            {
                'Key': 'Name',
                'Value': 'Metrics-Analytics'
            },
        ]
    )

    datasource_arn = datasource_response.get('Arn')

    time.sleep(1)

    response = client.create_data_set(
        AwsAccountId= account_id,
        DataSetId='MetricsDataSet-' + random_string,
        Name='MetricsDataSet-' +  random_string,
        PhysicalTableMap={
            'string': {
                'RelationalTable': {
                    'DataSourceArn': datasource_arn,
                    'Schema': 'public',
                    'Name': 'metrics',
                    'InputColumns': [
                        {
                            'Name': 'type',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'workload',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'context',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'tenant_id',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'tenant_name',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'tenant_tier',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'timerecorded',
                            'Type': 'DATETIME'
                        },
                        {
                            'Name': 'metric_name',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'metric_unit',
                            'Type': 'STRING'
                        },
                        {
                            'Name': 'metric_value',
                            'Type': 'INTEGER'
                        },
                        {
                            'Name': 'meta_data',
                            'Type': 'STRING'
                        },
                    ]
                }
            }
        },
        ImportMode='DIRECT_QUERY',
        Permissions=[
            {
                'Principal': quicksight_user_arn,
                'Actions': ["quicksight:DescribeDataSet","quicksight:DescribeDataSetPermissions","quicksight:PassDataSet","quicksight:DescribeIngestion","quicksight:ListIngestions","quicksight:UpdateDataSet","quicksight:DeleteDataSet","quicksight:CreateIngestion","quicksight:CancelIngestion","quicksight:UpdateDataSetPermissions"]
            },
        ],
        Tags=[
            {
                'Key': 'Name',
                'Value': 'Metrics-Analytics'
            },
        ]
    )


def randomString(stringLength=8):
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(stringLength))

def input_with_default(msg, default):
    value = input(msg + " [" + default + "] : ")
    if value == "":
        value = default
    return value

if __name__ == "__main__":
    try:
        current_session =  boto3.session.Session()
        current_credentials = current_session.get_credentials().get_frozen_credentials()

        region = input_with_default("Enter region associated with Quicksight account", current_session.region_name)
        access_key = input_with_default("Enter AWS Access Key associated with Quicksight account", current_credentials.access_key)
        secret_key = input_with_default("Enter AWS Secret Key associated with Quicksight account", current_credentials.secret_key)

        sts_client = boto3.client('sts', region_name=region, aws_access_key_id=access_key, aws_secret_access_key=secret_key)
        quicksight_client = boto3.client('quicksight', region_name=region, aws_access_key_id=access_key, aws_secret_access_key=secret_key)
        cfn_client = boto3.client('cloudformation', region_name=region, aws_access_key_id=access_key, aws_secret_access_key=secret_key)

        aws_account_id = sts_client.get_caller_identity().get('Account')

        response = quicksight_client.list_users(
                        AwsAccountId=aws_account_id,
                        Namespace='default'
                    )
        
        quick_user_name = input_with_default("Quicksight user name", response['UserList'][0].get('UserName')) 

        redshift_response = int(input_with_default("Enter 0 to provide Redshift connection details manually. Enter 1 to read the connection details from the deployed Cloudformation stack", "1"))

        if (redshift_response == 0):
            redshift_host_name = input("Redshift host name (endpoint address) for accessing metrics data: ")
            redshift_cluster_id = input("Redshift cluster id accessing metrics data: ")
            redshift_port_number = int(input_with_default("Redshift cluster port number", "8200"))
            redshift_database_name = input_with_default("Redshift database name", "metricsdb")
        else:
            cfn_name = input("Cloudformation stack name for Metrics & Analytics: ")
            outputs = cfn_client.describe_stacks()['Stacks'][0]['Outputs']
            
            for output in outputs:
                if output['OutputKey'] == 'RedshiftCluster':
                    redshift_cluster_id = output['OutputValue']
                if output['OutputKey'] == 'RedshiftEndpointAddress':
                    redshift_host_name = output['OutputValue']
                if output['OutputKey'] == 'RedshiftEndpointPort':
                    redshift_port_number = int(output['OutputValue'])
                if output['OutputKey'] == 'RedshiftDatabaseName':
                    redshift_database_name = output['OutputValue']


        quicksight_user = quicksight_client.describe_user(
            UserName=quick_user_name,
            AwsAccountId=aws_account_id,
            Namespace='default'
        )

        quicksight_user_arn = (quicksight_user.get('User').get('Arn'))
        
        redshift_user_name = input_with_default("Redshift user name for accessing metrics data", "metricsadmin")
        redshift_password = input("Redshift password for accessing metrics data: ")

        
        SetupQuicksight(client=quicksight_client,account_id=aws_account_id, quicksight_user_arn=quicksight_user_arn, random_string=randomString(), 
            redshift_host=redshift_host_name, redshift_port=redshift_port_number, redshift_db_name=redshift_database_name, 
            redshift_cluster_id=redshift_cluster_id, redshift_user_name=redshift_user_name, redshift_password=redshift_password)
        
    except Exception as e:
        print("error occured")
        print(e)
        raise