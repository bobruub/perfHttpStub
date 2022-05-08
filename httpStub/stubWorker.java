package httpStub;

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
    private Jedis jedis;

    public void setTemplate(String templateValue) {
        templateContent.add(templateValue);
        return;
    }

    public String getTemplate(int templateNumber) {
        return templateContent.get(templateNumber);
    }

    public void setVariable(String variableName, String variableValue) {
        // if element already exists do not insert again
        if (!variableContent.contains(variableName)) {
            this.variableContent.add(variableName + "~" + variableValue);
        }
        return;
    }

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

    public int getVariableLength() {
        return variableContent.size();
    }

    public boolean setResponseTemplate(Vector<String> inputMsgLines, String requestResponseString) {

        JSONParser parser = null;
        JSONObject responseVariables = null;
        JSONArray responseArray = null;

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
        while (loopCounter < responseArray.size()) {
            JSONObject variable = (JSONObject) responseArray.get(loopCounter);
            loopCounter++;
            String responseType = (String) variable.get("type");
            responseName = (String) variable.get("name");
            if (responseType.equals("path")) {
                String responseLookupWith = (String) variable.get("lookupWith");
                String responseLookupValue = (String) variable.get("lookupValue");
                currentLookupLine = inputMsgLines.get(0);
                if (responseLookupWith.equals("string")) {
                    String responseContents = (String) variable.get("contents");
                    if (currentLookupLine.contains(responseLookupValue)) {
                        templatePause = (String) variable.get("pause");
                        responseTemplate = responseContents;
                        responseTemplateMessage = true;
                        break; // found a match so break
                    }
                } else if (responseLookupWith.equals("regex")) {
                    String regexSearch = responseLookupValue;
                    var myPattern = Pattern.compile(regexSearch);
                    Matcher matcher = myPattern.matcher(currentLookupLine);
                    if (matcher.find()) {
                        responseTemplate = (String) variable.get("contents");
                        templatePause = (String) variable.get("pause");
                        responseTemplateMessage = true;
                        break;
                    }
                }
            } else if (responseType.equals("body")) {
                currentLookupLine = inputMsgLines.toString();
                String responseLookupWith = (String) variable.get("lookupWith");
                String responseLookupValue = (String) variable.get("lookupValue");
                if (responseLookupWith.contains("string")) {
                    String responseContents = (String) variable.get("contents");
                    if (currentLookupLine.contains(responseLookupValue)) {
                        responseTemplate = responseContents;
                        templatePause = (String) variable.get("pause");
                        responseTemplateMessage = true;
                        break; // found a match so break
                    }
                } else if (responseLookupWith.equals("regex")) {
                    String regexSearch = responseLookupValue;
                    var myPattern = Pattern.compile(regexSearch);
                    Matcher matcher = myPattern.matcher(currentLookupLine);
                    if (matcher.find()) {
                        responseTemplate = (String) variable.get("contents");
                        templatePause = (String) variable.get("pause");
                        responseTemplateMessage = true;
                        break;
                    }
                }
            }
        }
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
            // if (!responseMsg.contains("%"+variableName+"%") &&
            // allowBypass.equals("true")){
            // continue; // didnt find current name is response so move on
            // }
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
        // loop through all the input lines search for variables which require intput
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

        boolean returnFlag = false;

        if (!responseMsg.contains("%" + varName + "%") && byPassFlag.equals("true")) {
            returnFlag = true;
        }

        return returnFlag;
    }
}
