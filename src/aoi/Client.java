package aoi;

import java.awt.Canvas;
import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

import aoi.Logger.Rep;
import aoi.Protocol.ClientState;

import unreliablestream.UnreliableStream;

/**
 *  A class for connecting to the server and parsing the game state
 *  and sending the desired control parameters.
 */
public class Client implements Runnable {

  // Path to the default configuration file.
  private static final String DEFAULTCONFIGPATH = "client.conf";
  
  // The JLabel for the GUI.
  private static Canvas canvas;

  // The UDP stream used for receiving game states and sending control parameters.
  private static UnreliableStream uStream = null;
  
  /**
   * Constructs a new Client using the given configuration
   * and with the GUI drawn in frame.
   *
   * If config is null, the default configuration file
   * is used.
   */
  public Client(Canvas canvas) {
    this.canvas = canvas;

    // Load the default config file if none has been loaded so far. Used in the Applet.
    if (!ClientConfiguration.isLoaded()) {
      InputStream stream = Client.class.getClassLoader().getResourceAsStream(DEFAULTCONFIGPATH);
      ClientConfiguration.parse(stream);
    }

  }
  
  /**
   * Parse the received TCP message and return the resulting client state.
   */
  private ClientState nextState(ClientState current, String msg) {
    switch (current) {
    
//    case REQUESTEDMATCH:
//      return (parseMatchMade(msg))? ClientState.READYFORMATCH : current;
//      
//    case READYFORMATCH:
//      return (parsePlayerNo(msg))? ClientState.RECEIVEDPLAYERNO : current;
    
    }

    assert(false);
    return null;
  }

  public void run() {

    StdAudio.init();
    
    Socket socket = null;
    InputStream tcpIn = null;
    DataOutputStream tcpOut = null;

    // Read input options from stdin.
    try {
      socket = new Socket(ClientConfiguration.serverUrl, ClientConfiguration.serverPort);
      tcpIn = socket.getInputStream();
      tcpOut = new DataOutputStream(socket.getOutputStream());
    } catch (UnknownHostException e1) {
      Logger.println(Rep.FEW, "unknown host: "+ClientConfiguration.serverUrl+":"+ClientConfiguration.serverPort);
      return;
    } catch (IOException e1) {
      Logger.println(Rep.FEW, "failed to connect to game server");
      e1.printStackTrace();
      return;
    }


    // Initialize the GUI for human players
    if (!ClientConfiguration.nonhuman) {
      DrawUtils.initialize(canvas);
    }



    try {
      /* Process the handshake */

      // Read the matchid, player number, and unreliable stream id assigned to self.
      String msg = Protocol.readNextMsg(tcpIn);

      long[] matchParams = Protocol.parseMatchStarting(msg);
      if (matchParams==null) { Logger.println(Rep.FEW, "couldn't parse match params."); return; }
      
      ClientConfiguration.matchId = matchParams[0];
      ClientConfiguration.setPlayerNo((int) matchParams[1]);
      ClientConfiguration.unreliableStreamId = (short) matchParams[2];

      // ack the match params
      tcpOut.writeBytes(Protocol.ack());
      tcpOut.flush();
      
      // Parse the arena
      // First read all bytes into a buffer.
      msg = Protocol.readNextMsg(tcpIn);
      String arenaBuf = Protocol.parseSendingArena(msg);
      if (arenaBuf == null || arenaBuf.isEmpty()) { Logger.println(Rep.FEW, "couldn't parse arena."); return; }

      // ack the arena
      tcpOut.writeBytes(Protocol.ack());
      tcpOut.flush();
      
      // Now parse from the buffer.
      Arena arena = Arena.parse(new ByteArrayInputStream(arenaBuf.getBytes()));
      Arena.setCurrent(arena);
      
      Logger.println(Logger.Rep.FEW, ClientConfiguration.asString());
    } catch (IOException e1) {
      Logger.println(Rep.FEW, "handshake failed.");
      e1.printStackTrace();
      return;
    }
    /* End of handshake. */


    /* Run the intelligence in a separate thread so that parsing/serializing
       doesn't block thinking. */
    Intelligence intel;
    if (ClientConfiguration.nonhuman)
      intel = new Nonhuman();
    else
      intel = new HigherBeing();

    /* Set up the UDP connection */
    if (! ClientConfiguration.tcpOnly) {
      Logger.println(Logger.Rep.FEW, "Attempting to connect using UDP (id: " + ClientConfiguration.unreliableStreamId + ").");
      try {
        uStream = new UnreliableStream(new DatagramSocket(), socket.getInetAddress(), Server.UDPPORT, ClientConfiguration.unreliableStreamId);

        // This passes received game states to the intelligence.
        uStream.registerReceiver(new GameStateReceiver(intel));
        
        // connect and wait until we start getting some replies.
        uStream.connect();
      } catch (SocketException e) {
        Logger.println(Rep.FEW, "couldn't instantiate uStream.");
        e.printStackTrace();
        return;
      } catch (IOException e) {
        Logger.println(Rep.FEW, "couldn't connect uStream.");
        e.printStackTrace();
        return;
      }

      
      final long start = System.currentTimeMillis();
      while (uStream.sendSuccessRate() < 0) {
        Logger.println(Rep.FEW, "waiting for UDP reply... ");
        if (System.currentTimeMillis() - start > UnreliableStream.TIMEOUT) {
          Logger.println(Rep.FEW, "Failed to get a UDP reply. ");
          return;
        }
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          continue;
        }
      }
    }

    // This timer task keeps asking the intelligence for new parameters
    // and sends them to the server.
    TimerTask sendTask = new SendParamsTask(intel, tcpOut, uStream);
    Timer sendTimer = new Timer("send-timer");
    sendTimer.schedule(sendTask, 0, SimulationUtils.TIMESTEP);

    Logger.println(Logger.Rep.FEW, "Ready...");


    // wait for the server to tell that the game is over
    try {
      String msg = Protocol.readNextMsg(tcpIn);
      assert(Protocol.isMatchEnded(msg));
    } catch (IOException e1) {
      Logger.println(Logger.Rep.FEW, "Ready...");
      e1.printStackTrace();
    }

    Logger.println(Logger.Rep.FEW, "Game ended.");

    // don't request a rematch.
    
    Logger.println(Logger.Rep.FEW, "Game ended.");
    
    uStream.disconnect();
    
    try {
      socket.close();
    } catch (IOException e) {
      Logger.println(Rep.FEW, "couldn't close TCP socket.");
      e.printStackTrace();
    }

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
  
  public static Integer lagEstimate() {
    if (ClientConfiguration.tcpOnly)
      return 200; // we have no way of estimating lag for TCP
    else {
      assert(uStream != null);
      return uStream.averageLag();
    }
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
    double lastUdpSendSuccessRate = Double.NEGATIVE_INFINITY;
    double lastUdpReicSuccessRate = Double.NEGATIVE_INFINITY;
    
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
        
        if (udpOut.hasDied()) {
          Logger.println(Rep.FEW, "UnreliableStream has timed out. Falling back to TCP.");
          ClientConfiguration.tcpOnly = true;
          assert(tcpBufOut != null);
          dataOut = new DataOutputStream(this.tcpBufOut);
        }
        
      } else {
        assert(dataOut != null);
      }
      
      if (pcp.length == 0) {
 
        if (last == null) return; // the game didn't start yet.
        try {
          last.serialize(dataOut); // keep sending the same command if there's nothing new
        } catch (IOException e) {
          Logger.println(Rep.FEW, "Failed to send command parameters to server (effectiveOn=" + last.effectiveOn + " using " + ((ClientConfiguration.tcpOnly)? "TCP.":"UDP."));
          Logger.println(Rep.FEW, "Assuming we disconnected. Ending Client execution.");
          e.printStackTrace();
        }

      } else {

        // Otherwise send all new parameters.
        for (PlayerControlParameters p : pcp)
          try {
            p.serialize(dataOut);
          } catch (IOException e) {
            Logger.println(Rep.FEW, "Failed to send command parameters to server (effectiveOn=" + p.effectiveOn + " using " + ((ClientConfiguration.tcpOnly)? "TCP.":"UDP."));
            Logger.println(Rep.FEW, "Assuming we disconnected. Ending Client execution.");
            e.printStackTrace();
          }

        last = pcp[pcp.length-1];
      }
      
      // Flush UDP or TCP stream
      if (byteOut != null) {
        
        try {
          udpOut.send(byteOut.toByteArray());
        } catch (IOException e) {
          Logger.println(Rep.INFREQ, "Failed to send using UDP.");
          e.printStackTrace();
        }
        
        final double s = udpOut.sendSuccessRate();
        final double r = udpOut.receiveSuccessRate();
        if ((s < 0.999 || r < 0.999) && (s < lastUdpSendSuccessRate || r < lastUdpReicSuccessRate)) Logger.println(Rep.INFREQ, "UDP send/receive success rate: " + s + " / " +r);
        lastUdpSendSuccessRate = s;
        lastUdpReicSuccessRate = r;
        
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
      
      GameState newState;
      try {
        newState = GameState.parse(new DataInputStream(bin), prev);
      } catch (IOException e) {
        Logger.println(Rep.FEW, "Failed to parse game state from server.");
        e.printStackTrace();
        return false;
      }
      prev = newState;
      
      intelligence.processGameStateUpdate(newState);
      
//      if (newState.time%300 == 0) {
//        final Integer avglag = src.averageLag();
//        final Integer peslag = src.pessimisticLag();
//        if (avglag != null && peslag != null)
//          Logger.println(Rep.INFREQ, "lag estimate. average: " + avglag
//                                    +" pessimistic: " + peslag + " ms");
//      }
      
      return true;
    }
    
  }
}

