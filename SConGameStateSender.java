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
    private SConSimulation game;

    private int previousTime = -1;

    public SConGameStateSender(int player, SConSimulation sim, DataOutputStream o) {
	playerNo = player;
	game = sim;
	client = o;
    }

    public synchronized void run() {

	int t;
	SConPlayerState p1State;
	SConPlayerState p2State;


	// Sleep until interruption (triggered by SConSimulation
	// when game state is updated);
	while (true) {
	    
	    try {
		wait();
	    } catch (InterruptedException e) {

		// Serialize & send game state
		
		// First copy all the data to minimize the time
		// the game state data is locked.
		//
		// We (arbitrarily) choose player1State as the object
		// to lock when reading the game state data.
		synchronized (game.player1State) {
		    t = game.simulationTime;
		    p1State = game.player1State.clone();
		    p2State = game.player2State.clone();
		}


		if (previousTime != t) {


		    try {

			// Simulation time
			client.writeBytes("t");
			client.writeInt(t);
	   
			// Player 1 status:
			p1State.serialize(client);
			
			// Player 2 status:
			p2State.serialize(client);
		

		    } catch (IOException ioe) {
			assert(false);
		    }

		}

	    }

	}

    }

}

