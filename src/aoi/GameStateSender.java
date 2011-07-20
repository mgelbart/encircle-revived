package aoi;

import java.io.*;

import unreliablestream.*;

/**
 * A class dedicated to serializing and sending the game state
 * to a remote player.
 *
 * Meant to be run in a separate thread. The thread will sleep
 * immediately after sending the state until next interruption
 * (by Simulation when game state changes).
 * 
 */
public class GameStateSender implements Runnable {

  private int playerNo;
  private BufferedOutputStream tcpStream; // Add manual buffering since we set TCP_NODELAY
  private UnreliableStream udpStream = null;
  private Object sendingLock = new Object();
  private final short udpId;
  private Simulation sim;

  private int previousTime = -1;

  public GameStateSender(int player, Simulation sim, OutputStream o, short udpId) {
    this.playerNo = player;
    this.sim = sim;
    this.tcpStream = new BufferedOutputStream(o);
    this.udpId = udpId;

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }

  public void setTCPOutput(OutputStream out) {
    this.tcpStream = new BufferedOutputStream(out);
  }

  public void setUDPOutput(UnreliableStream out) {
    synchronized (sendingLock) {
      assert(udpId == udpStream.getId());
      this.udpStream = out;
    }
  }

  public void run() {

    GameState g;

    // First shake hands:
    try {
      DataOutputStream ds = new DataOutputStream(tcpStream);

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

      tcpStream.flush();

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.INFREQUENT, ioe.toString());
      assert(false);
    }


    // Sleep until interruption (triggered by Simulation
    // when game state is updated);
    while (true) {
	    
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {

        assert(tcpStream != null || udpStream != null);

        // Serialize & send game state
        // Note: don't need locking since
        //   GameState is immutable.
        g = sim.gameState();

        if (previousTime != g.time) {
          
          // send using UDP if available. otherwise fall back to TCP.
          synchronized (sendingLock) {
            
            ByteArrayOutputStream bout; // used only with UDP
            OutputStream out;
            if (udpStream != null) {
              bout = new ByteArrayOutputStream();
              out = bout;
            } else {
              bout = null;
              out = tcpStream;
            }
            
            g.serialize(out);
            previousTime = g.time;
  
            if (udpStream != null) {
              udpStream.send(bout.toByteArray());
            } else {
              try { tcpStream.flush(); }
              catch (Exception ex) { Logger.println(Logger.RepetitionRate.INFREQUENT, ex.toString()); assert(false); }
            }

          }
          
        }

      }

    }

  }

}
