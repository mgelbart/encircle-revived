package aoi;

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.*;

import aoi.Logger.RepetitionRate;

import unreliablestream.UnreliableStream;

/**
 *  A class for connecting to the server and parsing the game state
 *  and sending the desired control parameters.
 */
public class Client implements Runnable {

  // Path to the default configuration file.
  private static final String DEFAULTCONFIGPATH = "client.conf";

  // The number of milliseconds to wait for a UDP reply before fallning back to TCP
  private static final int UDPTIMEOUT = 4000;
  
  // The JLabel for the GUI.
  private JButton canvas;

  /**
   * Constructs a new Client using the given configuration
   * and with the GUI drawn in frame.
   *
   * If config is null, the default configuration file
   * is used.
   */
  public Client(JButton canvas) {
    this.canvas = canvas;

    // Load the default config file if none has been loaded so far. Used in the Applet.
    if (!ClientConfiguration.isLoaded()) {
      InputStream stream = Client.class.getClassLoader().getResourceAsStream(DEFAULTCONFIGPATH);
      ClientConfiguration.parse(stream);
    }

  }

  public void run() {

    // Make sure StdAudio is initialized by playing a silent sound clip.
    StdAudio.play(new double[]{0., 0.});
    
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
      DrawUtils.initialize(canvas);
    }


    try {

      DataInputStream stateStream = new DataInputStream(socket.getInputStream());
      BufferedOutputStream controlStream = new BufferedOutputStream(socket.getOutputStream());


      /* Process the handshake */

      // Read the player number assigned to self.
      Byte cmd = stateStream.readByte();
      assert(cmd == 'p');
      ClientConfiguration.setPlayerNo(stateStream.readInt());
      
      // Read the UnreliableStream id assigned to self.
      cmd = stateStream.readByte();
      assert(cmd == 'u');
      ClientConfiguration.unreliableStreamId = stateStream.readShort();

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
      Arena.setCurrent(arena);
      
      Logger.println(Logger.RepetitionRate.FEW, ClientConfiguration.asString());
      
      /* End of handshake. */


      /* Run the intelligence in a separate thread so that parsing/serializing
         doesn't block thinking. */
      Intelligence intel;
      if (ClientConfiguration.nonhuman)
        intel = new Nonhuman();
      else
        intel = new HigherBeing();

      /* Set up the UDP connection */
      UnreliableStream uStream = null;
      if (! ClientConfiguration.tcpOnly) {
        Logger.println(Logger.RepetitionRate.FEW, "Attempting to connect using UDP (id: " + ClientConfiguration.unreliableStreamId + ").");
        uStream = new UnreliableStream(new DatagramSocket(), socket.getInetAddress(), Server.UDPPORT, ClientConfiguration.unreliableStreamId);
      
        // This passes received game states to the intelligence.
        uStream.registerReceiver(new GameStateReceiver(intel));
        
        // connect and wait until we start getting some replies.
        uStream.connect();
        
        final long start = System.currentTimeMillis();
        while (uStream.sendSuccessRate() < 0) {
          Logger.println(RepetitionRate.FEW, "waiting for UDP reply... ");
          if (System.currentTimeMillis() - start > UDPTIMEOUT) {
            Logger.println(RepetitionRate.FEW, "Falling back to TCP. ");
            ClientConfiguration.tcpOnly = true;
            break;
          }
          Thread.sleep(500);
        }
    }

      // This timer task keeps asking the intelligence for new parameters
      // and sends them to the server.
      TimerTask sendTask = new SendParamsTask(intel, controlStream, uStream);
      Timer sendTimer = new Timer();
      sendTimer.schedule(sendTask, 0, SimulationUtils.TIMESTEP);

      Logger.println(Logger.RepetitionRate.FEW, "Ready...");
      
      GameState game = null;

      while (true) {
        if (ClientConfiguration.tcpOnly) {
          // use this thread to parse game states from the TCP stream
          game = GameState.parse(stateStream, game);
          intel.processGameStateUpdate(game);
        } else {
          // in UDP mode the GameStateReceiver handles this so there's nothing left for this thread to do!
          Thread.sleep(10000);
        }
      }

    } catch (Exception e) {

      try {
        socket.close();
      } catch (Exception ee) {
      }
      
      Logger.println(Logger.RepetitionRate.FEW, e.toString());
      e.printStackTrace(System.err);
      assert(false);
    }

    //socket.close();
  }

  /**
   * main() for running the client directly outside an Applet.
   */
  public static void main(String argv[]) throws Exception {

    ClientConfiguration.parse(System.in);

    Client client;

    if (!ClientConfiguration.nonhuman) {
      client = new Client(DrawUtils.createDefaultCanvas());
    } else {
      client = new Client(null);
    }

    client.run(); // Can just run this from the main() thread.
  }

  
  private static class SendParamsTask extends TimerTask {

    Intelligence intelligence;
    
    // UDP only
    UnreliableStream udpOut;
    
    // TCP only
    BufferedOutputStream tcpBufOut;
    
    // both UDP and TCP use this wrapper
    DataOutputStream dataOut;
    
    PlayerControlParameters last;
    double lastUdpSuccessRate = Double.NEGATIVE_INFINITY;
    
    public SendParamsTask(Intelligence intelligence, OutputStream tcpOut, UnreliableStream udpOut) {
      this.intelligence = intelligence;
      this.udpOut = udpOut;
      
      if (tcpOut != null) {
        this.tcpBufOut = new BufferedOutputStream(tcpOut);
        this.dataOut = new DataOutputStream(this.tcpBufOut);
      } else {
        this.tcpBufOut = null;
        this.dataOut = null;
      }
    }
    
    public void run() {
      
      // Send all queued control parameters.
      PlayerControlParameters[] pcp = intelligence.getNewControlParameters();
      
      ByteArrayOutputStream byteOut = null;
      
      // Choose UDP or TCP stream
      if (!ClientConfiguration.tcpOnly) {
        byteOut = new ByteArrayOutputStream();
        dataOut = new DataOutputStream(byteOut);
      } else {
        assert(dataOut != null);
      }
      
      if (pcp.length == 0) {
 
        if (last == null) return; // the game didn't start yet.
        last.serialize(dataOut); // keep sending the same command if there's nothing new

      } else {

        // Otherwise send all new parameters.
        for (PlayerControlParameters p : pcp)
          p.serialize(dataOut);

        last = pcp[pcp.length-1];
      }
      
      // Flush UDP or TCP stream
      if (byteOut != null) {
        
        udpOut.send(byteOut.toByteArray());
        
        final double s = udpOut.sendSuccessRate();
        if (s < 0.999 && s < lastUdpSuccessRate) Logger.println(RepetitionRate.INFREQUENT, "UDP success rate: " + s);
        lastUdpSuccessRate = s;
        
      } else {
        
        try {
          tcpBufOut.flush();
        } catch (IOException e) {
          e.printStackTrace();
          assert(false);
        }
        
      }
      
    }

  }
  
  private static class GameStateReceiver implements UnreliableStream.Receiver {
    GameState prev = null;
    Intelligence intelligence;
    
    GameStateReceiver(Intelligence intelligence) {
      this.intelligence = intelligence;
    }

    @Override
    public boolean received(byte[] data, UnreliableStream src) {
      if (data.length == 0) return false; // just a keep alive packet
      
      ByteArrayInputStream bin = new ByteArrayInputStream(data);
      
      GameState newState = GameState.parse(new DataInputStream(bin), prev);
      prev = newState;
      
      intelligence.processGameStateUpdate(newState);
      
      return true;
    }
    
  }
}

