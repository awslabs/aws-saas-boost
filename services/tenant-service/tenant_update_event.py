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

import boto3
import random
import json
from datetime import datetime, timedelta, timezone


EVENT_BUS_NAME = "sb-events-test3-us-west-2"
TENANT_IDS = ["f124d8ba-b3f4-4529-95de-41ed318342d1"]
EVENT_TYPE = "Tenant Update Resources"

def putEvents(events):
  ebClient = boto3.client("events")
  response = ebClient.put_events(Entries=events)
  print(response)

def createEvent():
  tenantID = random.choice(TENANT_IDS)
  # Milliseconds required
  timestamp = int(datetime.now().timestamp() * 1000)

  resources = {
    "alb": "http://my.alb2.com",
    "ecs": "http://new.ecs2.com",
    "rds": "http://new.rds2.com"
  }
  print("Resources: ", str(resources))

  detail = json.dumps({
    "tenantId": tenantID,
    "resources": json.dumps(resources),
    "timestamp": timestamp
  })

  event = {
    'Source': 'saas-boost',
    'Detail': str(detail),
    'DetailType': EVENT_TYPE,
    'EventBusName': EVENT_BUS_NAME }
  return event

  # detail = json.dumps({
  #   "TenantID": tenantID,
  #   "PlanID": PLAN_ID,
  #   "Timestamp": timestamp})

  # event = {
  #   #'Time': datetime.now().time(),
  #   'Source': 'saas-boost',
  #   'Detail': str(detail),
  #   'DetailType': EVENT_TYPE,
  #   'EventBusName': EVENT_BUS_NAME }
  # return event


if __name__ == "__main__":

  events = []
  event = createEvent()
  events.append(event)
  response = putEvents(events)
  print(response)
