package httpStub;

/**
class: stubWorker
Purpose: processes for inbound messages
Notes:
Author: Tim Lane
Date: 07/05/2022
**/
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class stubWorker {

    List<String> variableContent = new ArrayList<String>();
    List<String> templateContent = new ArrayList<String>();

    //
    // set and get array for the template message, this is used in other processes
    // for extracting details about the template.
    //
    public void setTemplate(String templateValue) {
        templateContent.add(templateValue);
        return;
    }

    public String getTemplate(int templateNumber) {
        return templateContent.get(templateNumber);
    }

    //
    // set and get array for the variables, when the are processed are stored in in a key/pair manner
    // variableName~VaribaleValue
    //     
    public void setVariable(String variableName, String variableValue) {
        // if element already exists do not insert again
        if (!variableContent.contains(variableName)) {
            this.variableContent.add(variableName + "~" + variableValue);
        }
        return;
    }

    //
    // get variable value by passing the variable name
    //
    public String getVariable(String variableName) {
        String variableValue = null;
        for (var temp : variableContent) {
            if (temp.contains(variableName)) {
                String[] variableParts = temp.split("~");
                variableValue = variableParts[1];
                break; // found the value by name no need to continue
            }
        }
        return variableValue;
    }

    //
    // get variable value by passing an element number
    // good for looping through all varibale array
    //
    public String getVariable(int variableNumber) {
        return variableContent.get(variableNumber);
    }

    public String getVariableName(String variable) {
        String variableName = null;
        for (var temp : variableContent) {
            if (temp.contains(variable)) {
                String[] variableParts = temp.split("~");
                variableName = variableParts[0];
                break; // found the name no need to continue
            }
        }
        return variableName;
    }

    //
    // get the count of elements in the variable array
    //
    public int getVariableLength() {
        return variableContent.size();
    }

    //
    // determine the response message based on the input crieteria
    //{
    //  "name": "01-Transactional-records",
	// 	"type": "path",
	// 	"lookupWith": "regex",
	// 	"lookupValue": "accounts/(.+?)/",
    //  "pause": "100",
	// 	"contents": "HTTP/1.1 200 OK\nTabcorpAuth: %TabcorpAuth%\nContent-Length: %Content-Length%\nConnection: close\nContent-Type: %Content-Type%\n\n{\"transactions\": [%transactions%],\"_links\": {\"self\": \"https://%selfUrl%\",\"next\": \"https://%nextUrl%?transactionRef=7761910\"},\"authentication\": {\"token\": \"%TabcorpAuth%\",\"inactivityExpiry\": \"2022-05-02T09:20:47.817Z\",\"absoluteExpiry\": \"2022-05-02T09:20:47.305Z\",\"scopes\": [\"*\"]}}"
	// }
    //
    public boolean setResponseTemplate(Vector<String> inputMsgLines, String requestResponseString) {

        JSONParser parser = null;
        JSONObject responseVariables = null;
        JSONArray responseArray = null;

        //
        // copy all json response messages into an array
        //
        boolean responseTemplateMessage = false;
        try {
            parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
            responseVariables = (JSONObject) parser.parse(requestResponseString);
            responseArray = (JSONArray) responseVariables.get("response");
        } catch (Exception e) {
            String processMessage = "error in json processing : " + e;
            System.out.println("stubWorker: " + processMessage + " - " + e);
        }

        int loopCounter = 0;
        String currentLookupLine = null;
        String responseTemplate = null;
        String templatePause = null;
        String responseName = null;
        //
        // loop through response message array looking for a match
        //
        while (loopCounter < responseArray.size()) {
            JSONObject variable = (JSONObject) responseArray.get(loopCounter);
            loopCounter++;
            String responseType = (String) variable.get("type");
            responseName = (String) variable.get("name");
            //
            // if the current response message array is of type path
            // 
            if (responseType.equals("path")) {
                String responseLookupWith = (String) variable.get("lookupWith");
                String responseLookupValue = (String) variable.get("lookupValue");
                //
                // extract the first line from the input message to see if it matches
                // GET 'http://192.168.0.81:8090/v1/account-service/tab/accounts/99999936/
                //
                currentLookupLine = inputMsgLines.get(0);
                //
                // if the current response message array is of lookup type string
                //
                if (responseLookupWith.equals("string")) {
                    //
                    // if the string exists then this is the correct message
                    //
                    if (currentLookupLine.contains(responseLookupValue)) {
                        templatePause = (String) variable.get("pause");
                        responseTemplate = (String) variable.get("contents");
                        responseTemplateMessage = true;
                        break; // found a match so break
                    }
                } else if (responseLookupWith.equals("regex")) {
                    //
                    // if the current response message array is of lookup type regex
                    //
                    String regexSearch = responseLookupValue;
                    var myPattern = Pattern.compile(regexSearch);
                    Matcher matcher = myPattern.matcher(currentLookupLine);
                    //
                    // if the regex matches then this is the correct message
                    //
                    if (matcher.find()) {
                        responseTemplate = (String) variable.get("contents");
                        templatePause = (String) variable.get("pause");
                        responseTemplateMessage = true;
                        break; // found a match so break
                    }
                }
            } else if (responseType.equals("body")) {
            //
            // if the current response message array is of type body
            // copy the entire input string for processing
            //
                currentLookupLine = inputMsgLines.toString();
                String responseLookupWith = (String) variable.get("lookupWith");
                String responseLookupValue = (String) variable.get("lookupValue");
                //
                // if the current response message array is of lookup type string
                //
                if (responseLookupWith.contains("string")) {
                    //
                    // if the string exists then this is the correct message
                    //
                    if (currentLookupLine.contains(responseLookupValue)) {
                        responseTemplate = (String) variable.get("contents");
                        templatePause = (String) variable.get("pause");
                        responseTemplateMessage = true;
                        break; // found a match so break
                    }
                } else if (responseLookupWith.equals("regex")) {
                    //
                    // if the current response message array is of lookup type regex
                    //
                    String regexSearch = responseLookupValue;
                    var myPattern = Pattern.compile(regexSearch);
                    Matcher matcher = myPattern.matcher(currentLookupLine);
                    //
                    // if the regex matches then this is the correct message
                    //
                    if (matcher.find()) {
                        responseTemplate = (String) variable.get("contents");
                        templatePause = (String) variable.get("pause");
                        responseTemplateMessage = true;
                        break;
                    }
                }
            }
        }
        //
        // if a teplate resppnse message is found store in in a template array
        //
        if (responseTemplateMessage){
            setTemplate(responseTemplate);
            setTemplate(templatePause);
            setTemplate(responseName);
        }

        return responseTemplateMessage;
    }

    public String processVariables(Vector<String> inputMsgLines, String dataVariablesString, JedisPool jedisPool,
            String responseMsg) {

        String processMessage = null;
        String currLine = null;
        int i;
        JSONParser parser = null;
        JSONObject dataVariables = null;
        JSONArray variableArray = null;
        try {
            parser = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
            dataVariables = (JSONObject) parser.parse(dataVariablesString);
            variableArray = (JSONArray) dataVariables.get("variable");
        } catch (Exception e) {
            processMessage = "error in json processing : " + e;
            System.out.println("stubWorker: " + processMessage + " - " + e);
        }
        //
        // loop required for those variables which aren't dependent on indivuduals lines
        // or any other variables being set primarily fixed strings
        //
        int loopCounter = 0;
        String variableReplace = null;
        while (loopCounter < variableArray.size()) {
            JSONObject variable = (JSONObject) variableArray.get(loopCounter);
            loopCounter++;
            String variableName = (String) variable.get("name");
            String allowBypass = (String) variable.get("allowBypass");
            // check the varibale is required in the repsonse message before processng
            // unless bypass is not set to true
            if (bypassVariable(variableName, allowBypass, responseMsg)) {
                continue;
            }
            String variableType = (String) variable.get("type");
            JSONArray formatArray = (JSONArray) variable.get("format");
            int oddsCounter = 0;
            variableReplace = null;
            JSONObject format = (JSONObject) formatArray.get(oddsCounter);
            if (variableType.equals("string")) {
                variableReplace = (String) format.get("value");
            }
            if (variableReplace != null) {
                setVariable(variableName, variableReplace);
            }
        }
        //
        // loop through all the input lines search for variables which require input
        // data
        //
        for (i = 0; i < inputMsgLines.size(); i++) {
            currLine = inputMsgLines.get(i);
            if (currLine.length() == 0) {
                continue; // skip empty lines
            }
            loopCounter = 0;
            variableReplace = null;
            while (loopCounter < variableArray.size()) {
                JSONObject variable = (JSONObject) variableArray.get(loopCounter);
                loopCounter++;
                String variableName = (String) variable.get("name");
                String allowBypass = (String) variable.get("allowBypass");
                // check the varibale is required in the repsonse message before processng
                // unless bypass is not set to true
                if (bypassVariable(variableName, allowBypass, responseMsg)) {
                    continue;
                }
                String variableType = (String) variable.get("type");
                JSONArray formatArray = (JSONArray) variable.get("format");
                int oddsCounter = 0;
                int formatEndPos = 0;
                variableReplace = null;
                JSONObject format = (JSONObject) formatArray.get(oddsCounter);
                if (variableType.equals("substring")) {
                    if (currLine.contains(variableName)) {
                        String formatStartPosString = (String) format.get("startPos");
                        int formatStartPos = Integer.parseInt(formatStartPosString);
                        String formatEndPosString = (String) format.get("endPos");
                        if (formatEndPosString.equals("EOL")) {
                            formatEndPos = currLine.length();
                        } else {
                            formatEndPos = Integer.parseInt(formatEndPosString);
                        }
                        variableReplace = currLine.substring(formatStartPos, formatEndPos);
                    }
                } else if (variableType.equals("regex")) {
                    String regexSearch = (String) format.get("regex");
                    var myPattern = Pattern.compile(regexSearch);
                    Matcher matcher = myPattern.matcher(currLine.toString());
                    if (matcher.find()) {
                        variableReplace = matcher.group(1);
                    }
                }
                if (variableReplace != null) {
                    setVariable(variableName, variableReplace);
                }

            }
        }
        //
        // another loop required for those which depend on variables already being set
        // above
        //
        loopCounter = 0;
        variableReplace = null;
        while (loopCounter < variableArray.size()) {
            JSONObject variable = (JSONObject) variableArray.get(loopCounter);
            loopCounter++;
            String variableName = (String) variable.get("name");
            String allowBypass = (String) variable.get("allowBypass");
            // check the varibale is required in the repsonse message before processng
            // unless bypass is not set to true
            if (bypassVariable(variableName, allowBypass, responseMsg)) {
                continue;
            }
            String variableType = (String) variable.get("type");
            JSONArray formatArray = (JSONArray) variable.get("format");
            int oddsCounter = 0;
            variableReplace = null;
            JSONObject format = (JSONObject) formatArray.get(oddsCounter);
            if (variableType.equals("redis")) {
                Jedis jedis = jedisPool.getResource();
                String redisSetName = (String) format.get("redisSetName");
                String redisKeyName = (String) format.get("redisKeyName");
                String variableValue = getVariable(redisKeyName);
                variableReplace = jedis.hget(redisSetName, variableValue);
                jedis.close();
            }
            if (variableReplace != null) {
                setVariable(variableName, variableReplace);
            }
        }
        return processMessage;
    }

    public String processContentLengthType(String responseMsg) {
        String variableValue = null;
        // splitting on empty line, header in part 0, body is remainder
        String[] parts = responseMsg.split("(?:\r\n|[\r\n])[ \t]*(?:\r\n|[\r\n])");
        // if response message has multiple blank line breaks
        // then loop through all adding the length
        if (parts.length > 2) {
            int bodyLen = 0;
            // start on second part of string, avoids header
            for (int x = 1; x < parts.length; x++) {
                bodyLen += parts[x].length();
            }
            bodyLen += parts.length; // add blank lines counter
            variableValue = Integer.toString(bodyLen);
        } else {
            // just determine length of second part.
            String stringBody = parts[1];
            variableValue = Integer.toString(stringBody.length());
        }

        return variableValue;
    }

    public boolean bypassVariable(String varName, String byPassFlag, String responseMsg) {
//
// if the varibale name does not exists in the templated response message 
// AND the variable bypassFlag = true then send back a negative response
//
        boolean returnFlag = false;

        if (!responseMsg.contains("%" + varName + "%") && byPassFlag.equals("true")) {
            returnFlag = true;
        }

        return returnFlag;
    }
}
