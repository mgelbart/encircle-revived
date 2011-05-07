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
    private DataOutputStream client;
    private Simulation sim;

    private int previousTime = -1;

    public GameStateSender(int player, Simulation sim, DataOutputStream o) {
	this.playerNo = player;
	this.sim = sim;
	this.client = o;

	assert(playerNo < 2);
	assert(playerNo >= 0);
    }

    public synchronized void run() {

	GameState g;

	// Sleep until interruption (triggered by Simulation

	// when game state is updated);
	while (true) {
	    
	    try {
		wait();
	    } catch (InterruptedException e) {

		// Serialize & send game state
		// Note: don't need locking since
		//   GameState is immutable.
		g = sim.game;

		if (previousTime != g.time) {
		    g.serialize(client);
		    previousTime = g.time;
		}

	    }

	}

    }

}

