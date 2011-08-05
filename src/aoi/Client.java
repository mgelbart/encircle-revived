package aoi;

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

import aoi.Logger.Rep;

import unreliablestream.UnreliableStream;

/**
 *  A class for connecting to the server and parsing the game state
 *  and sending the desired control parameters.
 */
public class Client implements Runnable {

  // Path to the default configuration file.
  private static final String DEFAULTCONFIGPATH = "client.conf";
  
  // The JLabel for the GUI.
  private static GameCanvas canvas;

  // The UDP stream used for receiving game states and sending control parameters.
  private static UnreliableStream uStream = null;
  
  // The TCP socket used to communicate with the game server.
  private static Socket socket = null;
  
  // Indicates that the client should exit.
  private static boolean stop = false;
  
  // The intelligence that decides what control params to send.
  private static Intelligence intel = null;
  
  // A timer that keeps sending new control parameters periodically.
  private static Timer sendTimer;
  
  /**
   * Constructs a new Client using the given configuration
   * and with the GUI drawn in frame.
   *
   * If config is null, the default configuration file
   * is used.
   */
  public Client(GameCanvas canvas) {
    this.canvas = canvas;

    // Load the default config file if none has been loaded so far. Used in the Applet.
    if (!ClientConfiguration.isLoaded()) {
      InputStream stream = Client.class.getClassLoader().getResourceAsStream(DEFAULTCONFIGPATH);
      ClientConfiguration.parse(stream);
    }
  }
  
  public void run() {
    StdAudio.init();
    
    /*
     * Ask the matchmaker for the matchId.
     */
    Socket mmSock;
    OutputStream mmOut;
    InputStream mmIn;
    try {
      mmSock = new Socket(ClientConfiguration.serverUrl,
          (ClientConfiguration.nonhuman)? Matchmaker.AIPORT : Protocol.MATCHMAKERTCPPORT);
      mmOut = mmSock.getOutputStream();
      mmIn = mmSock.getInputStream();
    } catch (UnknownHostException e) {
      Logger.println(Rep.FEW, "Unknown matchmaker.");
      e.printStackTrace();
      return;
    } catch (IOException e) {
      Logger.println(Rep.FEW, "Failed to connect to matchmaker.");
      e.printStackTrace();
      return;
    }

    // The game server address and match id that we are going
    // to receive from the matchmaker.
    String gameServ = null;
    long matchId = 0;
    
    // this file is created while the client is in queue
    File inQueue = null;
    if (ClientConfiguration.nonhuman)
      inQueue = new File("out/queueing_"
          + (int) (Integer.MAX_VALUE*Math.random()));

    
    try {
      // keep sending keep alives until we get a response
      long lastKeepAlive = System.currentTimeMillis()-2*Protocol.MATCHMAKERKEEPALIVETIMEOUT;
      boolean sentAIRequest = false;
      while (mmIn.available() == 0) {
        if (System.currentTimeMillis() - lastKeepAlive > Protocol.MATCHMAKERKEEPALIVETIMEOUT/2)
          mmOut.write(Protocol.keepAlive().getBytes());
        
        if (GameCanvas.wantsAI && !sentAIRequest) {
          // optionally send a request for an AI opponent
          Logger.println(Rep.FEW, "Requesting AI opponent from matchmaker.");
          mmOut.write(Protocol.requestAI().getBytes());
          sentAIRequest = true;
        }
        
        if (inQueue != null) inQueue.createNewFile();
        
        try { Thread.sleep(200); }
        catch (InterruptedException e) { Logger.println(Rep.FEW, "interrupted"); }
      }

      if (inQueue != null) inQueue.delete();

      // parse the server name and match id.
      String msg = Protocol.readNextMsg(mmIn);
      String[] matchMade = Protocol.parseMatchMade(msg);
      if (matchMade == null
          || matchMade[0] == null
          || matchMade[1] == null
          || matchMade[0].isEmpty()) {
        Logger.println(Rep.FEW, "Failed to parse game server from: " + msg);
        return;
      }
      gameServ = matchMade[0];
      matchId = Long.parseLong(matchMade[1]);
      
      // let the server know we received and parsed the matchMade.
      mmOut.write(Protocol.ack().getBytes());

      // done with the matchmaker.
      mmSock.close();
    } catch (IOException e) {
      Logger.println(Rep.FEW, "Failed to read matchMade.");
      e.printStackTrace();
      return;
    }
    
    connectToGameServer(gameServ, matchId);
  }

  /**
   * Connect to the game server and start playing.
   * 
   * @return true if everyone accepted a rematch.
   */
  public boolean connectToGameServer(String gameServ, long matchId) {
    Logger.println(Rep.FEW, "Connecting to game server "+gameServ+" "+matchId);

    InputStream tcpIn = null;
    DataOutputStream tcpOut = null;

    // connect to the game server
    try {
      socket = new Socket(gameServ, Protocol.GAMESERVERTCPPORT);
      tcpIn = socket.getInputStream();
      tcpOut = new DataOutputStream(socket.getOutputStream());
    } catch (UnknownHostException e1) {
      Logger.println(Rep.FEW, "unknown host: "+ClientConfiguration.serverUrl+":"+ClientConfiguration.serverPort);
      return false;
    } catch (IOException e1) {
      Logger.println(Rep.FEW, "TCP connection to game server "+gameServ+" failed.");
      e1.printStackTrace();
      stop();
      return false;
    }



    try {
      /* Process the handshake */
      tcpOut.writeBytes(Protocol.readyForMatch(matchId, ClientConfiguration.nonhuman));

      // Read the matchid, player number, and unreliable stream id assigned to self.
      String msg = Protocol.readNextMsg(tcpIn);

      long[] matchParams = Protocol.parseMatchStarting(msg);
      if (matchParams==null) { Logger.println(Rep.FEW, "couldn't parse match params."); stop(); return false; }
      
      ClientConfiguration.matchId = matchParams[0];
      ClientConfiguration.setPlayerNo((int) matchParams[1]);
      ClientConfiguration.unreliableStreamId = (short) matchParams[2];
      
      // ack the match params
//      tcpOut.writeBytes(Protocol.ack());
//      tcpOut.flush();
      
      // Parse the arena
      // First read all bytes into a buffer.
      msg = Protocol.readNextMsg(tcpIn);
      String arenaBuf = Protocol.parseSendingArena(msg);
      if (arenaBuf == null || arenaBuf.isEmpty()) { Logger.println(Rep.FEW, "couldn't parse arena."); stop(); return false; }

      // ack the arena
//      tcpOut.writeBytes(Protocol.ack());
//      tcpOut.flush();
      
      // Now parse from the buffer.
      Arena arena = Arena.parse(new ByteArrayInputStream(arenaBuf.getBytes()));
      Arena.setCurrent(arena);
      
      Logger.println(Logger.Rep.FEW, ClientConfiguration.asString());
    } catch (IOException e1) {
      Logger.println(Rep.FEW, "handshake failed.");
      e1.printStackTrace();
      stop();
      return false;
    }
    /* End of handshake. */


    // Initialize the GUI for human players
    if (!ClientConfiguration.nonhuman) {
      // TEMP
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      // END TEMP
      
      
      GameCanvas.toGameMode();
      DrawUtils.initialize(canvas);
    }
    
    /* Run the intelligence in a separate thread so that parsing/serializing
       doesn't block thinking. */
    if (ClientConfiguration.nonhuman)
      try {
        intel = new Nonhuman();
      } catch (FileNotFoundException e2) {
        Logger.println(Rep.FEW, "couldn't instantiate nonhuman.");
        e2.printStackTrace();
        stop();
        return false;
      }
    else
      intel = new HigherBeing();

    /* Set up the UDP connection */
    if (! ClientConfiguration.tcpOnly) {
      Logger.println(Logger.Rep.FEW, "Attempting to connect using UDP (id: " + ClientConfiguration.unreliableStreamId + ").");
      try {
        uStream = new UnreliableStream(new DatagramSocket(),
            socket.getInetAddress(),
            Protocol.GAMESERVERUDPPORT,
            ClientConfiguration.unreliableStreamId);

        // This passes received game states to the intelligence.
        uStream.registerReceiver(new GameStateReceiver(intel));
        
        // connect and wait until we start getting some replies.
        uStream.connect();
      } catch (SocketException e) {
        Logger.println(Rep.FEW, "couldn't instantiate uStream.");
        e.printStackTrace();
        stop();
        return false;
      } catch (IOException e) {
        Logger.println(Rep.FEW, "couldn't connect uStream.");
        e.printStackTrace();
        stop();
        return false;
      }

      
      final long start = System.currentTimeMillis();
      while (uStream.sendSuccessRate() < 0) {
        Logger.println(Rep.FEW, "waiting for UDP reply... ");
        if (System.currentTimeMillis() - start > UnreliableStream.TIMEOUT) {
          Logger.println(Rep.FEW, "Failed to get a UDP reply. ");
          stop();
          return false;
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
    TimerTask sendTask = new SendParamsTask(intel, uStream);
    sendTimer = new Timer("send-timer");
    sendTimer.schedule(sendTask, 0, SimulationUtils.TIMESTEP);

    Logger.println(Logger.Rep.FEW, "Ready...");


    // wait for the server to tell that the game is over
    try {
      String msg = Protocol.readNextMsg(tcpIn);
      assert(Protocol.isMatchEnded(msg));
    } catch (IOException e1) {
      Logger.println(Logger.Rep.FEW, "TCP read failed before game ended.");
      e1.printStackTrace();
      stop();
      return false;
    }

    Logger.println(Logger.Rep.FEW, "Game ended.");
    
    GameCanvas.toPostGame();

    // don't request a rematch.
    boolean rematch = false;
    try {
      tcpOut.writeBytes(Protocol.requestRematch(rematch));
    } catch (IOException e) {
      Logger.println(Rep.FEW, "failed to send requestRematch.");
      e.printStackTrace();
      stop();
      return false;
    }
    
    stopGracefully();
    
    return rematch;

  }
  
  /**
   * This functions tries to stop all the client threads ASAP.
   */
  public static void stop() {
    if (GameCanvas.isInitialized()) GameCanvas.toError();
    stopCurrent();
  }
  
  /**
   * This functions tries to stop all the client threads associated
   * with the  currently running game ASAP.
   */
  public static void stopCurrent() {
    stop = true;
    
    if (uStream != null)
      uStream.disconnect();
    
    try {
      //socket.setSoLinger(false, 0); // close immediately with a RST.
      socket.close();
    } catch (IOException e) {
      Logger.println(Rep.FEW, "couldn't close TCP socket.");
      e.printStackTrace();
    }
    
    if (intel != null) intel.stop();
    
    if (sendTimer != null)
      sendTimer.cancel();
  }
  
  /**
   * This functions tries to stop all the client threads gracefully.
   */
  public static void stopGracefully() {
    stopCurrent();
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
    assert(uStream != null);
    return uStream.averageLag();
  }

  
  private static class SendParamsTask extends TimerTask {

    Intelligence intelligence;
    UnreliableStream udpOut;
    
    PlayerControlParameters last;
    double lastUdpSendSuccessRate = Double.NEGATIVE_INFINITY;
    double lastUdpReicSuccessRate = Double.NEGATIVE_INFINITY;
    
    public SendParamsTask(Intelligence intelligence, UnreliableStream udpOut) {
      this.intelligence = intelligence;
      this.udpOut = udpOut;
    }
    
    public void run() {
      
      // Send all queued control parameters.
      PlayerControlParameters[] pcp = intelligence.getNewControlParameters();
      
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      DataOutputStream dataOut = new DataOutputStream(byteOut);
        
      if (udpOut.hasDied()) {
        Logger.println(Rep.FEW, "UnreliableStream has timed out. Giving up.");
        stop();
        try {
          socket.close();
        } catch (IOException e) {
          Logger.println(Rep.FEW, "couldn't close socket.");
          e.printStackTrace();
        }
        this.cancel();
        return;
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
      
      // Flush UDP

      try {
        udpOut.send(byteOut.toByteArray());
      } catch (IOException e) {
        Logger.println(Rep.INFREQ, "Failed to send using UDP. Not giving up until timeout though.");
        e.printStackTrace();
      }
      
      final double s = udpOut.sendSuccessRate();
      final double r = udpOut.receiveSuccessRate();
      if ((s < 0.999 || r < 0.999) && (s < lastUdpSendSuccessRate || r < lastUdpReicSuccessRate)) Logger.println(Rep.INFREQ, "UDP send/receive success rate: " + s + " / " +r);
      lastUdpSendSuccessRate = s;
      lastUdpReicSuccessRate = r;
      
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

