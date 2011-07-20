package aoi;

import java.io.*;
import java.util.Vector;

import aoi.Logger.RepetitionRate;
import unreliablestream.*;

/**
 *  A class dedicated to listening and parsing commands from
 *  a remote player.
 *
 *  Meant to be run in a separate thread. This class buffers
 *  up to CTRLBUFFERSIZE commands from the remote client
 *  and returns the most recent already effective one to
 *  the Simulation when it calls getCtrlParams().
 */
public class PlayerListener implements Runnable, UnreliableStream.Receiver {

  public final static int CTRLBUFFERSIZE = 200;

  private int playerNo;
  private DataInputStream tcpStream;
  
  private GameStateSender sender;
  private boolean udpEnabled = false;

  private Vector<PlayerControlParameters> ctrls = new Vector<PlayerControlParameters>(CTRLBUFFERSIZE);

  public PlayerListener(int player, Simulation sim, InputStream in, GameStateSender sender) {
    this.playerNo = player;
    this.tcpStream = new DataInputStream(in);
    this.sender = sender;

    // Never let the buffer be empty.
    ctrls.add(new PlayerControlParameters(0, 0, sim.gameState().pStates[player].ship.dir(), false, false));

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }

  public synchronized void setTCPStream(InputStream in) {
    this.tcpStream = new DataInputStream(in);
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


  public void run() {

    // TODO: should die gracefully
    while (true) readFromStream(tcpStream);

  }

  /**
   * This is called if command parameters were receive through an unreliable stream (UDP).
   */
  @Override
  public boolean received(byte[] data, UnreliableStream src) {
    final ByteArrayInputStream bin = new ByteArrayInputStream(data);
    final DataInputStream din = new DataInputStream(bin);
    
    // Switch to sending the game state via UDP as soon as the a command is received from
    // the client via UDP. this needs to be done only once.
    if (!udpEnabled) { sender.setUDPOutput(src); udpEnabled = true; }
    
    while (bin.available() > 0) readFromStream(din);

    return true;
  }

  private void readFromStream(DataInputStream din) {
    // TODO: this needs to be behave well even if the client send garbage.
    
    PlayerControlParameters prev;
    synchronized (ctrls) { prev = ctrls.lastElement(); }
    PlayerControlParameters ctrl = PlayerControlParameters.parse(tcpStream, prev);
    if (ctrl == null || ctrls.size() >= CTRLBUFFERSIZE) {
      Logger.println(RepetitionRate.FEW, "too many controls buffered for player " + playerNo);
      return;
    }

    // Keep ctrls ordered by effectiveOn.
    synchronized (ctrls) {
      ctrls.add(ctrl);
      java.util.Collections.sort(ctrls);
    }


    //Logger.println(Logger.RepetitionRate.FREQUENT, "Received from #");
    //Logger.println(Logger.RepetitionRate.FREQUENT, playerNo);
    //Logger.println(Logger.RepetitionRate.FREQUENT, " (queue size: ");
    //Logger.println(Logger.RepetitionRate.FREQUENT, ctrls.size());
    //Logger.println(Logger.RepetitionRate.FREQUENT, ")");
    //Logger.println(Logger.RepetitionRate.FREQUENT, ctrl.toString());
    
  }
  
}
