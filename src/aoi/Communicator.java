package aoi;

import java.io.*;
import java.net.Socket;
import java.util.Vector;

import unreliablestream.UnreliableStream;
import aoi.Logger.Rep;

/**
 * This class is dedicated to communicating with the client
 * both through TCP for game management and through UDP for
 * game state and player control parameter updates.
 */
public class Communicator implements UnreliableStream.Receiver {
  private Thread udpSenderThread;
  private Thread tcpListenerThread;
  
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
  public boolean isAlive() {
    return !stop && udpStream != null && udpStream.isAlive();
  }
  
  public void start() {
    
    // First shake hands:
    try {
      DataOutputStream ds = new DataOutputStream(tcpOut);

      // Send the (dynamically assigned) player number to the client.
      ds.writeBytes("p");
      ds.writeInt(playerNo);

      // Send the (dynamically assigned) UDP id (UnreliableStream id).
      ds.writeBytes("u");
      ds.writeShort(udpId);
      
      // Send the serialized arena.
      String arena = sim.arena().toString();
      ds.writeBytes("a");
      ds.writeInt(arena.length());
      ds.writeBytes(arena);

      tcpOut.flush();

    } catch (IOException ioe) {
      Logger.println(Logger.Rep.INFREQ, ioe.toString());
      assert(false);
    }
    
    udpSenderThread = new Thread(new Runnable() {

      @Override
      public void run() {
        sendGameStates();
      }
      
    }, "udpsender-"+sim.matchId+"-"+playerNo);
    
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
  }
  
  public void sendNewState() {
    udpSenderThread.interrupt();
  }
  
  public void stop() {
    stop = true; 
    if (udpStream != null) {
      udpStream.disconnect();
    }
  }

  /**
   * This methods sends game states in an infinite loop
   * using the UnreliableStream.
   */
  private void sendGameStates() {

    GameState g;

    // Sleep until interruption (triggered by Simulation
    // when game state is updated);
    while (true) {
      
      try {
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
