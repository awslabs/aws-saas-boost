#!/usr/bin/env python
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

#{
#  "TenantID": "sub|1234",
#  "ProductCode": "example-billing-plan",
#  "Quantity": 5,
#  "Timestamp": 1586898989000,
#  "EventType": "BILLING"
#}


import boto3
import random
import json
from datetime import datetime, timedelta, timezone
import time


#EVENT_BUS_NAME = "BillingEventBridge"
EVENT_BUS_NAME = "sb-events-aug22-us-west-2"
#PRODUCT_CODE = "example-billing-plan"
EVENT_TYPE = "Billing System Setup"
SOURCE="saas-boost"

def putEvents(events):
  ebClient = boto3.client("events")
  response = ebClient.put_events(Entries=events)
  print(response)

def createEvent():
  # Milliseconds required
  timestamp = int(datetime.now().timestamp() * 1000)

  detail = json.dumps({
    "Message": "System Setup",
    "Timestamp": timestamp})

  event = {
    #'Time': datetime.now().time(),
    'Source': SOURCE,
    'Detail': str(detail),
    'DetailType': EVENT_TYPE,
    'EventBusName': EVENT_BUS_NAME }
  return event


if __name__ == "__main__":

  events = []
  event = createEvent()
  print(event)
  events.append(event)
  response = putEvents(events)
  print(response)