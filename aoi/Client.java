package aoi;

import java.io.*;
import java.net.*;
import javax.swing.*;

/**
 *  A class for connecting to the server and parsing the game state
 *  and sending the desired control parameters.
 */
public class Client implements Runnable {

  // Path to the default configuration file.
  private static final String DEFAULTCONFIGPATH = "client.conf";

  // The actual configuration:
  private ClientConfiguration config;

  // The JLabel for the GUI.
  private JLabel gui;

  /**
   * Constructs a new Client using the given configuration
   * and with the GUI drawn in frame.
   *
   * If config is null, the default configuration file
   * is used.
   */
  public Client(ClientConfiguration config, JLabel gui) {
    this.gui = gui;
    this.config = config;

    if (config == null) {
      InputStream stream = Client.class.getClassLoader().getResourceAsStream(DEFAULTCONFIGPATH);
      this.config = ClientConfiguration.parse(stream);
    }

  }

  public void run() {

    Socket socket = null;

    try {

      // Read input options from stdin.
      socket = new Socket(config.serverUrl, config.serverPort);

      // Don't buffer data before sending TCP packets.
      socket.setTcpNoDelay(true);

    } catch (Exception e) {
      System.err.println(e);
      assert(false);
    } 

    // Initialize the GUI for human players
    if (!config.nonhuman) {
      DrawUtils.initialize(gui, 700, 700);
    }


    try {

      DataInputStream stateStream = new DataInputStream(socket.getInputStream());
      BufferedOutputStream bufferedControlStream = new BufferedOutputStream(socket.getOutputStream());
      DataOutputStream controlStream = new DataOutputStream(bufferedControlStream);


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
        bufferedControlStream.flush();
      }

    } catch (Exception e) {

      try {
        socket.close();
      } catch (Exception ee) {
      }

      System.err.println(e);
      assert(false);
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

  /**
   * main() for running the client directly outside an Applet.
   */
  public static void main(String argv[]) throws Exception {

    ClientConfiguration config = ClientConfiguration.parse(System.in);

    Client client;

    if (!config.nonhuman) {

      // The main application window
      JFrame frame = new JFrame();

      // The JLabel on which the whole GUI is drawn.
      JLabel gui = new JLabel();

      frame.setContentPane(gui);

      frame.setResizable(true);
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      frame.setTitle("AoI");
      JMenuBar menubar = new JMenuBar();
      menubar.add(new JMenu("Press here to focus!"));
      frame.setJMenuBar(menubar); // hackz... just need something focusable for keyboard events.
      frame.pack();
      frame.setVisible(true);


      client = new Client(config, gui);

    } else {
      client = new Client(config, null);
    }

    client.run(); // Can just run this from the main() thread.
  }

}
