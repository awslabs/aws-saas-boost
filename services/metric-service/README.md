Query Object for Post:
```json
{"id":"query1",
 "tenantId" : "[]",   //Optional if you want to query single tenant
 "timeRangeName" : "HOUR_24",  //Enumeration from TimeRange
 "stat":"Sum",  //use Sum or Average for most cases
 //-->These are only necessary if timeRangeName not specified
 "startDate":"2020-06-05T23:00:00Z",
 "endDate":"2020-06-06T01:00:00Z",
  "period":43200,
 //<--
 "dimensions":
    [
        {"metricName":"RequestCount","nameSpace":"AWS/ApplicationELB"},
    ],
  "topTenants":true,  //flag to indicate whether top tenants array is returned
  "statsMap":true   //flag to indicate whether stats P90, P70, P50, Average, and Sum are returned
} 
```

Sample Post command:
```shel
curl -X POST -H "Content-Type: application/json Accept: application/json" -d '{"id":"query1","startDate":"2020-06-05T23:00:00Z","endDate":"2020-06-06T01:00:00Z","dimensions": [{"metricName":"RequestCount","nameSpace":"AWS/ApplicationELB"}],"stat":"Sum","period":43200,"topTenants":true}' "https://0123456789.execute-api.us-west-2.amazonaws.com/Prod/metrics/query"
```