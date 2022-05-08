package httpStub;
/**
class: httpStub
Purpose: implements a http stup, sets up a socket and listens for incoming messages
Notes:
Author: Tim Lane
Date: 07/05/2022
**/
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class httpStub {

  private ServerSocket serverSocket;
  static JsonObject configObject = null;
  static JsonObject requestResponseObject = null;
  static String requestResponseString = null;
  static String dataVariablesString = null;
  static JsonObject dataVariableObject = null;
  static int socketTimeout = 0;
  static int clientTimeout = 0;
  static int threadCount = 0;
  static int port = 0;
  static int contentFirstPos = 0;
  static int contentLastPos = 0;
  static String redisServer = null;
  static int redisPort = 0;
  static Jedis jedis;
  static JedisPool jedisPool;
  static String httpStubVersion = "1.4";


  // Create an HTTPStub for a particular TCP port
  public httpStub() {
    // this.httpProperties = httpProperties;
    // this.logFileProperties = logFileProperties;
  }

  // static Logger logger = Logger.getLogger(httpStub.class);

  public static void main(String[] args) {

    //
    // load the configuration file
    //
    try {
      String fileName = "./config/config.json";
      System.out.println("httpStub: opening file: " + fileName);
      try {
        InputStream fis = new FileInputStream(fileName);
        JsonReader reader = Json.createReader(fis);
        configObject = reader.readObject();
        reader.close();
        fis.close();
        socketTimeout = configObject.getInt("socketTimeout");
        clientTimeout = configObject.getInt("clientTimeout");
        threadCount = configObject.getInt("threadCount");
        port = configObject.getInt("port");
        redisServer = configObject.getString("redisServer");
        redisPort = configObject.getInt("redisPort");
        System.out.println("httpStub: httpStubVersion: " + httpStubVersion);
        System.out.println("httpStub: socketTimeout: " + socketTimeout);
        System.out.println("httpStub: clientTimeout: " + clientTimeout);
        System.out.println("httpStub: threadCount: " + threadCount);
        System.out.println("httpStub: redis: " + redisServer + ":" + redisPort);
      } catch (Exception e) {
        System.out.println("httpStub: error processing file: " + fileName + "..." + e);
        System.exit(1);
      }
      //
      // get the request and response template
      //
      fileName = "./config/requestresponse.json";
      System.out.println("httpStub: opening file: " + fileName);
      try {
        byte[] bytes = Files.readAllBytes(Paths.get(fileName));
        requestResponseString = new String(bytes);
      } catch (Exception e) {
        System.out.println("httpStub: error processing file: " + fileName + "..." + e);
        System.exit(1);
      }
      //
      // get the variables from the json file 
      //
      fileName = "./config/datavariables.json";
      System.out.println("httpStub: opening file: " + fileName);
      try {
        byte[] bytes = Files.readAllBytes(Paths.get(fileName));
        dataVariablesString = new String(bytes);
      } catch (Exception e) {
        System.out.println("httpStub: error processing file: " + fileName + "..." + e);
        System.exit(1);
      }

      httpStub httpStub = new httpStub();
      httpStub.RunIsolator();
    } catch (Exception e) {
      System.out.println("httpStub: error processing file: " + e);
      e.printStackTrace();
      System.exit(1);
    }

  }

  ServerSocket getServerSocket() throws Exception {
    System.out.println("httpStub: Preparing a regular HTTP Server Socket on server:port " + port);
    return new ServerSocket(port);

  }

  public void RunIsolator() {
    //
    // setup redis server
    //
    String redisServer = configObject.getString("redisServer");
    int redisPort = configObject.getInt("redisPort");
    try {
      JedisPoolConfig config = new JedisPoolConfig();
      jedisPool = new JedisPool(config, redisServer, redisPort);
      jedis = jedisPool.getResource();
      System.out.println("httpStub: Redis running. PING - " + jedis.ping());
      jedis.close();
    } catch (Exception e) {
      System.out.println("httpStub: error opening redis: " + e);
      return;
    }
    /*
     * setup thread pool
     */
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    boolean socketLoop = true;
    boolean connectionLoop = true;
//
// wait for a socket call
//
    while (socketLoop) {

      serverSocket = null;
      try {
        // open a socket
        serverSocket = getServerSocket();
        serverSocket.setSoTimeout(5 * 1000);
      } catch (Exception e) {
        System.out.println("Unable to listen on " + port);
        e.printStackTrace();
        System.exit(1);
      }
      // * listen for connections...
      Socket clientConnection = null;
      while (connectionLoop) {
        try {
          // accept connections a connection on a new socket
          clientConnection = serverSocket.accept();
          clientConnection.setSoTimeout(5 * 1000);
          // Handle the connection with a separate thread
          if (clientConnection != null) {
            Runnable httpStubWorker = new HttpStubWorker(clientConnection,
                configObject,
                dataVariableObject,
                requestResponseString,
                jedisPool,
                dataVariablesString);
            executor.execute(httpStubWorker);
            
          }
        } catch (SocketTimeoutException e) {
          // System.out.println("socket timeout " + connectionLoopCntr + ".");
          // DO NOTHING - The timeout just allows the checking of the restart
          // request and will only close the socket server if a restart request
          // has been issued
        } catch (Exception e) {
          System.out.println("httpStub: socket exception.");
          e.printStackTrace();
        } finally {

        }

      }
      /*
       * shutdown threads
       */
      executor.shutdown();
      while (!executor.isTerminated()) {
      }

    }

  }

}
