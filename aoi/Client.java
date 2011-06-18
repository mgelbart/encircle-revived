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

    // Initialize the GUI for human players
    if (!config.nonhuman) {
      DrawUtils.initialize(new JFrame(), 700, 700);
    }


    try {

      DataInputStream stateStream = new DataInputStream(socket.getInputStream());
      DataOutputStream controlStream = new DataOutputStream(socket.getOutputStream());


      /* Process the handshake */

      // Read the player number assigned to self.
      Byte cmd = stateStream.readByte();
      assert(cmd == 'p');
      config.setPlayerNo(stateStream.readInt());


      // Parse the arena
      cmd = stateStream.readByte();
      assert(cmd == 'a');
      int arenaSize = stateStream.readInt(); // Length of the serialized arena in bytes
      assert(arenaSize > 0);

      // First read all bytes into a buffer.
      byte[] arenaBuf = new byte[arenaSize];
      stateStream.readFully(arenaBuf);

      // Now parse from the buffer.
      Arena arena = Arena.parse(new ByteArrayInputStream(arenaBuf));
      

      /* End of handshake. */

	    
      System.out.println(config);


      /* Run the intelligence in a separate thread so that parsing/serializing
         doesn't block thinking. */
      Intelligence intel;
      if (config.nonhuman)
        intel = new Nonhuman(config, arena);
      else
        intel = new HigherBeing(config, arena);

      GameState game;

      System.out.println("Ready...");

      while (true) {
        game = parseGameState(stateStream);
        intel.processGameStateUpdate(game);

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
