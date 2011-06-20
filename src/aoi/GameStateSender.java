package aoi;

import java.io.*;

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
  private BufferedOutputStream stream; // Add manual buffering since we set TCP_NODELAY
  private Simulation sim;

  private int previousTime = -1;

  public GameStateSender(int player, Simulation sim, OutputStream o) {
    this.playerNo = player;
    this.sim = sim;
    this.stream = new BufferedOutputStream(o);

    assert(playerNo < SimulationUtils.NUMPLAYERS);
    assert(playerNo >= 0);
  }

  public synchronized void setOutput(OutputStream out) {
    this.stream = new BufferedOutputStream(out);
  }

  public synchronized void run() {

    GameState g;

    // First shake hands:
    try {
      DataOutputStream ds = new DataOutputStream(stream);

      // Send the (dynamically assigned) player number to the client.
      ds.writeBytes("p");
      ds.writeInt(playerNo);

      // Send the serialized arena.
      String arena = sim.arena().toString();
      ds.writeBytes("a");
      ds.writeInt(arena.length());
      ds.writeBytes(arena);

      stream.flush();

    } catch (IOException ioe) {
      System.err.println(ioe.toString());
      assert(false);
    }


    // Sleep until interruption (triggered by Simulation
    // when game state is updated);
    while (true) {
	    
      try {
        wait();
      } catch (InterruptedException e) {

        assert(stream != null);

        // Serialize & send game state
        // Note: don't need locking since
        //   GameState is immutable.
        g = sim.gameState();

        if (previousTime != g.time) {
          g.serialize(stream);
          previousTime = g.time;

          try { stream.flush(); }
          catch (Exception ex) { System.err.println(ex); assert(false); }
        }

      }

    }

  }

}
