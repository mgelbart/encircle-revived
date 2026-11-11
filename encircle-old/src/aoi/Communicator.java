package aoi;

import java.io.*;
import java.net.*;
import java.util.Vector;

import unreliablestream.UnreliableStream;
import aoi.Logger.Rep;
import aoi.Protocol.ClientState;

/**
 * This class is dedicated to communicating with the client
 * both through TCP for game management and through UDP for
 * game state and player control parameter updates.
 */
public class Communicator implements UnreliableStream.Receiver {
  private Thread udpSenderThread;
  private Thread tcpNegotiatorThread;

  private int playerNo;
  private Socket tcpSocket = null;
  private InputStream tcpIn = null;
  private OutputStream tcpOut = null;
  private UnreliableStream udpStream = null;
  private Object sendingLock = new Object();
  private final short udpId;
  private Simulation sim;
  private int previousTime = -1;

  private final static int CTRLBUFFERSIZE = 200;
  private Vector<PlayerControlParameters> ctrls = new Vector<PlayerControlParameters>(CTRLBUFFERSIZE);

  private ClientState clientState = null;

  private long lastParamsReceived;
  private boolean stop = false;
  private boolean wasStarted = false;
  private boolean isAI = false;

  public Communicator(int playerNo, Simulation sim, short udpId) {
    this.playerNo = playerNo;
    this.sim = sim;
    this.udpId = udpId;

    // Never let the buffer be empty.
    ctrls.add(new PlayerControlParameters(0, 0, sim.gameState().pStates[playerNo].ship.dir(), false, false));

    assert(this.playerNo < SimulationUtils.NUMPLAYERS);
    assert(this.playerNo >= 0);
  }

  /**
   * Sets the TCP socket used to send and receive game management
   * related commands.
   * 
   * This should be called exactly once before calling start().
   * @throws IOException 
   */
  public void setTCPSocket(Socket socket) throws IOException {
    assert(tcpSocket == null);
    this.tcpSocket = socket;
    this.tcpOut = tcpSocket.getOutputStream();
    this.tcpIn = tcpSocket.getInputStream();
  }

  /**
   * Sets the UnreliableStream used to send game states.
   */
  public void setUDPOutput(UnreliableStream out) {
    synchronized (sendingLock) {
      assert(udpId == out.getId());
      this.udpStream = out;
    }
  }

  public void setIsAI(boolean isAI) { this.isAI = isAI; }
  public boolean isAI() { return isAI; }

  /**
   * Returns true if the player still appears to be connected.
   */
  public boolean stopped() {
    return stop || System.currentTimeMillis() - lastParamsReceived
    > UnreliableStream.TIMEOUT;
  }


  /**
   * Returns true if the player still appears to be connected.
   */
  public boolean wasStarted() {
    return wasStarted;
  }

  /**
   * This starts to send game data to the client.
   * This is non-blocking.
   */
  public void start() {
    assert(!stop);
    assert(!wasStarted);
    wasStarted = true;

    // don't start if the player never connected at all.
    if (tcpOut == null) {
      stop = true;
      return;
    }

    // initialize
    lastParamsReceived = System.currentTimeMillis();

    udpSenderThread = new Thread(new Runnable() {

      @Override
      public void run() {
        sendGameStates();
      }

    }, "udpsender-"+sim.matchId+"-"+playerNo);

    tcpNegotiatorThread = new Thread(new Runnable() {

      @Override
      public void run() {
        Logger.println(Rep.FEW, "starting tcpnegotiator for player "+playerNo);

        // First shake hands:
        try {
          final DataOutputStream dout = new DataOutputStream(tcpOut);
          String msg;

          // Send the (dynamically assigned) player number to the client.
          dout.writeBytes(Protocol.matchStarting(sim.matchId, playerNo, udpId));

          //          msg=Protocol.readNextMsg(tcpIn);
          //          if (!Protocol.isAck(msg)) { Logger.println(Rep.FEW, "no ack to matchStarting."); stop(); return; }
          clientState = ClientState.READYFORMATCH;

          // Send the serialized arena as one line.
          final String arenastr = sim.arena().toString();
          dout.writeBytes(Protocol.sendingArena(arenastr));

          tcpOut.flush();

          //          msg=Protocol.readNextMsg(tcpIn);
          //          if (!Protocol.isAck(msg)) { Logger.println(Rep.FEW, "no ack to arena."); stop(); return; }
          clientState = ClientState.RECEIVEDARENA;

        } catch (IOException ioe) {
          Logger.println(Rep.FEW, "hand shake failed.");
          ioe.printStackTrace();
          stop();
          return;
        }

        // Wait for the client to connect using UDP
        final long startedToWait = System.currentTimeMillis();
        lastParamsReceived = System.currentTimeMillis();
        while (udpStream == null){

          if (System.currentTimeMillis() - startedToWait < UnreliableStream.TIMEOUT)
            try {
              Thread.sleep(300);
            } catch (InterruptedException e) {
              Logger.println(Rep.FEW, "interrupted");
              continue;
            }
            else {
              Logger.println(Rep.FEW, "Client didn't form UDP connection.");
              stop();
              return;
            }

        }

        udpSenderThread.start();
        lastParamsReceived = System.currentTimeMillis();

        // wait for the game to end
        while (sim.ended == null) {
          if (stop || udpStream.hasDied() || tcpSocket.isClosed()) {
            // or for the client to disconnect
            Logger.println(Rep.FEW, "Client disconnected before game ended.");
            stop();
            return;
          }

          try {
            Thread.sleep(50000);
          } catch (InterruptedException e) {
            Logger.println(Rep.FEW, "interrupted.");
          }
        }

        final DataOutputStream dout = new DataOutputStream(tcpOut);

        try {

          // Send winner to the client.
          dout.writeBytes(Protocol.matchEnded(sim.matchWinner));

          tcpOut.flush();

          //          String msg=Protocol.readNextMsg(tcpIn);
          //          if (!Protocol.isAck(msg)) { Logger.println(Rep.FEW, "no ack to matchEnded."); stop(); return; }
          clientState = ClientState.GAMEENDED;

          Logger.println(Rep.FEW, "Game ended so stopping udpsender.");
          stopUdpSender();

        } catch (IOException ioe) {
          Logger.println(Rep.FEW, "failed to send matchEnded.");
          ioe.printStackTrace();
          assert(false);
          return;
        }

        // wait for the player to decide whether to rematch
        try {
          tcpSocket.setSoTimeout(1000); // don't wait forever
        } catch (SocketException e) {
          Logger.println(Rep.FEW, "couldn't set SOTIMEOUT=500");
          e.printStackTrace();
          return;
        }

        Boolean wantsToRematch = null;
        while (System.currentTimeMillis() - sim.ended < SimulationUtils.REMATCHCONSIDERATIONTIME
            && wantsToRematch == null && sim.rematchPossible()) {

          String msg;
          try {
            try {
              msg=Protocol.readNextMsg(tcpIn);
            } catch (SocketTimeoutException ioe) {
              continue;
            }
          } catch (IOException ioe) {
            // the client disconnected without closing the stream with a FIN
            Logger.println(Rep.FEW, "Client disconnected without sending rematchRequest.");
            stop();
            return;
          }

          if (msg == null) {
            // this happens if the client disconnects gracefully (with a FIN).
            stop();
            return;
          }

          wantsToRematch = Protocol.parseRequestRematch(msg);
          //          if (accepted != null)
          //            try {
          //              dout.writeBytes(Protocol.ack());
          //            } catch (IOException e) {
          //              Logger.println(Rep.FEW, "couldn't ack requestRematch");
          //              e.printStackTrace();
          //              return;
          //            }
        }
        sim.wantToRematch[playerNo] = wantsToRematch;

        try {
          tcpSocket.setSoTimeout(0); // wait forever again (default)
        } catch (SocketException e) {
          Logger.println(Rep.FEW, "couldn't set SOTIMEOUT=0");
          e.printStackTrace();
          return;
        }

        // wait for the decision
        while (sim.rematchAccepted() == null) {
          try { Thread.sleep(300); }
          catch (InterruptedException e) { continue; }
        }

        try {
          dout.writeBytes(Protocol.didOthersAcceptRematch(sim.rematchAccepted()));
        } catch (IOException e) {
          Logger.println(Rep.FEW, "couldn't send didOthersAcceptRematch "+sim.rematchAccepted());
          e.printStackTrace();
          return;
        }

        // nothing left to do.
        stopTcpNegotiator();
        stopUdpSender();
        Logger.println(Rep.FEW, "tcpnegotiator for player "+playerNo+" terminated normally.");

      }

    }, "tcpnegotiator-"+sim.matchId+"-"+playerNo);

    tcpNegotiatorThread.start();
  }

  public void sendNewState() {
    if (udpStream != null && clientState == ClientState.RECEIVEDARENA)
      udpSenderThread.interrupt();
  }

  public void sendGameEnded() {
    tcpNegotiatorThread.interrupt();

    stopUdpSender();
  }

  public void stop() {
    stop = true; 
    stopUdpSender();
    stopTcpNegotiator();
    if (sim != null)
      sim.wantToRematch[playerNo] = false;
  }

  private void stopUdpSender() {
    if (udpStream != null) {
      udpStream.disconnect();
      udpSenderThread.interrupt();
    }
  }

  private void stopTcpNegotiator() {
    if (tcpSocket != null && !tcpSocket.isClosed()) {
      try {
        //tcpSocket.setSoLinger(false, 0); // close immediately with a RST.
        tcpSocket.close();
      } catch (IOException e) {
        Logger.println(Rep.FEW, "closing socket failed.");
        e.printStackTrace();
      }
    }
    if (tcpNegotiatorThread != null) tcpNegotiatorThread.interrupt();
  }

  /**
   * This methods sends game states in an infinite loop
   * using the UnreliableStream.
   */
  private void sendGameStates() {
    if (tcpSocket == null) return; // the client never showed up

    GameState g;

    while (true) {

      try {
        // Sleep until interruption (triggered by Simulation
        // when game state is updated);
        Thread.sleep(200);
      } catch (InterruptedException e) {
        if (stop || udpStream == null || udpStream.hasDied()) {
          Logger.println(Rep.FEW, "GameStateSender stopping.");
          if (sim.ended == null)
            // this indicates that the UDP connection died before
            // the game ended.
            // therefore also stop tcpnegotiator.
            stop();
          return;
        }

        assert(udpStream != null);

        // Serialize & send game state
        // Note: don't need locking since
        //   GameState is immutable.
        g = sim.gameState();

        if (previousTime != g.time) {

          // send using UDP if available. otherwise fall back to TCP.
          synchronized (sendingLock) {  

            previousTime = g.time;

            try {
              udpStream.send(g.toBytes());
            } catch (IOException e1) {
              Logger.println(Rep.FEW, "Failed to send the game state for player " + playerNo + " at t=" + g.time);
              Logger.println(Rep.FEW, "uStream must be invalid. Ending GameStateSender execution.");
              e1.printStackTrace();
              if (clientState != ClientState.GAMEENDED) stop(); // also stop tcpnegotiator
              return;
            }

          }

        }

      }

    }

  }


  /**
   * This is called if command parameters were received through an unreliable stream (UDP).
   */
  @Override
  public boolean received(byte[] data, UnreliableStream src) {
    final ByteArrayInputStream bin = new ByteArrayInputStream(data);
    final DataInputStream din = new DataInputStream(bin);

    while (bin.available() > 0) {
      //      Logger.println(RepetitionRate.FREQUENT, "trying to read (UDP):");
      if(!readFromStream(din)) return false;
    }

    return true; // all reads succeeded
  }

  private boolean readFromStream(DataInputStream din) {

    PlayerControlParameters prev;
    synchronized (ctrls) { prev = ctrls.lastElement(); }

    PlayerControlParameters ctrl;
    try {
      ctrl = PlayerControlParameters.parse(din, prev);
    } catch (IOException e) {
      Logger.println(Rep.FEW, "Failed to parse command parameters from player " + playerNo);
      e.printStackTrace();
      return false;
    }


    if (ctrl == null || ctrls.size() >= CTRLBUFFERSIZE) {
      Logger.println(Rep.FEW, "too many controls buffered for player " + playerNo);
      stop();
      return true;
    }

    lastParamsReceived = System.currentTimeMillis();

    // Keep ctrls ordered by effectiveOn.
    synchronized (ctrls) {
      if (prev.effectiveOn != ctrl.effectiveOn)
        ctrls.add(ctrl);
      else
        // this special case is important when
        // the game hasn't started but a client
        // keeps transmitting the first command.
        ctrls.set(ctrls.indexOf(prev), ctrl);
      java.util.Collections.sort(ctrls);
    }


    //Logger.println(Logger.RepetitionRate.FREQUENT, "Received from #");
    //Logger.println(Logger.RepetitionRate.FREQUENT, playerNo);
    //Logger.println(Logger.RepetitionRate.FREQUENT, " (queue size: ");
    //Logger.println(Logger.RepetitionRate.FREQUENT, ctrls.size());
    //Logger.println(Logger.RepetitionRate.FREQUENT, ")");
    //Logger.println(Logger.RepetitionRate.FREQUENT, ctrl.toString());

    return true;
  }

  // Returns the control parameters for the simulation step time...time+1.
  public PlayerControlParameters getCtrlParams(int time) {

    synchronized (ctrls) {
      // Find the active command and prune old ones:
      // (never empty ctrls entirely though)
      while (ctrls.size() > 1 && ctrls.get(1).effectiveOn - time <= 0)
        ctrls.remove(0);

      //      if (ctrls.get(0).effectiveOn - time < -5) {
      //        Logger.println(Logger.RepetitionRate.INFREQUENT, "#" + playerNo + " Requested - retrieved time: " + (ctrls.get(0).effectiveOn - time));
      //        Logger.println(Logger.RepetitionRate.INFREQUENT, "Player " + playerNo + " has " + ctrls.size() + " commands in queue.");
      //      }

      return ctrls.get(0);
    }

  }
}
