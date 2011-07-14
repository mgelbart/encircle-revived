package aoi;

import java.io.*;
import java.util.Vector;

/**
 *  A class dedicated to listening and parsing commands from
 *  a remote player.
 *
 *  Meant to be run in a separate thread. This class buffers
 *  up to CTRLBUFFERSIZE commands from the remote client
 *  and returns the most recent already effective one to
 *  the Simulation when it calls getCtrlParams().
 */
public class PlayerListener implements Runnable {

  public final static int CTRLBUFFERSIZE = 30;

  private int playerNo;
  private DataInputStream client;
//  private Simulation sim;

  private Vector<PlayerControlParameters> ctrls = new Vector<PlayerControlParameters>(CTRLBUFFERSIZE);

  public PlayerListener(int player, Simulation sim, InputStream in) {
    this.playerNo = player;
    this.client = new DataInputStream(in);

    // Never let the buffer be empty.
    ctrls.add(new PlayerControlParameters(0, 0, 0, false, false));

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }

  public synchronized void setInput(InputStream in) {
    this.client = new DataInputStream(in);
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

    while (true) {

      PlayerControlParameters prev;
      synchronized (ctrls) { prev = ctrls.lastElement(); }
      PlayerControlParameters ctrl = PlayerControlParameters.parse(client, prev);
      if (ctrl == null || ctrls.size() >= CTRLBUFFERSIZE) continue;

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

}
