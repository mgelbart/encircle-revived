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

  // Player number in the game.
  public static int playerNo;

  // True if the player is controlled entirely by the computer.
  public static boolean nonhuman;

  // The number of milliseconds to predict the game into the future,
  // a.k.a. client-side-prediction.
  // Set to zero to disable prediction.
  public static int predictFuture;

  
  public static void setPlayerNo(int p) {
    playerNo = p;
  }


  public static String asString() {
    return "server:\n" + serverUrl
      + "\nport:\n" + serverPort
      + "\nplayer no:\n" + playerNo
      + "\nnon-human:\n" + nonhuman
      + "\npredict:\n" + predictFuture;
  }


  public static void parse(InputStream in) {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));

    try {

      reader.readLine();
      serverUrl = reader.readLine();

      reader.readLine();
      serverPort = Integer.parseInt(reader.readLine());

      reader.readLine();
      nonhuman = Boolean.parseBoolean(reader.readLine());

      reader.readLine();
      predictFuture = Integer.parseInt(reader.readLine());

    } catch (Exception e) {

      Logger.println(Logger.RepetitionRate.FEW, e.toString());
      assert(false);

    }
  
    assert(serverPort > 1023);
    assert(serverPort < 65536);
    
  }

  public static boolean isLoaded() {
    return serverUrl != null;
  }
  
}
