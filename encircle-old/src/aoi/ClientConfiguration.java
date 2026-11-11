package aoi;

import java.io.*;

/**
 *  A class used for storing client options, like
 * server address, enabled modules, etc.
 */
public abstract class ClientConfiguration {

  // The server address, without port information.
  public static String serverUrl = null;

  // The server port for receiving the game state and sending
  // the control parameters.
  public static int serverPort;
  
  // the current match id
  public static Long matchId = null;
  
  // The id of the UnreliableStream used to connect to the server.
  public static short unreliableStreamId;

  // Don't even attempt to use UDP.
  public static boolean tcpOnly = false;
  
  // Player number in the game.
  public static int playerNo;

  // True if the player is controlled entirely by the computer.
  public static boolean nonhuman;

  // The number of milliseconds to predict the game into the future,
  // a.k.a. client-side-prediction.
  // Set to zero to disable prediction.
  public static int predictFuture;

  // Use mouse input.
  public static boolean useMouse;
  
  
  public static void setPlayerNo(int p) {
    playerNo = p;
  }


  public static String asString() {
    return "server:\n" + serverUrl
      + "\nport:\n" + serverPort
      + "\nunreliable stream id:\n" + unreliableStreamId
      + "\nTCP only:\n" + tcpOnly
      + "\nplayer no:\n" + playerNo
      + "\nnon-human:\n" + nonhuman
      + "\npredict:\n" + predictFuture;
  }


  public static void parse(InputStream in) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));

    reader.readLine();
    serverUrl = reader.readLine();

    reader.readLine();
    serverPort = Integer.parseInt(reader.readLine());

    reader.readLine();
    tcpOnly = Boolean.parseBoolean(reader.readLine());

    reader.readLine();
    nonhuman = Boolean.parseBoolean(reader.readLine());

    reader.readLine();
    predictFuture = Integer.parseInt(reader.readLine());

    reader.readLine();
    useMouse = Boolean.parseBoolean(reader.readLine());
  
    assert(serverPort > 1023);
    assert(serverPort < 65536);
    
  }

  public static boolean isLoaded() {
    return serverUrl != null;
  }
  
}
