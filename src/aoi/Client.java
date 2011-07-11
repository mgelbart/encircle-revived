package aoi;

import java.io.*;
import java.net.*;
import java.awt.*;
import javax.swing.*;

/**
 *  A class for connecting to the server and parsing the game state
 *  and sending the desired control parameters.
 */
public class Client implements Runnable {

  // Path to the default configuration file.
  private static final String DEFAULTCONFIGPATH = "client.conf";

  // The JLabel for the GUI.
  private JLabel gui;

  /**
   * Constructs a new Client using the given configuration
   * and with the GUI drawn in frame.
   *
   * If config is null, the default configuration file
   * is used.
   */
  public Client(JLabel gui) {
    this.gui = gui;

    // Load the default config file if none has been loaded so far. Used in the Applet.
    if (!ClientConfiguration.isLoaded()) {
      InputStream stream = Client.class.getClassLoader().getResourceAsStream(DEFAULTCONFIGPATH);
      ClientConfiguration.parse(stream);
    }

  }

  public void run() {

    Socket socket = null;

    try {

      // Read input options from stdin.
      socket = new Socket(ClientConfiguration.serverUrl, ClientConfiguration.serverPort);

      // Don't buffer data before sending TCP packets.
      socket.setTcpNoDelay(true);

    } catch (Exception e) {
      Logger.println(Logger.RepetitionRate.FEW, e.toString());
      assert(false);
    } 

    // Initialize the GUI for human players
    if (!ClientConfiguration.nonhuman) {
      DrawUtils.initialize(gui);
    }


    try {

      DataInputStream stateStream = new DataInputStream(socket.getInputStream());
      BufferedOutputStream bufferedControlStream = new BufferedOutputStream(socket.getOutputStream());
      DataOutputStream controlStream = new DataOutputStream(bufferedControlStream);


      /* Process the handshake */

      // Read the player number assigned to self.
      Byte cmd = stateStream.readByte();
      assert(cmd == 'p');
      ClientConfiguration.setPlayerNo(stateStream.readInt());


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

	    
      Logger.println(Logger.RepetitionRate.FEW, ClientConfiguration.asString());


      /* Run the intelligence in a separate thread so that parsing/serializing
         doesn't block thinking. */
      Intelligence intel;
      if (ClientConfiguration.nonhuman)
        intel = new Nonhuman(arena);
      else
        intel = new HigherBeing(arena);

      Logger.println(Logger.RepetitionRate.FEW, "Ready...");
      
      GameState game = null;

      while (true) {
        game = parseGameState(stateStream, game);
      
        intel.processGameStateUpdate(game);
      
        PlayerControlParameters pcp = intel.getNewControlParameters(); 
      
        sendControlParameters(controlStream, pcp);
        bufferedControlStream.flush();
      }

    } catch (Exception e) {

      try {
        socket.close();
      } catch (Exception ee) {
      }

      Logger.println(Logger.RepetitionRate.FEW, e.toString());
      assert(false);
    }

    //socket.close();
  }

  private static GameState parseGameState(DataInputStream i, GameState prevState) {
    return GameState.parse(i, prevState);
  }

  private static void sendControlParameters(DataOutputStream o, PlayerControlParameters params) {

    if (params == null) return;

    params.serialize(o);

  }

  /**
   * main() for running the client directly outside an Applet.
   */
  public static void main(String argv[]) throws Exception {

    ClientConfiguration.parse(System.in);

    Client client;

    if (!ClientConfiguration.nonhuman) {

      // The main application window
      JFrame frame = new JFrame();

      // The JLabel on which the whole GUI is drawn.
      JLabel gui = new JLabel();
      gui.setPreferredSize(new Dimension(700, 700));

      frame.setContentPane(gui);

      frame.setResizable(false);
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      frame.setTitle("AoI");
      JMenuBar menubar = new JMenuBar();
      menubar.add(new JMenu("Press here to focus!"));
      frame.setJMenuBar(menubar); // hackz... just need something focusable for keyboard events.
      frame.pack();
      frame.setVisible(true);


      client = new Client(gui);

    } else {
      client = new Client(null);
    }

    client.run(); // Can just run this from the main() thread.
  }

}

