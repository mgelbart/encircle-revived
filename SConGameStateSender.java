/*
  A class dedicated to serializing and sending the game state
  to a remote player.

  Meant to be run in a separate thread. The thread will sleep
  immediately after sending the state until next interruption
  (by SConSimulation when game state changes).

 */

import java.io.*;


class SConGameStateSender implements Runnable {

    private int playerNo;
    private DataOutputStream client;
    private SConSimulation sim;

    private int previousTime = -1;

    public SConGameStateSender(int player, SConSimulation sim, DataOutputStream o) {
	this.playerNo = player;
	this.sim = sim;
	this.client = o;

	assert(playerNo < 2);
	assert(playerNo >= 0);
    }

    public synchronized void run() {

	// Sleep until interruption (triggered by SConSimulation
	// when game state is updated);
	while (true) {
	    
	    try {
		wait();
	    } catch (InterruptedException e) {

		// Serialize & send game state
		// Note: don't need locking since
		//   SConGameState is immutable.
		sim.game.serialize(client);

	    }

	}

    }

}

