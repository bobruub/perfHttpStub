package httpStub;

/**
class: HttpStubWorker
Purpose: processes an inbound message
Notes:
Author: Tim Lane
Date: 07/05/2022
**/
import javax.json.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.io.*;
import redis.clients.jedis.JedisPool;

public class HttpStubWorker extends stubWorker implements Runnable {

  public static final String DELIMITED_TYPE = "Delimited";
  private final Socket clientSocket;
  private JedisPool jedisPool;
  private String requestResponseString;
  private String dataVariablesString;
  private int defaultPause;

  public HttpStubWorker(Socket clientSocket,
      JsonObject configObject,
      JsonObject dataVariableObject,
      String requestResponseString,
      JedisPool jedisPool,
      String dataVariablesString) {
    this.clientSocket = clientSocket;
    this.requestResponseString = requestResponseString;
    this.dataVariablesString = dataVariablesString;
    this.jedisPool = jedisPool;
  }

  @Override

  public void run() {
    PrintWriter out = null;
    BufferedReader in = null;
    HttpInputStream inStream = null;
    int postLength = 0;
    //
    // create an input and output socket
    //
    try {
      out = new PrintWriter(clientSocket.getOutputStream(), true);
      in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    } catch (Exception e) {
      System.out.println("tcpWorker: error opening socket: " + e);
      return;
    }
    Vector<String> inputMsgLines = new Vector<String>();
    String firstLine = null;
    String httpRespCode = "200 OK";
    String responseMsg = null;
    String errorMessage = null;
    try {
      inStream = new HttpInputStream(clientSocket.getInputStream());
      // get header details
      String searchLine = null;
      int lineCntr = 0;
      while ((searchLine = inStream.readLine()).length() > 0) {
        if (lineCntr == 0) {
          firstLine = searchLine;
          lineCntr++;
        }
        inputMsgLines.addElement(searchLine);
        /*
         * get the content length for put post messages if required.
         */
        if (searchLine.contains("Content-Length:")) {
          postLength = Integer.parseInt(searchLine.substring(16, (searchLine.length())));
        }

      }
    } catch (Exception e) {
      System.out.println("httpStubWorker: error in getting header : " + e);
      httpRespCode = "400 Bad Request";
      errorMessage = "error in getting header : " + e;
    }
    // if a POST or PUT message you'll need to read the remainder of the message
    // based on the content length
    String postLine = null;
    try {
      if (!firstLine.contains("GET")) {
        postLine = inStream.readLine(postLength);
        inputMsgLines.addElement(postLine);
      }
    } catch (Exception e) {
      System.out.println("httpStubWorker: error in reading body : " + e);
      httpRespCode = "400 Bad Request";
      errorMessage = "error in reading body : " + e;
    }
    //
    // now we have the message need to determine what response template to use and how long to pause
    //
    boolean responseTemplateMessage = setResponseTemplate(inputMsgLines,requestResponseString);
    if (responseTemplateMessage) {
      responseMsg = getTemplate(0);
      defaultPause = Integer.parseInt(getTemplate(1));
    } else {
      errorMessage = "no matching response template found";
      httpRespCode = "400 Bad Request";
    }        
    //
    // loop through input message for variable extraction
    //
    String processMessage = processVariables(inputMsgLines, dataVariablesString, jedisPool,responseMsg);
    if (processMessage != null) {
      errorMessage = processMessage;
      httpRespCode = "400 Bad Request";
    }
    //
    // loop through all the variables and replace them in the response message
    //
    for (int x = 0; x < getVariableLength(); x++) {
      String variableName = getVariableName(getVariable(x));
      String variableValue = getVariable(variableName);
      String searchSequence = "%" + variableName + "%";
      if (responseMsg.contains(searchSequence)) {
        responseMsg = responseMsg.replace(searchSequence, variableValue);
      }
    }

    // if theres a http response code other than 200 then replace it here
    if (!httpRespCode.contains("200")) {
      responseMsg = responseMsg.replace("200 OK", httpRespCode);
    }

    // need to process the content length AFTER all other replacements are done
    String contentLength = processContentLengthType(responseMsg);
    responseMsg = responseMsg.replace("%Content-Length%", contentLength);

    //
    // time to write the output
    //
    try {
      BufferedOutputStream outStream = new BufferedOutputStream(clientSocket.getOutputStream());
      // if not a close message
      if (!inputMsgLines.toString().contains("Connection: close")) {
        // pause the response simulating downstream delay
        try {
          Thread.sleep(defaultPause);
        } catch (InterruptedException e) {
          System.out.println("httpStubWorker: Error in thread sleep : " + e);
          e.printStackTrace();
        }
        // write the response message to outpuit stream
        outStream.write(responseMsg.getBytes());

      }
      // close all open file handles
      outStream.flush();
      outStream.close();
      inStream.close();
      clientSocket.close();
    } catch (java.io.IOException e) {
      System.out.println("httpStubWorker: error in writing response : " + e);
    }

    // close all resources
    try {
      in.close();
      out.close();
      clientSocket.close();
    } catch (Exception e) {
      System.out.println("httpStubWorker: error closing resources: " + e);
    }
  }

}
