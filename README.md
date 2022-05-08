# perfHttpStub
perfHttpStub

## Overview

This is a java based http stub to support statements enrichment services call to the accoutn api.

The reason for this new framwork is it needs to get information via an external data store, redis, and mountebank does not support this

![Perf Http Overview](images/PerfHTTPStub-SES/png)

## Functions

| Function        | Triggered By           | Response Delay  |
| ------------- |:-------------:| -----:|
| errors | any error - returns 400 bad request      |   0 ms |
|01-get-transactional-records|path contains transactional-record |100ms|
|02-transactional-records|body regex accounts:(.+?): |100ms|

## Framework

### libs

httpStub.jar
javax.json-1.0.jar
commons-pool2-2.6.2.jar
jedis-2.8.1.jar:lib
jsr166.jar

### config and data

**./data/config.json - configuration of the stub**

```
{
  "ListenerVersion": "1.0",
  "socketTimeout": 0,
  "clientTimeout": 0,
  "threadCount": 100,
  "port": 8090,
  "redisServer": "192.168.0.81",
  "redisPort": 6379,
  "defaultPause": 100
}
```
**data/datavariables.json - contains all the variables required for the stub to function.**

| Name        | Description           |||
| ------------- |-------------|-------------|-------------|
| name | variable name|applies to all|must be unique|
| type| the type of variable|applies to all|
| allowBypass| if the name is not defined in the response but still required then enter false|applies to all|boolean (true/false)|
||string||a fixed string variable|
||substring||an extract of a line based on start and end positions
||regex||an extract of a line based of standard regex format
||redis||an extract from a redis in memory database (hashs only)
|format|the data requred to action the type||
||value|string only|any valid string|
||regex|regex only|any valid regex|
||startPos|substring only|number of the starting position of the extract|
||endPos|substring only|number of the ending position of the extract or EOL for end of line|
||redisSetName|redis only| the name of the redis set to read from (hashes only)|
||redisSetName|redis only| the name of the redis key of the set to read from (hashes only)|

**any variables (e.g. redis lookups) which reference other variables must be set at the BOTTOM of the file.**

```
{
	"__comment1__": "any variables (e.g. redis lookups) which reference other variables must be set at the BOTTOM of the file.",
	"variable": [
		{
			"name" : "nextUrl",
			"type" : "string",
			"allowBypass": "true",
			"format" : [
				{
					"value" : "api.congo.beta.tab.com.au/v1/account-service/tab/accounts/999999999/transactional-records"					
				}
			]
		},
		{
			"name" : "accountNumber",
			"type" : "regex",
			"allowBypass": "false",
			"format" : [
				{
					"regex" : "accounts/(.+?)/"
				}
			]
		},
		{
			"name" : "selfUrl",
			"type" : "string",
			"allowBypass": "true",
			"format" : [
				{
					"value" : "api.congo.beta.tab.com.au/v1/account-service/tab/accounts/999999999/transactional-records"					
				}
			]
		},

		{
			"name" : "contentType",
			"type" : "substring",
			"allowBypass": "true",
			"format" : [
				{
					"startPos" : "5",
					"endPos" : "EOL"
				}
			]
		},
		{
			"name" : "TabcorpAuth",
			"type" : "substring",
			"action": "replace",
			"allowBypass": "true",
			"format" : [
				{
					"startPos" : "13",
					"endPos" : "EOL"
				}
			]
		},
		
		{
			"name" : "Content-Type",
			"type" : "substring",
			"action": "variable",
			"allowBypass": "true",
			"format" : [
				{
					"startPos" : "14",
					"endPos" : "EOL"
				}
			]
		},
		{
			"name" : "transactions",
			"type" : "redis",
			"action": "variable",
			"allowBypass": "true",
			"format" : [
				{
					"redisSetName" : "ses_statements",
					"redisKeyName" : "accountNumber"
				}
			]
		}
	]
}
```
**data/requestresponse.json - contains all the response templates required for the stub to function.**

stub works on interrogating the input stream based on criteria in this file and provides a response template for processing.

Data variables in the content tag **should** match name(s) from the datavariables.json file and **must** be prefixed and suffixed with an percentage sign %

e.g. varible named **Content-Type** exists as a template tag **%Content-Type%**

| Name        | Description           |||
| ------------- |-------------|-------------|-------------|
| type | the type of lookup to perform|string|**mandatory**|
| type | the name of the reponse message|string|**mandatory**|
||path|searches through the inbound URL|string| 
||body|searches through the inbound body|string|
|lookupWith|how to perform the search|"regex" or "string"| **mandatory**|
||string|matches a string|applies to all types|string|
||regex|matches using standard regex format|string|
|lookupValue|the value to lookup|either a string or regex|applies to all types|
|pause|the amount of time in milliseconds to pause|a value in milliseconds|**mandatory**|
|content|the message template for processing|must be a single line, line breaks using \n|**mandatory**| 

```
{
	"response": [{
        "name": "01-Transactional-records",
		"type": "path",
		"lookupWith": "regex",
		"lookupValue": "accounts/(.+?)/",
        "pause": "100",
		"contents": "HTTP/1.1 200 OK\nTabcorpAuth: %TabcorpAuth%\nContent-Length: %Content-Length%\nConnection: close\nContent-Type: %Content-Type%\n\n{\"transactions\": [%transactions%],\"_links\": {\"self\": \"https://%selfUrl%\",\"next\": \"https://%nextUrl%?transactionRef=7761910\"},\"authentication\": {\"token\": \"%TabcorpAuth%\",\"inactivityExpiry\": \"2022-05-02T09:20:47.817Z\",\"absoluteExpiry\": \"2022-05-02T09:20:47.305Z\",\"scopes\": [\"*\"]}}"
	},
    {
        "name": "02-Transactional-records",
		"type": "body",
		"lookupWith": "string",
		"lookupValue": "accounts",
        "pause": "100",
		"contents": "HTTP/1.1 200 OK\nTabcorpAuth: %TabcorpAuth%\nContent-Length: %Content-Length%\nConnection: close\nContent-Type: %Content-Type%\n\n{\"transactionsssss\": [%transactions%],\"_links\": {\"self\": \"https://%selfUrl%\",\"next\": \"https://%nextUrl%?transactionRef=7761910\"},\"authentication\": {\"token\": \"%TabcorpAuth%\",\"inactivityExpiry\": \"2022-05-02T09:20:47.817Z\",\"absoluteExpiry\": \"2022-05-02T09:20:47.305Z\",\"scopes\": [\"*\"]}}"
	},{
        "name": "99-Default-404",
		"type": "path",
		"lookupWith": "string",
		"lookupValue": "/",
        "pause": "100",
		"contents": "HTTP/1.1 404 Not Found\nContent-Length: %Content-Length%\nConnection: close\nContent-Type: %Content-Type%\n\n 404 NOT FOUND"
	}]
}
```

## TODO


