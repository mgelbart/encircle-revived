package aoi;

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;
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
      BufferedOutputStream controlStream = new BufferedOutputStream(socket.getOutputStream());


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

      // This times task keeps asking the intelligence for new parameters
      // and sends them to the server.
      TimerTask sendTask = new SendParamsTask(intel, controlStream);
      Timer sendTimer = new Timer();
      sendTimer.schedule(sendTask, 0, SimulationUtils.TIMESTEP);

      Logger.println(Logger.RepetitionRate.FEW, "Ready...");
      
      GameState game = null;

      while (true) {
        // This keeps parsing and passing on new
        // game states to the Intelligence.
        game = parseGameState(stateStream, game);
        intel.processGameStateUpdate(game);
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

  
  private class SendParamsTask extends TimerTask {

    Intelligence intelligence;
    BufferedOutputStream out;
    
    PlayerControlParameters last;
    
    public SendParamsTask(Intelligence intelligence, BufferedOutputStream out) {
      this.intelligence = intelligence;
      this.out = out;
    }
    
    public void run() {
      
      // Send all queued control parameters.
      PlayerControlParameters[] pcp = intelligence.getNewControlParameters();
      
      if (pcp.length == 0) {
 
        if (last == null) return; // the game didn't start yet.
        last.serialize(out); // keep sending the same command if there's nothing new

      } else {

        // Otherwise send all new parameters.
        for (PlayerControlParameters p : pcp)
          p.serialize(out);

        last = pcp[pcp.length-1];
      }
      
      try {
        out.flush();
      } catch (IOException e) {
        e.printStackTrace();
        assert(false);
      }
      
    }

  }
  
}

