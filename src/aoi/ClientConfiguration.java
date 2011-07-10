package aoi;

import java.io.*;

/**
 *  A class used for storing client options, like
 * server address, enabled modules, etc.
 */
public class ClientConfiguration {

  // The server address, without port information.
  public final String serverUrl;

  // The server port for receiving the game state and sending
  // the control parameters.
  public final int serverPort;

  // Player number in the game.
  public int playerNo;

  // True if the player is controlled entirely by the computer.
  public final boolean nonhuman;


  public ClientConfiguration(String serverUrl, int serverPort,
                             int playerNo, boolean nonhuman) {
    this.serverUrl = serverUrl;
    this.serverPort = serverPort;
    this.playerNo = playerNo;
    this.nonhuman = nonhuman;

    assert(serverPort > 1023);
    assert(serverPort < 65536);
  }


  public void setPlayerNo(int p) {
    this.playerNo = p;
  }


  public String toString() {
    return "server:\n" + serverUrl
      + "\nport:\n" + serverPort
      + "\nplayer no:\n" + playerNo
      + "\nnon-human:\n" + nonhuman;
  }


  public static ClientConfiguration parse(InputStream in) {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));

    String serverUrl;
    int serverPort;
    boolean nonhuman;

    try {

      reader.readLine();
      serverUrl = reader.readLine();

      reader.readLine();
      serverPort = Integer.parseInt(reader.readLine());

      reader.readLine();
      nonhuman = Boolean.parseBoolean(reader.readLine());

    } catch (Exception e) {

      System.err.println(e.toString());
      assert(false);
      return null;

    }

    return new ClientConfiguration(serverUrl, serverPort,
                                   -1, nonhuman);
  }

}
