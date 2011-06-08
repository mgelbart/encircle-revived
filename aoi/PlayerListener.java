package aoi;

import java.io.*;
import java.util.Vector;

/**
 *  A class dedicated to listening and parsing commands from
 *  a remote player.
 *
 *  Meant to be run in a separate thread. Once the data for a new
 *  PlayerControlParameters object is parsed it is passed on
 *  to the simulation via the shared variable game.player1/2CtrlParams.
*/
public class PlayerListener implements Runnable {

  public final static int CTRLBUFFERSIZE = 30;

  private int playerNo;
  private DataInputStream client;
  private Simulation sim;

  private Vector<PlayerControlParameters> ctrls = new Vector<PlayerControlParameters>(CTRLBUFFERSIZE);

  public PlayerListener(int player, Simulation sim, InputStream in) {
    this.playerNo = player;
    this.client = new DataInputStream(in);
    this.sim = sim;

    // Never let the buffer be empty.
    ctrls.add(new PlayerControlParameters(0, 0, 0, 0, 0));

    assert(playerNo < Simulation.NUMPLAYERS);
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
      while (ctrls.size() > 1 && ctrls.get(1).effectiveOn - time <= 0) // TODO: check that this handles time wrapping
        ctrls.remove(0);

      //System.err.println("Player " + playerNo + " has " + ctrls.size() + " commands in queue.");

      return ctrls.get(0);
    }

  }

  public void run() {

    Byte cmd = 0;
    int j;
    PlayerControlParameters ctrl;

    while (true) {

      ctrl = PlayerControlParameters.parse(client);
      if (ctrl == null || ctrls.size() >= CTRLBUFFERSIZE) continue;

      // Keep ctrls ordered by effectiveOn.
      j = 0;
      synchronized (ctrls) {
        while (j < ctrls.size() && ctrl.effectiveOn - ctrls.get(j).effectiveOn > 0) // TODO: check that this handles time wrapping
          ++j;

        ctrls.insertElementAt(ctrl, j);
      }

      System.out.print("Received from #");
      System.out.println(playerNo);
      System.out.println(ctrl.toString());

    }

  }

}
