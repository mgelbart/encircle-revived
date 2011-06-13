package aoi;

import java.io.*;
import java.net.*;
import javax.swing.JFrame;

/**
 *  A class for connecting to the server and parsing the game state
 *  and sending the desired control parameters.
 */
public class Client {

  public static void main(String argv[]) throws Exception {


    Socket socket;

    ClientConfiguration config = ClientConfiguration.parse(System.in);
	
    // Read input options from stdin.
    socket = new Socket(config.serverUrl, config.serverPort);

    // Don't buffer data before sending TCP packets.
    socket.setTcpNoDelay(true);

    // Read the Arena from a local file for now...
    Arena arena = null;
    try {
      arena = Arena.parse(Client.class.getClassLoader().getResourceAsStream(Arena.ARENA_METADATA_FILENAME));

      System.out.println("Parsed the arena: ");
      System.out.println(arena);

    } catch (Exception e) {
      System.out.println(e.toString());
      assert(false);
    }


    // Initialize the GUI for human players
    if (!config.nonhuman) {
      DrawUtils.initialize(new JFrame(), 700, 700);
    }


    try {

      DataInputStream stateStream = new DataInputStream(socket.getInputStream());
      DataOutputStream controlStream = new DataOutputStream(socket.getOutputStream());


      /* Process the handshake */
      Byte cmd = stateStream.readByte();
      assert(cmd == 'p');
      config.setPlayerNo(stateStream.readInt());

	    
      System.out.println(config);


      /* Run the intelligence in a separate thread so that parsing/serializing
         doesn't block thinking. */
      Intelligence intel;
      if (config.nonhuman)
        intel = new Nonhuman(config, arena);
      else
        intel = new HigherBeing(config, arena);

      Thread intelThread = new Thread(intel);
      intelThread.start();

      GameState game;

      System.out.println("Ready...");

      while (true) {
        game = parseGameState(stateStream);
        intel.processGameStateUpdate(game);
        intelThread.interrupt(); // in case she was sleeping!

        sendControlParameters(controlStream, intel.getNewControlParameters());
      }

    } catch (Exception e) {
      socket.close();

      throw e;
    }

    //socket.close();
  }

  private static GameState parseGameState(DataInputStream i) {
    return GameState.parse(i);
  }

  private static void sendControlParameters(DataOutputStream o, PlayerControlParameters params) {

    if (params == null) return;

    params.serialize(o);

  }
}
