package aoi;

/*
  A class dedicated to serializing and sending the game state
  to a remote player.

  Meant to be run in a separate thread. The thread will sleep
  immediately after sending the state until next interruption
  (by Simulation when game state changes).

 */

import java.io.*;


public class GameStateSender implements Runnable {

    private int playerNo;
    private OutputStream client;
    private Simulation sim;

    private int previousTime = -1;

    public GameStateSender(int player, Simulation sim, OutputStream o) {
	this.playerNo = player;
	this.sim = sim;
	this.client = o;

	assert(playerNo < Simulation.NUMPLAYERS);
	assert(playerNo >= 0);
    }

    public synchronized void setOutput(OutputStream out) {
	this.client = out;
    }

    public synchronized void run() {

	GameState g;

	// First shake hands:
	try {
	    DataOutputStream ds = new DataOutputStream(client);
	    ds.writeBytes("p");
	    ds.writeInt(playerNo);
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

		assert(client != null);

		// Serialize & send game state
		// Note: don't need locking since
		//   GameState is immutable.
		g = sim.gameState();

		if (previousTime != g.time) {
		    g.serialize(client);
		    previousTime = g.time;
		}

	    }

	}

    }

}

