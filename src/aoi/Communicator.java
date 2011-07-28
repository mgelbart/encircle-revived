package aoi;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
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
  
  private boolean stop = false;


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
  
  /**
   * Returns true if the player still appears to be connected.
   */
  public boolean stopped() {
    return stop;
  }
  
  /**
   * This starts to send game data to the client.
   * This is non-blocking.
   */
  public void start() {
    assert(!stop);
    assert(tcpOut != null);
    assert(!tcpSocket.isClosed());
    
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
          assert(false);
        }
        
        // Wait for the client to connect using UDP
        final long startedToWait = System.currentTimeMillis();
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
        
        // wait for the game to end
        while (sim.ended == null) {
          if (stop) return;  // or for the player to disconnect
          
          try {
            Thread.sleep(10000000);
          } catch (InterruptedException e) {
            Logger.println(Rep.FEW, "interrupted.");
          }
        }
        
        final DataOutputStream dout = new DataOutputStream(tcpOut);

        try {

          // Send the (dynamically assigned) player number to the client.
          dout.writeBytes(Protocol.matchEnded(sim.matchId));

          tcpOut.flush();
          
//          String msg=Protocol.readNextMsg(tcpIn);
//          if (!Protocol.isAck(msg)) { Logger.println(Rep.FEW, "no ack to matchEnded."); stop(); return; }
          clientState = ClientState.GAMEENDED;
          
          stopUdpSender();

        } catch (IOException ioe) {
          Logger.println(Rep.FEW, "failed to send matchEnded.");
          ioe.printStackTrace();
          assert(false);
        }
        
        // wait for the player to decide whether to rematch
        try {
          tcpSocket.setSoTimeout(200); // don't wait forever
        } catch (SocketException e) {
          Logger.println(Rep.FEW, "couldn't set SOTIMEOUT=200");
          e.printStackTrace();
        }
        
        Boolean accepted = null;
        while (System.currentTimeMillis() - sim.ended < SimulationUtils.REMATCHCONSIDERATIONTIME
               && accepted == null && sim.rematchPossible()) {
          
          String msg;
          try {
            msg=Protocol.readNextMsg(tcpIn);
          } catch (IOException ioe) {
            continue;
          }
          
          accepted = Protocol.parseRequestRematch(msg);
//          if (accepted != null)
//            try {
//              dout.writeBytes(Protocol.ack());
//            } catch (IOException e) {
//              Logger.println(Rep.FEW, "couldn't ack requestRematch");
//              e.printStackTrace();
//              return;
//            }
        }
        
        try {
          tcpSocket.setSoTimeout(0); // wait forever again (default)
        } catch (SocketException e) {
          Logger.println(Rep.FEW, "couldn't set SOTIMEOUT=0");
          e.printStackTrace();
          return;
        }
        
        // wait for the decision
        while (sim.rematchPossible() && !sim.rematchAccepted()) {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            Logger.println(Rep.FEW, "interrupted.");
          }
        }
        

        try {
          dout.writeBytes(Protocol.didOthersAcceptRematch(accepted));
        } catch (IOException e) {
          Logger.println(Rep.FEW, "couldn't send didOthersAcceptRematch "+accepted);
          e.printStackTrace();
          return;
        }
        
        // nothing left to do.
        stopTcpNegotiator();
        Logger.println(Rep.FEW, "tcpnegotiator for player "+playerNo+" finished.");
        
      }
      
    }, "tcpnegotiator-"+sim.matchId+"-"+playerNo);

    tcpNegotiatorThread.start();
  }
  
  public void sendNewState() {
    if (udpStream != null && clientState == ClientState.RECEIVEDARENA);
    udpSenderThread.interrupt();
  }
  
  public void sendGameEnded() {
    if (clientState == ClientState.RECEIVEDARENA)
      tcpNegotiatorThread.interrupt();

    stopUdpSender();
  }
  
  public void sendDidOthersAcceptRematch(boolean accepted) {

  }
  
  public void stop() {
    stop = true; 
    stopUdpSender();
    stopTcpNegotiator();
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
        tcpSocket.setSoLinger(false, 0); // close immediately with a RST.
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

    GameState g;

    while (true) {
      
      try {
        // Sleep until interruption (triggered by Simulation
        // when game state is updated);
        Thread.sleep(200);
      } catch (InterruptedException e) {
        if (stop || udpStream == null) { Logger.println(Rep.FEW, "GameStateSender stopping."); return; }

        assert(udpStream != null);

        // Serialize & send game state
        // Note: don't need locking since
        //   GameState is immutable.
        g = sim.gameState();

        if (previousTime != g.time) {
          
          // Check whether the UnreliableStream is still alive.
          if (udpStream != null && udpStream.hasDied()) {
            Logger.println(Rep.FEW, "UnreliableStream has timed out for player " + playerNo);
            stop();
            continue;
          }
          
          
          // send using UDP if available. otherwise fall back to TCP.
          synchronized (sendingLock) {  

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            
            try {
              g.serialize(bout);
            } catch (IOException e1) {
              Logger.println(Rep.FEW, "Failed to buffer the game state for player " + playerNo + " at t=" + g.time);
              Logger.println(Rep.FEW, "This should never happen (serializing to memory should never fail). Ending GameStateSender execution.");
              e1.printStackTrace();
              stop();
              return;
            }
            
            previousTime = g.time;
  
            try {
              udpStream.send(bout.toByteArray());
            } catch (IOException e1) {
              Logger.println(Rep.FEW, "Failed to send the game state for player " + playerNo + " at t=" + g.time);
              Logger.println(Rep.FEW, "The socket must be invalid. Ending GameStateSender execution.");
              e1.printStackTrace();
              stop();
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
      return true;
    }

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
